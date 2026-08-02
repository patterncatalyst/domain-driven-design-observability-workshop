package com.example.order.domain.identifier;

import com.example.workshop.observability.DomainIdentifier;

import java.util.Objects;

/**
 * The set of {@link DomainIdentifier} keys the Order bounded context owns.
 *
 * <p>Each entry exposes a factory method {@link #of(String)} that produces a
 * {@link DomainIdentifier} ready to hand to {@code DomainContext.open(...)},
 * {@code BaggageHelpers.put(...)}, or {@code KafkaHeaderPropagator.write(...)}.
 *
 * <p>The key strings here are <em>Order's</em> ubiquitous-language names for
 * these concepts. When Order publishes to Kafka, it sends headers under
 * these keys. Consumers in other contexts (Notification etc.) read by their
 * <em>own</em> equivalent keys - if both sides agree on the string, propagation
 * works. That agreement is part of the wire contract between contexts and
 * is exactly the kind of thing Module 5's ACL discussion makes explicit.
 *
 * <p>Note that {@link #CUSTOMER_TIER} is intentionally an Order-managed key:
 * Order is where the customer profile lookup happens (in {@code
 * CustomerProfileLookup}), so Order is the producer of that baggage entry.
 * Other contexts that consume it do so via their own identifier definitions.
 */
public enum OrderContextKey {

    ORDER_ID("order.id"),
    CUSTOMER_ID("customer.id"),
    CART_ID("cart.id"),
    CUSTOMER_TIER("customer.tier");

    private final String key;

    OrderContextKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /**
     * Produce a {@link DomainIdentifier} carrying this key with the given value.
     */
    public DomainIdentifier of(String value) {
        Objects.requireNonNull(value, () -> name() + " value");
        return new SimpleIdentifier(key, value);
    }

    /**
     * Internal record - private to this package. Other modules never see
     * this type; they only see the {@link DomainIdentifier} interface from
     * shared-observability.
     */
    private record SimpleIdentifier(String key, String value)
            implements DomainIdentifier {
    }
}
