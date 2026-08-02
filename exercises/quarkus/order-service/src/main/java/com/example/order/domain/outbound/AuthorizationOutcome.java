package com.example.order.domain.outbound;

import java.util.Objects;

/**
 * The result of an attempt to authorize a payment, in Order's ubiquitous
 * language.
 */
public sealed interface AuthorizationOutcome
        permits AuthorizationOutcome.Authorized,
                AuthorizationOutcome.Declined,
                AuthorizationOutcome.Failure {

    record Authorized(String authorizationId) implements AuthorizationOutcome {
        public Authorized {
            Objects.requireNonNull(authorizationId, "authorizationId");
        }
    }

    /**
     * The Payment context responded coherently with "no" - bad card,
     * insufficient funds, fraud check failed, etc.
     */
    record Declined(String reason) implements AuthorizationOutcome {
        public Declined {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Failure(String category, String detail, Throwable cause)
            implements AuthorizationOutcome {
        public Failure {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(detail, "detail");
        }

        public static Failure of(String category, String detail) {
            return new Failure(category, detail, null);
        }
    }
}
