package com.example.notification.domain.event;

import java.time.Instant;
import java.util.UUID;

public record InboundOrderCancelled(
        UUID eventId,
        String orderId,
        Instant occurredAt,
        String customerId,
        String failedAt,
        String reason
) implements InboundOrderEvent {
}
