package com.example.shipping.application;

import java.util.Objects;

public record ScheduleShipmentCommand(
        String orderId,
        String customerId,
        String shippingClass
) {
    public ScheduleShipmentCommand {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(shippingClass, "shippingClass");
        if (customerId == null) customerId = "";
    }
}
