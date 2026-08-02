package com.example.notification.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The Notification aggregate. Records what was sent, to whom, of what kind,
 * with what tier-aware customization.
 *
 * <p>The {@code customerTier} field is the focus of Module 4's debugging
 * exercise: on the {@code cp-4-broken} branch, the consumer fails to
 * read tier from baggage, which means every notification gets recorded
 * with tier {@code "unknown"}. The "notifications by tier" dashboard
 * panel goes silent except for one bucket. Module 4 walks participants
 * through hunting that down.
 */
public record Notification(
        NotificationId id,
        NotificationKind kind,
        String orderId,
        String customerId,
        String customerTier,    // the workshop scenario's bug magnet
        String channel,
        Instant sentAt
) {

    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(customerTier, "customerTier");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(sentAt, "sentAt");
    }

    public static Notification send(NotificationKind kind,
                                    String orderId,
                                    String customerId,
                                    String customerTier,
                                    String channel) {
        return new Notification(
                NotificationId.generate(), kind, orderId, customerId,
                customerTier, channel, Instant.now());
    }
}
