"""Reserve-stock use case — the core application logic for the Inventory context.

Translates incoming SKUs into Inventory's ProductCode vocabulary, simulates
stock availability based on SKU prefixes, and emits OTel spans + metrics.
"""

from __future__ import annotations

from dataclasses import dataclass

import structlog
from opentelemetry import metrics, trace

from shared_observability import DomainContext, get_baggage

from inventory_service.domain.identifiers import InventoryContextKey
from inventory_service.domain.models import (
    ProductCode,
    Reservation,
    ReservationLine,
)

# ---------------------------------------------------------------------------
# OTel instruments
# ---------------------------------------------------------------------------

tracer = trace.get_tracer("inventory-service")
meter = metrics.get_meter("inventory-service")

reservation_counter = meter.create_counter(
    "inventory_reservations_total",
    description="Total inventory reservations by status and customer tier",
)

logger = structlog.get_logger()

# ---------------------------------------------------------------------------
# Command
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class ReserveItem:
    """One line-item in a reservation request (Order vocabulary: SKU)."""

    sku: str
    quantity: int


@dataclass(frozen=True)
class ReserveStockCommand:
    """Inbound command carrying Order-side SKUs to be reserved."""

    order_id: str
    items: tuple[ReserveItem, ...]


# ---------------------------------------------------------------------------
# Use Case
# ---------------------------------------------------------------------------


class ReserveStockUseCase:
    """Determines stock availability and creates a :class:`Reservation`.

    Stock simulation (workshop scaffolding — no real DB):

    * SKU starts with ``OUT_`` or ``OUT-`` → item **unavailable** (qty 0).
    * SKU starts with ``PARTIAL_`` → item available at **reduced** quantity.
    * Everything else → fully **available** (qty = requested).

    Overall reservation status follows Java-reference precedence:

    1. Any unavailable item → ``UNAVAILABLE``
    2. Else any partial item → ``PARTIALLY_RESERVED``
    3. Else → ``RESERVED``
    """

    _OUT_OF_STOCK_PREFIXES: tuple[str, ...] = ("OUT_", "OUT-")
    _PARTIAL_PREFIX: str = "PARTIAL_"

    @tracer.start_as_current_span("Inventory.Reserve")
    def reserve(self, command: ReserveStockCommand) -> Reservation:
        span = trace.get_current_span()
        tier = get_baggage("customer.tier") or "unknown"
        customer_id = get_baggage("customer.id") or "unknown"

        with DomainContext(
            InventoryContextKey.ORDER_ID.of(command.order_id),
            InventoryContextKey.CUSTOMER_ID.of(customer_id),
        ):
            span.set_attribute("order.id", command.order_id)
            span.set_attribute("customer.id", customer_id)
            span.set_attribute("customer.tier", tier)
            span.set_attribute("reservation.line_count", len(command.items))

            reservation = self._decide_outcome(command)

            # Enrich context with the newly-minted reservation ID
            span.set_attribute("reservation.id", reservation.id.value)
            span.set_attribute("reservation.status", reservation.status.value)

            logger.info(
                "Reservation %s: %s (lines=%d)",
                reservation.id.value,
                reservation.status.value,
                len(reservation.lines),
            )

            reservation_counter.add(
                1,
                {"status": reservation.status.value, "tier": tier},
            )

            return reservation

    # -- Private helpers ----------------------------------------------------

    def _decide_outcome(self, command: ReserveStockCommand) -> Reservation:
        """Build per-line results and derive overall status."""
        lines: list[ReservationLine] = []
        any_out_of_stock = False
        any_partial = False

        for item in command.items:
            product_code = self._sku_to_product_code(item.sku)

            if any(item.sku.startswith(p) for p in self._OUT_OF_STOCK_PREFIXES):
                any_out_of_stock = True
                lines.append(ReservationLine(
                    product_code=product_code,
                    quantity_reserved=0,
                    available=False,
                ))
            elif item.sku.startswith(self._PARTIAL_PREFIX):
                any_partial = True
                lines.append(ReservationLine(
                    product_code=product_code,
                    quantity_reserved=max(1, item.quantity // 2),
                    available=True,
                ))
            else:
                lines.append(ReservationLine(
                    product_code=product_code,
                    quantity_reserved=item.quantity,
                    available=True,
                ))

        if any_out_of_stock:
            return Reservation.unavailable(
                command.order_id, lines, "one or more items not in stock",
            )
        if any_partial:
            return Reservation.partially_reserved(
                command.order_id, lines, "some quantities reduced",
            )
        return Reservation.reserved(command.order_id, lines)

    @staticmethod
    def _sku_to_product_code(sku: str) -> ProductCode:
        """Translate Order's SKU vocabulary into Inventory's ProductCode.

        ``SKU-LAPTOP-PRO`` → ``PROD-LAPTOP-PRO``; SKUs without the ``SKU-``
        prefix pass through unchanged.
        """
        if sku.startswith("SKU-"):
            return ProductCode.of(f"PROD-{sku[4:]}")
        return ProductCode.of(sku)
