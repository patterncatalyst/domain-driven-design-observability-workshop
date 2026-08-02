package com.example.order.infrastructure.inventory.dto;

/**
 * Wire DTO for the Inventory REST endpoint's reserve response.
 *
 * <p>Uses Inventory's vocabulary including its status names ({@code AVAILABLE},
 * {@code PARTIAL}, {@code UNAVAILABLE}). The ACL in
 * {@code InventoryRestAdapter} translates these into Order's
 * {@code ReservationOutcome} subtypes - and the translation is exactly
 * where Module 5's drift discussion lives.
 */
public record InventoryReserveResponseDto(
        String reservationId,
        Status status,
        String reason            // populated for UNAVAILABLE, may be null otherwise
) {
    public enum Status {
        AVAILABLE,
        PARTIAL,
        UNAVAILABLE
    }
}
