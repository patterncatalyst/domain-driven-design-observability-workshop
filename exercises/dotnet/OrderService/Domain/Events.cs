namespace OrderService.Domain;

// ── Base Event ─────────────────────────────────────────────────────

public abstract record DomainEvent(string OrderId)
{
    public string EventId { get; init; } = Guid.NewGuid().ToString();
    public string OccurredAt { get; init; } = DateTime.UtcNow.ToString("o");

    public string EventType => GetType().Name;

    public abstract Dictionary<string, object?> ToDict();
}

// ── OrderPlaced ────────────────────────────────────────────────────

public sealed record OrderPlaced(
    string OrderId,
    string CustomerId,
    string CartId,
    double TotalAmount,
    string TotalCurrency,
    int LineItemCount) : DomainEvent(OrderId)
{
    public static OrderPlaced FromOrder(Order order)
    {
        var total = order.Total();
        return new OrderPlaced(
            order.Id.Value,
            order.CustomerId.Value,
            order.CartId.Value,
            (double)total.Amount,
            total.Currency,
            order.TotalLineItemCount());
    }

    public override Dictionary<string, object?> ToDict() => new()
    {
        ["eventType"] = EventType,
        ["eventId"] = EventId,
        ["orderId"] = OrderId,
        ["occurredAt"] = OccurredAt,
        ["customerId"] = CustomerId,
        ["cartId"] = CartId,
        ["total"] = new Dictionary<string, object?>
        {
            ["amount"] = TotalAmount,
            ["currency"] = TotalCurrency
        },
        ["lineItemCount"] = LineItemCount
    };
}

// ── OrderConfirmed ─────────────────────────────────────────────────

public sealed record OrderConfirmed(
    string OrderId,
    string CustomerId,
    double TotalAmount,
    string TotalCurrency,
    string ReservationId,
    string AuthorizationId,
    string ShipmentId) : DomainEvent(OrderId)
{
    public static OrderConfirmed FromOrder(
        Order order,
        string reservationId,
        string authorizationId,
        string shipmentId)
    {
        var total = order.Total();
        return new OrderConfirmed(
            order.Id.Value,
            order.CustomerId.Value,
            (double)total.Amount,
            total.Currency,
            reservationId,
            authorizationId,
            shipmentId);
    }

    public override Dictionary<string, object?> ToDict() => new()
    {
        ["eventType"] = EventType,
        ["eventId"] = EventId,
        ["orderId"] = OrderId,
        ["occurredAt"] = OccurredAt,
        ["customerId"] = CustomerId,
        ["total"] = new Dictionary<string, object?>
        {
            ["amount"] = TotalAmount,
            ["currency"] = TotalCurrency
        },
        ["reservationId"] = ReservationId,
        ["authorizationId"] = AuthorizationId,
        ["shipmentId"] = ShipmentId
    };
}

// ── OrderCancelled ─────────────────────────────────────────────────

public sealed record OrderCancelled(
    string OrderId,
    string CustomerId,
    string FailedAt,
    string Reason) : DomainEvent(OrderId)
{
    public static OrderCancelled FromOrder(
        Order order,
        string failedAt,
        string reason) =>
        new(order.Id.Value, order.CustomerId.Value, failedAt, reason);

    public override Dictionary<string, object?> ToDict() => new()
    {
        ["eventType"] = EventType,
        ["eventId"] = EventId,
        ["orderId"] = OrderId,
        ["occurredAt"] = OccurredAt,
        ["customerId"] = CustomerId,
        ["failedAt"] = FailedAt,
        ["reason"] = Reason
    };
}
