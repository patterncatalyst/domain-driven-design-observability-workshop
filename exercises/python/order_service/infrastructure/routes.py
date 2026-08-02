"""Order REST routes.

FastAPI router with Pydantic request/response models. Pure adapter layer --
maps HTTP DTOs to/from the application command and domain types, then
delegates to the CheckoutSaga.

The resource follows the same architectural discipline as the outbound
adapters: web vocabulary in, domain vocabulary out (to the saga), then
domain vocabulary in, web vocabulary out (in the response).
"""

from __future__ import annotations

from decimal import Decimal

from fastapi import APIRouter
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from application.checkout_saga import CheckoutCommand, CheckoutResult, CheckoutSaga
from domain.models import LineItem, Money, Sku
from infrastructure.customer_lookup import InMemoryCustomerProfileLookup
from infrastructure.inventory_adapter import InventoryRestAdapter
from infrastructure.kafka_publisher import OrderEventKafkaPublisher
from infrastructure.payment_adapter import PaymentRestAdapter
from infrastructure.shipping_adapter import ShippingRestAdapter

router = APIRouter(prefix="/api/orders", tags=["orders"])


# ---------------------------------------------------------------------------
# Wire DTOs
# ---------------------------------------------------------------------------


class LineItemDto(BaseModel):
    """Inbound DTO for a single line item."""

    sku: str
    quantity: int = Field(gt=0)
    unitPrice: float = Field(alias="unitPrice")

    model_config = {"populate_by_name": True}


class CheckoutRequest(BaseModel):
    """Inbound DTO for ``POST /api/orders/checkout``."""

    cartId: str = Field(..., alias="cartId")
    customerId: str = Field(..., alias="customerId")
    lineItems: list[LineItemDto] = Field(..., alias="lineItems")
    paymentMethod: str = Field(default="credit_card", alias="paymentMethod")
    shippingClass: str = Field(default="standard", alias="shippingClass")

    model_config = {"populate_by_name": True}


class CheckoutResponse(BaseModel):
    """Outbound DTO for the checkout response."""

    orderId: str = Field(..., alias="orderId")
    status: str
    reservationId: str | None = Field(default=None, alias="reservationId")
    authorizationId: str | None = Field(default=None, alias="authorizationId")
    shipmentId: str | None = Field(default=None, alias="shipmentId")
    message: str = ""

    model_config = {"populate_by_name": True}


# ---------------------------------------------------------------------------
# Dependency wiring -- simple singleton assembly (no DI framework)
# ---------------------------------------------------------------------------

_inventory_adapter = InventoryRestAdapter()
_payment_adapter = PaymentRestAdapter()
_shipping_adapter = ShippingRestAdapter()
_kafka_publisher = OrderEventKafkaPublisher()
_customer_lookup = InMemoryCustomerProfileLookup()

_saga = CheckoutSaga(
    inventory=_inventory_adapter,
    payment=_payment_adapter,
    shipping=_shipping_adapter,
    events=_kafka_publisher,
    customer_lookup=_customer_lookup,
)


# ---------------------------------------------------------------------------
# Endpoint
# ---------------------------------------------------------------------------


@router.post("/checkout")
async def checkout(request: CheckoutRequest) -> JSONResponse:
    """Execute the checkout saga.

    Returns 201 Created for confirmed orders, 422 Unprocessable Entity
    for cancelled orders (business-level failures).
    """
    # Translate web DTOs -> domain types -> CheckoutCommand
    command = _to_command(request)
    result: CheckoutResult = _saga.checkout(command)

    response = CheckoutResponse(
        orderId=result.order_id,
        status=result.status,
        reservationId=result.reservation_id,
        authorizationId=result.authorization_id,
        shipmentId=result.shipment_id,
        message=result.message,
    )

    if result.status == "CONFIRMED":
        return JSONResponse(
            status_code=201,
            content=response.model_dump(by_alias=True),
        )
    else:
        return JSONResponse(
            status_code=422,
            content=response.model_dump(by_alias=True),
        )


# ---------------------------------------------------------------------------
# Translation: web DTO -> domain CheckoutCommand
# ---------------------------------------------------------------------------


def _to_command(request: CheckoutRequest) -> CheckoutCommand:
    """Convert inbound HTTP DTO to a domain CheckoutCommand.

    Validation lives in the domain types' constructors (Sku, Money,
    LineItem) -- any malformed input raises ValueError, which FastAPI
    maps to a 422 response.
    """
    domain_lines = [
        LineItem(
            sku=Sku.of(li.sku),
            quantity=li.quantity,
            unit_price=Money.usd(Decimal(str(li.unitPrice))),
        )
        for li in request.lineItems
    ]

    return CheckoutCommand(
        customer_id=request.customerId,
        cart_id=request.cartId,
        line_items=domain_lines,
        payment_method=request.paymentMethod,
        shipping_class=request.shippingClass,
    )
