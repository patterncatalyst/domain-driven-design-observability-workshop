"""Schedule Shipment use case.

Orchestrates shipment scheduling: opens a domain context for structured
logging, reads customer tier from OTel baggage, creates the shipment
aggregate, and records metrics.
"""

from __future__ import annotations

from dataclasses import dataclass

import structlog
from opentelemetry import metrics, trace

from domain.identifiers import ShippingContextKey
from domain.models import Shipment
from shared_observability import DomainContext, get_baggage

logger = structlog.get_logger()
tracer = trace.get_tracer(__name__)
meter = metrics.get_meter(__name__)

shipments_counter = meter.create_counter(
    "shipping_shipments_scheduled_total",
    description="Total shipments scheduled by tier",
)


@dataclass(frozen=True)
class ScheduleShipmentCommand:
    """Command to schedule a shipment."""

    order_id: str
    customer_id: str
    shipping_class: str


class ScheduleShipmentUseCase:
    """Schedules a shipment for a fulfilled order.

    Shipping always succeeds in this workshop model -- the outcome is
    always SCHEDULED.
    """

    @tracer.start_as_current_span("Shipping.Schedule")
    def schedule(self, command: ScheduleShipmentCommand) -> Shipment:
        span = trace.get_current_span()

        with DomainContext(
            ShippingContextKey.ORDER_ID.of(command.order_id),
            ShippingContextKey.CUSTOMER_ID.of(command.customer_id),
        ):
            # Read customer tier from OTel baggage (propagated by upstream caller)
            tier = get_baggage("customer.tier") or "unknown"

            # Enrich the span with domain attributes
            span.set_attribute("order.id", command.order_id)
            span.set_attribute("customer.id", command.customer_id)
            span.set_attribute("customer.tier", tier)
            span.set_attribute("shipping.class", command.shipping_class)

            # Schedule the shipment (always succeeds)
            shipment = Shipment.schedule(
                command.order_id, command.shipping_class
            )

            # Enrich context with result
            span.set_attribute("shipment.id", shipment.id.value)

            logger.info(
                "Shipment scheduled",
                shipment_id=shipment.id.value,
                shipping_class=shipment.shipping_class,
                estimated_days=shipment.estimated_days,
            )

            shipments_counter.add(1, {"tier": tier})

            return shipment
