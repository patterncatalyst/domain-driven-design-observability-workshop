"""FastAPI routes for the Inventory REST API.

Thin infrastructure adapter: Pydantic DTOs in, domain command to use case,
domain result out as JSON.  No business logic lives here.
"""

from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel, Field

from inventory_service.application.reserve_stock import (
    ReserveItem,
    ReserveStockCommand,
    ReserveStockUseCase,
)
from inventory_service.domain.models import Reservation

# ---------------------------------------------------------------------------
# Router
# ---------------------------------------------------------------------------

router = APIRouter(prefix="/api/inventory", tags=["inventory"])

_use_case = ReserveStockUseCase()

# ---------------------------------------------------------------------------
# Request / Response DTOs
# ---------------------------------------------------------------------------


class ReserveRequestItem(BaseModel):
    sku: str
    quantity: int = Field(gt=0)


class ReserveRequest(BaseModel):
    order_id: str = Field(alias="orderId")
    items: list[ReserveRequestItem]

    model_config = {"populate_by_name": True}


class ReserveResponseLine(BaseModel):
    product_code: str = Field(serialization_alias="productCode")
    quantity_reserved: int = Field(serialization_alias="quantityReserved")
    available: bool


class ReserveResponse(BaseModel):
    reservation_id: str = Field(serialization_alias="reservationId")
    status: str
    reason: str | None
    lines: list[ReserveResponseLine]

    model_config = {"populate_by_name": True}


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@router.post("/reserve", response_model=ReserveResponse)
async def reserve_stock(request: ReserveRequest) -> ReserveResponse:
    command = _to_command(request)
    reservation = _use_case.reserve(command)
    return _from_domain(reservation)


# ---------------------------------------------------------------------------
# Mapping helpers
# ---------------------------------------------------------------------------


def _to_command(request: ReserveRequest) -> ReserveStockCommand:
    return ReserveStockCommand(
        order_id=request.order_id,
        items=tuple(
            ReserveItem(sku=item.sku, quantity=item.quantity)
            for item in request.items
        ),
    )


def _from_domain(reservation: Reservation) -> ReserveResponse:
    return ReserveResponse(
        reservation_id=reservation.id.value,
        status=reservation.status.value,
        reason=reservation.reason,
        lines=[
            ReserveResponseLine(
                product_code=line.product_code.value,
                quantity_reserved=line.quantity_reserved,
                available=line.available,
            )
            for line in reservation.lines
        ],
    )
