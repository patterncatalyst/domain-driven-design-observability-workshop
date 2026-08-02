package com.example.inventory.domain.model;

/**
 * The status of a reservation request, in Inventory's vocabulary.
 *
 * <p>Distinct from Order's {@code ReservationOutcome} - the ACL in
 * Order's {@code InventoryRestAdapter} translates between them.
 */
public enum ReservationStatus {

    /** All requested quantities reserved successfully. */
    AVAILABLE,

    /** Some requested quantities reserved; others not in stock. */
    PARTIAL,

    /** No requested quantities could be reserved. */
    UNAVAILABLE
}
