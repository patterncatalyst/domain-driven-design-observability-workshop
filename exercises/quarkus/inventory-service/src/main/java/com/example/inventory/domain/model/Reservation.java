package com.example.inventory.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The Reservation aggregate.
 *
 * <p>Created when Inventory accepts (or rejects) a reservation request.
 * The aggregate carries:
 *
 * <ul>
 *   <li>the foreign {@code orderId} that originated the request - useful
 *       for correlation but not Inventory's business identity</li>
 *   <li>Inventory's own {@code id} (the {@link ReservationId})</li>
 *   <li>the {@link ReservationStatus} representing the outcome</li>
 *   <li>the lines that were requested (kept for audit / partial decisions)</li>
 *   <li>an optional reason for non-AVAILABLE statuses</li>
 * </ul>
 *
 * <p>Following Khononov-flavored functional modeling: immutable record,
 * factory methods for construction, no setters.
 */
public record Reservation(
        ReservationId id,
        String orderId,
        List<ReservationLine> lines,
        ReservationStatus status,
        Instant createdAt,
        String reason   // null unless status != AVAILABLE
) {

    public Reservation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Reservation must have at least one line");
        }
        lines = List.copyOf(lines);
    }

    public static Reservation available(String orderId, List<ReservationLine> lines) {
        return new Reservation(
                ReservationId.generate(), orderId, lines,
                ReservationStatus.AVAILABLE, Instant.now(), null);
    }

    public static Reservation partial(String orderId, List<ReservationLine> lines, String reason) {
        return new Reservation(
                ReservationId.generate(), orderId, lines,
                ReservationStatus.PARTIAL, Instant.now(),
                Objects.requireNonNull(reason, "reason"));
    }

    public static Reservation unavailable(String orderId, List<ReservationLine> lines, String reason) {
        return new Reservation(
                ReservationId.generate(), orderId, lines,
                ReservationStatus.UNAVAILABLE, Instant.now(),
                Objects.requireNonNull(reason, "reason"));
    }
}
