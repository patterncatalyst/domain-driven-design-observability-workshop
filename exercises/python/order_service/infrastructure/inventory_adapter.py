"""REST + ACL adapter for InventoryPort.

This is the canonical example for the Anti-Corruption Layer discussion.
It does three things:

1. **Outbound translation** -- ``_to_wire(order)`` maps Order's domain
   types (Sku, LineItem) to Inventory's wire types (sku, quantity).
2. **Wire call** -- delegated to httpx, with OTel context propagation.
3. **Inbound translation + drift detection** -- ``_from_wire(response)``
   maps the response into a ReservationOutcome expressed in Order's
   vocabulary. Drift dies here.
"""

from __future__ import annotations

import os
from typing import Any

import httpx
import structlog
from opentelemetry import trace
from opentelemetry.propagate import inject

from domain.models import Order
from domain.ports import (
    ReservationFailure,
    ReservationOutcome,
    Reserved,
    Unavailable,
)

logger = structlog.get_logger()
tracer = trace.get_tracer("order-service")

_DEFAULT_INVENTORY_URL = "http://inventory-service:8081"


class InventoryRestAdapter:
    """REST + ACL adapter implementing InventoryPort.

    Translates between Order's domain vocabulary and Inventory's wire
    contract. Unknown or malformed responses are surfaced as typed
    ``ReservationFailure`` values so the saga's pattern matching remains
    exhaustive.
    """

    def __init__(self, base_url: str | None = None) -> None:
        self._base_url = (
            base_url
            or os.environ.get("INVENTORY_SERVICE_URL", _DEFAULT_INVENTORY_URL)
        )
        self._client = httpx.Client(timeout=30.0)

    def reserve(self, order: Order) -> ReservationOutcome:
        with tracer.start_as_current_span("Order.Acl.InventoryReserve") as span:
            span.set_attribute("acl.context", "inventory")
            span.set_attribute("acl.transport", "rest")

            try:
                # 1. Outbound translation: Order -> wire
                wire_request = self._to_wire(order)
                span.set_attribute(
                    "acl.wire.line_count", len(wire_request["items"])
                )

                # Inject OTel trace context for distributed tracing
                headers: dict[str, str] = {}
                inject(headers)

                # 2. Wire call
                url = f"{self._base_url}/api/inventory/reserve"
                response = self._client.post(
                    url, json=wire_request, headers=headers
                )
                response.raise_for_status()
                wire_response = response.json()

                # 3. Inbound translation -- drift dies here
                return self._from_wire(wire_response)

            except httpx.HTTPStatusError as e:
                logger.warning(
                    "Inventory REST call failed",
                    status=e.response.status_code,
                    error=str(e),
                )
                return ReservationFailure(
                    detail=f"Inventory REST call failed: HTTP {e.response.status_code}"
                )

            except httpx.HTTPError as e:
                logger.warning(
                    "Inventory REST call failed", error=str(e)
                )
                return ReservationFailure(
                    detail=f"Inventory REST call failed: {e}"
                )

            except _AclDriftError as e:
                return ReservationFailure(detail=str(e))

    # ------------------------------------------------------------------------
    # Pure translation functions -- the body of the ACL
    # ------------------------------------------------------------------------

    @staticmethod
    def _to_wire(order: Order) -> dict[str, Any]:
        """Translate Order domain types to Inventory's wire format.

        Order's Sku -> Inventory's ``sku`` field
        Order's LineItem.quantity -> Inventory's ``quantity`` field
        """
        wire_items = [
            {"sku": li.sku.value, "quantity": li.quantity}
            for li in order.line_items
        ]
        return {
            "orderId": order.id.value,
            "items": wire_items,
        }

    @staticmethod
    def _from_wire(wire: dict[str, Any]) -> ReservationOutcome:
        """Translate Inventory's wire response to Order's ReservationOutcome.

        Inventory returns status values: RESERVED, PARTIALLY_RESERVED,
        UNAVAILABLE. We map these to Order's outcome types.
        """
        if wire is None:
            raise _AclDriftError("Inventory returned a null response body")

        status = wire.get("status")
        if status is None:
            raise _AclDriftError("Inventory response missing status field")

        reservation_id = wire.get("reservationId") or wire.get("reservation_id")
        reason = wire.get("reason")

        if status == "RESERVED":
            if not reservation_id:
                raise _AclDriftError(
                    "Inventory returned RESERVED with no reservationId -- "
                    "wire contract violation"
                )
            return Reserved(reservation_id=reservation_id)

        if status == "PARTIALLY_RESERVED":
            # Workshop policy: partial reservation is treated as unavailable
            detail = reason or "partial reservation -- some items not in stock"
            return Unavailable(reason=detail)

        if status == "UNAVAILABLE":
            detail = reason or "stock unavailable"
            return Unavailable(reason=detail)

        raise _AclDriftError(f"Unknown inventory status: {status}")


class _AclDriftError(Exception):
    """Raised when the Inventory response violates the expected wire contract."""
