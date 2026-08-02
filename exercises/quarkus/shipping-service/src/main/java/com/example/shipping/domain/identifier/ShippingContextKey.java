package com.example.shipping.domain.identifier;

import com.example.workshop.observability.DomainIdentifier;

import java.util.Objects;

public enum ShippingContextKey {

    ORDER_ID("order.id"),
    CUSTOMER_ID("customer.id"),
    SHIPMENT_ID("shipment.id");

    private final String key;

    ShippingContextKey(String key) {
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
