package com.example.notification.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification's view of {@code OrderConfirmed}. Includes the downstream
 * IDs (reservation, authorization, shipment) so the confirmation email
 * can quote them - and so the trace through Notification carries them
 * as span attributes for cross-service correlation in Tempo.
 */
public record InboundOrderConfirmed(
        UUID eventId,
        String orderId,
        Instant occurredAt,
        String customerId,
        String reservationId,
        String authorizationId,
        String shipmentId
) implements InboundOrderEvent {
}
