package com.example.payment.domain.identifier;

import com.example.workshop.observability.DomainIdentifier;

import java.util.Objects;

/**
 * Payment's typed identifier vocabulary.
 *
 * <p>{@link #AUTHORIZATION_ID} is purely Payment's; the others use wire
 * strings agreed with Order ({@code order.id}, {@code customer.id}).
 */
public enum PaymentContextKey {

    ORDER_ID("order.id"),
    CUSTOMER_ID("customer.id"),
    AUTHORIZATION_ID("authorization.id");

    private final String key;

    PaymentContextKey(String key) {
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
