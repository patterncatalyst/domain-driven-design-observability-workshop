namespace NotificationService.Domain;

/// <summary>Unique identifier for a notification, prefixed with "notif_".</summary>
public record NotificationId(string Value)
{
    private const string Prefix = "notif_";

    public static NotificationId Generate() => new($"{Prefix}{Guid.NewGuid()}");

    public override string ToString() => Value;
}

/// <summary>
/// The kind of notification sent, driven by the originating order event.
/// </summary>
public enum NotificationKind
{
    PlacedAck,
    Confirmation,
    Cancellation
}

public static class NotificationKindExtensions
{
    private static readonly Dictionary<NotificationKind, string> Labels = new()
    {
        [NotificationKind.PlacedAck] = "PLACED_ACK",
        [NotificationKind.Confirmation] = "CONFIRMATION",
        [NotificationKind.Cancellation] = "CANCELLATION",
    };

    /// <summary>Returns the wire/metric label for this kind (e.g. "PLACED_ACK").</summary>
    public static string ToLabel(this NotificationKind kind) => Labels[kind];
}

/// <summary>
/// Aggregate root: a notification sent to a customer about an order event.
/// Created exclusively via the <see cref="Send"/> factory method.
/// </summary>
public record Notification(
    NotificationId Id,
    NotificationKind Kind,
    string OrderId,
    string CustomerId,
    string CustomerTier,
    string Channel,
    DateTime SentAt)
{
    /// <summary>
    /// Factory: creates a new Notification with a generated ID and current UTC timestamp.
    /// </summary>
    public static Notification Send(
        NotificationKind kind,
        string orderId,
        string customerId,
        string customerTier,
        string channel)
    {
        return new Notification(
            NotificationId.Generate(),
            kind,
            orderId,
            customerId,
            customerTier,
            channel,
            DateTime.UtcNow);
    }
}
