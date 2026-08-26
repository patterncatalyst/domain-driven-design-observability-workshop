package com.example.order.application;

import com.example.order.domain.event.DomainEvent;
import com.example.order.domain.event.OrderCancelled;
import com.example.order.domain.event.OrderConfirmed;
import com.example.order.domain.event.OrderPlaced;
import com.example.order.domain.identifier.OrderContextKey;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.outbound.AuthorizationOutcome;
import com.example.order.domain.outbound.InventoryPort;
import com.example.order.domain.outbound.OrderEventPublisher;
import com.example.order.domain.outbound.PaymentPort;
import com.example.order.domain.outbound.ReservationOutcome;
import com.example.order.domain.outbound.ShipmentOutcome;
import com.example.order.domain.outbound.ShippingPort;
import com.example.order.domain.service.CustomerProfile;
import com.example.order.domain.service.CustomerProfileLookup;
import com.example.workshop.observability.BaggageHelpers;
import com.example.workshop.observability.DomainContext;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.context.Scope;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The checkout saga - the application use case that orchestrates calls
 * across the Inventory, Payment, and Shipping bounded contexts and emits
 * the resulting domain events to Notification (via Kafka).
 *
 * <h2>Architecture</h2>
 *
 * <p>The saga depends only on domain types and outbound ports. It has
 * no awareness of REST, gRPC, JSON, Kafka, or any specific transport.
 * Adapter implementations of those ports live in {@code infrastructure/}.
 *
 * <h2>Observability instrumentation reference</h2>
 *
 * <p>This class is the canonical example for Modules 3a, 3b, 3c. The
 * {@code main} branch ships it fully instrumented; the workshop's
 * retrograde branches strip pieces back so participants experience adding
 * each layer themselves. Specifically:
 *
 * <ul>
 *   <li><b>Module 3a (structured logs)</b>: the try-with-resources
 *       {@code DomainContext.open(...)} block at the top of
 *       {@code checkout()} populates SLF4J MDC with this context's
 *       identifiers. Every log line emitted in that scope - and
 *       transitively from the adapters this method calls - carries
 *       {@code order.id}, {@code customer.id}, {@code cart.id}.</li>
 *   <li><b>Module 3b (domain spans + business attributes)</b>: the
 *       {@code @WithSpan("Order.Checkout")} annotation names the saga's
 *       span in domain language. {@code Span.current().setAttribute(...)}
 *       calls add business-meaningful attributes ({@code order.value},
 *       {@code customer.tier}, {@code order.line_items_count}). The
 *       {@code BaggageHelpers.put(...)} block propagates
 *       {@code customer.tier} downstream so spans in other services
 *       can be tagged by tier without each service re-fetching the
 *       profile.</li>
 *   <li><b>Module 3c (business metrics)</b>: the three custom Micrometer
 *       meters - {@code checkout_orders_in_payment_verification} (gauge),
 *       {@code checkout_outcomes_total} (counter with {@code outcome}
 *       and {@code tier} labels), and {@code checkout_duration_seconds}
 *       (timer) - answer the three business questions from the module.</li>
 * </ul>
 */
@ApplicationScoped
public class CheckoutSaga {

    private static final Logger log = LoggerFactory.getLogger(CheckoutSaga.class);

    // -----------------------------------------------------------------------
    // Collaborators - all injected via constructor
    // -----------------------------------------------------------------------
    private final InventoryPort inventory;
    private final PaymentPort payment;
    private final ShippingPort shipping;
    private final OrderEventPublisher events;
    private final CustomerProfileLookup customerLookup;

    // -----------------------------------------------------------------------
    // Custom metrics - registered eagerly in the constructor
    // -----------------------------------------------------------------------
    private final AtomicInteger ordersInPaymentVerification = new AtomicInteger(0);
    private final MeterRegistry meterRegistry;
    private final DoubleHistogram checkoutDurationHistogram;

    public CheckoutSaga(InventoryPort inventory,
                        PaymentPort payment,
                        ShippingPort shipping,
                        OrderEventPublisher events,
                        CustomerProfileLookup customerLookup,
                        MeterRegistry meterRegistry) {
        this.inventory = inventory;
        this.payment = payment;
        this.shipping = shipping;
        this.events = events;
        this.customerLookup = customerLookup;
        this.meterRegistry = meterRegistry;

        // Module 3c: gauge for "orders currently in payment verification"
        meterRegistry.gauge(
                "checkout_orders_in_payment_verification",
                ordersInPaymentVerification,
                AtomicInteger::get);

        // Module 3c: end-to-end checkout duration, recorded via the OpenTelemetry
        // API directly (not a Micrometer Timer) so the exported name matches the
        // Python and C# services and the Grafana dashboards exactly:
        // workshop_checkout_duration_seconds{_bucket,_count,_sum}. A Micrometer
        // Timer would export a milliseconds unit and become
        // ..._seconds_milliseconds, which no dashboard queries.
        this.checkoutDurationHistogram = GlobalOpenTelemetry.get()
                .getMeter("order-service")
                .histogramBuilder("checkout_duration_seconds")
                .setDescription("End-to-end checkout saga duration")
                .setUnit("s")
                .setExplicitBucketBoundariesAdvice(List.of(
                        0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0))
                .build();
    }

