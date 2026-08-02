package com.example.order.domain.model;

/**
 * The lifecycle states of an {@link Order}.
 *
 * <p>Legal transitions:
 * <ul>
 *   <li>{@link #PLACED} → {@link #CONFIRMED} (saga succeeded)</li>
 *   <li>{@link #PLACED} → {@link #CANCELLED} (saga failed at any downstream step)</li>
 * </ul>
 *
 * <p>{@code CONFIRMED} and {@code CANCELLED} are terminal in this workshop's
 * model. A real system might add states for refunded, returned, partially
 * shipped, etc. - we deliberately keep this small.
 */
public enum OrderStatus {
    PLACED,
    CONFIRMED,
    CANCELLED
}
