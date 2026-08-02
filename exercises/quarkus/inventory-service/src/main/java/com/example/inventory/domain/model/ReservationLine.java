package com.example.inventory.domain.model;

import java.util.Objects;

/**
 * One line on a reservation: which product, how many.
 *
 * <p>Inventory deliberately doesn't carry price information - that's
 * Order's concern. Inventory just decides whether the requested
 * quantities can be reserved.
 */
public record ReservationLine(ProductCode productCode, int requestedQuantity) {

    public ReservationLine {
        Objects.requireNonNull(productCode, "productCode");
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException(
                    "requestedQuantity must be > 0, got: " + requestedQuantity);
        }
    }
}
