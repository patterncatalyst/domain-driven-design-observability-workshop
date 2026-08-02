package com.example.order.domain.service;

import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.CustomerTier;

/**
 * Domain service for resolving a {@link CustomerId} to enough customer
 * context that the saga can make tier-aware decisions and propagate
 * tier downstream as baggage.
 *
 * <p>This interface is in the domain layer; the implementation lives in
 * {@code infrastructure} (or, in this workshop's simplified setup, it can
 * be a CDI-managed in-memory implementation right alongside it - a real
 * system would hit a customer service or database).
 *
 * <p>Why a domain service rather than just hitting a "customer profile"
 * outbound port: in the workshop scenario, the customer profile is stable
 * enough that lookup is cached / in-memory, and the saga's logic depends
 * on the tier value rather than on the act of fetching it. Treating it
 * as a domain service rather than a port keeps the saga's collaboration
 * graph small.
 */
public interface CustomerProfileLookup {

    /**
     * Look up a customer profile by id.
     *
     * @return the profile - never null. Unknown customers default to
     *         {@code BRONZE} so the saga can always proceed.
     */
    CustomerProfile lookup(CustomerId customerId);
}
