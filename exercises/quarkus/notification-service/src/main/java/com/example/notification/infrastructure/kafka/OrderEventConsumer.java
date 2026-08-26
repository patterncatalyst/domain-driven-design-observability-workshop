package com.example.notification.infrastructure.kafka;

import com.example.notification.application.SendNotificationUseCase;
import com.example.notification.domain.event.InboundOrderEvent;
import com.example.notification.domain.identifier.NotificationContextKey;
import com.example.workshop.observability.BaggageHelpers;
import com.example.workshop.observability.DomainContext;
import com.example.workshop.observability.DomainIdentifier;
import com.example.workshop.observability.KafkaHeaderPropagator;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.common.header.Headers;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * The {@code @Incoming} Kafka consumer for {@code order-events}.
 *
 * <h2>Threading model</h2>
 *
 * <p>{@code @RunOnVirtualThread} dispatches each message to its own
 * virtual thread. The handler is free to do blocking work (REST calls,
 * JDBC, etc. - in our case, just SLF4J + counter increments) without
 * starving the underlying carrier thread. This is the modern Quarkus
 * recommendation for Kafka consumers on Java 21+; we're on Java 25.
 *
 * <h2>Backpressure / commit strategy</h2>
 *
 * <p>The throttled commit strategy (set in {@code application.properties})
 * tracks per-record acks and commits the highest contiguously-acked
 * offset periodically. Module 6 lets participants tune this and the
 * {@code max.poll.records} batch size to compare latency vs throughput.
 *
 * <h2>Boundary discipline</h2>
 *
 * <p>This is the consumer entry point - the boundary where domain
 * identifiers and baggage need to be lifted from Kafka headers into
 * MDC and span attributes. The {@code SendNotificationUseCase} stays
 * pure-ish; this class does the lifting.
 *
 * <h2>Module 4 deliberate bug</h2>
 *
 * <p>The {@code cp-4-broken} branch removes the
 * {@code BaggageHelpers.get("customer.tier")} call below, replacing it
 * with a hardcoded {@code "unknown"}. The consumer still works, the
 * notifications still get "sent" (logged), but the per-tier metric
 * breakdown silently collapses to one bucket. Module 4 walks
 * participants through hunting that down via the dashboard, the trace
 * tree (where {@code customer.tier} is missing on Notification's spans
 * but present on Order/Inventory/Payment/Shipping spans), and finally
 * the offending line of code.
 */
@ApplicationScoped
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final SendNotificationUseCase useCase;

    public OrderEventConsumer(SendNotificationUseCase useCase) {
        this.useCase = useCase;
    }

    @Incoming("order-events")
    @RunOnVirtualThread
    @WithSpan("Notification.Consume")
    public CompletionStage<Void> consume(Message<InboundOrderEvent> message) {
        InboundOrderEvent event = message.getPayload();

        // Lift Order's domain identifiers from Kafka headers into MDC.
        // Each context defines its own typed identifier; we look up the
        // wire string and wrap it in our local NotificationContextKey.
        Headers headers = headersFrom(message);
        List<DomainIdentifier> ids = restoreIdentifiers(headers);

        try (var ctx = DomainContext.open(ids)) {

            // Read customer.tier from OTel baggage. The Quarkus OTel Kafka
            // interceptor extracts the W3C 'baggage' header from the record
            // and makes it the current context's baggage automatically -
            // so this just works without us touching headers.
            //
            // Module 4: this line is the bug magnet. cp-4-broken replaces
            // the get with a hardcoded "unknown".
            String customerTier = "unknown";  // BUG: forgot to read from baggage

            Span span = Span.current();
            span.setAttribute("event.type", event.getClass().getSimpleName());
            span.setAttribute("order.id", event.orderId());
            span.setAttribute("customer.tier", customerTier);

            try {
                useCase.send(event, customerTier);
                return message.ack();
            } catch (RuntimeException e) {
                log.error("Failed to send notification for {}: {}",
                        event.getClass().getSimpleName(), e.getMessage(), e);
                return message.nack(e);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static Headers headersFrom(Message<?> message) {
        return message.getMetadata(IncomingKafkaRecordMetadata.class)
                .map(IncomingKafkaRecordMetadata::getHeaders)
                .orElse(null);
    }

    /**
     * Read the workshop's {@code domain.*} headers and wrap each present
     * value in Notification's own {@link NotificationContextKey}. Order
     * publishes under {@code order.id}/{@code customer.id}/{@code cart.id};
     * we read by those wire strings via OUR enum's key().
     */
    private static List<DomainIdentifier> restoreIdentifiers(Headers headers) {
        if (headers == null) return List.of();
        var ids = new ArrayList<DomainIdentifier>(4);
        addIfPresent(ids, headers, NotificationContextKey.ORDER_ID);
        addIfPresent(ids, headers, NotificationContextKey.CUSTOMER_ID);
        addIfPresent(ids, headers, NotificationContextKey.CART_ID);
        return ids;
    }

    private static void addIfPresent(List<DomainIdentifier> out,
                                     Headers headers,
                                     NotificationContextKey key) {
        String value = KafkaHeaderPropagator.read(headers, key.key());
        if (value != null) {
            out.add(key.of(value));
        }
    }
}
