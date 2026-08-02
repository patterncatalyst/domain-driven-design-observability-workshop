"""Domain models for the Shipping bounded context.

Pure Python -- no framework dependencies. The Shipment aggregate root
and its value objects live here.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import datetime, timezone


@dataclass(frozen=True)
class ShipmentId:
    """Value object wrapping a shipment identifier."""

    value: str

    _PREFIX = "ship_"

    @classmethod
    def generate(cls) -> ShipmentId:
        """Create a new, unique shipment identifier."""
        return cls(f"{cls._PREFIX}{uuid.uuid4()}")


# Estimated delivery days by shipping class.
_ESTIMATED_DAYS: dict[str, int] = {
    "overnight": 1,
    "express": 2,
    "priority": 3,
    "standard": 5,
}

_DEFAULT_ESTIMATED_DAYS = 5


@dataclass(frozen=True)
class Shipment:
    """The Shipment aggregate root.

    Shipping always succeeds in this workshop model -- there are no
    failed/rejected/in-transit/delivered states.  No persistence;
    created in-memory and returned.
    """

    id: ShipmentId
    order_id: str
    shipping_class: str
    estimated_days: int
    scheduled_at: datetime

    @classmethod
    def schedule(cls, order_id: str, shipping_class: str) -> Shipment:
        """Factory: schedule a new shipment.

        Estimated delivery days are derived from the shipping class.
        """
        estimated = _ESTIMATED_DAYS.get(
            shipping_class.lower(), _DEFAULT_ESTIMATED_DAYS
        )
        return cls(
            id=ShipmentId.generate(),
            order_id=order_id,
            shipping_class=shipping_class,
            estimated_days=estimated,
            scheduled_at=datetime.now(timezone.utc),
        )
