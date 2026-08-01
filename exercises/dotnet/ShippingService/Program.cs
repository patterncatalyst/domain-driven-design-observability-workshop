using SharedObservability;

var builder = WebApplication.CreateBuilder(args);

// Wire up OpenTelemetry tracing, metrics, and logging
builder.Services.AddOpenTelemetryWorkshop(builder.Configuration);

var app = builder.Build();

// Health endpoints — match Quarkus /q/health paths
app.MapGet("/q/health/ready", () => new { status = "UP" });
app.MapGet("/q/health/live", () => new { status = "UP" });

// TODO: POST /api/shipments/schedule — schedule a shipment for an order
// TODO: POST /api/shipments/cancel   — cancel shipment (compensation)

app.Run();
