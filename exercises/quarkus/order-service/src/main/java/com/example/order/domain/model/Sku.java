package com.example.order.domain.model;

import java.util.Objects;

/**
 * Stock-keeping unit identifier - the product code in Order's vocabulary.
 *
 * <p>Inventory's bounded context calls this a {@code product_code}. The
 * Anti-Corruption Layer in {@code InventoryRestAdapter} /
 * {@code InventoryGrpcAdapter} translates between Order's {@code Sku} and
 * Inventory's {@code product_code} on the wire. That translation is one
 * of the boundary moments Module 5 highlights.
 */
public record Sku(String value) {

    public Sku {
        Objects.requireNonNull(value, "Sku value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Sku value must not be blank");
        }
    }

    public static Sku of(String value) {
        return new Sku(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
