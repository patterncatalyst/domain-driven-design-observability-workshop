"""Authorize Payment use case.

Orchestrates the payment authorization flow: opens a domain context for
structured logging, reads customer tier from OTel baggage, decides the
authorization outcome, and records metrics.
"""

from __future__ import annotations

from dataclasses import dataclass

import structlog
from opentelemetry import metrics, trace

from domain.identifiers import PaymentContextKey
from domain.models import Authorization, AuthorizationOutcome
from shared_observability import DomainContext, get_baggage

logger = structlog.get_logger()
tracer = trace.get_tracer(__name__)
meter = metrics.get_meter(__name__)

authorizations_counter = meter.create_counter(
    "payment_authorizations_total",
    description="Total payment authorization attempts by outcome and tier",
)


@dataclass(frozen=True)
class AuthorizePaymentCommand:
    """Command to authorize a payment."""

    order_id: str
    customer_id: str
    amount: float
    currency: str
    payment_method: str


class AuthorizePaymentUseCase:
    """Decides whether to authorize or decline a payment.

    Outcome is determined by customer-id pattern matching (simulated
    for the workshop -- no real payment gateway).
    """

    @tracer.start_as_current_span("Payment.Authorize")
    def authorize(self, command: AuthorizePaymentCommand) -> Authorization:
        span = trace.get_current_span()

        with DomainContext(
            PaymentContextKey.ORDER_ID.of(command.order_id),
            PaymentContextKey.CUSTOMER_ID.of(command.customer_id),
        ):
            # Read customer tier from OTel baggage (propagated by upstream caller)
            tier = get_baggage("customer.tier") or "unknown"

            # Enrich the span with domain attributes
            span.set_attribute("order.id", command.order_id)
            span.set_attribute("customer.id", command.customer_id)
            span.set_attribute("customer.tier", tier)
            span.set_attribute("payment.method", command.payment_method)
            span.set_attribute("payment.amount", command.amount)
            span.set_attribute("payment.currency", command.currency)

            # Decide outcome based on customer ID
            authorization = self._decide_outcome(command)

            # Enrich context with result
            span.set_attribute("authorization.id", authorization.id.value)
            span.set_attribute(
                "authorization.outcome", authorization.outcome.value
            )

            logger.info(
                "Payment authorization decided",
                authorization_id=authorization.id.value,
                outcome=authorization.outcome.value,
                reason=authorization.reason,
            )

            authorizations_counter.add(
                1,
                {"outcome": authorization.outcome.value, "tier": tier},
            )

            return authorization

    @staticmethod
    def _decide_outcome(command: AuthorizePaymentCommand) -> Authorization:
        """Simulate authorization outcome based on customer ID patterns.

        - Customer IDs containing "fail" trigger a FAILURE (gateway error).
        - Customer IDs containing "decline" trigger a DECLINED outcome.
        - Everything else is AUTHORIZED.

        "fail" is checked first so it takes precedence if both appear.
        """
        cid_lower = command.customer_id.lower()

        if "fail" in cid_lower:
            return Authorization.failure(
                command.order_id,
                command.amount,
                command.currency,
                f"Simulated failure for customer: {command.customer_id}",
            )

        if "decline" in cid_lower:
            return Authorization.declined(
                command.order_id,
                command.amount,
                command.currency,
                f"Simulated decline for customer: {command.customer_id}",
            )

        return Authorization.authorized(
            command.order_id, command.amount, command.currency
        )
