package com.example.notification.infrastructure.kafka;

import com.example.notification.domain.event.InboundOrderEvent;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/**
 * Concrete Jackson-based deserializer for {@link InboundOrderEvent}.
 *
 * <p>Quarkus's {@code ObjectMapperDeserializer} is abstract; Kafka's
 * client instantiates configured deserializers via reflection using the
 * no-arg constructor, so we need a concrete subclass that pins the
 * target type. This class is referenced by application.properties:
 *
 * <pre>
 * mp.messaging.incoming.order-events.value.deserializer=
 *     com.example.notification.infrastructure.kafka.InboundOrderEventDeserializer
 * </pre>
 */
public class InboundOrderEventDeserializer extends ObjectMapperDeserializer<InboundOrderEvent> {
    public InboundOrderEventDeserializer() {
        super(InboundOrderEvent.class);
    }
}
