using OpenTelemetry;
using OpenTelemetry.Metrics;
using OpenTelemetry.Trace;
using PaymentService.Application;
using PaymentService.Infrastructure;
using SharedObservability;

var builder = WebApplication.CreateBuilder(args);

// Wire up OpenTelemetry tracing, metrics, and logging
builder.Services.AddOpenTelemetryWorkshop(builder.Configuration);

// Register custom ActivitySource and Meter with the OTel providers
builder.Services.AddOpenTelemetry()
    .WithTracing(tracing => tracing.AddSource("PaymentService"))
    .WithMetrics(metrics => metrics.AddMeter("PaymentService"));

// Application services
builder.Services.AddSingleton<AuthorizePaymentUseCase>();

var app = builder.Build();

// Health endpoints — match Quarkus /q/health paths
app.MapGet("/q/health/ready", () => new { status = "UP" });
app.MapGet("/q/health/live", () => new { status = "UP" });

// POST /api/payments/authorize — authorize payment for an order
app.MapPost("/api/payments/authorize", (AuthorizeRequest request, AuthorizePaymentUseCase useCase) =>
{
    var command = new AuthorizePaymentCommand(
        request.OrderId,
        request.CustomerId,
        request.Amount,
        request.Currency,
        request.PaymentMethod);

    var authorization = useCase.Authorize(command);

    return Results.Ok(new AuthorizeResponse(
        authorization.Id.Value,
        authorization.Outcome.ToString(),
        authorization.Reason));
});

app.Run();
