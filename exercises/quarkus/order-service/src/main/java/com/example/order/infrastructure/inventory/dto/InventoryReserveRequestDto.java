package com.example.order.infrastructure.inventory.dto;

import java.util.List;

/**
 * Wire DTO for the Inventory REST endpoint's reserve request.
 *
 * <p>Uses <em>Inventory's</em> vocabulary - {@code productCode} where Order
 * calls it {@code Sku}; {@code requestedQuantity} where Order says
 * {@code quantity}. The {@code InventoryRestAdapter} translates between
 * these wire types and Order's domain types. <strong>This is the wire
 * contract</strong>; it must not leak past the adapter package.
 *
 * <p>Package-private visibility is deliberate: only classes in
 * {@code infrastructure.inventory} should ever construct or read these.
 */
public record InventoryReserveRequestDto(
        String orderId,
        String customerId,
        List<Line> lineItems
) {
    public record Line(String productCode, int requestedQuantity) {}
}
