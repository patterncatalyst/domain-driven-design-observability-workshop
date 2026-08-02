package com.example.order.domain.outbound;

import java.util.Objects;

/**
 * The result of an attempt to schedule a shipment, in Order's ubiquitous
 * language.
 *
 * <p>Note no "Declined" variant - in this workshop's model the shipping
 * context doesn't reject orders. If real-world considerations made this
 * a possibility (e.g., "we don't ship to that address"), we'd add a third
 * variant here and the saga's switch over outcomes would need updating.
 */
public sealed interface ShipmentOutcome
        permits ShipmentOutcome.Scheduled, ShipmentOutcome.Failure {

    record Scheduled(String shipmentId) implements ShipmentOutcome {
        public Scheduled {
            Objects.requireNonNull(shipmentId, "shipmentId");
        }
    }

    record Failure(String category, String detail, Throwable cause)
            implements ShipmentOutcome {
        public Failure {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(detail, "detail");
        }

        public static Failure of(String category, String detail) {
            return new Failure(category, detail, null);
        }
    }
}
