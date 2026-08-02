using OpenTelemetry.Metrics;
using OpenTelemetry.Trace;
using SharedObservability;
using NotificationService.Application;
using NotificationService.Infrastructure;

var builder = WebApplication.CreateBuilder(args);

// Wire up OpenTelemetry tracing, metrics, and logging
builder.Services.AddOpenTelemetryWorkshop(builder.Configuration);

// Register the notification-service ActivitySource and Meter with OTel
builder.Services.ConfigureOpenTelemetryTracerProvider(t => t.AddSource("notification-service"));
builder.Services.ConfigureOpenTelemetryMeterProvider(m => m.AddMeter("notification-service"));

// Application services
builder.Services.AddSingleton<SendNotificationUseCase>();

// Background Kafka consumer -- listens to order-events topic
builder.Services.AddHostedService<OrderEventConsumer>();

var app = builder.Build();

// Health endpoints -- match Quarkus /q/health paths
app.MapGet("/q/health/ready", () => new { status = "UP" });
app.MapGet("/q/health/live", () => new { status = "UP" });

app.Run();
