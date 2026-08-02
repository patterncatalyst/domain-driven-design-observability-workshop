"""Thin REST client adapter for ShippingPort.

Parallel to PaymentRestAdapter -- Order and Shipping share vocabulary
closely enough that a full ACL isn't justified.
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
    Scheduled,
    ShipmentFailure,
    ShipmentOutcome,
)

logger = structlog.get_logger()
tracer = trace.get_tracer("order-service")

_DEFAULT_SHIPPING_URL = "http://shipping-service:8083"


class ShippingRestAdapter:
    """REST client adapter implementing ShippingPort.

    Thin client -- no separate DTO package, no drift counter.
    """

    def __init__(self, base_url: str | None = None) -> None:
        self._base_url = (
            base_url
            or os.environ.get("SHIPPING_SERVICE_URL", _DEFAULT_SHIPPING_URL)
        )
        self._client = httpx.Client(timeout=30.0)

    def schedule(self, order: Order) -> ShipmentOutcome:
        with tracer.start_as_current_span("Order.Shipping.Schedule") as span:
            shipping_class = "standard"
            span.set_attribute("shipping.class", shipping_class)
            span.set_attribute(
                "order.line_items_count", len(order.line_items)
            )

            try:
                wire_request: dict[str, Any] = {
                    "orderId": order.id.value,
                    "customerId": order.customer_id.value,
                    "shippingClass": shipping_class,
                }

                # Inject OTel trace context for distributed tracing
                headers: dict[str, str] = {}
                inject(headers)

                url = f"{self._base_url}/api/shipments/schedule"
                response = self._client.post(
                    url, json=wire_request, headers=headers
                )
                response.raise_for_status()
                wire_response = response.json()

                shipment_id = wire_response.get("shipmentId")
                if shipment_id:
                    span.set_attribute("shipment.id", shipment_id)
                    return Scheduled(shipment_id=shipment_id)

                return ShipmentFailure(
                    detail="Shipping response missing shipmentId"
                )

            except httpx.HTTPStatusError as e:
                logger.warning(
                    "Shipping REST call failed",
                    status=e.response.status_code,
                    error=str(e),
                )
                return ShipmentFailure(
                    detail=f"Shipping REST call failed: HTTP {e.response.status_code}"
                )

            except httpx.HTTPError as e:
                logger.warning(
                    "Shipping REST call failed", error=str(e)
                )
                return ShipmentFailure(
                    detail=f"Shipping REST call failed: {e}"
                )
