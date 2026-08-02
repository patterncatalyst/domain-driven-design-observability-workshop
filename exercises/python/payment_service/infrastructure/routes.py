"""Payment REST routes.

FastAPI router with Pydantic request/response models. Pure adapter layer --
maps HTTP DTOs to/from the application command and domain aggregate.
"""

from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel, Field

from application.authorize_payment import (
    AuthorizePaymentCommand,
    AuthorizePaymentUseCase,
)

router = APIRouter(prefix="/api/payments", tags=["payments"])

# Singleton use case (stateless, no DI framework needed)
_use_case = AuthorizePaymentUseCase()


# ---------------------------------------------------------------------------
# Wire DTOs (kept co-located with the route, not a separate dto/ package)
# ---------------------------------------------------------------------------


class AuthorizeRequest(BaseModel):
    """Inbound DTO for payment authorization."""

    orderId: str = Field(..., alias="orderId")
    customerId: str = Field(..., alias="customerId")
    amount: float
    currency: str
    paymentMethod: str = Field(..., alias="paymentMethod")

    model_config = {"populate_by_name": True}


class AuthorizeResponse(BaseModel):
    """Outbound DTO for payment authorization result."""

    authorizationId: str = Field(..., alias="authorizationId")
    outcome: str
    reason: str | None = None

    model_config = {"populate_by_name": True}


# ---------------------------------------------------------------------------
# Endpoint
# ---------------------------------------------------------------------------


@router.post("/authorize", response_model=AuthorizeResponse)
async def authorize_payment(request: AuthorizeRequest) -> AuthorizeResponse:
    """Authorize a payment for an order."""
    command = AuthorizePaymentCommand(
        order_id=request.orderId,
        customer_id=request.customerId,
        amount=request.amount,
        currency=request.currency,
        payment_method=request.paymentMethod,
    )

    authorization = _use_case.authorize(command)

    return AuthorizeResponse(
        authorizationId=authorization.id.value,
        outcome=authorization.outcome.value,
        reason=authorization.reason,
    )
