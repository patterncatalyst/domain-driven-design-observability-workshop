using SharedObservability;

namespace PaymentService.Domain.Identifiers;

/// <summary>
/// Payment's typed identifier vocabulary.
/// <see cref="AuthorizationId"/> is purely Payment's; the others use wire
/// strings agreed with Order (order.id, customer.id).
/// </summary>
public sealed class PaymentContextKey
{
    public static readonly PaymentContextKey OrderId = new("order.id");
    public static readonly PaymentContextKey CustomerId = new("customer.id");
    public static readonly PaymentContextKey AuthorizationId = new("authorization.id");

    public string Key { get; }

    private PaymentContextKey(string key) => Key = key;

    /// <summary>
    /// Bind this key to a concrete value, returning an <see cref="IDomainIdentifier"/>.
    /// </summary>
    public IDomainIdentifier Of(string value) => new BoundIdentifier(Key, value);

    private sealed record BoundIdentifier(string Key, string Value) : IDomainIdentifier;
}
