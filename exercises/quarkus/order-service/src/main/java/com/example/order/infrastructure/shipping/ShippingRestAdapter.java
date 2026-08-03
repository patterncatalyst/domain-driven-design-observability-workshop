package com.example.order.infrastructure.shipping;

import com.example.order.domain.model.Order;
import com.example.order.domain.outbound.ShipmentOutcome;
import com.example.order.domain.outbound.ShippingPort;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin REST client adapter for {@link ShippingPort}. Parallel to
 * {@link com.example.order.infrastructure.payment.PaymentRestAdapter} -
 * Order and Shipping share vocabulary closely enough that a full ACL
 * isn't justified. See Module 5 for the discussion.
 */
@ApplicationScoped
public class ShippingRestAdapter implements ShippingPort {

    private static final Logger log = LoggerFactory.getLogger(ShippingRestAdapter.class);

    private final ShippingRestClient client;

    public ShippingRestAdapter(@RestClient ShippingRestClient client) {
        this.client = client;
    }

    @Override
    @WithSpan("Order.Shipping.Schedule")
    public ShipmentOutcome schedule(Order order) {
        // The workshop scenario uses a single shipping class. A real Order
        // would carry the customer's preferred class on the order itself.
        String shippingClass = "standard";
        Span span = Span.current();
        span.setAttribute("shipping.class", shippingClass);
        span.setAttribute("order.line_items_count", order.lineItems().size());

        try {
            var response = client.schedule(new ShippingRestClient.ScheduleRequest(
                    order.id().value(),
                    order.customerId().value(),
                    shippingClass));

            span.setAttribute("shipment.id", response.shipmentId());
            return new ShipmentOutcome.Scheduled(response.shipmentId());

        } catch (WebApplicationException e) {
            log.warn("Shipping REST call failed: {}", e.getMessage());
            return new ShipmentOutcome.Failure(
                    "transport",
                    "Shipping REST call failed: " + e.getMessage(),
                    e);
        }
    }
}
