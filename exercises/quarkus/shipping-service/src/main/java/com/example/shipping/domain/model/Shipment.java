package com.example.shipping.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Shipment(
        ShipmentId id,
        String orderId,
        String shippingClass,
        int totalLineItemCount,
        int estimatedDays,
        Instant scheduledAt
) {
    public Shipment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(shippingClass, "shippingClass");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        if (totalLineItemCount <= 0) {
            throw new IllegalArgumentException(
                    "totalLineItemCount must be > 0, got: " + totalLineItemCount);
        }
    }

    public static Shipment schedule(String orderId, String shippingClass, int totalLineItemCount) {
        int days = switch (shippingClass.toLowerCase()) {
            case "overnight" -> 1;
            case "express" -> 2;
            case "priority" -> 3;
            default -> 5;
        };
        return new Shipment(
                ShipmentId.generate(), orderId, shippingClass, totalLineItemCount,
                days, Instant.now());
    }
}
