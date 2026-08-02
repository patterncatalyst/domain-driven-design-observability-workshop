namespace ShippingService.Application;

/// <summary>
/// Command to schedule a shipment.
/// </summary>
public record ScheduleShipmentCommand(
    string OrderId,
    string CustomerId,
    string ShippingClass);
