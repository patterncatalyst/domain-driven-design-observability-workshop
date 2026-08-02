package com.example.notification.application;

import com.example.notification.domain.event.InboundOrderCancelled;
import com.example.notification.domain.event.InboundOrderConfirmed;
import com.example.notification.domain.event.InboundOrderEvent;
import com.example.notification.domain.event.InboundOrderPlaced;
import com.example.notification.domain.identifier.NotificationContextKey;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationKind;
import com.example.workshop.observability.DomainContext;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The send-notification use case.
 *
 * <p>Pattern-matches the inbound event to a notification kind, builds a
 * {@link Notification}, "sends" it (logs - no real email/SMS in the
 * workshop), and records a per-tier-per-kind metric.
 *
 * <p><strong>Module 4 reference:</strong> the {@code customerTier}
 * parameter is what the deliberate bug breaks. On the {@code cp-4-broken}
 * branch, the consumer fails to read the tier from baggage and passes
 * {@code "unknown"} for every event - so the dashboard's "notifications
 * by tier" panel goes silent except for one bucket. The bug is in the
 * <em>consumer</em> entry point, not here, but the symptom shows up here.
 */
@ApplicationScoped
public class SendNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendNotificationUseCase.class);

    private static final String CHANNEL = "email";

    private final MeterRegistry meterRegistry;

    public SendNotificationUseCase(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @WithSpan("Notification.Send")
    public Notification send(InboundOrderEvent event, String customerTier) {

        NotificationKind kind = pickKind(event);
        String customerId = customerIdFor(event);

        Notification notification = Notification.send(
                kind, event.orderId(), customerId, customerTier, CHANNEL);

        // Update MDC with the freshly minted notification id so any log
        // lines emitted by this use case (or downstream of it) carry the
        // right correlation id.
        try (var ctx = DomainContext.open(
                NotificationContextKey.NOTIFICATION_ID.of(notification.id().value()))) {

            Span span = Span.current();
            span.setAttribute("notification.id", notification.id().value());
            span.setAttribute("notification.kind", kind.name());
            span.setAttribute("notification.channel", CHANNEL);
            span.setAttribute("customer.tier", customerTier);
            span.setAttribute("order.id", event.orderId());
            span.setAttribute("customer.id", customerId);

            // Annotations specific to confirmed events.
            if (event instanceof InboundOrderConfirmed c) {
                span.setAttribute("reservation.id", c.reservationId());
                span.setAttribute("authorization.id", c.authorizationId());
                span.setAttribute("shipment.id", c.shipmentId());
            }
            if (event instanceof InboundOrderCancelled x) {
                span.setAttribute("order.failed_at", x.failedAt());
            }

            log.info("Sent {} notification {} for order {} (tier={})",
                    kind, notification.id(), event.orderId(), customerTier);

            meterRegistry.counter("notifications_sent_total",
                    "kind", kind.name(),
                    "tier", customerTier).increment();

            return notification;
        }
    }

    private static NotificationKind pickKind(InboundOrderEvent event) {
        return switch (event) {
            case InboundOrderPlaced p    -> NotificationKind.PLACED_ACK;
            case InboundOrderConfirmed c -> NotificationKind.CONFIRMATION;
            case InboundOrderCancelled x -> NotificationKind.CANCELLATION;
        };
    }

    private static String customerIdFor(InboundOrderEvent event) {
        return switch (event) {
            case InboundOrderPlaced p    -> p.customerId();
            case InboundOrderConfirmed c -> c.customerId();
            case InboundOrderCancelled x -> x.customerId();
        };
    }
}
