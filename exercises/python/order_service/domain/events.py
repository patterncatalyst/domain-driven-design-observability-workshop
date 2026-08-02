"""Domain events for the Order bounded context.

All events are frozen dataclasses. Each carries an ``event_type`` property
used as the JSON discriminator (``eventType`` on the wire), mirroring the
Java ``@JsonTypeInfo`` contract.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

from domain.models import CartId, CustomerId, Money, Order, OrderId


# ---------------------------------------------------------------------------
# Base event
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class DomainEvent:
    """Base class for Order domain events.

    Subclasses add context-specific fields. The ``event_type`` property
    serves as the JSON discriminator (``eventType`` on the wire).
    """

    event_id: str = field(default_factory=lambda: str(uuid.uuid4()))
    order_id: str = ""
    occurred_at: str = field(
        default_factory=lambda: datetime.now(timezone.utc).isoformat()
    )

    @property
    def event_type(self) -> str:
        return self.__class__.__name__

    def to_dict(self) -> dict[str, Any]:
        """Serialize to a dict suitable for JSON encoding.

        Includes the ``eventType`` discriminator field.
        """
        raise NotImplementedError


# ---------------------------------------------------------------------------
# Concrete events
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class OrderPlaced(DomainEvent):
    """An order has been placed -- the customer's checkout intent is recorded.

    Fires before any downstream calls (inventory, payment), so consumers
    should not assume the order is fulfillable yet.
    """

    customer_id: str = ""
    cart_id: str = ""
    total_amount: float = 0.0
    total_currency: str = "USD"
    line_item_count: int = 0

    @classmethod
    def from_order(cls, order: Order) -> OrderPlaced:
        total = order.total()
        return cls(
            order_id=order.id.value,
            customer_id=order.customer_id.value,
            cart_id=order.cart_id.value,
            total_amount=float(total.amount),
            total_currency=total.currency,
            line_item_count=order.total_line_item_count(),
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "eventType": self.event_type,
            "eventId": self.event_id,
            "orderId": self.order_id,
            "occurredAt": self.occurred_at,
            "customerId": self.customer_id,
            "cartId": self.cart_id,
            "total": {
                "amount": self.total_amount,
                "currency": self.total_currency,
            },
            "lineItemCount": self.line_item_count,
        }


@dataclass(frozen=True)
class OrderConfirmed(DomainEvent):
    """The saga has completed all steps -- order is now CONFIRMED.

    Primary trigger for customer-facing notifications.
    """

    customer_id: str = ""
    total_amount: float = 0.0
    total_currency: str = "USD"
    reservation_id: str = ""
    authorization_id: str = ""
    shipment_id: str = ""

    @classmethod
    def from_order(
        cls,
        order: Order,
        reservation_id: str,
        authorization_id: str,
        shipment_id: str,
    ) -> OrderConfirmed:
        total = order.total()
        return cls(
            order_id=order.id.value,
            customer_id=order.customer_id.value,
            total_amount=float(total.amount),
            total_currency=total.currency,
            reservation_id=reservation_id,
            authorization_id=authorization_id,
            shipment_id=shipment_id,
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "eventType": self.event_type,
            "eventId": self.event_id,
            "orderId": self.order_id,
            "occurredAt": self.occurred_at,
            "customerId": self.customer_id,
            "total": {
                "amount": self.total_amount,
                "currency": self.total_currency,
            },
            "reservationId": self.reservation_id,
            "authorizationId": self.authorization_id,
            "shipmentId": self.shipment_id,
        }


@dataclass(frozen=True)
class OrderCancelled(DomainEvent):
    """The saga aborted at some step -- order is now CANCELLED.

    ``failed_at`` indicates which saga step caused the cancellation
    (e.g. "inventory", "payment", "shipping").
    """

    customer_id: str = ""
    failed_at: str = ""
    reason: str = ""

    @classmethod
    def from_order(
        cls, order: Order, failed_at: str, reason: str
    ) -> OrderCancelled:
        return cls(
            order_id=order.id.value,
            customer_id=order.customer_id.value,
            failed_at=failed_at,
            reason=reason,
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "eventType": self.event_type,
            "eventId": self.event_id,
            "orderId": self.order_id,
            "occurredAt": self.occurred_at,
            "customerId": self.customer_id,
            "failedAt": self.failed_at,
            "reason": self.reason,
        }
