"""Outbound ports and result types for the Order bounded context.

Ports are expressed as Protocols (structural subtyping) so the domain layer
has no knowledge of HTTP, gRPC, JSON, or any specific transport. Adapter
implementations live in ``infrastructure/``.

Result types use a tagged-union pattern with dataclasses -- Python's
equivalent of Java's sealed interfaces for exhaustive pattern matching.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from domain.events import DomainEvent
from domain.models import Order


# ---------------------------------------------------------------------------
# Reservation outcome (tagged union)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class _ReservationBase:
    """Internal base -- not part of the public API."""


@dataclass(frozen=True)
class Reserved(_ReservationBase):
    """Happy path: stock reserved successfully."""

    reservation_id: str

    def __post_init__(self) -> None:
        if not self.reservation_id or not self.reservation_id.strip():
            raise ValueError("reservation_id must not be blank")


@dataclass(frozen=True)
class Unavailable(_ReservationBase):
    """Business-level "no" -- stock not available."""

    reason: str


@dataclass(frozen=True)
class ReservationFailure(_ReservationBase):
    """System failure -- transport error, ACL drift, etc."""

    detail: str


# Union type for type checkers
ReservationOutcome = Reserved | Unavailable | ReservationFailure


# ---------------------------------------------------------------------------
# Authorization outcome (tagged union)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class _AuthorizationBase:
    """Internal base -- not part of the public API."""


@dataclass(frozen=True)
class Authorized(_AuthorizationBase):
    """Happy path: payment authorized."""

    authorization_id: str

    def __post_init__(self) -> None:
        if not self.authorization_id:
            raise ValueError("authorization_id must not be empty")


@dataclass(frozen=True)
class Declined(_AuthorizationBase):
    """Payment declined -- bad card, insufficient funds, etc."""

    reason: str


@dataclass(frozen=True)
class AuthorizationFailure(_AuthorizationBase):
    """System failure during payment authorization."""

    detail: str


AuthorizationOutcome = Authorized | Declined | AuthorizationFailure


# ---------------------------------------------------------------------------
# Shipment outcome (tagged union)
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class _ShipmentBase:
    """Internal base -- not part of the public API."""


@dataclass(frozen=True)
class Scheduled(_ShipmentBase):
    """Happy path: shipment scheduled."""

    shipment_id: str

    def __post_init__(self) -> None:
        if not self.shipment_id:
            raise ValueError("shipment_id must not be empty")


@dataclass(frozen=True)
class ShipmentFailure(_ShipmentBase):
    """System failure during shipment scheduling."""

    detail: str


ShipmentOutcome = Scheduled | ShipmentFailure


# ---------------------------------------------------------------------------
# Port protocols
# ---------------------------------------------------------------------------


class InventoryPort(Protocol):
    """Outbound port for stock reservation, in Order's ubiquitous language.

    The port deliberately knows nothing about HTTP, JSON, or Inventory's
    vocabulary. Adapter implementations contain their own ACL.
    """

    def reserve(self, order: Order) -> ReservationOutcome: ...


class PaymentPort(Protocol):
    """Outbound port for payment authorization, in Order's ubiquitous language."""

    def authorize(self, order: Order) -> AuthorizationOutcome: ...


class ShippingPort(Protocol):
    """Outbound port for shipment scheduling, in Order's ubiquitous language."""

    def schedule(self, order: Order) -> ShipmentOutcome: ...


class OrderEventPublisher(Protocol):
    """Outbound port for publishing domain events.

    The publisher implementation is responsible for putting domain
    identifiers into Kafka headers so consumers can correlate across
    the asynchronous boundary.
    """

    def publish(self, event: DomainEvent) -> None: ...
