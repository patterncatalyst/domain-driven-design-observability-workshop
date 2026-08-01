using SharedObservability;

var builder = WebApplication.CreateBuilder(args);

// Wire up OpenTelemetry tracing, metrics, and logging
builder.Services.AddOpenTelemetryWorkshop(builder.Configuration);

var app = builder.Build();

// Health endpoints — match Quarkus /q/health paths
app.MapGet("/q/health/ready", () => new { status = "UP" });
app.MapGet("/q/health/live", () => new { status = "UP" });

// TODO: POST /api/payments/authorize — authorize payment for an order
// TODO: POST /api/payments/refund    — refund payment (compensation)

app.Run();
