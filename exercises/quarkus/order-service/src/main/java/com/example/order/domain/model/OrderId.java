package com.example.order.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed identifier for an Order aggregate.
 *
 * <p>The wire / log representation is the {@link #value()} string, prefixed
 * {@code ord_} for human grep-ability. Inside the domain, never pass a
 * raw {@code String} where an {@code OrderId} is meant - the type
 * distinction prevents the classic "we mixed up the customer id and the
 * order id" bug.
 */
public record OrderId(@JsonValue String value) {

    private static final String PREFIX = "ord_";

    public OrderId {
        Objects.requireNonNull(value, "OrderId value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("OrderId value must not be blank");
        }
    }

    /**
     * Produce a new OrderId. The current implementation uses a UUID; the
     * value is opaque to consumers, so the algorithm is free to change.
     */
    public static OrderId generate() {
        return new OrderId(PREFIX + UUID.randomUUID());
    }

    /**
     * Reconstitute from a string (for example, recovered from a Kafka header
     * or a request body). Validates the prefix to catch obvious cross-context
     * confusion early.
     */
    public static OrderId of(String value) {
        Objects.requireNonNull(value, "OrderId value");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException(
                    "OrderId value must start with '" + PREFIX + "', got: " + value);
        }
        return new OrderId(value);
    }

    /**
     * Jackson constructor for deserializing from a wire string.
     * Delegates to {@link #of(String)} so prefix validation still fires.
     */
    @JsonCreator
    public static OrderId fromValue(String value) {
        return of(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
