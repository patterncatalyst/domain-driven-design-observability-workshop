package com.example.order.application;

import com.example.order.domain.model.OrderId;

import java.util.Objects;

/**
 * Output of {@link CheckoutSaga#checkout}. Sealed - the saga either
 * confirms an order (happy path with all the downstream IDs filled in) or
 * cancels it (with reason and the step at which it failed).
 */
public sealed interface CheckoutResult
        permits CheckoutResult.Confirmed, CheckoutResult.Cancelled {

    OrderId orderId();

    record Confirmed(
            OrderId orderId,
            String reservationId,
            String authorizationId,
            String shipmentId
    ) implements CheckoutResult {
        public Confirmed {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(reservationId, "reservationId");
            Objects.requireNonNull(authorizationId, "authorizationId");
            Objects.requireNonNull(shipmentId, "shipmentId");
        }
    }

    record Cancelled(
            OrderId orderId,
            String failedAt,
            String reason
    ) implements CheckoutResult {
        public Cancelled {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(failedAt, "failedAt");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
