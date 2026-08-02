package com.example.order.infrastructure.web.dto;

import com.example.order.application.CheckoutResult;

public record CheckoutResponseDto(
        String orderId,
        String status,
        String reservationId,
        String authorizationId,
        String shipmentId,
        String message
) {

    public static CheckoutResponseDto confirmed(CheckoutResult.Confirmed c) {
        return new CheckoutResponseDto(
                c.orderId().value(),
                "CONFIRMED",
                c.reservationId(),
                c.authorizationId(),
                c.shipmentId(),
                "Order confirmed successfully");
    }

    public static CheckoutResponseDto cancelled(CheckoutResult.Cancelled c) {
        return new CheckoutResponseDto(
                c.orderId().value(),
                "CANCELLED",
                null,
                null,
                null,
                "Order cancelled at " + c.failedAt() + ": " + c.reason());
    }
}
