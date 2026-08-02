using System.Text.Json;
using OpenTelemetry.Metrics;
using OpenTelemetry.Trace;
using SharedObservability;
using OrderService.Application;
using OrderService.Domain;
using OrderService.Infrastructure;

var builder = WebApplication.CreateBuilder(args);

// Wire up OpenTelemetry tracing, metrics, and logging
builder.Services.AddOpenTelemetryWorkshop(builder.Configuration);

// Register the custom ActivitySource and Meter with OTel
builder.Services.ConfigureOpenTelemetryTracerProvider(t => t.AddSource("order-service"));
builder.Services.ConfigureOpenTelemetryMeterProvider(m => m.AddMeter("order-service"));

// DI registrations
builder.Services.AddSingleton<ICustomerProfileLookup, InMemoryCustomerProfileLookup>();
builder.Services.AddSingleton<IOrderEventPublisher>(sp =>
{
    var config = sp.GetRequiredService<IConfiguration>();
    var logger = sp.GetRequiredService<ILogger<OrderEventKafkaPublisher>>();
    return new OrderEventKafkaPublisher(config, logger);
});

// Named HttpClients for service adapters
var inventoryUrl = Environment.GetEnvironmentVariable("INVENTORY_SERVICE_URL")
    ?? "http://inventory-service:8081";
var paymentUrl = Environment.GetEnvironmentVariable("PAYMENT_SERVICE_URL")
    ?? "http://payment-service:8082";
var shippingUrl = Environment.GetEnvironmentVariable("SHIPPING_SERVICE_URL")
    ?? "http://shipping-service:8083";

builder.Services.AddHttpClient("InventoryService", c => c.BaseAddress = new Uri(inventoryUrl));
builder.Services.AddHttpClient("PaymentService", c => c.BaseAddress = new Uri(paymentUrl));
builder.Services.AddHttpClient("ShippingService", c => c.BaseAddress = new Uri(shippingUrl));

builder.Services.AddSingleton<IInventoryPort>(sp =>
{
    var factory = sp.GetRequiredService<IHttpClientFactory>();
    return new InventoryRestAdapter(factory.CreateClient("InventoryService"));
});
builder.Services.AddSingleton<IPaymentPort>(sp =>
{
    var factory = sp.GetRequiredService<IHttpClientFactory>();
    return new PaymentRestAdapter(factory.CreateClient("PaymentService"));
});
builder.Services.AddSingleton<IShippingPort>(sp =>
{
    var factory = sp.GetRequiredService<IHttpClientFactory>();
    return new ShippingRestAdapter(factory.CreateClient("ShippingService"));
});

builder.Services.AddSingleton<CheckoutSaga>();

var app = builder.Build();

var jsonOptions = new JsonSerializerOptions { PropertyNamingPolicy = JsonNamingPolicy.CamelCase };

// Health endpoints — match Quarkus /q/health paths
app.MapGet("/q/health/ready", () => new { status = "UP" });
app.MapGet("/q/health/live", () => new { status = "UP" });

// POST /api/orders/checkout
app.MapPost("/api/orders/checkout", async (HttpContext httpContext, CheckoutSaga saga) =>
{
    var request = await httpContext.Request.ReadFromJsonAsync<CheckoutRequest>(jsonOptions);
    if (request is null)
        return Results.BadRequest(new { error = "Invalid request body" });

    var lineItems = request.LineItems.Select(li =>
        new LineItem(Sku.Of(li.Sku), li.Quantity, Money.Usd(li.UnitPrice))).ToList();

    var command = new CheckoutCommand(
        request.CustomerId,
        request.CartId,
        lineItems,
        request.PaymentMethod,
        request.ShippingClass);

    var result = await saga.Checkout(command);

    var response = new CheckoutResponse(
        result.OrderId,
        result.Status,
        result.ReservationId,
        result.AuthorizationId,
        result.ShipmentId,
        result.Message);

    return result.Status == "CONFIRMED"
        ? Results.Json(response, jsonOptions, statusCode: 201)
        : Results.Json(response, jsonOptions, statusCode: 422);
});

// Shutdown hook to flush Kafka
app.Lifetime.ApplicationStopping.Register(() =>
{
    var publisher = app.Services.GetRequiredService<IOrderEventPublisher>();
    publisher.Flush(5.0);
});

app.Run();

// ── HTTP Wire DTOs ─────────────────────────────────────────────────

public sealed record CheckoutRequest(
    string CartId,
    string CustomerId,
    List<CheckoutLineItemDto> LineItems,
    string PaymentMethod,
    string ShippingClass);

public sealed record CheckoutLineItemDto(
    string Sku,
    int Quantity,
    decimal UnitPrice);

public sealed record CheckoutResponse(
    string OrderId,
    string Status,
    string? ReservationId,
    string? AuthorizationId,
    string? ShipmentId,
    string Message);
