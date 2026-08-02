package com.example.order.domain.event;

import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: the saga aborted at some step. The order is now
 * {@code CANCELLED} and downstream side effects (if any) have been or
 * will be compensated.
 *
 * <p>{@code failedAt} indicates which saga step caused the cancellation.
 * In the workshop scenario this is one of {@code "inventory"},
 * {@code "payment"}, {@code "shipping"}, or {@code "notification"} -
 * we keep it as a string rather than an enum to avoid coupling the event
 * schema to the saga's internal step-name vocabulary.
 *
 * <p>{@code reason} is a short human-readable description suitable for
 * logging and for inclusion in the customer-facing apology email
 * Notification will send.
 */
public record OrderCancelled(
        UUID eventId,
        OrderId orderId,
        Instant occurredAt,
        CustomerId customerId,
        String failedAt,
        String reason
) implements DomainEvent {

    public static OrderCancelled from(Order order, String failedAt, String reason) {
        return new OrderCancelled(
                UUID.randomUUID(),
                order.id(),
                Instant.now(),
                order.customerId(),
                failedAt,
                reason);
    }
}
