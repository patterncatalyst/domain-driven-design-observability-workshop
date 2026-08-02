namespace InventoryService.Application;

/// <summary>One line-item in a reservation request (Order vocabulary: SKU).</summary>
public record ReserveItem(string Sku, int Quantity);

/// <summary>Inbound command carrying Order-side SKUs to be reserved.</summary>
public record ReserveStockCommand(string OrderId, IReadOnlyList<ReserveItem> Items);
