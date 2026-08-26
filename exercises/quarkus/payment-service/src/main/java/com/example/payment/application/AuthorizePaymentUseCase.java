package com.example.payment.application;

import com.example.payment.domain.identifier.PaymentContextKey;
import com.example.payment.domain.model.Authorization;
import com.example.workshop.observability.BaggageHelpers;
import com.example.workshop.observability.DomainContext;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The authorize-payment use case.
 *
 * <p>Workshop simplification: the decision is driven by a configured
 * customer-id suffix. Customers whose id ends with the configured
 * decline-suffix get DECLINED; all others get AUTHORIZED. This makes the
 * payment-decline scenario in {@code load-test-with-bug.sh} deterministic.
 */
@ApplicationScoped
public class AuthorizePaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthorizePaymentUseCase.class);

    private final String declineSuffix;
    private final MeterRegistry meterRegistry;

    public AuthorizePaymentUseCase(
            @ConfigProperty(name = "workshop.payment.simulator.decline-suffix",
                    defaultValue = "_decline") String declineSuffix,
            MeterRegistry meterRegistry) {
        this.declineSuffix = declineSuffix;
        this.meterRegistry = meterRegistry;
    }

    @WithSpan("Payment.Authorize")                           // <-- domain-named span
    public Authorization authorize(AuthorizePaymentCommand command) {

        try (var ctx = DomainContext.open(
                PaymentContextKey.ORDER_ID.of(command.orderId()),
                PaymentContextKey.CUSTOMER_ID.of(command.customerId()))) {

            String tier = BaggageHelpers.get("customer.tier");
            if (tier == null) tier = "unknown";

            Span span = Span.current();
            span.setAttribute("order.id", command.orderId());
            span.setAttribute("customer.id", command.customerId());
            span.setAttribute("customer.tier", tier);
            span.setAttribute("payment.method", command.paymentMethod());
            span.setAttribute("payment.amount", command.amount().doubleValue());
            span.setAttribute("payment.currency", command.currency());

            Authorization auth = decideOutcome(command);

            ctx.include(PaymentContextKey.AUTHORIZATION_ID.of(auth.id().value()));
            span.setAttribute("authorization.id", auth.id().value());
            span.setAttribute("authorization.outcome", auth.outcome().name());

            log.info("Authorization {}: {} (amount={} {})",
                    auth.outcome(), auth.id(), command.amount(), command.currency());

            meterRegistry.counter("payment_authorizations_total",
                    "outcome", auth.outcome().name(),
                    "tier", tier).increment();

            return auth;
        }
    }

    private Authorization decideOutcome(AuthorizePaymentCommand command) {
        if (command.customerId().toLowerCase().endsWith(declineSuffix)) {
            return Authorization.declined(
                    command.orderId(), command.amount(), command.currency(),
                    "card declined - workshop simulator");
        }
        return Authorization.authorized(
                command.orderId(), command.amount(), command.currency());
    }
}
