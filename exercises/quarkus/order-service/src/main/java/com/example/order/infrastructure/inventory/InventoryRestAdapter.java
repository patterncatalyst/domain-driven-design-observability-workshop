package com.example.order.infrastructure.inventory;

import com.example.order.domain.model.Order;
import com.example.order.domain.outbound.InventoryPort;
import com.example.order.domain.outbound.ReservationOutcome;
import com.example.order.infrastructure.inventory.dto.InventoryReserveRequestDto;
import com.example.order.infrastructure.inventory.dto.InventoryReserveResponseDto;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * REST + ACL adapter for {@link InventoryPort}.
 *
 * <p>This is the canonical example for Module 5's Anti-Corruption Layer
 * discussion. It does three things, separated cleanly:
 *
 * <ol>
 *   <li><strong>Outbound translation</strong> - {@link #toWire(Order)} maps
 *       Order's domain types to Inventory's wire types. Pure function;
 *       no I/O.</li>
 *   <li><strong>Wire call</strong> - delegated to {@link InventoryRestClient}.
 *       The {@code @WithSpan} on this method names the ACL boundary span;
 *       the rest-client extension adds its own child span for the actual
 *       HTTP call.</li>
 *   <li><strong>Inbound translation + drift detection</strong> -
 *       {@link #fromWire} maps the response into a {@link ReservationOutcome}
 *       expressed in Order's vocabulary. <em>Drift dies here.</em> Unknown
 *       status enum values, inconsistent field combinations, or anything
 *       the contract didn't predict is converted to a typed
 *       {@link ReservationOutcome.Failure} and a counter increments so
 *       it shows up on the Saga dashboard.</li>
 * </ol>
 *
 * <p>Active when {@code workshop.inventory.adapter=rest} (the default).
 * The Module 5 transport switch flips to the gRPC variant by setting that
 * property to {@code grpc}; both adapters exist in the application but
 * only the configured one is registered as the {@link InventoryPort}
 * bean. See {@code application.properties}.
 */
@ApplicationScoped
@IfBuildProperty(name = "workshop.inventory.adapter", stringValue = "rest")
public class InventoryRestAdapter implements InventoryPort {

    private static final Logger log = LoggerFactory.getLogger(InventoryRestAdapter.class);

    private final InventoryRestClient client;
    private final MeterRegistry meterRegistry;

    public InventoryRestAdapter(@RestClient InventoryRestClient client,
                                MeterRegistry meterRegistry) {
        this.client = client;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @WithSpan("Order.Acl.InventoryReserve")
    public ReservationOutcome reserve(Order order) {
        Span span = Span.current();
        span.setAttribute("acl.context", "inventory");
        span.setAttribute("acl.transport", "rest");

        try {
            // 1. Outbound translation: Order -> wire
            InventoryReserveRequestDto wireRequest = toWire(order);
            span.setAttribute("acl.wire.line_count", wireRequest.items().size());

            // 2. Wire call - the rest-client extension adds its own span
            //    underneath this one for the HTTP exchange.
            InventoryReserveResponseDto wireResponse = client.reserve(wireRequest);

            // 3. Inbound translation - drift dies here, not in the saga.
            return fromWire(wireResponse);

        } catch (WebApplicationException e) {
            // HTTP-level failure: 4xx/5xx, connection refused, timeout.
            // Surface as a typed Failure so the saga's switch handles it
            // exhaustively without an ad-hoc catch.
            recordTranslationFailure("transport", e);
            return new ReservationOutcome.Failure(
                    "transport",
                    "Inventory REST call failed: " + e.getMessage(),
                    e);

        } catch (InventoryAclTranslationException e) {
            // Drift-detection failure - rethrown from fromWire().
            // Already recorded the counter; just convert to Failure.
            return new ReservationOutcome.Failure(
                    "drift",
                    e.getMessage(),
                    e);
        }
    }

    // ------------------------------------------------------------------------
    // Pure translation functions - the body of the ACL
    // ------------------------------------------------------------------------

    private static InventoryReserveRequestDto toWire(Order order) {
        List<InventoryReserveRequestDto.Item> wireItems = order.lineItems().stream()
                .map(li -> new InventoryReserveRequestDto.Item(
                        li.sku().value(),
                        li.quantity()))
                .toList();
        return new InventoryReserveRequestDto(
                order.id().value(),
                wireItems);
    }

    private ReservationOutcome fromWire(InventoryReserveResponseDto wire) {
        if (wire == null) {
            recordDrift("null_response");
            throw new InventoryAclTranslationException(
                    "Inventory returned a null response body");
        }
        if (wire.status() == null) {
            recordDrift("null_status");
            throw new InventoryAclTranslationException(
                    "Inventory response missing status");
        }

        return switch (wire.status()) {
            case "RESERVED" -> {
                if (wire.reservationId() == null || wire.reservationId().isBlank()) {
                    recordDrift("missing_reservation_id");
                    throw new InventoryAclTranslationException(
                            "Inventory returned RESERVED with no reservationId");
                }
                yield new ReservationOutcome.Reserved(wire.reservationId());
            }

            case "PARTIALLY_RESERVED" -> {
                String detail = wire.reason() != null
                        ? wire.reason()
                        : "partial reservation - some items not in stock";
                yield new ReservationOutcome.Unavailable(detail);
            }

            case "UNAVAILABLE" -> {
                String detail = wire.reason() != null
                        ? wire.reason()
                        : "stock unavailable";
                yield new ReservationOutcome.Unavailable(detail);
            }

            default -> {
                recordDrift("unknown_status");
                throw new InventoryAclTranslationException(
                        "Unknown inventory status: " + wire.status());
            }
        };
    }

    // ------------------------------------------------------------------------
    // Drift accounting - small but important
    // ------------------------------------------------------------------------

    private void recordDrift(String type) {
        meterRegistry.counter("acl_drift_total",
                "context", "inventory",
                "transport", "rest",
                "type", type).increment();
        log.warn("ACL drift detected: context=inventory type={}", type);
    }

    private void recordTranslationFailure(String category, Throwable cause) {
        meterRegistry.counter("acl_failures_total",
                "context", "inventory",
                "transport", "rest",
                "category", category).increment();
        log.warn("ACL transport failure: context=inventory category={} cause={}",
                category, cause.getMessage());
    }
}
