using System.Text.Json;

namespace NotificationService.Domain;

/// <summary>
/// Base record for inbound order events consumed from the order-events Kafka topic.
/// These are the Notification service's own view of order events -- NOT shared types.
/// </summary>
public record InboundOrderEvent(
    string EventType,
    string EventId,
    string OrderId,
    string OccurredAt)
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    /// <summary>
    /// Deserializes a JSON payload into the correct <see cref="InboundOrderEvent"/>
    /// subtype by reading the <c>eventType</c> discriminator field.
    /// </summary>
    public static InboundOrderEvent DeserializeEvent(string json)
    {
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;

        if (!root.TryGetProperty("eventType", out var eventTypeProp))
            throw new InvalidOperationException("Missing eventType discriminator in event JSON");

        var eventType = eventTypeProp.GetString()
            ?? throw new InvalidOperationException("eventType is null");

        return eventType switch
        {
            "OrderPlaced" => JsonSerializer.Deserialize<InboundOrderPlaced>(json, JsonOptions)!,
            "OrderConfirmed" => JsonSerializer.Deserialize<InboundOrderConfirmed>(json, JsonOptions)!,
            "OrderCancelled" => JsonSerializer.Deserialize<InboundOrderCancelled>(json, JsonOptions)!,
            _ => throw new InvalidOperationException($"Unknown eventType: {eventType}"),
        };
    }
}

/// <summary>An order was placed by a customer.</summary>
public record InboundOrderPlaced(
    string EventType,
    string EventId,
    string OrderId,
    string OccurredAt,
    string CustomerId,
    string CartId)
    : InboundOrderEvent(EventType, EventId, OrderId, OccurredAt);

/// <summary>An order was confirmed after successful reservation, payment, and shipping.</summary>
public record InboundOrderConfirmed(
    string EventType,
    string EventId,
    string OrderId,
    string OccurredAt,
    string CustomerId,
    string ReservationId,
    string AuthorizationId,
    string ShipmentId)
    : InboundOrderEvent(EventType, EventId, OrderId, OccurredAt);

/// <summary>An order was cancelled due to a failure in the saga.</summary>
public record InboundOrderCancelled(
    string EventType,
    string EventId,
    string OrderId,
    string OccurredAt,
    string CustomerId,
    string FailedAt,
    string Reason)
    : InboundOrderEvent(EventType, EventId, OrderId, OccurredAt);
