using System.Diagnostics;
using System.Net.Http.Json;
using System.Text.Json;
using OrderService.Domain;

namespace OrderService.Infrastructure;

public sealed class InventoryRestAdapter : IInventoryPort
{
    private static readonly ActivitySource ActivitySource = new("order-service");
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    private readonly HttpClient _httpClient;

    public InventoryRestAdapter(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<ReservationOutcome> Reserve(Order order)
    {
        using var activity = ActivitySource.StartActivity("Order.Acl.InventoryReserve");
        activity?.SetTag("acl.context", "inventory");
        activity?.SetTag("acl.transport", "rest");

        try
        {
            var wireRequest = new
            {
                orderId = order.Id.Value,
                items = order.LineItems.Select(li => new
                {
                    sku = li.Sku.Value,
                    quantity = li.Quantity
                }).ToArray()
            };

            var response = await _httpClient.PostAsJsonAsync(
                "/api/inventory/reserve", wireRequest, JsonOptions);

            response.EnsureSuccessStatusCode();

            var responseBody = await response.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(responseBody);
            var root = doc.RootElement;
            var status = root.GetProperty("status").GetString();

            return status switch
            {
                "RESERVED" => new ReservationOutcome.Reserved(
                    root.GetProperty("reservationId").GetString()
                        ?? throw new InvalidOperationException("Missing reservationId in RESERVED response")),
                "PARTIALLY_RESERVED" => new ReservationOutcome.Unavailable(
                    root.TryGetProperty("reason", out var pr) ? pr.GetString() ?? "Partially reserved" : "Partially reserved"),
                "UNAVAILABLE" => new ReservationOutcome.Unavailable(
                    root.TryGetProperty("reason", out var ur) ? ur.GetString() ?? "Unavailable" : "Unavailable"),
                _ => new ReservationOutcome.ReservationFailure($"Unknown inventory status: {status}")
            };
        }
        catch (HttpRequestException ex)
        {
            return new ReservationOutcome.ReservationFailure(
                $"Inventory service error: {ex.Message}");
        }
    }
}
