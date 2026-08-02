package com.example.inventory.domain.model;

import java.util.Objects;

public record ReservationLine(
        ProductCode productCode,
        int requestedQuantity,
        int reservedQuantity,
        boolean available
) {

    public ReservationLine {
        Objects.requireNonNull(productCode, "productCode");
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException(
                    "requestedQuantity must be > 0, got: " + requestedQuantity);
        }
    }

    public static ReservationLine reserved(ProductCode productCode, int quantity) {
        return new ReservationLine(productCode, quantity, quantity, true);
    }

    public static ReservationLine unavailable(ProductCode productCode, int quantity) {
        return new ReservationLine(productCode, quantity, 0, false);
    }

    public static ReservationLine partial(ProductCode productCode, int requested, int reserved) {
        return new ReservationLine(productCode, requested, reserved, reserved > 0);
    }
}
