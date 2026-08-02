"""Send Notification use case.

Pattern-matches the inbound event to a notification kind, builds a
Notification, "sends" it (logs -- no real email/SMS in the workshop),
and records a per-tier-per-kind metric.

Module 4 reference: the ``customer_tier`` parameter is what the
deliberate bug breaks. On the broken branch, the consumer fails to
read the tier from baggage and passes "unknown" for every event --
so the dashboard's "notifications by tier" panel goes silent except
for one bucket. The bug is in the consumer entry point, not here,
but the symptom shows up here.
"""

from __future__ import annotations

import structlog
from opentelemetry import metrics, trace

from domain.events import (
    InboundOrderCancelled,
    InboundOrderConfirmed,
    InboundOrderEvent,
    InboundOrderPlaced,
)
from domain.identifiers import NotificationContextKey
from domain.models import Notification, NotificationKind
from shared_observability import DomainContext

logger = structlog.get_logger()
tracer = trace.get_tracer(__name__)
meter = metrics.get_meter(__name__)

notifications_counter = meter.create_counter(
    "notifications_sent_total",
    description="Total notifications sent by kind and customer tier",
)

_CHANNEL = "email"


class SendNotificationUseCase:
    """Decides notification kind from the inbound event and 'sends' it.

    Sending is simulated (logged) -- no real email/SMS in the workshop.
    """

    @tracer.start_as_current_span("Notification.Send")
    def send(self, event: InboundOrderEvent, customer_tier: str) -> Notification:
        span = trace.get_current_span()

        kind = _pick_kind(event)
        customer_id = _customer_id_for(event)

        notification = Notification.send(
            kind=kind,
            order_id=event.order_id,
            customer_id=customer_id,
            customer_tier=customer_tier,
            channel=_CHANNEL,
        )

        # Update structured logging context with the freshly minted
        # notification id so any log lines carry the right correlation id.
        with DomainContext(
            NotificationContextKey.NOTIFICATION_ID.of(notification.id.value),
        ):
            span.set_attribute("notification.id", notification.id.value)
            span.set_attribute("notification.kind", kind.value)
            span.set_attribute("notification.channel", _CHANNEL)
            span.set_attribute("customer.tier", customer_tier)
            span.set_attribute("order.id", event.order_id)
            span.set_attribute("customer.id", customer_id)

            # Annotations specific to confirmed events.
            if isinstance(event, InboundOrderConfirmed):
                span.set_attribute("reservation.id", event.reservation_id)
                span.set_attribute("authorization.id", event.authorization_id)
                span.set_attribute("shipment.id", event.shipment_id)
            if isinstance(event, InboundOrderCancelled):
                span.set_attribute("order.failed_at", event.failed_at)

            logger.info(
                "Sent notification",
                kind=kind.value,
                notification_id=notification.id.value,
                order_id=event.order_id,
                customer_tier=customer_tier,
            )

            notifications_counter.add(
                1,
                {"kind": kind.value, "tier": customer_tier},
            )

            return notification


def _pick_kind(event: InboundOrderEvent) -> NotificationKind:
    """Map the inbound event to the appropriate notification kind."""
    if isinstance(event, InboundOrderPlaced):
        return NotificationKind.PLACED_ACK
    if isinstance(event, InboundOrderConfirmed):
        return NotificationKind.CONFIRMATION
    if isinstance(event, InboundOrderCancelled):
        return NotificationKind.CANCELLATION
    # Defensive -- should never reach here if deserialization is correct.
    raise ValueError(f"Unhandled event type: {type(event).__name__}")


def _customer_id_for(event: InboundOrderEvent) -> str:
    """Extract the customer ID from the event (type-specific field)."""
    if isinstance(event, (InboundOrderPlaced, InboundOrderConfirmed, InboundOrderCancelled)):
        return event.customer_id
    return "unknown"
