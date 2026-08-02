using SharedObservability;

namespace NotificationService.Domain;

/// <summary>
/// Notification's typed identifier vocabulary. Keys use dotted names aligned
/// with the rest of the workshop services for cross-service correlation.
/// </summary>
public sealed class NotificationContextKey
{
    public static readonly NotificationContextKey OrderId = new("order.id");
    public static readonly NotificationContextKey CustomerId = new("customer.id");
    public static readonly NotificationContextKey CartId = new("cart.id");
    public static readonly NotificationContextKey NotificationId = new("notification.id");

    public string Key { get; }

    private NotificationContextKey(string key) => Key = key;

    /// <summary>
    /// Bind this key to a concrete value, returning an <see cref="IDomainIdentifier"/>.
    /// </summary>
    public IDomainIdentifier Of(string value) => new BoundIdentifier(Key, value);

    /// <summary>
    /// Looks up a <see cref="NotificationContextKey"/> by its dotted key name.
    /// Returns null if the key is not recognized.
    /// </summary>
    public static NotificationContextKey? FromKey(string key) =>
        key switch
        {
            "order.id" => OrderId,
            "customer.id" => CustomerId,
            "cart.id" => CartId,
            "notification.id" => NotificationId,
            _ => null,
        };

    private sealed record BoundIdentifier(string Key, string Value) : IDomainIdentifier;
}
