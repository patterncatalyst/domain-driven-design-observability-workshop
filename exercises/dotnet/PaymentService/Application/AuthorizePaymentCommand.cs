namespace PaymentService.Application;

/// <summary>
/// Command to authorize a payment.
/// </summary>
public record AuthorizePaymentCommand(
    string OrderId,
    string CustomerId,
    decimal Amount,
    string Currency,
    string PaymentMethod);
