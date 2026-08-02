package com.example.order.domain.event;

import com.example.order.domain.model.CartId;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: an order has been placed - that is, the customer's
 * checkout intent has been recorded and assigned an OrderId. This fires
 * <em>before</em> any downstream calls (inventory reservation, payment
 * authorization), so consumers should not assume the order is fulfillable
 * yet.
 *
 * <p>The event carries enough context to be useful on its own:
 * {@code customerId}, {@code cartId}, the order total, and the line
 * count. Consumers that need more should subscribe to subsequent events
 * (OrderConfirmed) rather than treating this as a starting point for
 * additional callbacks.
 */
public record OrderPlaced(
        UUID eventId,
        OrderId orderId,
        Instant occurredAt,
        CustomerId customerId,
        CartId cartId,
        Money total,
        int lineItemCount
) implements DomainEvent {

    /**
     * Build from an Order aggregate. The factory is the recommended way
     * to construct events, so the field-by-field record constructor only
     * has to be used in deserialization.
     */
    public static OrderPlaced from(Order order) {
        return new OrderPlaced(
                UUID.randomUUID(),
                order.id(),
                Instant.now(),
                order.customerId(),
                order.cartId(),
                order.total(),
                order.totalLineItemCount());
    }
}
