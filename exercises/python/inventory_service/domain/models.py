"""Domain model for the Inventory bounded context.

Value objects, enums, and the Reservation aggregate — pure domain types
with no infrastructure dependencies.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from enum import Enum


# ---------------------------------------------------------------------------
# Value Objects
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ReservationId:
    """Typed identifier for a stock reservation."""

    value: str

    _PREFIX = "res_"

    @staticmethod
    def generate() -> ReservationId:
        return ReservationId(value=f"res_{uuid.uuid4()}")

    @staticmethod
    def of(value: str) -> ReservationId:
        if not value or not value.strip():
            raise ValueError("ReservationId cannot be blank")
        return ReservationId(value=value)


@dataclass(frozen=True)
class ProductCode:
    """Inventory's vocabulary for a product identifier.

    Order's domain calls the same concept *Sku*; the anti-corruption layer
    translates between the two.
    """

    value: str

    @staticmethod
    def of(value: str) -> ProductCode:
        if not value or not value.strip():
            raise ValueError("ProductCode cannot be blank")
        return ProductCode(value=value)


# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------


class ReservationStatus(Enum):
    """Outcome of a stock-reservation attempt."""

    RESERVED = "RESERVED"
    PARTIALLY_RESERVED = "PARTIALLY_RESERVED"
    UNAVAILABLE = "UNAVAILABLE"


# ---------------------------------------------------------------------------
# Reservation Line (per-item result)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ReservationLine:
    """One line within a reservation, tracking per-item availability."""

    product_code: ProductCode
    quantity_reserved: int
    available: bool


# ---------------------------------------------------------------------------
# Reservation Aggregate
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class Reservation:
    """The Reservation aggregate root.

    Constructed exclusively through factory methods that enforce valid
    state transitions — callers never set fields directly.
    """

    id: ReservationId
    order_id: str
    status: ReservationStatus
    reason: str | None
    lines: tuple[ReservationLine, ...]

    # -- Factory methods (Khononov functional-model pattern) ----------------

    @staticmethod
    def reserved(order_id: str, lines: list[ReservationLine]) -> Reservation:
        """All requested quantities fulfilled."""
        return Reservation(
            id=ReservationId.generate(),
            order_id=order_id,
            status=ReservationStatus.RESERVED,
            reason=None,
            lines=tuple(lines),
        )

    @staticmethod
    def partially_reserved(
        order_id: str, lines: list[ReservationLine], reason: str,
    ) -> Reservation:
        """Some quantities reduced."""
        if not reason:
            raise ValueError("Partial reservation requires a reason")
        return Reservation(
            id=ReservationId.generate(),
            order_id=order_id,
            status=ReservationStatus.PARTIALLY_RESERVED,
            reason=reason,
            lines=tuple(lines),
        )

    @staticmethod
    def unavailable(
        order_id: str, lines: list[ReservationLine], reason: str,
    ) -> Reservation:
        """No requested quantities could be fulfilled."""
        if not reason:
            raise ValueError("Unavailable reservation requires a reason")
        return Reservation(
            id=ReservationId.generate(),
            order_id=order_id,
            status=ReservationStatus.UNAVAILABLE,
            reason=reason,
            lines=tuple(lines),
        )
