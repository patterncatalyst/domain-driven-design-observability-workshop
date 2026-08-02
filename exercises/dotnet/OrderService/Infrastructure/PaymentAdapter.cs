using System.Diagnostics;
using System.Net.Http.Json;
using System.Text.Json;
using OrderService.Domain;

namespace OrderService.Infrastructure;

public sealed class PaymentRestAdapter : IPaymentPort
{
    private static readonly ActivitySource ActivitySource = new("order-service");
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    private readonly HttpClient _httpClient;

    public PaymentRestAdapter(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<AuthorizationOutcome> Authorize(Order order)
    {
        var total = order.Total();

        using var activity = ActivitySource.StartActivity("Order.Payment.Authorize");
        activity?.SetTag("payment.method", "credit_card");
        activity?.SetTag("order.value", (double)total.Amount);

        try
        {
            var wireRequest = new
            {
                orderId = order.Id.Value,
                customerId = order.CustomerId.Value,
                amount = total.Amount,
                currency = total.Currency,
                paymentMethod = "credit_card"
            };

            var response = await _httpClient.PostAsJsonAsync(
                "/api/payments/authorize", wireRequest, JsonOptions);

            response.EnsureSuccessStatusCode();

            var responseBody = await response.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(responseBody);
            var root = doc.RootElement;
            var outcome = root.GetProperty("outcome").GetString();

            return outcome switch
            {
                "AUTHORIZED" => new AuthorizationOutcome.Authorized(
                    root.GetProperty("authorizationId").GetString()
                        ?? throw new InvalidOperationException("Missing authorizationId in AUTHORIZED response")),
                "DECLINED" => new AuthorizationOutcome.Declined(
                    root.TryGetProperty("reason", out var dr) ? dr.GetString() ?? "Declined" : "Declined"),
                _ => new AuthorizationOutcome.AuthorizationFailure(
                    $"Unknown payment outcome: {outcome}")
            };
        }
        catch (HttpRequestException ex)
        {
            return new AuthorizationOutcome.AuthorizationFailure(
                $"Payment service error: {ex.Message}");
        }
    }
}
