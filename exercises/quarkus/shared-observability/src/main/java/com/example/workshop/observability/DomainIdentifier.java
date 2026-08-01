package com.example.workshop.observability;

/**
 * The minimal contract for a value carrying a context-managed identifier.
 *
 * <p>Each bounded context defines its own identifiers -- typically as a typed
 * enum implementing this interface -- and uses them with {@link DomainContext}
 * and {@link KafkaHeaderPropagator}. The shared-observability module
 * <em>does not know</em> which identifiers exist in any specific context;
 * it only knows the shape of an identifier and how to propagate one.
 *
 * <h2>Conventions</h2>
 * <ul>
 *   <li>{@link #key()} should be a dotted name in the bounded context's
 *       ubiquitous language: {@code order.id}, {@code customer.id},
 *       {@code reservation.id}, {@code shipment.id}.</li>
 *   <li>{@link #value()} is whatever string representation the identifier
 *       has on the wire / in logs.</li>
 *   <li>Both {@code key()} and {@code value()} must be non-null.</li>
 * </ul>
 */
public interface DomainIdentifier {

    /**
     * The propagation key -- dotted, lowercase, in the bounded context's
     * ubiquitous language. Example: {@code "order.id"}.
     */
    String key();

    /**
     * The string representation of the identifier value.
     */
    String value();
}
