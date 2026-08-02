package com.example.order.infrastructure.customer;

import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.CustomerTier;
import com.example.order.domain.service.CustomerProfile;
import com.example.order.domain.service.CustomerProfileLookup;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * In-memory implementation of {@link CustomerProfileLookup}.
 *
 * <p>For workshop simplicity, customer tier is derived deterministically
 * from a suffix in the customer id ({@code _bronze}, {@code _silver},
 * {@code _gold}, {@code _platinum}). This avoids needing a real customer
 * database while keeping the workshop scenarios reproducible:
 *
 * <pre>{@code
 *   cust_alice_silver  -> SILVER
 *   cust_dave_gold     -> GOLD
 *   cust_xyz           -> BRONZE   (default for unknown customers)
 * }</pre>
 *
 * <p>A real Order context would talk to a Customer service (or read from a
 * customer-projection table) here. The split between this in-memory variant
 * and a hypothetical "real" variant is exactly what the {@link
 * CustomerProfileLookup} port is for - swap implementations, the saga
 * doesn't change.
 */
@ApplicationScoped
public class InMemoryCustomerProfileLookup implements CustomerProfileLookup {

    @Override
    public CustomerProfile lookup(CustomerId customerId) {
        return new CustomerProfile(customerId, deriveTierFromId(customerId.value()));
    }

    private static CustomerTier deriveTierFromId(String id) {
        // Match the longest suffix first - "_platinum" before "_p..."
        String lower = id.toLowerCase();
        if (lower.endsWith("_platinum")) return CustomerTier.PLATINUM;
        if (lower.endsWith("_gold"))     return CustomerTier.GOLD;
        if (lower.endsWith("_silver"))   return CustomerTier.SILVER;
        if (lower.endsWith("_bronze"))   return CustomerTier.BRONZE;
        return CustomerTier.BRONZE;
    }
}
