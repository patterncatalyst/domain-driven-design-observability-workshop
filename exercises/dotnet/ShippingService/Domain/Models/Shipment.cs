namespace ShippingService.Domain.Models;

/// <summary>
/// The Shipment aggregate root. In the workshop's model, shipping always
/// succeeds -- there is no failed or rejected state. No persistence;
/// created in-memory and returned.
/// </summary>
public record Shipment(
    ShipmentId Id,
    string OrderId,
    string ShippingClass,
    int EstimatedDays,
    DateTime ScheduledAt)
{
    /// <summary>Estimated delivery days by shipping class.</summary>
    private static readonly Dictionary<string, int> EstimatedDaysMap = new(StringComparer.OrdinalIgnoreCase)
    {
        ["overnight"] = 1,
        ["express"] = 2,
        ["priority"] = 3,
        ["standard"] = 5,
    };

    private const int DefaultEstimatedDays = 5;

    /// <summary>
    /// Factory: schedule a new shipment. Estimated delivery days are derived
    /// from the shipping class.
    /// </summary>
    public static Shipment Schedule(string orderId, string shippingClass)
    {
        var days = EstimatedDaysMap.GetValueOrDefault(shippingClass, DefaultEstimatedDays);
        return new Shipment(
            ShipmentId.Generate(),
            orderId,
            shippingClass,
            days,
            DateTime.UtcNow);
    }
}
