using System.Diagnostics;
using System.Diagnostics.Metrics;
using ShippingService.Domain.Identifiers;
using ShippingService.Domain.Models;
using SharedObservability;

namespace ShippingService.Application;

/// <summary>
/// The schedule-shipment use case. Schedules a shipment for a fulfilled order.
/// Shipping always succeeds in this workshop model -- the outcome is always SCHEDULED.
/// </summary>
public class ScheduleShipmentUseCase
{
    private static readonly ActivitySource Source = new("ShippingService");
    private static readonly Meter ShippingMeter = new("ShippingService");
    private static readonly Counter<long> ShipmentsCounter =
        ShippingMeter.CreateCounter<long>("shipping_shipments_scheduled_total",
            description: "Total shipments scheduled by class and tier");

    private readonly ILogger<ScheduleShipmentUseCase> _logger;

    public ScheduleShipmentUseCase(ILogger<ScheduleShipmentUseCase> logger)
    {
        _logger = logger;
    }

    public Shipment Schedule(ScheduleShipmentCommand command)
    {
        using var activity = Source.StartActivity("Shipping.Schedule");

        using var ctx = new DomainContext(_logger,
            ShippingContextKey.OrderId.Of(command.OrderId),
            ShippingContextKey.CustomerId.Of(command.CustomerId));

        var tier = BaggageHelpers.Get("customer.tier") ?? "unknown";

        activity?.SetTag("order.id", command.OrderId);
        activity?.SetTag("customer.id", command.CustomerId);
        activity?.SetTag("customer.tier", tier);
        activity?.SetTag("shipping.class", command.ShippingClass);

        var shipment = Shipment.Schedule(command.OrderId, command.ShippingClass);

        // Enrich context with result
        BaggageHelpers.Set("shipment.id", shipment.Id.Value);
        activity?.SetTag("shipment.id", shipment.Id.Value);
        activity?.SetTag("shipment.estimated_days", shipment.EstimatedDays);

        _logger.LogInformation("Shipment scheduled: {ShipmentId} (class={ShippingClass} estimatedDays={EstimatedDays})",
            shipment.Id, command.ShippingClass, shipment.EstimatedDays);

        ShipmentsCounter.Add(1,
            new KeyValuePair<string, object?>("class", command.ShippingClass),
            new KeyValuePair<string, object?>("tier", tier));

        return shipment;
    }
}
