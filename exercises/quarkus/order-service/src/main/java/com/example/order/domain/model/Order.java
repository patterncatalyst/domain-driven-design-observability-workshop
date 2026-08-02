package com.example.order.domain.model;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * The Order aggregate.
 *
 * <p>This is the central domain object the saga acts on. The lifecycle is:
 *
 * <pre>{@code
 *   place() -> PLACED -> confirm() -> CONFIRMED
 *                     -> cancel(reason) -> CANCELLED
 * }</pre>
 *
 * <p>State transitions are explicit methods returning a new {@code Order}
 * instance - we don't mutate. The aggregate validates that transitions are
 * legal (you can't confirm a cancelled order, can't cancel a confirmed one)
 * and throws {@link IllegalStateException} when they aren't.
 *
 * <p>The choice of immutability isn't ceremonial: it makes the saga's
 * orchestration logic easier to reason about (each step returns the
 * post-step state, which is then carried into the next step) and keeps
 * the domain layer free of any framework-aware lifecycle plumbing.
 */
public record Order(
        OrderId id,
        CustomerId customerId,
        CartId cartId,
        List<LineItem> lineItems,
        OrderStatus status,
        Instant placedAt,
        String cancelReason  // null unless status == CANCELLED
) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(cartId, "cartId");
        Objects.requireNonNull(lineItems, "lineItems");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(placedAt, "placedAt");
        if (lineItems.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one line item");
        }
        // Defensive copy to keep the record genuinely immutable
        lineItems = List.copyOf(lineItems);
    }

    /**
     * Place a new order. Returns an Order in {@link OrderStatus#PLACED}.
     */
    public static Order place(OrderId id,
                              CustomerId customerId,
                              CartId cartId,
                              List<LineItem> lineItems) {
        return new Order(id, customerId, cartId, lineItems,
                OrderStatus.PLACED, Instant.now(), null);
    }

    /**
     * Compute the total order value by summing line totals. Throws if
     * line items use mixed currencies (which our domain doesn't allow).
     */
    public Money total() {
        Currency currency = lineItems.get(0).unitPrice().currency();
        return lineItems.stream()
                .map(LineItem::lineTotal)
                .reduce(Money.zero(currency), Money::add);
    }

    /**
     * Convenience: sum of quantities across all lines.
     */
    public int totalLineItemCount() {
        return lineItems.stream().mapToInt(LineItem::quantity).sum();
    }

    /**
     * Transition to CONFIRMED. Legal only from PLACED.
     */
    public Order confirm() {
        if (status != OrderStatus.PLACED) {
            throw new IllegalStateException(
                    "Cannot confirm order in status " + status);
        }
        return new Order(id, customerId, cartId, lineItems,
                OrderStatus.CONFIRMED, placedAt, null);
    }

    /**
     * Transition to CANCELLED. Legal only from PLACED. The reason is
     * recorded on the aggregate so domain events can carry it.
     */
    public Order cancel(String reason) {
        Objects.requireNonNull(reason, "cancellation reason");
        if (status != OrderStatus.PLACED) {
            throw new IllegalStateException(
                    "Cannot cancel order in status " + status);
        }
        return new Order(id, customerId, cartId, lineItems,
                OrderStatus.CANCELLED, placedAt, reason);
    }
}
