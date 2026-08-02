package com.example.order.domain.outbound;

import java.util.Objects;

/**
 * The result of an attempt to reserve stock, expressed in Order's
 * ubiquitous language.
 *
 * <p>Sealed - the three variants are the entire vocabulary the saga has to
 * deal with for inventory outcomes:
 *
 * <ul>
 *   <li>{@link Reserved} - the happy path; the reservation id is what
 *       Notification will quote in the confirmation email and what
 *       Module 4's "follow the IDs across services" debugging exercise
 *       traces.</li>
 *   <li>{@link Unavailable} - business-level "no, you can't have this stock"
 *       (out of stock, etc.). Not a failure of the system; a legitimate
 *       outcome that the saga should treat as a normal cancellation
 *       reason.</li>
 *   <li>{@link Failure} - the system failed to determine an outcome:
 *       transport error, ACL translation drift, the Inventory service is
 *       returning garbage. The saga still needs to cancel the order, but
 *       the failure should be reported with more urgency than an
 *       Unavailable.</li>
 * </ul>
 *
 * <p>Module 5's drift discussion lives here: the ACL inside an adapter
 * uses {@link Failure} to surface schema drift, semantic drift, or
 * transport problems - keeping the saga's logic clean and the failure
 * mode visible to operators.
 */
public sealed interface ReservationOutcome
        permits ReservationOutcome.Reserved,
                ReservationOutcome.Unavailable,
                ReservationOutcome.Failure {

    record Reserved(String reservationId) implements ReservationOutcome {
        public Reserved {
            Objects.requireNonNull(reservationId, "reservationId");
            if (reservationId.isBlank()) {
                throw new IllegalArgumentException("reservationId must not be blank");
            }
        }
    }

    /**
     * The Inventory context could be reached and responded coherently;
     * the answer was "no."
     *
     * @param reason a short human-readable description suitable for the
     *               cancellation event
     */
    record Unavailable(String reason) implements ReservationOutcome {
        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The system could not produce a coherent outcome - transport error,
     * malformed response, ACL drift, etc. The {@code cause} field
     * (nullable) carries the original exception for logging.
     *
     * @param category short, enumerable category for metrics labels (don't
     *                 use unbounded strings here)
     * @param detail   longer description for logs / traces
     * @param cause    original exception, if any
     */
    record Failure(String category, String detail, Throwable cause)
            implements ReservationOutcome {
        public Failure {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(detail, "detail");
        }

        public static Failure of(String category, String detail) {
            return new Failure(category, detail, null);
        }
    }
}
