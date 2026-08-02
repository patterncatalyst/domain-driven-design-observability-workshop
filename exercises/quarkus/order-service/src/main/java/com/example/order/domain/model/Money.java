package com.example.order.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * A monetary amount with currency. BigDecimal-backed - never use {@code double}
 * for money.
 *
 * <p>For the workshop we limit ourselves to single-currency arithmetic:
 * adding or comparing two {@code Money} values requires the same currency,
 * and we throw on mismatches rather than silently converting.
 *
 * <p>The workshop's customer profiles all use USD - this isn't a deliberate
 * limitation, just a simplification. A real Order context would model
 * currency conversion as either a separate concern (talking to an FX
 * service) or by carrying the customer's preferred currency on the order
 * from the start.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Money amount must not be negative, got: " + amount);
        }
        // Normalize to currency's standard precision so equality is reliable.
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money usd(BigDecimal amount) {
        return new Money(amount, Currency.getInstance("USD"));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money multiply(int multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("multiplier must be >= 0");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(multiplier)), currency);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
