"""Kafka consumer for inbound order events.

Runs a confluent-kafka consumer loop in a background thread, consuming
from the ``order-events`` topic. For each message:

1. Extracts OTel trace context from Kafka headers (traceparent, baggage)
   to establish distributed trace continuity.
2. Extracts domain identifiers (order.id, customer.id, cart.id) from
   Kafka headers and opens a DomainContext for structured logging.
3. Deserializes the message value into an InboundOrderEvent subclass.
4. Reads customer.tier from OTel baggage (propagated by the order service).
5. Delegates to SendNotificationUseCase.

The consumer group is ``notification-service-v2`` (not v1 -- matches
the Java version's recovery from the wire contract fix).

Module 4 deliberate bug: the ``cp-4-broken`` branch removes the
``get_baggage("customer.tier")`` call, replacing it with a hardcoded
"unknown". The consumer still works, but the per-tier metric breakdown
silently collapses to one bucket.
"""

from __future__ import annotations

import threading
from typing import Any

import structlog
from confluent_kafka import Consumer, KafkaError, KafkaException
from opentelemetry import context as otel_context
from opentelemetry import trace
from opentelemetry.propagate import extract

from application.send_notification import SendNotificationUseCase
from domain.events import deserialize_event
from domain.identifiers import NotificationContextKey
from shared_observability import DomainContext, extract_domain_identifiers, get_baggage

logger = structlog.get_logger()
tracer = trace.get_tracer(__name__)


class OrderEventConsumer:
    """Background Kafka consumer for order events.

    Runs a poll loop in a daemon thread. The thread is started via
    ``start()`` and stopped via ``stop()``, which signals the loop to
    exit and waits for the thread to finish.
    """

    def __init__(self, bootstrap_servers: str, topic: str = "order-events") -> None:
        self._bootstrap_servers = bootstrap_servers
        self._topic = topic
        self._use_case = SendNotificationUseCase()
        self._stop_event = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        """Start the consumer loop in a background daemon thread."""
        if self._thread is not None:
            return
        self._stop_event.clear()
        self._thread = threading.Thread(
            target=self._run,
            name="notification-kafka-consumer",
            daemon=True,
        )
        self._thread.start()
        logger.info(
            "Kafka consumer started",
            topic=self._topic,
            bootstrap_servers=self._bootstrap_servers,
        )

    def stop(self) -> None:
        """Signal the consumer loop to stop and wait for the thread."""
        self._stop_event.set()
        if self._thread is not None:
            self._thread.join(timeout=10.0)
            self._thread = None
        logger.info("Kafka consumer stopped")

    # ------------------------------------------------------------------
    # Consumer loop
    # ------------------------------------------------------------------

    def _run(self) -> None:
        """Main consumer loop -- runs in the background thread."""
        consumer = Consumer(
            {
                "bootstrap.servers": self._bootstrap_servers,
                "group.id": "notification-service-v2",
                "auto.offset.reset": "earliest",
                "enable.auto.commit": True,
            }
        )
        consumer.subscribe([self._topic])

        try:
            while not self._stop_event.is_set():
                msg = consumer.poll(timeout=1.0)
                if msg is None:
                    continue
                if msg.error():
                    if msg.error().code() == KafkaError._PARTITION_EOF:
                        continue
                    logger.error("Kafka consumer error", error=str(msg.error()))
                    continue

                self._handle_message(msg)
        except KafkaException as exc:
            logger.error("Kafka consumer fatal error", error=str(exc))
        finally:
            consumer.close()

    def _handle_message(self, msg: Any) -> None:
        """Process a single Kafka message with full OTel context propagation."""
        raw_headers = msg.headers() or []

        # Convert Kafka headers to a dict for OTel propagator extraction.
        # Headers are list[tuple[str, bytes]]; propagator expects str values.
        headers_dict: dict[str, str] = {}
        for key, value in raw_headers:
            if value is not None:
                headers_dict[key] = value.decode("utf-8")

        # Extract OTel context (traceparent + baggage) from Kafka headers
        # to establish distributed trace continuity with the producer.
        extracted_ctx = extract(headers_dict)
        token = otel_context.attach(extracted_ctx)

        try:
            with tracer.start_as_current_span("Notification.Consume") as span:
                # Extract domain identifiers from Kafka headers and open
                # a DomainContext for structured logging.
                domain_ids = extract_domain_identifiers(raw_headers)
                identifiers = _restore_identifiers(domain_ids)

                with DomainContext(*identifiers):
                    # Read customer.tier from OTel baggage. The order service
                    # propagates tier via the W3C 'baggage' header; our
                    # extract() call above made it available in the current
                    # context.
                    #
                    # Module 4: this line is the bug magnet. cp-4-broken
                    # replaces the get with a hardcoded "unknown".
                    customer_tier = get_baggage("customer.tier") or "unknown"

                    try:
                        event = deserialize_event(msg.value().decode("utf-8"))
                    except (ValueError, Exception) as exc:
                        logger.error(
                            "Failed to deserialize order event",
                            error=str(exc),
                            raw_value=msg.value()[:200] if msg.value() else None,
                        )
                        return

                    span.set_attribute("event.type", type(event).__name__)
                    span.set_attribute("order.id", event.order_id)
                    span.set_attribute("customer.tier", customer_tier)

                    try:
                        self._use_case.send(event, customer_tier)
                    except RuntimeError as exc:
                        logger.error(
                            "Failed to send notification",
                            event_type=type(event).__name__,
                            error=str(exc),
                        )
        finally:
            otel_context.detach(token)


# --------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------


def _restore_identifiers(domain_ids: dict[str, str]) -> list[Any]:
    """Wrap extracted domain identifier strings in NotificationContextKey instances.

    Order publishes under order.id / customer.id / cart.id; we wrap each
    present value in our own enum's ``of()`` method.
    """
    out = []
    _KEY_MAP = {
        "order.id": NotificationContextKey.ORDER_ID,
        "customer.id": NotificationContextKey.CUSTOMER_ID,
        "cart.id": NotificationContextKey.CART_ID,
    }
    for wire_key, ctx_key in _KEY_MAP.items():
        value = domain_ids.get(wire_key)
        if value:
            out.append(ctx_key.of(value))
    return out
