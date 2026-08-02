package com.example.inventory.domain.model;

import java.util.Objects;

/**
 * Strongly-typed identifier for a reservation aggregate.
 *
 * <p>Inventory mints these on a successful reserve. The {@code res_} prefix
 * is for grep-ability; the value is opaque to consumers.
 */
public record ReservationId(String value) {

    private static final String PREFIX = "res_";

    public ReservationId {
        Objects.requireNonNull(value, "ReservationId value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ReservationId value must not be blank");
        }
    }

    public static ReservationId of(String value) {
        return new ReservationId(value);
    }

    public static ReservationId generate() {
        return new ReservationId(PREFIX + java.util.UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value;
    }
}
