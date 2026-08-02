package com.example.order.infrastructure.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inbound web DTO for {@code POST /api/orders/checkout}.
 *
 * <p>Plain record - no validation annotations, no domain types. The
 * resource translates this into a {@link
 * com.example.order.application.CheckoutCommand} using domain factory
 * methods, where validation lives. Two layers, two distinct
 * responsibilities.
 */
public record CheckoutRequestDto(
        String cartId,
        String customerId,
        List<LineDto> lineItems,
        String paymentMethod,
        String shippingClass
) {
    public record LineDto(String sku, int quantity, BigDecimal unitPrice) {}
}
