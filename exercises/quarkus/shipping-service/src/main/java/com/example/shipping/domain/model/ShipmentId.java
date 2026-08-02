package com.example.shipping.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ShipmentId(String value) {

    private static final String PREFIX = "ship_";

    public ShipmentId {
        Objects.requireNonNull(value, "ShipmentId value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ShipmentId value must not be blank");
        }
    }

    public static ShipmentId generate() {
        return new ShipmentId(PREFIX + UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value;
    }
}
