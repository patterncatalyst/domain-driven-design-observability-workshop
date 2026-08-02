using System.Diagnostics;
using System.Diagnostics.Metrics;
using InventoryService.Domain;
using SharedObservability;

namespace InventoryService.Application;

/// <summary>
/// Determines stock availability and creates a Reservation.
/// Stock levels are simulated via SKU-prefix conventions so the workshop
/// can exercise every reservation outcome without a real database.
/// </summary>
public sealed class ReserveStockUseCase
{
    internal static readonly ActivitySource Source = new("inventory-service");
    private static readonly Meter ServiceMeter = new("inventory-service");
    private static readonly Counter<long> ReservationCounter =
        ServiceMeter.CreateCounter<long>(
            "inventory_reservations_total",
            description: "Total inventory reservations by status and customer tier");

    private static readonly string[] OutOfStockPrefixes = ["OUT_", "OUT-"];
    private const string PartialPrefix = "PARTIAL_";

    private readonly ILogger<ReserveStockUseCase> _logger;

    public ReserveStockUseCase(ILogger<ReserveStockUseCase> logger)
    {
        _logger = logger;
    }

    public Reservation Reserve(ReserveStockCommand command)
    {
        using var activity = Source.StartActivity("Inventory.Reserve");

        var tier = BaggageHelpers.Get("customer.tier") ?? "unknown";
        var customerId = BaggageHelpers.Get("customer.id") ?? "unknown";

        using var ctx = new DomainContext(_logger,
            InventoryContextKey.ORDER_ID.Of(command.OrderId),
            InventoryContextKey.CUSTOMER_ID.Of(customerId));

        activity?.SetTag("order.id", command.OrderId);
        activity?.SetTag("customer.id", customerId);
        activity?.SetTag("customer.tier", tier);
        activity?.SetTag("reservation.line_count", command.Items.Count);

        var reservation = DecideOutcome(command);

        activity?.SetTag("reservation.id", reservation.Id.Value);
        activity?.SetTag("reservation.status", reservation.Status.ToString());

        _logger.LogInformation(
            "Reservation {Status}: {ReservationId} (lines={LineCount})",
            reservation.Status, reservation.Id.Value, reservation.Lines.Count);

        ReservationCounter.Add(1,
            new KeyValuePair<string, object?>("status", reservation.Status.ToString()),
            new KeyValuePair<string, object?>("tier", tier));

        return reservation;
    }

    // ------------------------------------------------------------------ //
    //  SKU-prefix stock simulation (matches Java & Python exactly)        //
    // ------------------------------------------------------------------ //

    private static Reservation DecideOutcome(ReserveStockCommand command)
    {
        var lines = new List<ReservationLine>();
        var anyOutOfStock = false;
        var anyPartial = false;

        foreach (var item in command.Items)
        {
            var productCode = SkuToProductCode(item.Sku);

            if (OutOfStockPrefixes.Any(p => item.Sku.StartsWith(p, StringComparison.Ordinal)))
            {
                anyOutOfStock = true;
                lines.Add(new ReservationLine(productCode, QuantityReserved: 0, Available: false));
            }
            else if (item.Sku.StartsWith(PartialPrefix, StringComparison.Ordinal))
            {
                anyPartial = true;
                lines.Add(new ReservationLine(productCode,
                    QuantityReserved: Math.Max(1, item.Quantity / 2), Available: true));
            }
            else
            {
                lines.Add(new ReservationLine(productCode,
                    QuantityReserved: item.Quantity, Available: true));
            }
        }

        if (anyOutOfStock)
            return Reservation.Unavailable(command.OrderId, lines,
                "one or more items not in stock");

        if (anyPartial)
            return Reservation.PartiallyReserved(command.OrderId, lines,
                "some quantities reduced");

        return Reservation.Reserved(command.OrderId, lines);
    }

    /// <summary>
    /// Translates an Order-side SKU into an Inventory-side ProductCode.
    /// "SKU-LAPTOP-PRO" becomes "PROD-LAPTOP-PRO".
    /// </summary>
    private static ProductCode SkuToProductCode(string sku)
    {
        if (sku.StartsWith("SKU-", StringComparison.Ordinal))
            return ProductCode.Of($"PROD-{sku[4..]}");
        return ProductCode.Of(sku);
    }
}
