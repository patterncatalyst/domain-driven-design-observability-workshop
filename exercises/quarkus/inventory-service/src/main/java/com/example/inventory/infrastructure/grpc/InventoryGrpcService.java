package com.example.inventory.infrastructure.grpc;

import com.example.inventory.application.ReserveStockCommand;
import com.example.inventory.application.ReserveStockUseCase;
import com.example.inventory.domain.model.ProductCode;
import com.example.inventory.domain.model.Reservation;
import com.example.inventory.domain.model.ReservationLine;
import com.example.inventory.domain.model.ReservationStatus;
import com.example.inventory.v1.InventoryServiceGrpc;
import com.example.inventory.v1.ReserveRequest;
import com.example.inventory.v1.ReserveResponse;
// NOTE: the protobuf-generated ReservationStatus enum lives in
// com.example.inventory.v1 and shares a simple name with the domain
// ReservationStatus enum. To avoid ambiguity, we fully-qualify the
// protobuf one at use sites below rather than importing it.

import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;

import java.util.List;

/**
 * gRPC service implementation. Same business logic as
 * {@link com.example.inventory.infrastructure.web.InventoryRestResource} -
 * translates wire types to {@link ReserveStockCommand}, calls the shared
 * {@link ReserveStockUseCase}, translates the result back.
 *
 * <p>The {@code @GrpcService} annotation is Quarkus's marker that this
 * class is a gRPC server-side bean (not a client stub). The base class
 * {@code InventoryServiceGrpc.InventoryServiceImplBase} is generated from
 * {@code inventory.proto} during the build.
 */
@GrpcService
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final ReserveStockUseCase useCase;

    public InventoryGrpcService(ReserveStockUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void reserve(ReserveRequest request, StreamObserver<ReserveResponse> response) {
        try {
            Reservation reservation = useCase.reserve(toCommand(request));
            response.onNext(toWire(reservation));
            response.onCompleted();
        } catch (RuntimeException e) {
            response.onError(e);
        }
    }

    // ------------------------------------------------------------------------
    // Translation: protobuf wire types <-> domain types
    // ------------------------------------------------------------------------

    private static ReserveStockCommand toCommand(ReserveRequest request) {
        List<ReservationLine> lines = request.getLineItemsList().stream()
                .map(li -> ReservationLine.reserved(
                        ProductCode.of(li.getSku()),
                        li.getQuantity()))
                .toList();
        return new ReserveStockCommand(
                request.getOrderId(),
                request.getCustomerId(),
                lines);
    }

    private static ReserveResponse toWire(Reservation reservation) {
        return ReserveResponse.newBuilder()
                .setReservationId(reservation.id().value())
                .setStatus(toWireStatus(reservation.status()))
                .build();
    }

    private static com.example.inventory.v1.ReservationStatus toWireStatus(
            ReservationStatus status) {
        return switch (status) {
            case AVAILABLE   ->
                    com.example.inventory.v1.ReservationStatus.RESERVATION_STATUS_RESERVED;
            case PARTIAL     ->
                    com.example.inventory.v1.ReservationStatus.RESERVATION_STATUS_PARTIAL;
            case UNAVAILABLE ->
                    com.example.inventory.v1.ReservationStatus.RESERVATION_STATUS_UNAVAILABLE;
        };
    }
}
