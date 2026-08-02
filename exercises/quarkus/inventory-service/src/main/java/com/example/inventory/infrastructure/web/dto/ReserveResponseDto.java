package com.example.inventory.infrastructure.web.dto;

/**
 * Outbound REST wire DTO. The status enum is Inventory's vocabulary -
 * Order's ACL translates from these values into Order's
 * ReservationOutcome subtypes.
 */
public record ReserveResponseDto(
        String reservationId,
        Status status,
        String reason
) {
    public enum Status {
        AVAILABLE,
        PARTIAL,
        UNAVAILABLE
    }
}
