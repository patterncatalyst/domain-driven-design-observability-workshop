package com.example.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * The Authorization aggregate. Created when Payment processes a request.
 *
 * <p>Carries the foreign {@code orderId} for correlation, Payment's own
 * {@link AuthorizationId}, the resulting {@link AuthorizationOutcome},
 * the amount + currency it covers, and an optional reason on DECLINED.
 */
public record Authorization(
        AuthorizationId id,
        String orderId,
        BigDecimal amount,
        String currency,
        AuthorizationOutcome outcome,
        Instant createdAt,
        String reason   // null on AUTHORIZED
) {

    public Authorization {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Authorization authorized(String orderId, BigDecimal amount, String currency) {
        return new Authorization(
                AuthorizationId.generate(), orderId, amount, currency,
                AuthorizationOutcome.AUTHORIZED, Instant.now(), null);
    }

    public static Authorization declined(String orderId, BigDecimal amount, String currency,
                                         String reason) {
        return new Authorization(
                AuthorizationId.generate(), orderId, amount, currency,
                AuthorizationOutcome.DECLINED, Instant.now(),
                Objects.requireNonNull(reason, "reason"));
    }
}
