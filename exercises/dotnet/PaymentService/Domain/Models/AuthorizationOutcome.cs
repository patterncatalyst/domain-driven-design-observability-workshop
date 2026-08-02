namespace PaymentService.Domain.Models;

/// <summary>
/// Possible outcomes of a payment authorization attempt.
/// Wire names are intentionally aligned with Order service's vocabulary.
/// </summary>
public enum AuthorizationOutcome
{
    AUTHORIZED,
    DECLINED,
    FAILURE
}
