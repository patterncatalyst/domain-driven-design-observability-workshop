package com.example.order.infrastructure.inventory;

/**
 * Internal signal that the wire response from Inventory could not be
 * translated into Order's vocabulary - schema drift, semantic drift, or
 * an impossible field combination.
 *
 * <p>Package-private. The adapter catches it locally and converts it to
 * a {@link com.example.order.domain.outbound.ReservationOutcome.Failure}
 * with category {@code "drift"} - the saga only ever sees the typed
 * outcome, never this exception.
 */
class InventoryAclTranslationException extends RuntimeException {

    InventoryAclTranslationException(String message) {
        super(message);
    }
}
