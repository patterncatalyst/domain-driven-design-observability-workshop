package com.example.inventory.infrastructure.web.dto;

import java.util.List;

public record ReserveRequestDto(
        String orderId,
        List<Item> items
) {
    public record Item(String sku, int quantity) {}
}
