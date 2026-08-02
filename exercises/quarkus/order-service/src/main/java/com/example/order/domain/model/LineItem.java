package com.example.order.domain.model;

import java.util.Objects;

/**
 * One line on an order: a SKU, a quantity, and the unit price at the moment
 * of checkout.
 *
 * <p>We capture {@code unitPrice} on the line rather than re-deriving it
 * from a catalog later - prices may change between checkout and fulfillment,
 * and the order is the historical record of what the customer agreed to pay.
 */
public record LineItem(Sku sku, int quantity, Money unitPrice) {

    public LineItem {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0, got: " + quantity);
        }
    }

    /**
     * The total cost of this line: {@code unitPrice * quantity}.
     */
    public Money lineTotal() {
        return unitPrice.multiply(quantity);
    }
}
