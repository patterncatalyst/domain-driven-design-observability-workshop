package com.example.workshop.observability;

import org.slf4j.MDC;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A scoped helper for populating SLF4J MDC with a bounded context's
 * {@link DomainIdentifier} values, with guaranteed cleanup via
 * {@link AutoCloseable}.
 */
public final class DomainContext implements AutoCloseable {

    private final Set<String> ownedKeys;

    private DomainContext(Set<String> ownedKeys) {
        this.ownedKeys = ownedKeys;
    }

    /**
     * Open a scope, putting each supplied identifier into MDC.
     */
    public static DomainContext open(DomainIdentifier... identifiers) {
        var owned = new LinkedHashSet<String>();
        for (var id : identifiers) {
            putIdentifier(owned, id);
        }
        return new DomainContext(owned);
    }

    /**
     * Open a scope from any iterable of identifiers.
     */
    public static DomainContext open(Iterable<? extends DomainIdentifier> identifiers) {
        var owned = new LinkedHashSet<String>();
        for (var id : identifiers) {
            putIdentifier(owned, id);
        }
        return new DomainContext(owned);
    }

    /**
     * Add one more identifier to the current scope.
     */
    public DomainContext include(DomainIdentifier identifier) {
        putIdentifier(ownedKeys, identifier);
        return this;
    }

    @Override
    public void close() {
        for (var key : ownedKeys) {
            MDC.remove(key);
        }
        ownedKeys.clear();
    }

    private static void putIdentifier(Set<String> owned, DomainIdentifier id) {
        Objects.requireNonNull(id, "identifier");
        Objects.requireNonNull(id.key(),   "identifier.key()");
        Objects.requireNonNull(id.value(), "identifier.value()");
        MDC.put(id.key(), id.value());
        owned.add(id.key());
    }
}
