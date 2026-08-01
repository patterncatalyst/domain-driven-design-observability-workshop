package com.example.workshop.observability;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.context.Scope;

import java.util.Objects;

/**
 * Convenience wrappers around OpenTelemetry's Baggage API, keyed off
 * {@link DomainIdentifier}.
 */
public final class BaggageHelpers {

    private BaggageHelpers() {
        // utility class
    }

    /**
     * Read a baggage entry from the current OTel context, or {@code null}
     * if not present.
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
     * Add multiple identifiers in a single scope.
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
