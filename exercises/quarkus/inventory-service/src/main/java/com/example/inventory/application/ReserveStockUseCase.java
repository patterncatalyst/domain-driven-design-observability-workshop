package com.example.inventory.application;

import com.example.inventory.domain.identifier.InventoryContextKey;
import com.example.inventory.domain.model.ProductCode;
import com.example.inventory.domain.model.Reservation;
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
 * The reserve-stock use case - the single business-logic entry point that
 * both transports (REST and gRPC) delegate into.
 *
 * <p>Workshop simplification: stock-availability decisions are driven by
 * SKU prefixes via configured properties. SKUs starting with the
 * out-of-stock prefix produce {@code UNAVAILABLE}; SKUs starting with
 * the partial prefix produce {@code PARTIAL}; everything else produces
 * {@code AVAILABLE}. This is enough determinism for Module 4's debugging
 * exercise without requiring real persistent stock state.
 *
 * <h2>Observability instrumentation reference</h2>
 *
 * <ul>
 *   <li><strong>MDC:</strong> a {@code DomainContext} populated at the use-case
 *       entry tags every log line with {@code order.id}, {@code customer.id},
 *       and (after success) {@code reservation.id}.</li>
 *   <li><strong>Spans:</strong> {@code @WithSpan("Inventory.Reserve")} names
 *       the operation in domain language. We also tag the span with
 *       {@code customer.tier} read from baggage - the producer of that
 *       baggage is Order, and we consume it here without re-fetching
 *       the customer profile.</li>
 *   <li><strong>Metrics:</strong> a per-status, per-tier counter
 *       {@code inventory_reservations_total{status,tier}} feeds the
 *       saga dashboard's "stock unavailability by tier" panel.</li>
 * </ul>
 */
@ApplicationScoped
public class ReserveStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReserveStockUseCase.class);

    private final String partialPrefix;
    private final String outOfStockPrefix;
    private final MeterRegistry meterRegistry;

    public ReserveStockUseCase(
            @ConfigProperty(name = "workshop.inventory.simulator.partial-prefix",
                    defaultValue = "PARTIAL_") String partialPrefix,
            @ConfigProperty(name = "workshop.inventory.simulator.out-of-stock-prefix",
                    defaultValue = "OUT_") String outOfStockPrefix,
            MeterRegistry meterRegistry) {
        this.partialPrefix = partialPrefix;
        this.outOfStockPrefix = outOfStockPrefix;
        this.meterRegistry = meterRegistry;
    }

    @WithSpan("Inventory.Reserve")
    public Reservation reserve(ReserveStockCommand command) {

        try (var ctx = DomainContext.open(
                InventoryContextKey.ORDER_ID.of(command.orderId()),
                InventoryContextKey.CUSTOMER_ID.of(command.customerId()))) {

            // Read tier from baggage (set by Order at the saga entry point).
            // If absent (e.g., direct curl test), default to "unknown" -
            // the metric label is bounded so this is safe.
            String tier = BaggageHelpers.get("customer.tier");
            if (tier == null) tier = "unknown";

            Span span = Span.current();
            span.setAttribute("order.id", command.orderId());
            span.setAttribute("customer.id", command.customerId());
            span.setAttribute("customer.tier", tier);
            span.setAttribute("reservation.line_count", command.lines().size());

            // Decide outcome based on the SKU patterns in the lines.
            Reservation reservation = decideOutcome(command);

            // Add reservation.id to MDC for any subsequent log lines.
            ctx.include(InventoryContextKey.RESERVATION_ID.of(reservation.id().value()));
            span.setAttribute("reservation.id", reservation.id().value());
            span.setAttribute("reservation.status", reservation.status().name());

            log.info("Reservation {}: {} (lines={})",
                    reservation.status(), reservation.id(), command.lines().size());

            // Per-status, per-tier counter for the dashboard breakdown.
            meterRegistry.counter("inventory_reservations_total",
                    "status", reservation.status().name(),
                    "tier", tier).increment();

            return reservation;
        }
    }

    private Reservation decideOutcome(ReserveStockCommand command) {
        boolean anyOutOfStock = command.lines().stream()
                .map(li -> li.productCode().value())
                .anyMatch(p -> p.startsWith(outOfStockPrefix));
        if (anyOutOfStock) {
            return Reservation.unavailable(command.orderId(), command.lines(),
                    "one or more items not in stock");
        }

        boolean anyPartial = command.lines().stream()
                .map(li -> li.productCode().value())
                .anyMatch(p -> p.startsWith(partialPrefix));
        if (anyPartial) {
            return Reservation.partial(command.orderId(), command.lines(),
                    "some quantities reduced");
        }

        return Reservation.available(command.orderId(), command.lines());
    }
}
