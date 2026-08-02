package com.example.workshop.observability;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Round-trips {@link DomainIdentifier} values through Kafka record headers
 * so the workshop's "every log line tagged with the right domain ids"
 * discipline survives the asynchronous boundary between producer and
 * consumer services.
 *
 * <p>The shared module owns one cross-cutting decision: the
 * <em>{@code domain.}</em> prefix that distinguishes workshop-managed
 * propagation from infrastructure-managed propagation (for example
 * {@code traceparent} added automatically by the Quarkus OTel extension).
 * Beyond that, the keys themselves come from the bounded contexts.
 *
 * <h2>Producer side</h2>
 *
 * <pre>{@code
 * var record = new ProducerRecord<>(topic, key, payload);
 * KafkaHeaderPropagator.write(record.headers(), OrderContextKey.ORDER_ID.of(orderId.value()));
 * KafkaHeaderPropagator.write(record.headers(), OrderContextKey.CUSTOMER_ID.of(customerId.value()));
 * producer.send(record);
 * }</pre>
 *
 * <h2>Consumer side</h2>
 *
 * <p>Consumers read by key — typically obtained from <em>their own</em>
 * bounded context's identifier definitions:
 *
 * <pre>{@code
 * @Incoming("order-events")
 * public void consume(Message<OrderEvent> msg) {
 *     var headers = msg.getMetadata(IncomingKafkaRecordMetadata.class).getHeaders();
 *     String orderId = KafkaHeaderPropagator.read(headers, "order.id");
 *
 *     // Consumer rebuilds its own DomainContext using its own identifiers,
 *     // backed by whatever survived the propagation.
 *     try (var ctx = DomainContext.open(
 *             NotificationContextKey.ORDER_ID.of(orderId))) {
 *         service.handle(msg.getPayload());
 *     }
 * }
 * }</pre>
 *
 * <p>Note that <em>both</em> Order's {@code OrderContextKey.ORDER_ID} and
 * Notification's {@code NotificationContextKey.ORDER_ID} use the key
 * string {@code "order.id"} — that agreement is part of the wire contract
 * between the two contexts. Module 5's ACL discussion is exactly about
 * making such cross-context agreements explicit and coupling-aware.
 *
 * <h2>Why not auto-propagate everything in MDC?</h2>
 *
 * <p>An earlier sketch of this helper had a {@code copyMdcToHeaders()} that
 * reflected MDC into the wire. We removed it: deciding which identifiers
 * cross a context boundary is a <em>modeling</em> decision, not a generic
 * convenience. Producers should explicitly write the identifiers they
 * intend to share. The cost of one or two extra lines per send site is
 * worth the clarity of "this is what crosses our boundary."
 */
public final class KafkaHeaderPropagator {

    /** Prefix for all workshop-managed domain headers. */
    public static final String HEADER_PREFIX = "domain.";

    private KafkaHeaderPropagator() {
        // utility class
    }

    /**
     * Write a single domain identifier into a record's headers as
     * {@code domain.<key>}. Existing values for the same header are
     * not removed — Kafka headers are a multi-map. Callers who need
     * single-valued semantics should remove first, then write.
     */
    public static void write(Headers headers, DomainIdentifier identifier) {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(identifier, "identifier");
        headers.add(new RecordHeader(
                HEADER_PREFIX + identifier.key(),
                identifier.value().getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Read the most recent value for a domain header, or {@code null} if
     * absent. The {@code key} parameter is the unprefixed domain key
     * (for example {@code "order.id"}); the {@code domain.} prefix is
     * applied internally.
     */
    public static String read(Headers headers, String key) {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(key, "key");
        Header h = headers.lastHeader(HEADER_PREFIX + key);
        if (h == null || h.value() == null) {
            return null;
        }
        return new String(h.value(), StandardCharsets.UTF_8);
    }
}
