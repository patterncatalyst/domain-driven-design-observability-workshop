package com.example.workshop.observability;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.context.Scope;

import java.util.Objects;

/**
 * Convenience wrappers around OpenTelemetry's Baggage API, keyed off
 * {@link DomainIdentifier}.
 *
 * <p>Baggage is OpenTelemetry's standard mechanism for propagating arbitrary
 * key/value pairs across service boundaries (HTTP {@code baggage} header,
 * gRPC metadata, Kafka headers configured by the OTel agent). It's the right
 * channel for cross-cutting domain attributes that downstream services may
 * want to label spans, logs, or metrics with — for example, propagating a
 * customer-tier classification from the entry-point service through the saga.
 *
 * <p>The key design point: <em>each bounded context decides what it puts
 * into baggage and what it reads from baggage.</em> The shared module never
 * enumerates baggage keys. {@link DomainIdentifier} is the seam.
 *
 * <h2>Producer side</h2>
 *
 * <pre>{@code
 * // in the Order service's checkout entry point
 * try (var scope = BaggageHelpers.put(OrderContextKey.CUSTOMER_TIER.of(profile.tier().name()))) {
 *     // every span and outbound call in this scope carries customer.tier
 *     return checkoutSaga.checkout(orderId, request);
 * }
 * }</pre>
 *
 * <h2>Consumer side</h2>
 *
 * <p>Consumers ask the helper for a baggage value by the key their own
 * domain uses for that concept:
 *
 * <pre>{@code
 * // in the Notification service
 * String tier = BaggageHelpers.get(NotificationContextKey.CUSTOMER_TIER.key());
 * if (tier != null) {
 *     Span.current().setAttribute("customer.tier", tier);
 * }
 * }</pre>
 *
 * <p>If Order and Notification agree on the key string {@code customer.tier},
 * propagation works. The agreement is part of the wire contract between
 * the two contexts (along with event schemas, etc.) — exactly the thing
 * Module 5's ACL discussion treats as a coupling decision.
 *
 * <h2>Scope discipline</h2>
 *
 * <p>The {@link Scope} returned by {@link #put} MUST be closed (use
 * try-with-resources). Forgetting leaves the baggage entry attached to
 * the current context, which in Quarkus's request-per-thread model leaks
 * into subsequent requests — exactly the kind of bug Module 4's debugging
 * exercise hunts.
 */
public final class BaggageHelpers {

    private BaggageHelpers() {
        // utility class
    }

    /**
     * Read a baggage entry from the current OTel context, or {@code null}
     * if not present. Callers are responsible for handling absence
     * appropriately for their context.
     */
    public static String get(String key) {
        Objects.requireNonNull(key, "key");
        return Baggage.current().getEntryValue(key);
    }

    /**
     * Add a single domain identifier to the current OTel baggage and return
     * a {@link Scope} that, when closed, restores the previous baggage state.
     */
    public static Scope put(DomainIdentifier identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return Baggage.current().toBuilder()
                .put(identifier.key(), identifier.value())
                .build()
                .makeCurrent();
    }

    /**
     * Add multiple identifiers in a single scope. Useful when the entry-point
     * service has more than one cross-cutting attribute to propagate
     * (customer tier, order priority, etc.) and you want one
     * try-with-resources block instead of nested ones.
     */
    public static Scope putAll(Iterable<? extends DomainIdentifier> identifiers) {
        Objects.requireNonNull(identifiers, "identifiers");
        BaggageBuilder builder = Baggage.current().toBuilder();
        for (var id : identifiers) {
            builder.put(id.key(), id.value());
        }
        return builder.build().makeCurrent();
    }
}
