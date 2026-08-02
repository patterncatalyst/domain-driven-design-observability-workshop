package com.example.order.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * Strongly-typed identifier for a shopping cart that becomes an order at
 * checkout.
 *
 * <p>The workshop hand-waves the Cart context (we don't model it as a
 * separate service); it appears here as just an inbound identifier carried
 * for correlation. {@link CartId} flows in via the checkout request and
 * becomes part of the saga's {@code DomainContext} so log lines can be
 * correlated back to the originating cart.
 */
public record CartId(@JsonValue String value) {

    private static final String PREFIX = "cart_";

    public CartId {
        Objects.requireNonNull(value, "CartId value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("CartId value must not be blank");
        }
    }

    public static CartId of(String value) {
        Objects.requireNonNull(value, "CartId value");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException(
                    "CartId value must start with '" + PREFIX + "', got: " + value);
        }
        return new CartId(value);
    }

    /**
     * Jackson constructor for deserializing from a wire string.
     * Delegates to {@link #of(String)} so prefix validation still fires.
     */
    @JsonCreator
    public static CartId fromValue(String value) {
        return of(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
