"""Kafka-backed implementation of OrderEventPublisher.

Two responsibilities:

1. **Explicit header propagation** -- each event publication writes
   Order's domain identifiers into the Kafka record's headers via
   ``inject_domain_identifiers``. We do this explicitly per identifier
   rather than via a "copy everything in MDC" convenience.

2. **Domain-named span** -- the publish operation runs inside an
   ``Order.Events.Publish`` span with attributes identifying the event
   type and order.
"""

from __future__ import annotations

import json
import os
from typing import Any

import structlog
from confluent_kafka import Producer
from opentelemetry import trace
from opentelemetry.propagate import inject

from domain.events import (
    DomainEvent,
    OrderCancelled,
    OrderConfirmed,
    OrderPlaced,
)
from domain.identifiers import OrderContextKey
from shared_observability import inject_domain_identifiers

logger = structlog.get_logger()
tracer = trace.get_tracer("order-service")

_TOPIC = "order-events"


class OrderEventKafkaPublisher:
    """Publishes Order domain events to Kafka.

    Events are serialized as JSON with an ``eventType`` discriminator.
    Domain identifiers and OTel trace context are injected into Kafka
    headers for cross-service correlation.
    """

    def __init__(self, producer: Producer | None = None) -> None:
        if producer is not None:
            self._producer = producer
        else:
            bootstrap = os.environ.get(
                "KAFKA_BOOTSTRAP_SERVERS", "kafka:9092"
            )
            self._producer = Producer({"bootstrap.servers": bootstrap})

    def publish(self, event: DomainEvent) -> None:
        with tracer.start_as_current_span("Order.Events.Publish") as span:
            span.set_attribute("event.type", event.event_type)
            span.set_attribute("order.id", event.order_id)

            # Build domain identifier headers based on event type
            identifiers = [OrderContextKey.ORDER_ID.of(event.order_id)]

            if isinstance(event, OrderPlaced):
                identifiers.append(
                    OrderContextKey.CUSTOMER_ID.of(event.customer_id)
                )
                identifiers.append(
                    OrderContextKey.CART_ID.of(event.cart_id)
                )
            elif isinstance(event, OrderConfirmed):
                identifiers.append(
                    OrderContextKey.CUSTOMER_ID.of(event.customer_id)
                )
            elif isinstance(event, OrderCancelled):
                identifiers.append(
                    OrderContextKey.CUSTOMER_ID.of(event.customer_id)
                )

            # Convert domain identifiers to Kafka header format
            kafka_headers = inject_domain_identifiers(identifiers)

            # Also inject OTel trace context (traceparent, baggage)
            otel_headers: dict[str, str] = {}
            inject(otel_headers)
            for key, value in otel_headers.items():
                kafka_headers.append((key, value.encode("utf-8")))

            # Serialize the event as JSON with eventType discriminator
            event_json = json.dumps(event.to_dict()).encode("utf-8")

            self._producer.produce(
                topic=_TOPIC,
                key=event.order_id.encode("utf-8"),
                value=event_json,
                headers=kafka_headers,
                on_delivery=self._on_delivery,
            )
            self._producer.poll(0)

            logger.info(
                "Event published to Kafka",
                event_type=event.event_type,
                topic=_TOPIC,
            )

    @staticmethod
    def _on_delivery(err: Any, msg: Any) -> None:
        """Kafka delivery callback for logging."""
        if err is not None:
            logger.error(
                "Kafka delivery failed",
                error=str(err),
                topic=msg.topic() if msg else "unknown",
            )

    def flush(self, timeout: float = 5.0) -> None:
        """Flush any buffered messages. Call on shutdown."""
        self._producer.flush(timeout)
