package com.example.inventory.domain.model;

import java.util.Objects;

/**
 * Inventory's vocabulary for the product identifier. Order's domain calls
 * the same concept {@code Sku}; the difference between vocabularies is
 * what the Anti-Corruption Layer in Order's {@code InventoryRestAdapter}
 * translates.
 */
public record ProductCode(String value) {

    public ProductCode {
        Objects.requireNonNull(value, "ProductCode value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProductCode value must not be blank");
        }
    }

    public static ProductCode of(String value) {
        return new ProductCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
