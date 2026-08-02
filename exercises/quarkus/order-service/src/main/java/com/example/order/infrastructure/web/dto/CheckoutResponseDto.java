package com.example.order.infrastructure.web.dto;

import com.example.order.application.CheckoutResult;

/**
 * Outbound web DTO for the checkout response.
 *
 * <p>Carries either the confirmed-order info or the cancellation info,
 * with a status field clients can switch on. Keeping the JSON simple
 * (one shape, optional fields) is friendlier for HTTP clients than
 * exposing the Java sealed-type hierarchy directly.
 */
public record CheckoutResponseDto(
        String orderId,
        String status,            // "confirmed" or "cancelled"
        String reservationId,     // null on cancelled
        String authorizationId,   // null on cancelled
        String shipmentId,        // null on cancelled
        String failedAt,          // null on confirmed
        String reason             // null on confirmed
) {

    public static CheckoutResponseDto confirmed(CheckoutResult.Confirmed c) {
        return new CheckoutResponseDto(
                c.orderId().value(),
                "confirmed",
                c.reservationId(),
                c.authorizationId(),
                c.shipmentId(),
                null,
                null);
    }

    public static CheckoutResponseDto cancelled(CheckoutResult.Cancelled c) {
        return new CheckoutResponseDto(
                c.orderId().value(),
                "cancelled",
                null,
                null,
                null,
                c.failedAt(),
                c.reason());
    }
}
