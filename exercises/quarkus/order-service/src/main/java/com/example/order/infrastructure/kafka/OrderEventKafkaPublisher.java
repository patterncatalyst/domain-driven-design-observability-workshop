package com.example.order.infrastructure.kafka;

import com.example.order.domain.event.DomainEvent;
import com.example.order.domain.event.OrderCancelled;
import com.example.order.domain.event.OrderConfirmed;
import com.example.order.domain.event.OrderPlaced;
import com.example.order.domain.identifier.OrderContextKey;
import com.example.order.domain.outbound.OrderEventPublisher;
import com.example.workshop.observability.KafkaHeaderPropagator;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

/**
 * Kafka-backed implementation of {@link OrderEventPublisher}.
 *
 * <p>Two responsibilities worth highlighting:
 *
 * <ol>
 *   <li><strong>Explicit header propagation.</strong> Each event publication
 *       writes Order's domain identifiers into the Kafka record's headers
 *       via {@link KafkaHeaderPropagator}. We do this <em>explicitly</em>
 *       per identifier rather than via a "copy everything in MDC"
 *       convenience - the explicitness is the design (see the
 *       {@code shared-observability} module README).</li>
 *   <li><strong>Domain-named span.</strong> The publish operation runs
 *       inside an {@code Order.Events.Publish} span with attributes
 *       identifying the event type and order. Module 4's debugging
 *       exercise depends on these identifiers being present in headers
 *       so the Notification consumer can rebuild MDC and the trace can
 *       follow the event across the asynchronous boundary.</li>
 * </ol>
 *
 * <p>The OpenTelemetry Quarkus extension automatically writes
 * {@code traceparent} and {@code baggage} headers via Kafka interceptors
 * - we don't need to do that ourselves. Our headers are a separate,
 * domain-level concern.
 */
@ApplicationScoped
public class OrderEventKafkaPublisher implements OrderEventPublisher {

    private final Emitter<DomainEvent> emitter;

    public OrderEventKafkaPublisher(@Channel("order-events") Emitter<DomainEvent> emitter) {
        this.emitter = emitter;
    }

    @Override
    @WithSpan("Order.Events.Publish")
    public void publish(DomainEvent event) {
        Span span = Span.current();
        span.setAttribute("event.type", event.eventType());
        span.setAttribute("order.id", event.orderId().value());

        // Build Kafka headers carrying our domain identifiers. We pick which
        // identifiers to propagate based on the event type - for example,
        // OrderPlaced carries the cart id (so consumers can correlate back
        // to the originating cart) while OrderConfirmed doesn't need it.
        var headers = new RecordHeaders();
        KafkaHeaderPropagator.write(headers,
                OrderContextKey.ORDER_ID.of(event.orderId().value()));

        switch (event) {
            case OrderPlaced p -> {
                KafkaHeaderPropagator.write(headers,
                        OrderContextKey.CUSTOMER_ID.of(p.customerId().value()));
                KafkaHeaderPropagator.write(headers,
                        OrderContextKey.CART_ID.of(p.cartId().value()));
            }
            case OrderConfirmed c -> {
                KafkaHeaderPropagator.write(headers,
                        OrderContextKey.CUSTOMER_ID.of(c.customerId().value()));
            }
            case OrderCancelled x -> {
                KafkaHeaderPropagator.write(headers,
                        OrderContextKey.CUSTOMER_ID.of(x.customerId().value()));
            }
        }

        Message<DomainEvent> message = Message.of(event)
                .withMetadata(Metadata.of(
                        OutgoingKafkaRecordMetadata.<String>builder()
                                .withKey(event.orderId().value())
                                .withHeaders(headers)
                                .build()));

        emitter.send(message);
    }
}
