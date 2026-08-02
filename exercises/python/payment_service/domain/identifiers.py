"""Domain identifier keys for the Payment bounded context.

Each key maps to a structured observability attribute (span attribute,
structured log field, Kafka header) that propagates across service
boundaries.
"""

from __future__ import annotations

from enum import Enum

from shared_observability import DomainIdentifier


class _BoundIdentifier:
    """A DomainIdentifier bound to a concrete key and value."""

    __slots__ = ("_key", "_value")

    def __init__(self, key: str, value: str) -> None:
        self._key = key
        self._value = value

    def key(self) -> str:
        return self._key

    def value(self) -> str:
        return self._value


class PaymentContextKey(Enum):
    """Typed identifier vocabulary for the Payment context.

    Shared wire names (order.id, customer.id) are intentionally aligned
    with the Order service for cross-service correlation.
    """

    ORDER_ID = "order.id"
    CUSTOMER_ID = "customer.id"
    AUTHORIZATION_ID = "authorization.id"

    def of(self, value: str) -> DomainIdentifier:
        """Bind this key to a concrete value, returning a DomainIdentifier."""
        return _BoundIdentifier(self.value, value)
