namespace OrderService.Domain;

// ── Enums ──────────────────────────────────────────────────────────

public enum OrderStatus { Placed, Confirmed, Cancelled }

public static class OrderStatusExtensions
{
    public static string ToValue(this OrderStatus status) => status switch
    {
        OrderStatus.Placed => "PLACED",
        OrderStatus.Confirmed => "CONFIRMED",
        OrderStatus.Cancelled => "CANCELLED",
        _ => throw new ArgumentOutOfRangeException(nameof(status))
    };
}

public enum CustomerTier { Bronze, Silver, Gold, Platinum }

public static class CustomerTierExtensions
{
    public static string ToValue(this CustomerTier tier) => tier switch
    {
        CustomerTier.Bronze => "BRONZE",
        CustomerTier.Silver => "SILVER",
        CustomerTier.Gold => "GOLD",
        CustomerTier.Platinum => "PLATINUM",
        _ => throw new ArgumentOutOfRangeException(nameof(tier))
    };
}

// ── Value Objects ──────────────────────────────────────────────────

public sealed record OrderId(string Value)
{
    private const string Prefix = "ord_";

    public static OrderId Generate() =>
        new($"{Prefix}{Guid.NewGuid()}");

    public static OrderId Of(string value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(value);
        if (!value.StartsWith(Prefix, StringComparison.Ordinal))
            throw new ArgumentException($"OrderId must start with '{Prefix}', got: {value}");
        return new OrderId(value);
    }

    public override string ToString() => Value;
}

public sealed record CustomerId(string Value)
{
    private const string Prefix = "cust_";

    public static CustomerId Of(string value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(value);
        if (!value.StartsWith(Prefix, StringComparison.Ordinal))
            throw new ArgumentException($"CustomerId must start with '{Prefix}', got: {value}");
        return new CustomerId(value);
    }

    public override string ToString() => Value;
}

public sealed record CartId(string Value)
{
    private const string Prefix = "cart_";

    public static CartId Of(string value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(value);
        if (!value.StartsWith(Prefix, StringComparison.Ordinal))
            throw new ArgumentException($"CartId must start with '{Prefix}', got: {value}");
        return new CartId(value);
    }

    public override string ToString() => Value;
}

public sealed record Sku(string Value)
{
    public static Sku Of(string value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(value);
        return new Sku(value);
    }

    public override string ToString() => Value;
}

public sealed record Money(decimal Amount, string Currency)
{
    public static Money Usd(decimal amount) => new(amount, "USD");

    public static Money Zero(string currency) => new(0m, currency);

    public Money Add(Money other)
    {
        if (Currency != other.Currency)
            throw new InvalidOperationException(
                $"Cannot add {Currency} and {other.Currency}");
        return new Money(Amount + other.Amount, Currency);
    }

    public Money Multiply(int quantity) => new(Amount * quantity, Currency);
}

// ── Line Item ──────────────────────────────────────────────────────

public sealed record LineItem(Sku Sku, int Quantity, Money UnitPrice)
{
    public Money LineTotal() => UnitPrice.Multiply(Quantity);
}

// ── Aggregate ──────────────────────────────────────────────────────

public sealed record Order(
    OrderId Id,
    CustomerId CustomerId,
    CartId CartId,
    IReadOnlyList<LineItem> LineItems,
    OrderStatus Status,
    DateTime PlacedAt,
    string? CancelReason = null)
{
    public static Order Place(
        OrderId orderId,
        CustomerId customerId,
        CartId cartId,
        IReadOnlyList<LineItem> lineItems) =>
        new(orderId, customerId, cartId, lineItems,
            OrderStatus.Placed, DateTime.UtcNow);

    public Money Total()
    {
        if (LineItems.Count == 0)
            return Money.Usd(0m);

        var currency = LineItems[0].UnitPrice.Currency;
        var sum = Money.Zero(currency);
        foreach (var item in LineItems)
            sum = sum.Add(item.LineTotal());
        return sum;
    }

    public int TotalLineItemCount() =>
        LineItems.Sum(li => li.Quantity);

    public Order Confirm()
    {
        if (Status != OrderStatus.Placed)
            throw new IllegalStateException(
                $"Cannot confirm order in {Status.ToValue()} status");
        return this with { Status = OrderStatus.Confirmed };
    }

    public Order Cancel(string reason)
    {
        if (Status != OrderStatus.Placed)
            throw new IllegalStateException(
                $"Cannot cancel order in {Status.ToValue()} status");
        return this with { Status = OrderStatus.Cancelled, CancelReason = reason };
    }
}

// ── Exception ──────────────────────────────────────────────────────

public class IllegalStateException : Exception
{
    public IllegalStateException(string message) : base(message) { }
    public IllegalStateException(string message, Exception inner) : base(message, inner) { }
}
