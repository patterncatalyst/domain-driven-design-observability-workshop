package com.example.shipping.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The Shipment aggregate. In the workshop's model, shipping always
 * succeeds - there's no "failed" or "rejected" state. A real Shipping
 * context would have those, plus statuses for in-transit / delivered /
 * etc. We deliberately keep this small.
 */
public record Shipment(
        ShipmentId id,
        String orderId,
        String shippingClass,
        int totalLineItemCount,
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
        return new Shipment(
                ShipmentId.generate(), orderId, shippingClass, totalLineItemCount,
                Instant.now());
    }
}
