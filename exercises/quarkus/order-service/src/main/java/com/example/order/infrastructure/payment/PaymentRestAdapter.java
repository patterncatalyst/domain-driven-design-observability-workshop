package com.example.order.infrastructure.payment;

import com.example.order.domain.model.Order;
import com.example.order.domain.outbound.AuthorizationOutcome;
import com.example.order.domain.outbound.PaymentPort;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin REST client adapter for {@link PaymentPort}.
 *
 * <p>Module 5 contrasts this with {@link
 * com.example.order.infrastructure.inventory.InventoryRestAdapter}: there's
 * no full Anti-Corruption Layer here because Order and Payment share
 * vocabulary closely enough that the cost of a full ACL outweighs the
 * benefit. We do still:
 *
 * <ul>
 *   <li>name the span in domain language ({@code Order.Payment.Authorize})</li>
 *   <li>tag the span with business attributes ({@code payment.method},
 *       {@code order.value}) for trace search</li>
 *   <li>convert transport failures to typed outcomes so the saga's switch
 *       remains exhaustive</li>
 * </ul>
 *
 * <p>What we don't do: a separate {@code dto/} package, separate
 * translation methods, or a drift counter. Those have a place when
 * vocabularies differ, and adding them here would be Khononov's
 * "intrusive coupling masquerading as discipline."
 */
@ApplicationScoped
public class PaymentRestAdapter implements PaymentPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentRestAdapter.class);

    private final PaymentRestClient client;

    public PaymentRestAdapter(@RestClient PaymentRestClient client) {
        this.client = client;
    }

    @Override
    @WithSpan("Order.Payment.Authorize")
    public AuthorizationOutcome authorize(Order order) {
        Span span = Span.current();
        // The workshop scenario hard-codes credit_card as the only method.
        // A real Order would carry the method on the order itself.
        String paymentMethod = "credit_card";
        span.setAttribute("payment.method", paymentMethod);
        span.setAttribute("order.value", order.total().amount().doubleValue());

        try {
            var request = new PaymentRestClient.AuthorizeRequest(
                    order.id().value(),
                    order.customerId().value(),
                    order.total().amount(),
                    order.total().currency().getCurrencyCode(),
                    paymentMethod);

            var response = client.authorize(request);

            return switch (response.outcome()) {
                case AUTHORIZED ->
                        new AuthorizationOutcome.Authorized(response.authorizationId());
                case DECLINED ->
                        new AuthorizationOutcome.Declined(
                                response.reason() != null ? response.reason() : "declined");
            };

        } catch (WebApplicationException e) {
            log.warn("Payment REST call failed: {}", e.getMessage());
            return new AuthorizationOutcome.Failure(
                    "transport",
                    "Payment REST call failed: " + e.getMessage(),
                    e);
        }
    }
}
