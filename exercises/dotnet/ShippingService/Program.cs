using OpenTelemetry;
using OpenTelemetry.Metrics;
using OpenTelemetry.Trace;
using ShippingService.Application;
using ShippingService.Infrastructure;
using SharedObservability;

var builder = WebApplication.CreateBuilder(args);

// Wire up OpenTelemetry tracing, metrics, and logging
builder.Services.AddOpenTelemetryWorkshop(builder.Configuration);

// Register custom ActivitySource and Meter with the OTel providers
builder.Services.AddOpenTelemetry()
    .WithTracing(tracing => tracing.AddSource("ShippingService"))
    .WithMetrics(metrics => metrics.AddMeter("ShippingService"));

// Application services
builder.Services.AddSingleton<ScheduleShipmentUseCase>();

var app = builder.Build();

// Health endpoints — match Quarkus /q/health paths
app.MapGet("/q/health/ready", () => new { status = "UP" });
app.MapGet("/q/health/live", () => new { status = "UP" });

// POST /api/shipments/schedule — schedule a shipment for an order
app.MapPost("/api/shipments/schedule", (ScheduleRequest request, ScheduleShipmentUseCase useCase) =>
{
    var command = new ScheduleShipmentCommand(
        request.OrderId,
        request.CustomerId,
        request.ShippingClass);

    var shipment = useCase.Schedule(command);

    return Results.Ok(new ScheduleResponse(
        shipment.Id.Value,
        "SCHEDULED",
        shipment.EstimatedDays));
});

app.Run();
