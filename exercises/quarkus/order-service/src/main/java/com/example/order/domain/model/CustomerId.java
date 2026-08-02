package com.example.order.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * Strongly-typed identifier for a customer in Order's perspective.
 *
 * <p>Note that this is <em>Order's</em> view of a customer - other contexts
 * may have their own identifiers for the same person. We don't try to
 * model a global Customer aggregate here; Order just needs enough to
 * coordinate with the customer profile lookup.
 *
 * <p>The workshop uses tier-suffixed identifiers ({@code cust_alice_silver},
 * {@code cust_dave_gold}) so the in-memory profile lookup can derive
 * {@link CustomerTier} deterministically without a real database. See
 * {@code CustomerProfileLookup} for that.
 */
public record CustomerId(@JsonValue String value) {

    private static final String PREFIX = "cust_";

    public CustomerId {
        Objects.requireNonNull(value, "CustomerId value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("CustomerId value must not be blank");
        }
    }

    public static CustomerId of(String value) {
        Objects.requireNonNull(value, "CustomerId value");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException(
                    "CustomerId value must start with '" + PREFIX + "', got: " + value);
        }
        return new CustomerId(value);
    }

    /**
     * Jackson constructor for deserializing from a wire string.
     * Delegates to {@link #of(String)} so prefix validation still fires.
     */
    @JsonCreator
    public static CustomerId fromValue(String value) {
        return of(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
