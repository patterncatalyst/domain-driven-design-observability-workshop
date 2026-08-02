package com.example.order.infrastructure.inventory;

import com.example.inventory.v1.InventoryServiceGrpc;
import com.example.inventory.v1.LineItem;
import com.example.inventory.v1.ReservationStatus;
import com.example.inventory.v1.ReserveRequest;
import com.example.inventory.v1.ReserveResponse;

import com.example.order.domain.model.Order;
import com.example.order.domain.outbound.InventoryPort;
import com.example.order.domain.outbound.ReservationOutcome;

import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC + ACL adapter for {@link InventoryPort}.
 *
 * <p>Structurally identical to {@link InventoryRestAdapter}: the same three
 * phases (outbound translation, wire call, inbound translation with drift
 * detection), the same span name ({@code Order.Acl.InventoryReserve}), the
 * same drift counter labels - only the transport differs. Module 5 uses
 * exactly this parallelism to make the architectural point: wire format
 * and translation discipline are independent design axes.
 *
 * <p>Active when {@code workshop.inventory.adapter=grpc}. The gRPC client
 * configuration ({@code quarkus.grpc.clients."inventory-grpc"...}) lives
 * in {@code application.properties}.
 */
@ApplicationScoped
@IfBuildProperty(name = "workshop.inventory.adapter", stringValue = "grpc")
public class InventoryGrpcAdapter implements InventoryPort {

    private static final Logger log = LoggerFactory.getLogger(InventoryGrpcAdapter.class);

    private final InventoryServiceGrpc.InventoryServiceBlockingStub stub;
    private final MeterRegistry meterRegistry;

    public InventoryGrpcAdapter(
            @GrpcClient("inventory-grpc")
            InventoryServiceGrpc.InventoryServiceBlockingStub stub,
            MeterRegistry meterRegistry) {
        this.stub = stub;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @WithSpan("Order.Acl.InventoryReserve")
    public ReservationOutcome reserve(Order order) {
        Span span = Span.current();
        span.setAttribute("acl.context", "inventory");
        span.setAttribute("acl.transport", "grpc");

        try {
            ReserveRequest wireRequest = toWire(order);
            span.setAttribute("acl.wire.line_count", wireRequest.getLineItemsCount());

            ReserveResponse wireResponse = stub.reserve(wireRequest);

            return fromWire(wireResponse);

        } catch (StatusRuntimeException e) {
            // gRPC-level failure: deadline exceeded, unavailable, internal, etc.
            recordTranslationFailure("transport", e);
            return new ReservationOutcome.Failure(
                    "transport",
                    "Inventory gRPC call failed: " + e.getStatus() + " " + e.getMessage(),
                    e);

        } catch (InventoryAclTranslationException e) {
            return new ReservationOutcome.Failure(
                    "drift",
                    e.getMessage(),
                    e);
        }
    }

    // ------------------------------------------------------------------------
    // Pure translation functions
    // ------------------------------------------------------------------------

    private static ReserveRequest toWire(Order order) {
        ReserveRequest.Builder builder = ReserveRequest.newBuilder()
                .setOrderId(order.id().value())
                .setCustomerId(order.customerId().value());
        for (var li : order.lineItems()) {
            builder.addLineItems(LineItem.newBuilder()
                    .setSku(li.sku().value())
                    .setQuantity(li.quantity())
                    .build());
        }
        return builder.build();
    }

    private ReservationOutcome fromWire(ReserveResponse wire) {
        if (wire == null) {
            recordDrift("null_response");
            throw new InventoryAclTranslationException(
                    "Inventory returned a null response");
        }

        ReservationStatus status = wire.getStatus();

        return switch (status) {
            case RESERVATION_STATUS_RESERVED -> {
                if (wire.getReservationId().isBlank()) {
                    recordDrift("missing_reservation_id");
                    throw new InventoryAclTranslationException(
                            "Inventory returned RESERVED with no reservationId - "
                            + "wire contract violation");
                }
                yield new ReservationOutcome.Reserved(wire.getReservationId());
            }
            case RESERVATION_STATUS_PARTIAL ->
                    new ReservationOutcome.Unavailable(
                            "partial reservation - some items not in stock");
            case RESERVATION_STATUS_UNAVAILABLE ->
                    new ReservationOutcome.Unavailable("stock unavailable");

            // Unspecified is the protobuf default - shows up if upstream
            // forgot to set the field, or if a new enum value was added
            // and we're an older client.
            case RESERVATION_STATUS_UNSPECIFIED -> {
                recordDrift("unspecified_status");
                throw new InventoryAclTranslationException(
                        "Inventory returned UNSPECIFIED status - "
                        + "wire contract violation or unrecognized version");
            }

            // protobuf enums also surface unknown values as UNRECOGNIZED.
            // Treat as drift.
            case UNRECOGNIZED -> {
                recordDrift("unrecognized_status");
                throw new InventoryAclTranslationException(
                        "Inventory returned an UNRECOGNIZED enum value - "
                        + "newer schema than this client knows about");
            }
        };
    }

    // ------------------------------------------------------------------------
    // Drift accounting - parallel to the REST variant
    // ------------------------------------------------------------------------

    private void recordDrift(String type) {
        meterRegistry.counter("acl_drift_total",
                "context", "inventory",
                "transport", "grpc",
                "type", type).increment();
        log.warn("ACL drift detected: context=inventory transport=grpc type={}", type);
    }

    private void recordTranslationFailure(String category, Throwable cause) {
        meterRegistry.counter("acl_failures_total",
                "context", "inventory",
                "transport", "grpc",
                "category", category).increment();
        log.warn("ACL transport failure: context=inventory transport=grpc "
                + "category={} cause={}", category, cause.getMessage());
    }
}
