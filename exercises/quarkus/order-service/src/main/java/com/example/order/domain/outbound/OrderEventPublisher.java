package com.example.order.domain.outbound;

import com.example.order.domain.event.DomainEvent;

/**
 * Outbound port for publishing {@link DomainEvent}s.
 *
 * <p>The Kafka-backed implementation lives in
 * {@code infrastructure/kafka/OrderEventKafkaPublisher}. Keeping the saga
 * dependent only on this interface means the saga is decoupled from
 * messaging concerns - it doesn't know whether events go to Kafka, an
 * in-process bus, or anywhere else.
 *
 * <p>The publisher implementation is responsible for putting the workshop's
 * domain identifiers into Kafka headers (see {@code KafkaHeaderPropagator}
 * in shared-observability), so consumers in other contexts can correlate
 * across the asynchronous boundary.
 */
public interface OrderEventPublisher {

    void publish(DomainEvent event);
}
