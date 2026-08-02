package com.example.inventory.infrastructure.web;

import com.example.inventory.application.ReserveStockCommand;
import com.example.inventory.application.ReserveStockUseCase;
import com.example.inventory.domain.model.ProductCode;
import com.example.inventory.domain.model.Reservation;
import com.example.inventory.domain.model.ReservationLine;
import com.example.inventory.domain.model.ReservationStatus;
import com.example.inventory.infrastructure.web.dto.ReserveRequestDto;
import com.example.inventory.infrastructure.web.dto.ReserveResponseDto;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * REST resource for stock reservation. Translates Order's wire shape into
 * {@link ReserveStockCommand}, calls the shared use case, translates the
 * resulting {@link Reservation} back to the wire shape.
 *
 * <p>Both this resource and the gRPC service ({@code InventoryGrpcService})
 * delegate into the same {@link ReserveStockUseCase} - the transport
 * choice doesn't change business behavior. Module 5 uses this parallelism
 * as the architectural payoff for the ACL discussion.
 */
@Path("/api/inventory")
public class InventoryRestResource {

    private final ReserveStockUseCase useCase;

    public InventoryRestResource(ReserveStockUseCase useCase) {
        this.useCase = useCase;
    }

    @POST
    @Path("/reserve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ReserveResponseDto reserve(ReserveRequestDto request) {
        ReserveStockCommand command = toCommand(request);
        Reservation reservation = useCase.reserve(command);
        return fromDomain(reservation);
    }

    private static ReserveStockCommand toCommand(ReserveRequestDto request) {
        List<ReservationLine> lines = request.lineItems().stream()
                .map(li -> new ReservationLine(
                        ProductCode.of(li.productCode()),
                        li.requestedQuantity()))
                .toList();
        return new ReserveStockCommand(
                request.orderId(), request.customerId(), lines);
    }

    private static ReserveResponseDto fromDomain(Reservation reservation) {
        return new ReserveResponseDto(
                reservation.id().value(),
                wireStatus(reservation.status()),
                reservation.reason());
    }

    private static ReserveResponseDto.Status wireStatus(ReservationStatus status) {
        return switch (status) {
            case AVAILABLE   -> ReserveResponseDto.Status.AVAILABLE;
            case PARTIAL     -> ReserveResponseDto.Status.PARTIAL;
            case UNAVAILABLE -> ReserveResponseDto.Status.UNAVAILABLE;
        };
    }
}
