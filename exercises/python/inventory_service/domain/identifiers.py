"""Domain identifiers for the Inventory bounded context.

Each enum value carries a dotted key string that matches the wire contract
shared by convention (not shared kernel) across bounded contexts —
e.g. ``"order.id"`` is the same string Order uses.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from shared_observability import DomainIdentifier


# ---------------------------------------------------------------------------
# Private helper — satisfies the DomainIdentifier protocol structurally
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class _SimpleIdentifier:
    _key: str
    _value: str

    def key(self) -> str:
        return self._key

    def value(self) -> str:
        return self._value


# ---------------------------------------------------------------------------
# Context keys for structured logging / OTel attributes
# ---------------------------------------------------------------------------


class InventoryContextKey(Enum):
    """Inventory-specific domain identifiers.

    Usage::

        with DomainContext(
            InventoryContextKey.ORDER_ID.of("ord_abc"),
            InventoryContextKey.RESERVATION_ID.of("res_xyz"),
        ):
            logger.info("reserved")
    """

    ORDER_ID = "order.id"
    CUSTOMER_ID = "customer.id"
    RESERVATION_ID = "reservation.id"
    PRODUCT_CODE = "product.code"

    def of(self, value: str) -> DomainIdentifier:
        """Create a :class:`DomainIdentifier` for this key with *value*."""
        return _SimpleIdentifier(_key=self.value, _value=value)
