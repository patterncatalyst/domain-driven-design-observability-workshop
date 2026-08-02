using InventoryService.Application;
using OpenTelemetry.Metrics;
using OpenTelemetry.Trace;
using SharedObservability;

var builder = WebApplication.CreateBuilder(args);

// Wire up OpenTelemetry tracing, metrics, and logging
builder.Services.AddOpenTelemetryWorkshop(builder.Configuration);

// Register the custom ActivitySource and Meter so OTel exports them
builder.Services.AddOpenTelemetry()
    .WithTracing(tracing => tracing.AddSource("inventory-service"))
    .WithMetrics(metrics => metrics.AddMeter("inventory-service"));

// Application services
builder.Services.AddSingleton<ReserveStockUseCase>();

var app = builder.Build();

// Health endpoints — match Quarkus /q/health paths
app.MapGet("/q/health/ready", () => new { status = "UP" });
app.MapGet("/q/health/live", () => new { status = "UP" });

// POST /api/inventory/reserve — reserve stock for an order
app.MapPost("/api/inventory/reserve",
    (ReserveRequestDto request, ReserveStockUseCase useCase) =>
    {
        var command = new ReserveStockCommand(
            request.OrderId,
            request.Items
                .Select(i => new ReserveItem(i.Sku, i.Quantity))
                .ToList()
                .AsReadOnly());

        var reservation = useCase.Reserve(command);

        return new ReserveResponseDto(
            reservation.Id.Value,
            reservation.Status.ToString(),
            reservation.Reason,
            reservation.Lines.Select(l => new ReserveResponseLineDto(
                l.ProductCode.Value,
                l.QuantityReserved,
                l.Available)).ToList());
    });

app.Run();

// ------------------------------------------------------------------ //
//  Request / Response DTOs  (camelCase via JsonSerializerDefaults.Web) //
// ------------------------------------------------------------------ //

record ReserveRequestItemDto(string Sku, int Quantity);

record ReserveRequestDto(string OrderId, List<ReserveRequestItemDto> Items);

record ReserveResponseLineDto(string ProductCode, int QuantityReserved, bool Available);

record ReserveResponseDto(
    string ReservationId,
    string Status,
    string? Reason,
    List<ReserveResponseLineDto> Lines);
