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
        List<ReservationLine> lines = request.items().stream()
                .map(item -> {
                    String pc = item.sku().startsWith("SKU-")
                            ? "PROD-" + item.sku().substring(4)
                            : item.sku();
                    return new ReservationLine(
                            ProductCode.of(pc), item.quantity(), item.quantity(), true);
                })
                .toList();
        return new ReserveStockCommand(request.orderId(), "", lines);
    }

    private static ReserveResponseDto fromDomain(Reservation reservation) {
        boolean isAvailable = reservation.status() == ReservationStatus.AVAILABLE;
        List<ReserveResponseDto.LineDto> wireLines = reservation.lines().stream()
                .map(line -> new ReserveResponseDto.LineDto(
                        line.productCode().value(),
                        isAvailable ? line.requestedQuantity() : 0,
                        isAvailable))
                .toList();

        return new ReserveResponseDto(
                reservation.id().value(),
                wireStatus(reservation.status()),
                reservation.reason(),
                wireLines);
    }

    private static String wireStatus(ReservationStatus status) {
        return switch (status) {
            case AVAILABLE   -> "RESERVED";
            case PARTIAL     -> "PARTIALLY_RESERVED";
            case UNAVAILABLE -> "UNAVAILABLE";
        };
    }
}
