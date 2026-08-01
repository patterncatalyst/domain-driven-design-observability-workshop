package com.example.workshop.observability;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Round-trips {@link DomainIdentifier} values through Kafka record headers
 * so domain identifier tagging survives the asynchronous boundary between
 * producer and consumer services.
 */
public final class KafkaHeaderPropagator {

    /** Prefix for all workshop-managed domain headers. */
    public static final String HEADER_PREFIX = "domain.";

    private KafkaHeaderPropagator() {
        // utility class
    }

    /**
     * Write a single domain identifier into a record's headers as
     * {@code domain.<key>}.
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
