namespace OrderService.Domain;

// ── Reservation Outcome ────────────────────────────────────────────

public abstract record ReservationOutcome
{
    public sealed record Reserved(string ReservationId) : ReservationOutcome;
    public sealed record Unavailable(string Reason) : ReservationOutcome;
    public sealed record ReservationFailure(string Detail) : ReservationOutcome;
}

// ── Authorization Outcome ──────────────────────────────────────────

public abstract record AuthorizationOutcome
{
    public sealed record Authorized(string AuthorizationId) : AuthorizationOutcome;
    public sealed record Declined(string Reason) : AuthorizationOutcome;
    public sealed record AuthorizationFailure(string Detail) : AuthorizationOutcome;
}

// ── Shipment Outcome ───────────────────────────────────────────────

public abstract record ShipmentOutcome
{
    public sealed record Scheduled(string ShipmentId) : ShipmentOutcome;
    public sealed record ShipmentFailure(string Detail) : ShipmentOutcome;
}

// ── Port Interfaces ────────────────────────────────────────────────

public interface IInventoryPort
{
    Task<ReservationOutcome> Reserve(Order order);
}

public interface IPaymentPort
{
    Task<AuthorizationOutcome> Authorize(Order order);
}

public interface IShippingPort
{
    Task<ShipmentOutcome> Schedule(Order order);
}

public interface IOrderEventPublisher
{
    void Publish(DomainEvent ev);
    void Flush(double timeoutSeconds);
}
