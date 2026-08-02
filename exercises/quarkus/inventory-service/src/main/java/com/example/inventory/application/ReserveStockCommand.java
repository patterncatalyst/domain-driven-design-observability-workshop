package com.example.inventory.application;

import com.example.inventory.domain.model.ReservationLine;

import java.util.List;
import java.util.Objects;

/**
 * Input to the {@link ReserveStockUseCase}.
 *
 * <p>Plain record - both the REST resource and the gRPC service translate
 * their respective wire types into this domain command before invoking
 * the use case.
 */
public record ReserveStockCommand(
        String orderId,
        String customerId,
        List<ReservationLine> lines
) {
    public ReserveStockCommand {
        Objects.requireNonNull(orderId, "orderId");
        if (customerId == null) customerId = "";
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        lines = List.copyOf(lines);
    }
}
