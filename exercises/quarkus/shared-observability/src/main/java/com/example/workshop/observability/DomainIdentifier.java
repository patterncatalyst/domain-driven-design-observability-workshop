package com.example.workshop.observability;

/**
 * The minimal contract for a value carrying a context-managed identifier.
 *
 * <p>Each bounded context defines its own identifiers — typically as a typed
 * enum implementing this interface — and uses them with {@link DomainContext}
 * and {@link KafkaHeaderPropagator}. The shared-observability module
 * <em>does not know</em> which identifiers exist in any specific context;
 * it only knows the shape of an identifier and how to propagate one.
 *
 * <p>This is a deliberate Khononov-aligned design: keeping the shared module
 * generic prevents it from becoming a Shared Kernel that leaks one context's
 * vocabulary into others. Order owns its {@code OrderId}, Inventory owns
 * its {@code ReservationId}, and neither has to import the other to put
 * its own identifier in MDC or in a Kafka header.
 *
 * <h2>Conventions</h2>
 *
 * <ul>
 *   <li>{@link #key()} should be a dotted name in the bounded context's
 *       ubiquitous language: {@code order.id}, {@code customer.id},
 *       {@code reservation.id}, {@code shipment.id}. The dots make it
 *       compatible with OpenTelemetry semantic conventions and with JSON
 *       log appenders that promote MDC entries to top-level fields.</li>
 *   <li>{@link #value()} is whatever string representation the identifier
 *       has on the wire / in logs. For typed identifiers, this is usually
 *       just the wrapped string; for opaque tokens, the token itself.</li>
 *   <li>Both {@code key()} and {@code value()} must be non-null. A null
 *       value is not a "no identifier" — it's a programmer error. Use
 *       {@code Optional} or skip the call if the value is genuinely
 *       unknown.</li>
 * </ul>
 *
 * <h2>Recommended implementation pattern</h2>
 *
 * <p>In each bounded context, define a small typed enum or record:
 *
 * <pre>{@code
 * // in the order-service module
 * public record OrderId(String value) implements DomainIdentifier {
 *     public OrderId {
 *         Objects.requireNonNull(value, "OrderId value");
 *     }
 *     @Override public String key()   { return "order.id"; }
 * }
 * }</pre>
 *
 * <p>Or, when several identifiers in one context share key conventions,
 * a small enum factory:
 *
 * <pre>{@code
 * public enum OrderContextKey {
 *     ORDER_ID("order.id"), CUSTOMER_ID("customer.id"), CART_ID("cart.id");
 *     private final String key;
 *     OrderContextKey(String key) { this.key = key; }
 *     public DomainIdentifier of(String value) {
 *         return new SimpleId(key, value);
 *     }
 *     private record SimpleId(String key, String value) implements DomainIdentifier {}
 * }
 * }</pre>
 *
 * <p>Either way: the strings live with the context that owns them, never
 * here.
 */
public interface DomainIdentifier {

    /**
     * The propagation key — dotted, lowercase, in the bounded context's
     * ubiquitous language. Example: {@code "order.id"}.
     */
    String key();

    /**
     * The string representation of the identifier value.
     */
    String value();
}
