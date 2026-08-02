namespace PaymentService.Domain.Models;

/// <summary>
/// Value object wrapping a payment authorization identifier.
/// </summary>
public record AuthorizationId(string Value)
{
    private const string Prefix = "auth_";

    public static AuthorizationId Generate() => new($"{Prefix}{Guid.NewGuid()}");

    public override string ToString() => Value;
}
