namespace ShippingService.Domain.Models;

/// <summary>
/// Value object wrapping a shipment identifier.
/// </summary>
public record ShipmentId(string Value)
{
    private const string Prefix = "ship_";

    public static ShipmentId Generate() => new($"{Prefix}{Guid.NewGuid()}");

    public override string ToString() => Value;
}
