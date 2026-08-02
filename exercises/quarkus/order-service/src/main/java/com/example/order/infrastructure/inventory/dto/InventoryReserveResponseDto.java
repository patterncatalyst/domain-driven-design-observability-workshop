package com.example.order.infrastructure.inventory.dto;

import java.util.List;

public record InventoryReserveResponseDto(
        String reservationId,
        String status,
        String reason,
        List<LineDto> lines
) {
    public record LineDto(
            String productCode,
            int quantityReserved,
            boolean available
    ) {}
}
