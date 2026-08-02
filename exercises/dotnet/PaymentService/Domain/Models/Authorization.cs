namespace PaymentService.Domain.Models;

/// <summary>
/// The Authorization aggregate root. Created in-memory when Payment processes
/// a request. Carries the foreign orderId for correlation, Payment's own
/// <see cref="AuthorizationId"/>, the resulting <see cref="AuthorizationOutcome"/>,
/// amount + currency, and an optional reason on non-AUTHORIZED outcomes.
/// </summary>
public record Authorization(
    AuthorizationId Id,
    string OrderId,
    decimal Amount,
    string Currency,
    AuthorizationOutcome Outcome,
    string? Reason)
{
    public static Authorization Authorized(string orderId, decimal amount, string currency)
        => new(AuthorizationId.Generate(), orderId, amount, currency,
            AuthorizationOutcome.AUTHORIZED, null);

    public static Authorization Declined(string orderId, decimal amount, string currency, string reason)
        => new(AuthorizationId.Generate(), orderId, amount, currency,
            AuthorizationOutcome.DECLINED, reason);

    public static Authorization Failure(string orderId, decimal amount, string currency, string reason)
        => new(AuthorizationId.Generate(), orderId, amount, currency,
            AuthorizationOutcome.FAILURE, reason);
}
