"""Domain identifier keys for the Order bounded context.

Each key maps to a structured observability attribute (span attribute,
structured log field, Kafka header) that propagates across service boundaries.
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


class OrderContextKey(Enum):
    """Typed identifier vocabulary for the Order context.

    These key strings are Order's ubiquitous-language names. When Order
    publishes to Kafka, it sends headers under these keys. Consumers in
    other contexts read by their own equivalent keys.

    ``CUSTOMER_TIER`` is intentionally Order-managed: Order is where the
    customer profile lookup happens, so Order is the producer of that
    baggage entry.
    """

    ORDER_ID = "order.id"
    CUSTOMER_ID = "customer.id"
    CART_ID = "cart.id"
    CUSTOMER_TIER = "customer.tier"

    def of(self, value: str) -> DomainIdentifier:
        """Bind this key to a concrete value, returning a DomainIdentifier."""
        return _BoundIdentifier(self.value, value)
