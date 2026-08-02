package com.example.workshop.observability;

import org.slf4j.MDC;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A scoped helper for populating SLF4J MDC with a bounded context's
 * {@link DomainIdentifier} values, with guaranteed cleanup via
 * {@link AutoCloseable}.
 *
 * <p>This class manages the <em>mechanism</em> — putting entries into MDC at
 * the start of a scope, removing exactly those entries at the end. It does
 * not know which identifiers exist in any specific context. Callers supply
 * their own context-specific {@link DomainIdentifier} instances.
 *
 * <h2>Typical usage at a request entry point</h2>
 *
 * <pre>{@code
 * try (var ctx = DomainContext.open(
 *         OrderContextKey.ORDER_ID.of(orderId.value()),
 *         OrderContextKey.CUSTOMER_ID.of(customerId.value()))) {
 *     // every log line emitted in this scope carries order.id and customer.id
 *     return checkoutSaga.checkout(orderId, request);
 * }
 * // MDC is restored to its prior state here, even on exception
 * }</pre>
 *
 * <h2>Adding identifiers mid-scope</h2>
 *
 * <p>When a downstream operation discovers a new context-local identifier
 * (an Inventory adapter that just received a {@code ReservationId}, for
 * example), use {@link #include}:
 *
 * <pre>{@code
 * try (var ctx = DomainContext.open(orderId, customerId)) {
 *     var reservation = inventoryPort.reserve(order);
 *     ctx.include(InventoryContextKey.RESERVATION_ID.of(reservation.id()));
 *     // reservation.id is also cleared when ctx closes
 * }
 * }</pre>
 *
 * <h2>Scope semantics</h2>
 *
 * <p>The contract is <em>caller-scoped</em>: this class never blanket-clears
 * MDC, only removes the entries it added or had explicitly added to it.
 * If MDC already contained an entry for one of our keys when {@link #open}
 * was called, that pre-existing entry is overwritten and not restored on
 * close. That choice keeps the implementation small; in the workshop's
 * request-per-thread model with Quarkus, MDC entries should never have
 * been set before request entry anyway.
 *
 * <p>If your application requires save-and-restore semantics for nested
 * scopes, wrap calls in a higher-level helper rather than complicating
 * this one — keeping the mechanism small is part of why it can live in a
 * shared module without becoming an attractor for context-specific logic.
 */
public final class DomainContext implements AutoCloseable {

    private final Set<String> ownedKeys;

    private DomainContext(Set<String> ownedKeys) {
        this.ownedKeys = ownedKeys;
    }

    /**
     * Open a scope, putting each supplied identifier into MDC.
     * Identifiers with the same {@link DomainIdentifier#key()} that appear
     * later in the argument list overwrite earlier ones.
     */
    public static DomainContext open(DomainIdentifier... identifiers) {
        var owned = new LinkedHashSet<String>();
        for (var id : identifiers) {
            putIdentifier(owned, id);
        }
        return new DomainContext(owned);
    }

    /**
     * Open a scope from any iterable of identifiers — useful when the set
     * of identifiers is built dynamically (for example, restored from
     * Kafka headers in a consumer).
     */
    public static DomainContext open(Iterable<? extends DomainIdentifier> identifiers) {
        var owned = new LinkedHashSet<String>();
        for (var id : identifiers) {
            putIdentifier(owned, id);
        }
        return new DomainContext(owned);
    }

    /**
     * Add one more identifier to the current scope. The added identifier
     * will be cleared when this {@code DomainContext} is closed, along
     * with any others that were already in it.
     */
    public DomainContext include(DomainIdentifier identifier) {
        putIdentifier(ownedKeys, identifier);
        return this;
    }

    @Override
    public void close() {
        // Remove only the keys this scope owns. Never blanket-clear MDC.
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
