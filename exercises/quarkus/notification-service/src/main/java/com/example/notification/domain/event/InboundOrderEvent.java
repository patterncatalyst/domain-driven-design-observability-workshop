package com.example.notification.domain.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * Notification's own view of the events Order publishes.
 *
 * <p>Crucially, this is <em>not</em> the same Java type as Order's
 * {@code DomainEvent}. Order owns its event hierarchy; Notification
 * defines its own that happens to align with the on-wire JSON shape.
 * The wire IS the contract between contexts; sharing Java types would
 * be a Shared Kernel violation per Khononov.
 *
 * <p>Jackson polymorphic deserialization picks the subtype based on the
 * {@code eventType} field on the wire (set by Order's {@code DomainEvent}
 * via its default {@code eventType()} method, which uses the simple
 * class name).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = InboundOrderPlaced.class,    name = "OrderPlaced"),
        @JsonSubTypes.Type(value = InboundOrderConfirmed.class, name = "OrderConfirmed"),
        @JsonSubTypes.Type(value = InboundOrderCancelled.class, name = "OrderCancelled"),
})
public sealed interface InboundOrderEvent
        permits InboundOrderPlaced, InboundOrderConfirmed, InboundOrderCancelled {

    String orderId();

    Instant occurredAt();
}
