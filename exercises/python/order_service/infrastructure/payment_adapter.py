"""Thin REST client adapter for PaymentPort.

No full Anti-Corruption Layer here because Order and Payment share
vocabulary closely enough that the cost of a full ACL outweighs the
benefit. We do still:

- Name the span in domain language (``Order.Payment.Authorize``)
- Tag the span with business attributes for trace search
- Convert transport failures to typed outcomes so the saga's pattern
  matching remains exhaustive
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
    AuthorizationFailure,
    AuthorizationOutcome,
    Authorized,
    Declined,
)

logger = structlog.get_logger()
tracer = trace.get_tracer("order-service")

_DEFAULT_PAYMENT_URL = "http://payment-service:8082"


class PaymentRestAdapter:
    """REST client adapter implementing PaymentPort.

    Thin client -- no separate DTO package, no drift counter. The
    vocabularies are close enough that translation cost outweighs
    translation benefit.
    """

    def __init__(self, base_url: str | None = None) -> None:
        self._base_url = (
            base_url
            or os.environ.get("PAYMENT_SERVICE_URL", _DEFAULT_PAYMENT_URL)
        )
        self._client = httpx.Client(timeout=30.0)

    def authorize(self, order: Order) -> AuthorizationOutcome:
        with tracer.start_as_current_span("Order.Payment.Authorize") as span:
            payment_method = "credit_card"
            span.set_attribute("payment.method", payment_method)
            span.set_attribute("order.value", float(order.total().amount))

            try:
                wire_request: dict[str, Any] = {
                    "orderId": order.id.value,
                    "customerId": order.customer_id.value,
                    "amount": float(order.total().amount),
                    "currency": order.total().currency,
                    "paymentMethod": payment_method,
                }

                # Inject OTel trace context for distributed tracing
                headers: dict[str, str] = {}
                inject(headers)

                url = f"{self._base_url}/api/payments/authorize"
                response = self._client.post(
                    url, json=wire_request, headers=headers
                )
                response.raise_for_status()
                wire_response = response.json()

                outcome = wire_response.get("outcome")

                if outcome == "AUTHORIZED":
                    auth_id = wire_response.get("authorizationId")
                    return Authorized(authorization_id=auth_id)

                if outcome == "DECLINED":
                    reason = wire_response.get("reason") or "declined"
                    return Declined(reason=reason)

                # FAILURE or unknown outcome
                reason = wire_response.get("reason") or f"unknown outcome: {outcome}"
                return AuthorizationFailure(detail=reason)

            except httpx.HTTPStatusError as e:
                logger.warning(
                    "Payment REST call failed",
                    status=e.response.status_code,
                    error=str(e),
                )
                return AuthorizationFailure(
                    detail=f"Payment REST call failed: HTTP {e.response.status_code}"
                )

            except httpx.HTTPError as e:
                logger.warning(
                    "Payment REST call failed", error=str(e)
                )
                return AuthorizationFailure(
                    detail=f"Payment REST call failed: {e}"
                )
