"""Domain models for the Order bounded context.

Value objects, enums, and the Order aggregate -- pure domain types with no
infrastructure dependencies.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from enum import Enum
from typing import Sequence


# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------


class OrderStatus(Enum):
    """Lifecycle states of an Order.

    Legal transitions:
    - PLACED -> CONFIRMED (saga succeeded)
    - PLACED -> CANCELLED (saga failed at any downstream step)

    CONFIRMED and CANCELLED are terminal in this workshop's model.
    """

    PLACED = "PLACED"
    CONFIRMED = "CONFIRMED"
    CANCELLED = "CANCELLED"


class CustomerTier(Enum):
    """Customer tier -- bounded enumeration safe to use as a metric label
    without exploding cardinality."""

    BRONZE = "BRONZE"
    SILVER = "SILVER"
    GOLD = "GOLD"
    PLATINUM = "PLATINUM"


# ---------------------------------------------------------------------------
# Value Objects
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class OrderId:
    """Strongly-typed identifier for an Order aggregate.

    Prefixed ``ord_`` for human grep-ability.
    """

    value: str

    _PREFIX = "ord_"

    def __post_init__(self) -> None:
        if not self.value or not self.value.strip():
            raise ValueError("OrderId value must not be blank")

    @classmethod
    def generate(cls) -> OrderId:
        """Produce a new OrderId backed by a UUID."""
        return cls(f"{cls._PREFIX}{uuid.uuid4()}")

    @classmethod
    def of(cls, value: str) -> OrderId:
        """Reconstitute from a string with prefix validation."""
        if not value:
            raise ValueError("OrderId value must not be None or empty")
        if not value.startswith(cls._PREFIX):
            raise ValueError(
                f"OrderId value must start with '{cls._PREFIX}', got: {value}"
            )
        return cls(value)

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class CustomerId:
    """Strongly-typed identifier for a customer in Order's perspective.

    The workshop uses tier-suffixed identifiers (``cust_alice_silver``,
    ``cust_dave_gold``) so the in-memory profile lookup can derive
    CustomerTier deterministically.
    """

    value: str

    _PREFIX = "cust_"

    def __post_init__(self) -> None:
        if not self.value or not self.value.strip():
            raise ValueError("CustomerId value must not be blank")

    @classmethod
    def of(cls, value: str) -> CustomerId:
        """Reconstitute from a string with prefix validation."""
        if not value:
            raise ValueError("CustomerId value must not be None or empty")
        if not value.startswith(cls._PREFIX):
            raise ValueError(
                f"CustomerId value must start with '{cls._PREFIX}', got: {value}"
            )
        return cls(value)

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class CartId:
    """Strongly-typed identifier for a shopping cart."""

    value: str

    _PREFIX = "cart_"

    def __post_init__(self) -> None:
        if not self.value or not self.value.strip():
            raise ValueError("CartId value must not be blank")

    @classmethod
    def of(cls, value: str) -> CartId:
        """Reconstitute from a string with prefix validation."""
        if not value:
            raise ValueError("CartId value must not be None or empty")
        if not value.startswith(cls._PREFIX):
            raise ValueError(
                f"CartId value must start with '{cls._PREFIX}', got: {value}"
            )
        return cls(value)

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Sku:
    """Stock-keeping unit identifier -- the product code in Order's vocabulary.

    Inventory's bounded context calls this a ``product_code``. The ACL in
    ``InventoryRestAdapter`` translates between Order's Sku and Inventory's
    wire vocabulary.
    """

    value: str

    def __post_init__(self) -> None:
        if not self.value or not self.value.strip():
            raise ValueError("Sku value must not be blank")

    @classmethod
    def of(cls, value: str) -> Sku:
        return cls(value)

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class Money:
    """A monetary amount with currency.

    Decimal-backed -- never use float for money.
    """

    amount: Decimal
    currency: str

    def __post_init__(self) -> None:
        if self.amount is None:
            raise ValueError("amount must not be None")
        if not self.currency:
            raise ValueError("currency must not be None or empty")
        if self.amount < 0:
            raise ValueError(f"Money amount must not be negative, got: {self.amount}")
        # Normalize to 2 decimal places for currency precision
        normalized = self.amount.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
        object.__setattr__(self, "amount", normalized)

    @classmethod
    def of(cls, amount: Decimal, currency: str) -> Money:
        return cls(amount, currency)

    @classmethod
    def usd(cls, amount: Decimal | float | str) -> Money:
        return cls(Decimal(str(amount)), "USD")

    @classmethod
    def zero(cls, currency: str) -> Money:
        return cls(Decimal("0"), currency)

    def add(self, other: Money) -> Money:
        if self.currency != other.currency:
            raise ValueError(
                f"Currency mismatch: {self.currency} vs {other.currency}"
            )
        return Money(self.amount + other.amount, self.currency)

    def multiply(self, multiplier: int) -> Money:
        if multiplier < 0:
            raise ValueError("multiplier must be >= 0")
        return Money(self.amount * multiplier, self.currency)


@dataclass(frozen=True)
class LineItem:
    """One line on an order: a SKU, a quantity, and the unit price at checkout.

    We capture ``unit_price`` on the line rather than re-deriving it from a
    catalog later -- prices may change between checkout and fulfillment.
    """

    sku: Sku
    quantity: int
    unit_price: Money

    def __post_init__(self) -> None:
        if self.sku is None:
            raise ValueError("sku must not be None")
        if self.unit_price is None:
            raise ValueError("unit_price must not be None")
        if self.quantity <= 0:
            raise ValueError(f"quantity must be > 0, got: {self.quantity}")

    def line_total(self) -> Money:
        """The total cost of this line: ``unit_price * quantity``."""
        return self.unit_price.multiply(self.quantity)


# ---------------------------------------------------------------------------
# Order Aggregate
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class Order:
    """The Order aggregate.

    Lifecycle::

        place() -> PLACED -> confirm() -> CONFIRMED
                           -> cancel(reason) -> CANCELLED

    State transitions return new Order instances -- we don't mutate.
    """

    id: OrderId
    customer_id: CustomerId
    cart_id: CartId
    line_items: tuple[LineItem, ...]
    status: OrderStatus
    placed_at: datetime
    cancel_reason: str | None = None

    def __post_init__(self) -> None:
        if not self.line_items:
            raise ValueError("Order must have at least one line item")

    @classmethod
    def place(
        cls,
        order_id: OrderId,
        customer_id: CustomerId,
        cart_id: CartId,
        line_items: Sequence[LineItem],
    ) -> Order:
        """Place a new order. Returns an Order in PLACED status."""
        return cls(
            id=order_id,
            customer_id=customer_id,
            cart_id=cart_id,
            line_items=tuple(line_items),
            status=OrderStatus.PLACED,
            placed_at=datetime.now(timezone.utc),
        )

    def total(self) -> Money:
        """Compute the total order value by summing line totals."""
        currency = self.line_items[0].unit_price.currency
        result = Money.zero(currency)
        for item in self.line_items:
            result = result.add(item.line_total())
        return result

    def total_line_item_count(self) -> int:
        """Sum of quantities across all lines."""
        return sum(item.quantity for item in self.line_items)

    def confirm(self) -> Order:
        """Transition to CONFIRMED. Legal only from PLACED."""
        if self.status != OrderStatus.PLACED:
            raise IllegalStateError(
                f"Cannot confirm order in status {self.status.value}"
            )
        return replace(self, status=OrderStatus.CONFIRMED)

    def cancel(self, reason: str) -> Order:
        """Transition to CANCELLED. Legal only from PLACED."""
        if not reason:
            raise ValueError("cancellation reason must not be empty")
        if self.status != OrderStatus.PLACED:
            raise IllegalStateError(
                f"Cannot cancel order in status {self.status.value}"
            )
        return replace(self, status=OrderStatus.CANCELLED, cancel_reason=reason)


class IllegalStateError(Exception):
    """Raised when a domain state transition is invalid."""
