package com.example.inventory.infrastructure.web.dto;

import java.util.List;

/**
 * Inbound REST wire DTO. Same shape as Order's outbound DTO - the wire
 * contract physically present in two places (and Module 5 calls this out).
 */
public record ReserveRequestDto(
        String orderId,
        String customerId,
        List<Line> lineItems
) {
    public record Line(String productCode, int requestedQuantity) {}
}
