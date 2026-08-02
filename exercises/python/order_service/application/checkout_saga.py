"""Checkout saga -- the application use case that orchestrates calls
across the Inventory, Payment, and Shipping bounded contexts and emits
the resulting domain events to Notification (via Kafka).

Architecture
~~~~~~~~~~~~
The saga depends only on domain types and outbound ports. It has no
awareness of REST, gRPC, JSON, Kafka, or any specific transport. Adapter
implementations of those ports live in ``infrastructure/``.

Observability instrumentation reference
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
This class is the canonical example for Modules 3a, 3b, 3c:

- **Module 3a** (structured logs): ``DomainContext`` populates structlog
  context with ``order.id``, ``customer.id``, ``cart.id``.
- **Module 3b** (domain spans + business attributes): ``@WithSpan``-style
  span naming + ``set_baggage("customer.tier")`` propagation.
- **Module 3c** (business metrics): ``checkout_outcomes_total`` counter
  and ``checkout_duration_seconds`` histogram.
"""

from __future__ import annotations

import time
from dataclasses import dataclass

import structlog
from opentelemetry import metrics, trace

from domain.events import OrderCancelled, OrderConfirmed, OrderPlaced
from domain.identifiers import OrderContextKey
from domain.models import Order, OrderId, OrderStatus
from domain.ports import (
    Authorized,
    AuthorizationFailure,
    AuthorizationOutcome,
    Declined,
    InventoryPort,
    OrderEventPublisher,
    PaymentPort,
    ReservationFailure,
    ReservationOutcome,
    Reserved,
    Scheduled,
    ShipmentFailure,
    ShipmentOutcome,
    ShippingPort,
    Unavailable,
)
from domain.services import CustomerProfile, CustomerProfileLookup
from shared_observability import DomainContext, set_baggage

logger = structlog.get_logger()
tracer = trace.get_tracer("order-service")
meter = metrics.get_meter("order-service")

# Module 3c: business metrics
checkout_outcomes_counter = meter.create_counter(
    "checkout_outcomes_total",
    description="Total checkout outcomes by result and customer tier",
)

checkout_duration_histogram = meter.create_histogram(
    "checkout_duration_seconds",
    description="End-to-end checkout saga duration",
    unit="s",
)


# ---------------------------------------------------------------------------
# Command & Result
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class CheckoutCommand:
    """Input to the checkout saga use case.

    Plain dataclass, no framework dependencies. Inputs are expressed in
    domain types -- the conversion from inbound HTTP DTOs lives in the
    web adapter.
    """

    customer_id: str
    cart_id: str
    line_items: list  # list[LineItem] -- kept untyped to avoid circular import
    payment_method: str = "credit_card"
    shipping_class: str = "standard"


@dataclass(frozen=True)
class CheckoutResult:
    """Output of the checkout saga.

    A single dataclass covers both confirmed and cancelled outcomes.
    The ``status`` field discriminates between them.
    """

    order_id: str
    status: str  # "CONFIRMED" or "CANCELLED"
    reservation_id: str | None = None
    authorization_id: str | None = None
    shipment_id: str | None = None
    message: str = ""


# ---------------------------------------------------------------------------
# Saga
# ---------------------------------------------------------------------------


