using SharedObservability;

namespace OrderService.Domain;

/// <summary>
/// Domain identifier keys for the Order bounded context.
/// Each nested class represents a context key that can produce
/// an <see cref="IDomainIdentifier"/> via its <c>Of(string)</c> method.
/// </summary>
public static class OrderContextKey
{
    public static class OrderId
    {
        public const string Key = "order.id";
        public static IDomainIdentifier Of(string value) => new BoundIdentifier(Key, value);
    }

    public static class CustomerId
    {
        public const string Key = "customer.id";
        public static IDomainIdentifier Of(string value) => new BoundIdentifier(Key, value);
    }

    public static class CartId
    {
        public const string Key = "cart.id";
        public static IDomainIdentifier Of(string value) => new BoundIdentifier(Key, value);
    }

    public static class CustomerTier
    {
        public const string Key = "customer.tier";
        public static IDomainIdentifier Of(string value) => new BoundIdentifier(Key, value);
    }

    private sealed record BoundIdentifier(string Key, string Value) : IDomainIdentifier;
}
