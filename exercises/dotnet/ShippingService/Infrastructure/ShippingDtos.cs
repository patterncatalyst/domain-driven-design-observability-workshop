using System.Text.Json.Serialization;

namespace ShippingService.Infrastructure;

/// <summary>Inbound DTO for shipment scheduling.</summary>
public record ScheduleRequest(
    [property: JsonPropertyName("orderId")] string OrderId,
    [property: JsonPropertyName("customerId")] string CustomerId,
    [property: JsonPropertyName("shippingClass")] string ShippingClass);

/// <summary>Outbound DTO for shipment scheduling result.</summary>
public record ScheduleResponse(
    [property: JsonPropertyName("shipmentId")] string ShipmentId,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("estimatedDays")] int EstimatedDays);
