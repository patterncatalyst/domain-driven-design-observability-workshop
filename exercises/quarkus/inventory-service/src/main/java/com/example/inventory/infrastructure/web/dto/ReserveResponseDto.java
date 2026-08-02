package com.example.inventory.infrastructure.web.dto;

import java.util.List;

public record ReserveResponseDto(
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
