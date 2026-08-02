using SharedObservability;

namespace InventoryService.Domain;

/// <summary>
/// Domain identifiers for the Inventory bounded context.
/// Each enum value maps to a structured-logging / baggage key.
/// </summary>
public enum InventoryContextKey
{
    ORDER_ID,
    CUSTOMER_ID,
    RESERVATION_ID,
    PRODUCT_CODE
}

public static class InventoryContextKeyExtensions
{
    private static readonly Dictionary<InventoryContextKey, string> Keys = new()
    {
        [InventoryContextKey.ORDER_ID] = "order.id",
        [InventoryContextKey.CUSTOMER_ID] = "customer.id",
        [InventoryContextKey.RESERVATION_ID] = "reservation.id",
        [InventoryContextKey.PRODUCT_CODE] = "product.code",
    };

    /// <summary>Returns the dotted key name used in baggage and structured logs.</summary>
    public static string KeyName(this InventoryContextKey key) => Keys[key];

    /// <summary>Creates an <see cref="IDomainIdentifier"/> binding this key to a value.</summary>
    public static IDomainIdentifier Of(this InventoryContextKey key, string value) =>
        new SimpleIdentifier(Keys[key], value);

    private record SimpleIdentifier(string Key, string Value) : IDomainIdentifier;
}
