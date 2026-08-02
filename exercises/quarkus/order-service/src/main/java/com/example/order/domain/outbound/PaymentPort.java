package com.example.order.domain.outbound;

import com.example.order.domain.model.Order;

/**
 * Outbound port for payment authorization, in Order's ubiquitous language.
 *
 * <p>Unlike {@link InventoryPort}, the Payment context shares enough of
 * Order's vocabulary that the adapter behind this port is a thin client,
 * not a full Anti-Corruption Layer. Module 5 discusses this distinction:
 * an ACL is a coupling-management tool, not a code-cleanliness ritual,
 * and adding one between Order and Payment would be overkill for the
 * shape of that relationship in our scenario.
 */
public interface PaymentPort {

    AuthorizationOutcome authorize(Order order);
}
