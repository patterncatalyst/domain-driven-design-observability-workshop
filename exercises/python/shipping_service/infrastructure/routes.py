"""Shipping REST routes.

FastAPI router with Pydantic request/response models. Pure adapter layer --
maps HTTP DTOs to/from the application command and domain aggregate.
"""

from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel, Field

from application.schedule_shipment import (
    ScheduleShipmentCommand,
    ScheduleShipmentUseCase,
)

router = APIRouter(prefix="/api/shipments", tags=["shipments"])

# Singleton use case (stateless, no DI framework needed)
_use_case = ScheduleShipmentUseCase()


# ---------------------------------------------------------------------------
# Wire DTOs (kept co-located with the route, not a separate dto/ package)
# ---------------------------------------------------------------------------


class ScheduleRequest(BaseModel):
    """Inbound DTO for shipment scheduling."""

    orderId: str = Field(..., alias="orderId")
    customerId: str = Field(..., alias="customerId")
    shippingClass: str = Field(..., alias="shippingClass")

    model_config = {"populate_by_name": True}


class ScheduleResponse(BaseModel):
    """Outbound DTO for shipment scheduling result."""

    shipmentId: str = Field(..., alias="shipmentId")
    outcome: str
    estimatedDays: int = Field(..., alias="estimatedDays")

    model_config = {"populate_by_name": True}


# ---------------------------------------------------------------------------
# Endpoint
# ---------------------------------------------------------------------------


@router.post("/schedule", response_model=ScheduleResponse)
async def schedule_shipment(request: ScheduleRequest) -> ScheduleResponse:
    """Schedule a shipment for an order."""
    command = ScheduleShipmentCommand(
        order_id=request.orderId,
        customer_id=request.customerId,
        shipping_class=request.shippingClass,
    )

    shipment = _use_case.schedule(command)

    return ScheduleResponse(
        shipmentId=shipment.id.value,
        outcome="SCHEDULED",
        estimatedDays=shipment.estimated_days,
    )
