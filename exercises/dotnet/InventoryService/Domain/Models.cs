namespace InventoryService.Domain;

/// <summary>Unique identifier for a stock reservation.</summary>
public record ReservationId(string Value)
{
    private const string Prefix = "res_";

    public static ReservationId Generate() => new(Prefix + Guid.NewGuid());

    public static ReservationId Of(string value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(value);
        return new ReservationId(value);
    }

    public override string ToString() => Value;
}

/// <summary>Inventory-side product identifier (translated from Order-side SKU).</summary>
public record ProductCode(string Value)
{
    public static ProductCode Of(string value)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(value);
        return new ProductCode(value);
    }

    public override string ToString() => Value;
}

/// <summary>Outcome of a reservation attempt.</summary>
public enum ReservationStatus
{
    RESERVED,
    PARTIALLY_RESERVED,
    UNAVAILABLE
}

/// <summary>One line in a reservation — maps a product to reserved quantity.</summary>
public record ReservationLine(
    ProductCode ProductCode,
    int QuantityReserved,
    bool Available);

/// <summary>Aggregate root: the result of a stock-reservation attempt.</summary>
public record Reservation(
    ReservationId Id,
    string OrderId,
    ReservationStatus Status,
    string? Reason,
    IReadOnlyList<ReservationLine> Lines)
{
    public static Reservation Reserved(string orderId, List<ReservationLine> lines) =>
        new(ReservationId.Generate(), orderId, ReservationStatus.RESERVED,
            null, lines.AsReadOnly());

    public static Reservation PartiallyReserved(
        string orderId, List<ReservationLine> lines, string reason)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(reason);
        return new(ReservationId.Generate(), orderId, ReservationStatus.PARTIALLY_RESERVED,
            reason, lines.AsReadOnly());
    }

    public static Reservation Unavailable(
        string orderId, List<ReservationLine> lines, string reason)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(reason);
        return new(ReservationId.Generate(), orderId, ReservationStatus.UNAVAILABLE,
            reason, lines.AsReadOnly());
    }
}
