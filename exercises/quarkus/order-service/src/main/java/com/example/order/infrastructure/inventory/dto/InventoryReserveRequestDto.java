package com.example.order.infrastructure.inventory.dto;

import java.util.List;

public record InventoryReserveRequestDto(
        String orderId,
        List<Item> items
) {
    public record Item(String sku, int quantity) {}
}
