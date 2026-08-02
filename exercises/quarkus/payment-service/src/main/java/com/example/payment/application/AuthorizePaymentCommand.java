package com.example.payment.application;

import java.math.BigDecimal;
import java.util.Objects;

public record AuthorizePaymentCommand(
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        String paymentMethod
) {
    public AuthorizePaymentCommand {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(paymentMethod, "paymentMethod");
    }
}
