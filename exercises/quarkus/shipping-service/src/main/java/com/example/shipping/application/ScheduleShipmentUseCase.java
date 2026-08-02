package com.example.shipping.application;

import com.example.shipping.domain.identifier.ShippingContextKey;
import com.example.shipping.domain.model.Shipment;
import com.example.workshop.observability.BaggageHelpers;
import com.example.workshop.observability.DomainContext;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ScheduleShipmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ScheduleShipmentUseCase.class);

    private final MeterRegistry meterRegistry;

    public ScheduleShipmentUseCase(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @WithSpan("Shipping.Schedule")
    public Shipment schedule(ScheduleShipmentCommand command) {

        try (var ctx = DomainContext.open(
                ShippingContextKey.ORDER_ID.of(command.orderId()),
                ShippingContextKey.CUSTOMER_ID.of(command.customerId()))) {

            String tier = BaggageHelpers.get("customer.tier");
            if (tier == null) tier = "unknown";

            Span span = Span.current();
            span.setAttribute("order.id", command.orderId());
            span.setAttribute("customer.id", command.customerId());
            span.setAttribute("customer.tier", tier);
            span.setAttribute("shipping.class", command.shippingClass());
            span.setAttribute("shipping.line_items_count", command.totalLineItemCount());

            Shipment shipment = Shipment.schedule(
                    command.orderId(),
                    command.shippingClass(),
                    command.totalLineItemCount());

            ctx.include(ShippingContextKey.SHIPMENT_ID.of(shipment.id().value()));
            span.setAttribute("shipment.id", shipment.id().value());

            log.info("Shipment scheduled: {} (class={} items={})",
                    shipment.id(), command.shippingClass(), command.totalLineItemCount());

            meterRegistry.counter("shipping_shipments_scheduled_total",
                    "class", command.shippingClass(),
                    "tier", tier).increment();

            return shipment;
        }
    }
}
