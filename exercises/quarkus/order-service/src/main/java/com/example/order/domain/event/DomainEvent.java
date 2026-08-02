package com.example.order.domain.event;

import com.example.order.domain.model.OrderId;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * The parent type for all domain events emitted by the Order context.
 *
 * <p>Sealed - the three permitted subtypes are the entire domain event
 * vocabulary for Order. New event types require an explicit code change
 * here, which is the right kind of friction: a domain event added without
 * coordinated downstream updates is a coupling breach.
 *
 * <p>Module 5 references this file when discussing Anti-Corruption Layers:
 * the events Order publishes are part of its <em>outbound</em> wire
 * contract with consumers like Notification. Changes here cross context
 * boundaries.
 *
 * <h2>Wire contract</h2>
 *
 * <p>The {@code @JsonTypeInfo} / {@code @JsonSubTypes} annotations make
 * this the explicit, declared outbound wire contract: every event published
 * to Kafka carries an {@code "eventType"} property whose value is one of
 * the names below. Notification's {@link
 * com.example.notification.domain.event.InboundOrderEvent} mirrors this
 * declaration on the consumer side, with the <em>identical</em> set of
 * wire names. The two sides do <em>not</em> share Java types - that would
 * be a Shared Kernel violation per Khononov - but they DO share this
 * narrow, explicit string contract.
 *
 * <p>Per Khononov's <em>Balancing Coupling</em>: when integration distance
 * is high (cross-context, async over Kafka), the contract should be
 * <em>visible and explicit</em> at both endpoints rather than implicit in
 * a publisher's serialization plumbing. Hiding the discriminator in
 * {@code OrderEventKafkaPublisher} would create implicit knowledge
 * coupling between the domain and infrastructure layer - worse than
 * the small framework leak of two annotations declared right next to the
 * events they classify.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderPlaced.class,    name = "OrderPlaced"),
        @JsonSubTypes.Type(value = OrderConfirmed.class, name = "OrderConfirmed"),
        @JsonSubTypes.Type(value = OrderCancelled.class, name = "OrderCancelled"),
})
public sealed interface DomainEvent
        permits OrderPlaced, OrderConfirmed, OrderCancelled {

    /**
     * A unique identifier for this specific event instance. Useful for
     * idempotent consumers and de-duplication.
     */
    UUID eventId();

    /**
     * The aggregate this event is about. All Order events belong to one
     * Order.
     */
    OrderId orderId();

    /**
     * Domain time of occurrence - when, in business terms, this event
     * happened. Distinct from "when the event was published to Kafka",
     * which can differ if there's a publishing delay.
     */
    Instant occurredAt();

    /**
     * The event type name, in past tense, in the ubiquitous language.
     * Defaults to the simple class name - subclasses may override if a
     * specific wire name is required.
     */
    default String eventType() {
        return getClass().getSimpleName();
    }
}
