package com.example.payment.domain.model;

import java.util.Objects;
import java.util.UUID;

public record AuthorizationId(String value) {

    private static final String PREFIX = "auth_";

    public AuthorizationId {
        Objects.requireNonNull(value, "AuthorizationId value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("AuthorizationId value must not be blank");
        }
    }

    public static AuthorizationId generate() {
        return new AuthorizationId(PREFIX + UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value;
    }
}
