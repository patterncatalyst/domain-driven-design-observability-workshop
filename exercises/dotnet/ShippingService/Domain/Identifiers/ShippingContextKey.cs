using SharedObservability;

namespace ShippingService.Domain.Identifiers;

/// <summary>
/// Shipping's typed identifier vocabulary. Shared wire names (order.id,
/// customer.id) are intentionally aligned with the Order service for
/// cross-service correlation.
/// </summary>
public sealed class ShippingContextKey
{
    public static readonly ShippingContextKey OrderId = new("order.id");
    public static readonly ShippingContextKey CustomerId = new("customer.id");
    public static readonly ShippingContextKey ShipmentId = new("shipment.id");

    public string Key { get; }

    private ShippingContextKey(string key) => Key = key;

    /// <summary>
    /// Bind this key to a concrete value, returning an <see cref="IDomainIdentifier"/>.
    /// </summary>
    public IDomainIdentifier Of(string value) => new BoundIdentifier(Key, value);

    private sealed record BoundIdentifier(string Key, string Value) : IDomainIdentifier;
}
