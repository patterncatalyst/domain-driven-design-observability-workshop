package com.example.order.domain.outbound;

import com.example.order.domain.model.Order;

/**
 * Outbound port for stock reservation, expressed in <em>Order's</em>
 * ubiquitous language.
 *
 * <p>The Order context calls {@code reserve(order)} and gets back a
 * {@link ReservationOutcome}. The port deliberately knows nothing about
 * HTTP, gRPC, JSON, protobuf, or the Inventory context's vocabulary. Two
 * separate adapters - {@code InventoryRestAdapter} and
 * {@code InventoryGrpcAdapter} - implement this interface; each contains
 * its own Anti-Corruption Layer that translates between Order's
 * vocabulary and whatever the wire requires.
 *
 * <p>This is the architectural seam Module 5's ACL section highlights.
 * It exists in the domain layer so the saga's logic stays pure; the
 * adapters live in {@code infrastructure/inventory/}.
 */
public interface InventoryPort {

    /**
     * Reserve stock for the given order. The implementation is responsible
     * for translating the order into whatever the wire requires, calling
     * the Inventory context, and translating the response back into a
     * {@link ReservationOutcome} expressed in Order's terms.
     *
     * <p>The contract is total - the implementation must always return a
     * non-null outcome. Use {@link ReservationOutcome.Failure} to signal
     * any kind of error, including transport errors, schema drift, or
     * business-level "stock is unavailable." The caller (the saga)
     * decides how to react to each.
     */
    ReservationOutcome reserve(Order order);
}
