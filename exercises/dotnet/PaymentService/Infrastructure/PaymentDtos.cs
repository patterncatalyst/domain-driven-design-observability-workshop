using System.Text.Json.Serialization;

namespace PaymentService.Infrastructure;

/// <summary>Inbound DTO for payment authorization.</summary>
public record AuthorizeRequest(
    [property: JsonPropertyName("orderId")] string OrderId,
    [property: JsonPropertyName("customerId")] string CustomerId,
    [property: JsonPropertyName("amount")] decimal Amount,
    [property: JsonPropertyName("currency")] string Currency,
    [property: JsonPropertyName("paymentMethod")] string PaymentMethod);

/// <summary>Outbound DTO for payment authorization result.</summary>
public record AuthorizeResponse(
    [property: JsonPropertyName("authorizationId")] string AuthorizationId,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("reason")] string? Reason);