class CheckoutSaga:
    """Orchestration-based saga for the checkout flow.

    Coordinates Inventory -> Payment -> Shipping, publishing domain events
    at each milestone. If any step fails, the order is cancelled and an
    ``OrderCancelled`` event is published.
    """

    def __init__(
        self,
        inventory: InventoryPort,
        payment: PaymentPort,
        shipping: ShippingPort,
        events: OrderEventPublisher,
        customer_lookup: CustomerProfileLookup,
    ) -> None:
        self._inventory = inventory
        self._payment = payment
        self._shipping = shipping
        self._events = events
        self._customer_lookup = customer_lookup

    def checkout(self, command: CheckoutCommand) -> CheckoutResult:
        """Execute the checkout saga.

        Returns a CheckoutResult that the caller (typically a FastAPI
        route) translates into an HTTP response.
        """
        from domain.models import CartId, CustomerId, LineItem, Money, Sku

        order_id = OrderId.generate()

        # Module 3a: populate structlog context with domain identifiers
        with DomainContext(
            OrderContextKey.ORDER_ID.of(order_id.value),
            OrderContextKey.CUSTOMER_ID.of(command.customer_id),
            OrderContextKey.CART_ID.of(command.cart_id),
        ):
            # Build domain objects from command
            customer_id = CustomerId.of(command.customer_id)
            cart_id = CartId.of(command.cart_id)
            line_items = command.line_items

            # Place the order in the domain
            order = Order.place(order_id, customer_id, cart_id, line_items)

            # Look up the customer's tier
            profile = self._customer_lookup.lookup(customer_id)

            # Module 3b: annotate this span with business attributes
            with tracer.start_as_current_span("Order.Checkout") as span:
                span.set_attribute("order.id", order.id.value)
                span.set_attribute("order.value", float(order.total().amount))
                span.set_attribute(
                    "order.line_items_count", len(order.line_items)
                )
                span.set_attribute("customer.id", order.customer_id.value)
                span.set_attribute("customer.tier", profile.tier.value)

                logger.info(
                    "Checkout starting",
                    total=str(order.total().amount),
                    items=len(order.line_items),
                )

                # Module 3b: propagate customer.tier downstream as baggage
                set_baggage("customer.tier", profile.tier.value)

                return self._run_saga(order, profile)

    # ------------------------------------------------------------------------
    # Saga steps
    # ------------------------------------------------------------------------

    def _run_saga(
        self, order: Order, profile: CustomerProfile
    ) -> CheckoutResult:
        start_time = time.monotonic()
        try:
            # Step 1: publish OrderPlaced (always, regardless of saga outcome)
            self._publish_event(OrderPlaced.from_order(order))

            # Step 2: reserve stock
            reservation_outcome = self._inventory.reserve(order)

            if isinstance(reservation_outcome, Reserved):
                return self._continue_after_reservation(
                    order,
                    profile,
                    reservation_outcome.reservation_id,
                    start_time,
                )
            elif isinstance(reservation_outcome, Unavailable):
                return self._cancel_and_record(
                    order,
                    profile,
                    "inventory",
                    reservation_outcome.reason,
                    start_time,
                )
            else:  # ReservationFailure
                return self._cancel_and_record(
                    order,
                    profile,
                    "inventory",
                    f"inventory failure: {reservation_outcome.detail}",
                    start_time,
                )

        except Exception as e:
            logger.error("Saga aborted unexpectedly", error=str(e))
            return self._cancel_and_record(
                order, profile, "saga", str(e), start_time
            )

    def _continue_after_reservation(
        self,
        order: Order,
        profile: CustomerProfile,
        reservation_id: str,
        start_time: float,
    ) -> CheckoutResult:
        # Step 3: authorize payment
        auth_outcome = self._payment.authorize(order)

        if isinstance(auth_outcome, Authorized):
            return self._continue_after_authorization(
                order,
                profile,
                reservation_id,
                auth_outcome.authorization_id,
                start_time,
            )
        elif isinstance(auth_outcome, Declined):
            return self._cancel_and_record(
                order,
                profile,
                "payment",
                auth_outcome.reason,
                start_time,
            )
        else:  # AuthorizationFailure
            return self._cancel_and_record(
                order,
                profile,
                "payment",
                f"payment failure: {auth_outcome.detail}",
                start_time,
            )

    def _continue_after_authorization(
        self,
        order: Order,
        profile: CustomerProfile,
        reservation_id: str,
        authorization_id: str,
        start_time: float,
    ) -> CheckoutResult:
        # Step 4: schedule shipment
        shipping_outcome = self._shipping.schedule(order)

        if isinstance(shipping_outcome, Scheduled):
            confirmed = order.confirm()
            self._publish_event(
                OrderConfirmed.from_order(
                    confirmed,
                    reservation_id,
                    authorization_id,
                    shipping_outcome.shipment_id,
                )
            )
            self._record_outcome("success", profile, start_time)
            return CheckoutResult(
                order_id=confirmed.id.value,
                status="CONFIRMED",
                reservation_id=reservation_id,
                authorization_id=authorization_id,
                shipment_id=shipping_outcome.shipment_id,
                message="Order confirmed successfully",
            )
        else:  # ShipmentFailure
            return self._cancel_and_record(
                order,
                profile,
                "shipping",
                f"shipping failure: {shipping_outcome.detail}",
                start_time,
            )

    # ------------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------------

    def _cancel_and_record(
        self,
        order: Order,
        profile: CustomerProfile,
        failed_at: str,
        reason: str,
        start_time: float,
    ) -> CheckoutResult:
        cancelled = order.cancel(reason)
        self._publish_event(
            OrderCancelled.from_order(cancelled, failed_at, reason)
        )
        self._record_outcome(f"cancelled_{failed_at}", profile, start_time)
        logger.warning(
            "Checkout cancelled", failed_at=failed_at, reason=reason
        )
        return CheckoutResult(
            order_id=cancelled.id.value,
            status="CANCELLED",
            message=f"Cancelled at {failed_at}: {reason}",
        )

    def _publish_event(self, event) -> None:
        """Publish a domain event, swallowing failures to avoid aborting the saga."""
        try:
            self._events.publish(event)
        except Exception as e:
            # Event publication failure should not abort the saga
            logger.error(
                "Failed to publish event",
                event_type=event.event_type,
                error=str(e),
            )

    def _record_outcome(
        self, outcome: str, profile: CustomerProfile, start_time: float
    ) -> None:
        """Record per-outcome, per-tier counter and duration histogram."""
        duration = time.monotonic() - start_time
        checkout_outcomes_counter.add(
            1,
            {"outcome": outcome, "tier": profile.tier.value},
        )
        checkout_duration_histogram.record(
            duration,
            {"outcome": outcome, "tier": profile.tier.value},
        )
