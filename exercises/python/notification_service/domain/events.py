"""Inbound domain events for the Notification bounded context.

These are Notification's own view of the events that Order publishes.
Crucially, these are NOT the same types as Order's DomainEvent hierarchy.
Order owns its event hierarchy; Notification defines its own that aligns
with the on-wire JSON shape. The wire IS the contract between contexts;
sharing Python types would be a Shared Kernel violation per Khononov.

Deserialization uses the ``eventType`` discriminator field on the wire
payload to select the correct subclass.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class InboundOrderEvent:
    """Base class for all inbound order events.

    Every event on the wire carries at least these fields.
    """

    event_type: str
    event_id: str
    order_id: str
    occurred_at: str


@dataclass(frozen=True)
class InboundOrderPlaced(InboundOrderEvent):
    """Notification's view of OrderPlaced.

    Carries just what Notification needs: the order, the customer, the
    time. The total and line_item_count are on the wire but Notification's
    placed-ack doesn't quote them -- they're accepted for deserialization
    completeness.
    """

    customer_id: str = ""
    cart_id: str = ""
    total: dict[str, Any] | None = None
    line_item_count: int = 0


@dataclass(frozen=True)
class InboundOrderConfirmed(InboundOrderEvent):
    """Notification's view of OrderConfirmed.

    Includes the downstream IDs (reservation, authorization, shipment) so
    the confirmation email can quote them -- and so the trace through
    Notification carries them as span attributes for cross-service
    correlation in Tempo.
    """

    customer_id: str = ""
    reservation_id: str = ""
    authorization_id: str = ""
    shipment_id: str = ""


@dataclass(frozen=True)
class InboundOrderCancelled(InboundOrderEvent):
    """Notification's view of OrderCancelled."""

    customer_id: str = ""
    failed_at: str = ""
    reason: str = ""


# Map of wire eventType values to their Python dataclass constructors.
_EVENT_TYPE_MAP: dict[str, type[InboundOrderEvent]] = {
    "OrderPlaced": InboundOrderPlaced,
    "OrderConfirmed": InboundOrderConfirmed,
    "OrderCancelled": InboundOrderCancelled,
}

# Map from camelCase wire keys to snake_case Python field names.
_FIELD_MAP: dict[str, str] = {
    "eventType": "event_type",
    "eventId": "event_id",
    "orderId": "order_id",
    "occurredAt": "occurred_at",
    "customerId": "customer_id",
    "cartId": "cart_id",
    "lineItemCount": "line_item_count",
    "reservationId": "reservation_id",
    "authorizationId": "authorization_id",
    "shipmentId": "shipment_id",
    "failedAt": "failed_at",
}


def _snake_case_keys(data: dict[str, Any]) -> dict[str, Any]:
    """Convert camelCase wire keys to snake_case Python field names."""
    return {_FIELD_MAP.get(k, k): v for k, v in data.items()}


def deserialize_event(json_str: str) -> InboundOrderEvent:
    """Deserialize a JSON string into the appropriate InboundOrderEvent subclass.

    Uses the ``eventType`` field as the discriminator to select the correct
    Python dataclass. Unknown event types raise ``ValueError``.

    Args:
        json_str: Raw JSON string from the Kafka message value.

    Returns:
        The deserialized event as the appropriate subclass.

    Raises:
        ValueError: If the eventType is missing or unrecognized.
        json.JSONDecodeError: If the string is not valid JSON.
    """
    data = json.loads(json_str)
    event_type = data.get("eventType")
    if not event_type:
        raise ValueError("Missing 'eventType' in event payload")

    cls = _EVENT_TYPE_MAP.get(event_type)
    if cls is None:
        raise ValueError(f"Unknown event type: {event_type}")

    # Convert wire keys to Python field names and filter to known fields.
    snake_data = _snake_case_keys(data)

    # Only pass fields that the target dataclass accepts.
    import dataclasses

    valid_fields = {f.name for f in dataclasses.fields(cls)}
    filtered = {k: v for k, v in snake_data.items() if k in valid_fields}

    return cls(**filtered)