    /**
     * Execute the checkout saga. Returns a {@link CheckoutResult} that the
     * caller (typically a JAX-RS resource) translates into a response.
     */
    @WithSpan("Order.Checkout")
    public CheckoutResult checkout(CheckoutCommand command) {
        var orderId = OrderId.generate();

        // Module 3a: populate MDC with this context's identifiers for the
        // duration of the saga. All log lines in this scope carry these.
        try (var ctx = DomainContext.open(
                OrderContextKey.ORDER_ID.of(orderId.value()),
                OrderContextKey.CUSTOMER_ID.of(command.customerId().value()),
                OrderContextKey.CART_ID.of(command.cartId().value()))) {

            // Place the order in the domain.
            var order = Order.place(orderId, command.customerId(),
                    command.cartId(), command.lineItems());

            // Look up the customer's tier - drives downstream baggage and
            // the per-tier metrics breakdown.
            CustomerProfile profile = customerLookup.lookup(command.customerId());

            // Module 3b: annotate this span with business attributes.
            Span span = Span.current();
            span.setAttribute("order.id", order.id().value());
            span.setAttribute("order.value", order.total().amount().doubleValue());
            span.setAttribute("order.line_items_count", order.lineItems().size());
            span.setAttribute("customer.id", order.customerId().value());
            span.setAttribute("customer.tier", profile.tier().name());

            log.info("Checkout starting: total={} items={}",
                    order.total().amount(), order.lineItems().size());

            // Module 3b: propagate customer.tier downstream as baggage so
            // every service in the saga can tag its own observability by
            // tier without re-fetching.
            try (Scope baggageScope = BaggageHelpers.put(
                    OrderContextKey.CUSTOMER_TIER.of(profile.tier().name()))) {

                return runSaga(order, profile);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Saga steps
    // ------------------------------------------------------------------------

    private CheckoutResult runSaga(Order order, CustomerProfile profile) {
        long startNanos = System.nanoTime();
        try {
            // Step 1: place-event. Always published, regardless of saga outcome -
            // OrderPlaced records the customer's intent.
            publishEvent(OrderPlaced.from(order));

            // Step 2: reserve stock. Sealed-interface switch is exhaustive,
            // so every path produces exactly one CheckoutResult and exactly
            // one outcome metric increment.
            var reservationOutcome = inventory.reserve(order);
            return switch (reservationOutcome) {
                case ReservationOutcome.Reserved r ->
                        continueAfterReservation(order, profile, r.reservationId(), startNanos);
                case ReservationOutcome.Unavailable u ->
                        cancelAndRecord(order, profile, "inventory", u.reason(), startNanos);
                case ReservationOutcome.Failure f ->
                        cancelAndRecord(order, profile, "inventory",
                                "inventory failure: " + f.detail(), startNanos);
            };
        } catch (RuntimeException e) {
            log.error("Saga aborted unexpectedly", e);
            return cancelAndRecord(order, profile, "saga", e.getMessage(), startNanos);
        }
    }

    private CheckoutResult continueAfterReservation(Order order,
                                                    CustomerProfile profile,
                                                    String reservationId,
                                                    long startNanos) {
        // Step 3: authorize payment. Module 3c: gauge in/out around this call.
        ordersInPaymentVerification.incrementAndGet();
        AuthorizationOutcome authOutcome;
        try {
            authOutcome = payment.authorize(order);
        } finally {
            ordersInPaymentVerification.decrementAndGet();
        }

        return switch (authOutcome) {
            case AuthorizationOutcome.Authorized a ->
                    continueAfterAuthorization(order, profile, reservationId,
                            a.authorizationId(), startNanos);
            case AuthorizationOutcome.Declined d ->
                    cancelAndRecord(order, profile, "payment", d.reason(), startNanos);
            case AuthorizationOutcome.Failure f ->
                    cancelAndRecord(order, profile, "payment",
                            "payment failure: " + f.detail(), startNanos);
        };
    }

    private CheckoutResult continueAfterAuthorization(Order order,
                                                      CustomerProfile profile,
                                                      String reservationId,
                                                      String authorizationId,
                                                      long startNanos) {
        // Step 4: schedule shipment.
        var shippingOutcome = shipping.schedule(order);
        return switch (shippingOutcome) {
            case ShipmentOutcome.Scheduled s -> {
                var confirmed = order.confirm();
                publishEvent(OrderConfirmed.from(confirmed, reservationId,
                        authorizationId, s.shipmentId()));
                recordOutcome("success", profile, startNanos);
                yield new CheckoutResult.Confirmed(
                        confirmed.id(), reservationId, authorizationId, s.shipmentId());
            }
            case ShipmentOutcome.Failure f ->
                    cancelAndRecord(order, profile, "shipping",
                            "shipping failure: " + f.detail(), startNanos);
        };
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private CheckoutResult cancelAndRecord(Order order,
                                           CustomerProfile profile,
                                           String failedAt,
                                           String reason,
                                           long startNanos) {
        var cancelled = order.cancel(reason);
        publishEvent(OrderCancelled.from(cancelled, failedAt, reason));
        recordOutcome("cancelled_" + failedAt, profile, startNanos);
        log.warn("Checkout cancelled at {}: {}", failedAt, reason);
        return new CheckoutResult.Cancelled(cancelled.id(), failedAt, reason);
    }

    private void publishEvent(DomainEvent event) {
        try {
            events.publish(event);
        } catch (RuntimeException e) {
            // Event publication failure should not abort the saga - log loudly
            // and continue. A real system would also write the event to an
            // outbox table for retry.
            log.error("Failed to publish {}: {}", event.eventType(), e.getMessage(), e);
        }
    }

    private void recordOutcome(String outcome, CustomerProfile profile, long startNanos) {
        long durationNanos = System.nanoTime() - startNanos;
        // Module 3c: per-outcome, per-tier counter and timer.
        meterRegistry.counter("checkout_outcomes_total",
                "outcome", outcome,
                "tier", profile.tier().name()).increment();
        checkoutDurationHistogram.record(durationNanos / 1_000_000_000.0,
                Attributes.of(
                        AttributeKey.stringKey("outcome"), outcome,
                        AttributeKey.stringKey("tier"), profile.tier().name()));
    }
}
