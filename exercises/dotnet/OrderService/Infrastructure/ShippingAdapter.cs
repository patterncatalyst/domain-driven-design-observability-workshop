using System.Diagnostics;
using System.Net.Http.Json;
using System.Text.Json;
using OrderService.Domain;

namespace OrderService.Infrastructure;

public sealed class ShippingRestAdapter : IShippingPort
{
    private static readonly ActivitySource ActivitySource = new("order-service");
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    private readonly HttpClient _httpClient;

    public ShippingRestAdapter(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<ShipmentOutcome> Schedule(Order order)
    {
        using var activity = ActivitySource.StartActivity("Order.Shipping.Schedule");
        activity?.SetTag("shipping.class", "standard");
        activity?.SetTag("order.line_items_count", order.TotalLineItemCount());

        try
        {
            var wireRequest = new
            {
                orderId = order.Id.Value,
                customerId = order.CustomerId.Value,
                shippingClass = "standard"
            };

            var response = await _httpClient.PostAsJsonAsync(
                "/api/shipments/schedule", wireRequest, JsonOptions);

            response.EnsureSuccessStatusCode();

            var responseBody = await response.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(responseBody);
            var root = doc.RootElement;

            if (root.TryGetProperty("shipmentId", out var shipmentIdProp)
                && shipmentIdProp.GetString() is { } shipmentId)
            {
                return new ShipmentOutcome.Scheduled(shipmentId);
            }

            var detail = root.TryGetProperty("detail", out var detailProp)
                ? detailProp.GetString() ?? "Shipment scheduling failed"
                : "Shipment scheduling failed";

            return new ShipmentOutcome.ShipmentFailure(detail);
        }
        catch (HttpRequestException ex)
        {
            return new ShipmentOutcome.ShipmentFailure(
                $"Shipping service error: {ex.Message}");
        }
    }
}
