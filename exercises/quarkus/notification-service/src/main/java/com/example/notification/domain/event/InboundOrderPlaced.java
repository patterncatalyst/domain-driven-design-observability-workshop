package com.example.notification.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification's view of {@code OrderPlaced}.
 *
 * <p>Carries just what Notification needs to send the placed-acknowledgment:
 * the order, the customer, the time. We deliberately ignore the order
 * total and line-item count from the wire payload - Notification's
 * placed-ack doesn't quote them.
 */
public record InboundOrderPlaced(
        UUID eventId,
        String orderId,
        Instant occurredAt,
        String customerId,
        String cartId
) implements InboundOrderEvent {
}
