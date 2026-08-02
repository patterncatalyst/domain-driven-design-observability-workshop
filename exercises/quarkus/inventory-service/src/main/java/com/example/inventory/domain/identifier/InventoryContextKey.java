package com.example.inventory.domain.identifier;

import com.example.workshop.observability.DomainIdentifier;

import java.util.Objects;

/**
 * The set of {@link DomainIdentifier} keys the Inventory bounded context owns.
 *
 * <p>Note that {@link #ORDER_ID} uses the key string {@code "order.id"} -
 * the same string Order's {@code OrderContextKey.ORDER_ID} uses. This is
 * <em>not</em> a shared kernel: Inventory and Order each define their own
 * enum, and the two happen to agree on the wire string. That agreement
 * IS the wire contract for cross-context identifier correlation. If
 * Order changed its key to {@code order.uuid}, Inventory would have to
 * either match it (coordinated change) or accept that consumers calling
 * the old shape can't correlate.
 *
 * <p>{@link #RESERVATION_ID} is purely Inventory's - other contexts can
 * read it from a Reservation event/header but only Inventory mints it.
 */
public enum InventoryContextKey {

    ORDER_ID("order.id"),
    CUSTOMER_ID("customer.id"),
    RESERVATION_ID("reservation.id"),
    PRODUCT_CODE("product.code");

    private final String key;

    InventoryContextKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public DomainIdentifier of(String value) {
        Objects.requireNonNull(value, () -> name() + " value");
        return new SimpleIdentifier(key, value);
    }

    private record SimpleIdentifier(String key, String value)
            implements DomainIdentifier {
    }
}
