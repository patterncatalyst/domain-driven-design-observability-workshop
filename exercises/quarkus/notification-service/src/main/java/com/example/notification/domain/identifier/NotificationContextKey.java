package com.example.notification.domain.identifier;

import com.example.workshop.observability.DomainIdentifier;

import java.util.Objects;

/**
 * Notification's typed identifier vocabulary.
 *
 * <p>{@link #NOTIFICATION_ID} is purely Notification's. The other keys
 * use the wire strings agreed with Order via the Kafka header contract -
 * {@code domain.order.id}, {@code domain.customer.id}, {@code domain.cart.id}.
 * Notification's own enum names them, the wire string IS the cross-context
 * agreement.
 */
public enum NotificationContextKey {

    ORDER_ID("order.id"),
    CUSTOMER_ID("customer.id"),
    CART_ID("cart.id"),
    NOTIFICATION_ID("notification.id");

    private final String key;

    NotificationContextKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public DomainIdentifier of(String value) {
        Objects.requireNonNull(value, () -> name() + " value");
        return new SimpleIdentifier(key, value);
    }

    private record SimpleIdentifier(String key, String value)
            implements DomainIdentifier {
    }
}
