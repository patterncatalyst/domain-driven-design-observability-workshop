package com.example.order.domain.event;

import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: the saga has successfully completed all downstream steps -
 * stock reserved, payment authorized, shipment scheduled - and the order is
 * now {@code CONFIRMED}.
 *
 * <p>This is the primary trigger for customer-facing notifications.
 * Notification's Kafka consumer subscribes to events of this type to send
 * the confirmation email. The IDs of the downstream operations
 * ({@code reservationId}, {@code authorizationId}, {@code shipmentId})
 * are included on the event so the email body can quote them - and so
 * the trace going through Notification has them as span attributes.
 */
public record OrderConfirmed(
        UUID eventId,
        OrderId orderId,
        Instant occurredAt,
        CustomerId customerId,
        Money total,
        String reservationId,
        String authorizationId,
        String shipmentId
) implements DomainEvent {

    public static OrderConfirmed from(Order order,
                                      String reservationId,
                                      String authorizationId,
                                      String shipmentId) {
        return new OrderConfirmed(
                UUID.randomUUID(),
                order.id(),
                Instant.now(),
                order.customerId(),
                order.total(),
                reservationId,
                authorizationId,
                shipmentId);
    }
}
