package com.example.shipping.application;

import java.util.Objects;

public record ScheduleShipmentCommand(
        String orderId,
        String customerId,
        String shippingClass,
        int totalLineItemCount
) {
    public ScheduleShipmentCommand {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(shippingClass, "shippingClass");
        if (totalLineItemCount <= 0) {
            throw new IllegalArgumentException(
                    "totalLineItemCount must be > 0, got: " + totalLineItemCount);
        }
    }
}
