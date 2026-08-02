package com.example.notification.domain.model;

import java.util.Objects;
import java.util.UUID;

public record NotificationId(String value) {

    private static final String PREFIX = "notif_";

    public NotificationId {
        Objects.requireNonNull(value, "NotificationId value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("NotificationId value must not be blank");
        }
    }

    public static NotificationId generate() {
        return new NotificationId(PREFIX + UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value;
    }
}
