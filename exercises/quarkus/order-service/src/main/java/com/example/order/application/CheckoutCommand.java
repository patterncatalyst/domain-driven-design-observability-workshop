package com.example.order.application;

import com.example.order.domain.model.CartId;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.LineItem;

import java.util.List;
import java.util.Objects;

/**
 * Input to the {@link CheckoutSaga} use case.
 *
 * <p>Plain record, no framework dependencies. The application layer's
 * inputs are expressed in domain types - the conversion from inbound HTTP
 * (or, hypothetically, gRPC inbound) lives in the web adapter.
 */
public record CheckoutCommand(
        CustomerId customerId,
        CartId cartId,
        List<LineItem> lineItems
) {
    public CheckoutCommand {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(cartId, "cartId");
        Objects.requireNonNull(lineItems, "lineItems");
        if (lineItems.isEmpty()) {
            throw new IllegalArgumentException("lineItems must not be empty");
        }
        lineItems = List.copyOf(lineItems);
    }
}
