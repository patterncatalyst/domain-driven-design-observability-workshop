package com.example.order.domain.model;

/**
 * Customer tier - a bounded enumeration that's safe to use as a metric label
 * without exploding cardinality.
 *
 * <p>Order looks up the tier via {@code CustomerProfileLookup} at the saga
 * entry point, then propagates it via OpenTelemetry baggage so downstream
 * spans, logs, and metrics in other contexts can also tag by tier.
 *
 * <p>Module 3c's "checkout success rate by tier" panel and Module 4's
 * debugging exercise both rely on this enum's values being stable and
 * exhaustive - if a fifth tier appeared, the dashboard panel would silently
 * drop the new tier's traffic until updated. That's an instance of the
 * cardinality discipline Module 6 covers.
 */
public enum CustomerTier {
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM
}
