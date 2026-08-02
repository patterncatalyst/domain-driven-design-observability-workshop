package com.example.order.domain.service;

import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.CustomerTier;

import java.util.Objects;

/**
 * The slice of customer information the Order context needs.
 *
 * <p>We deliberately don't carry a full customer profile here - name,
 * address, billing details, etc. would all be Customer's vocabulary, not
 * Order's. Order needs the customer's tier to make tier-aware decisions
 * and to propagate the tier as baggage; everything else stays in
 * Customer's bounded context.
 */
public record CustomerProfile(CustomerId id, CustomerTier tier) {

    public CustomerProfile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tier, "tier");
    }
}
