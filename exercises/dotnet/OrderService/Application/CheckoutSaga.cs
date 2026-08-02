using System.Diagnostics;
using System.Diagnostics.Metrics;
using Microsoft.Extensions.Logging;
using OrderService.Domain;
using SharedObservability;

namespace OrderService.Application;

// ── Wire DTOs ──────────────────────────────────────────────────────

public sealed record CheckoutCommand(
    string CustomerId,
    string CartId,
    List<LineItem> LineItems,
    string PaymentMethod,
    string ShippingClass);

public sealed record CheckoutResult(
    string OrderId,
    string Status,
    string? ReservationId,
    string? AuthorizationId,
    string? ShipmentId,
    string Message);

// ── Saga Orchestrator ──────────────────────────────────────────────

public sealed class CheckoutSaga
{
    private static readonly ActivitySource ActivitySource = new("order-service");
    private static readonly Meter Meter = new("order-service");
    private static readonly Counter<long> OutcomeCounter =
        Meter.CreateCounter<long>("checkout_outcomes_total");
    private static readonly Histogram<double> DurationHistogram =
        Meter.CreateHistogram<double>("checkout_duration_seconds", "s");

    private readonly IInventoryPort _inventory;
    private readonly IPaymentPort _payment;
    private readonly IShippingPort _shipping;
    private readonly IOrderEventPublisher _events;
    private readonly ICustomerProfileLookup _customerLookup;
    private readonly ILogger<CheckoutSaga> _logger;

    public CheckoutSaga(
        IInventoryPort inventory,
        IPaymentPort payment,
        IShippingPort shipping,
        IOrderEventPublisher events,
        ICustomerProfileLookup customerLookup,
        ILogger<CheckoutSaga> logger)
    {
        _inventory = inventory;
        _payment = payment;
        _shipping = shipping;
        _events = events;
        _customerLookup = customerLookup;
        _logger = logger;
    }

    public async Task<CheckoutResult> Checkout(CheckoutCommand command)
    {
        var orderId = Domain.OrderId.Generate();
        var customerId = Domain.CustomerId.Of(command.CustomerId);
        var cartId = Domain.CartId.Of(command.CartId);

        using var domainContext = new DomainContext(
            _logger,
            OrderContextKey.OrderId.Of(orderId.Value),
            OrderContextKey.CustomerId.Of(customerId.Value),
            OrderContextKey.CartId.Of(cartId.Value));

        var order = Order.Place(orderId, customerId, cartId, command.LineItems);
        var profile = _customerLookup.Lookup(customerId);
        var tierValue = profile.Tier.ToValue();

        using var activity = ActivitySource.StartActivity("Order.Checkout");
        activity?.SetTag("order.id", orderId.Value);
        activity?.SetTag("order.value", (double)order.Total().Amount);
        activity?.SetTag("order.line_items_count", order.TotalLineItemCount());
        activity?.SetTag("customer.id", customerId.Value);
        activity?.SetTag("customer.tier", tierValue);

        _logger.LogInformation("Checkout starting");

        BaggageHelpers.Set(OrderContextKey.CustomerTier.Key, tierValue);

        var startTime = Stopwatch.GetTimestamp();

        // Step 1: publish OrderPlaced
        PublishEvent(OrderPlaced.FromOrder(order));

        // Step 2: reserve inventory
        var reservationOutcome = await _inventory.Reserve(order);
        return await (reservationOutcome switch
        {
            ReservationOutcome.Reserved r =>
                ContinueAfterReservation(order, profile, r.ReservationId, startTime),
            ReservationOutcome.Unavailable u =>
                Task.FromResult(CancelAndRecord(order, profile, "inventory", u.Reason, startTime)),
            ReservationOutcome.ReservationFailure f =>
                Task.FromResult(CancelAndRecord(order, profile, "inventory", $"inventory failure: {f.Detail}", startTime)),
            _ => throw new InvalidOperationException("Unexpected reservation outcome")
        });
    }

    private async Task<CheckoutResult> ContinueAfterReservation(
        Order order, CustomerProfile profile, string reservationId, long startTime)
    {
        // Step 3: authorize payment
        var authOutcome = await _payment.Authorize(order);
        return await (authOutcome switch
        {
            AuthorizationOutcome.Authorized a =>
                ContinueAfterPayment(order, profile, reservationId, a.AuthorizationId, startTime),
            AuthorizationOutcome.Declined d =>
                Task.FromResult(CancelAndRecord(order, profile, "payment", d.Reason, startTime)),
            AuthorizationOutcome.AuthorizationFailure f =>
                Task.FromResult(CancelAndRecord(order, profile, "payment", $"payment failure: {f.Detail}", startTime)),
            _ => throw new InvalidOperationException("Unexpected authorization outcome")
        });
    }

    private async Task<CheckoutResult> ContinueAfterPayment(
        Order order, CustomerProfile profile, string reservationId,
        string authorizationId, long startTime)
    {
        // Step 4: schedule shipping
        var shipOutcome = await _shipping.Schedule(order);
        return shipOutcome switch
        {
            ShipmentOutcome.Scheduled s => ConfirmAndRecord(
                order, profile, reservationId, authorizationId, s.ShipmentId, startTime),
            ShipmentOutcome.ShipmentFailure f => CancelAndRecord(
                order, profile, "shipping", $"shipping failure: {f.Detail}", startTime),
            _ => throw new InvalidOperationException("Unexpected shipment outcome")
        };
    }

    private CheckoutResult ConfirmAndRecord(
        Order order, CustomerProfile profile, string reservationId,
        string authorizationId, string shipmentId, long startTime)
    {
        var confirmed = order.Confirm();
        PublishEvent(OrderConfirmed.FromOrder(confirmed, reservationId, authorizationId, shipmentId));
        RecordOutcome("success", profile.Tier.ToValue(), startTime);

        return new CheckoutResult(
            confirmed.Id.Value,
            "CONFIRMED",
            reservationId,
            authorizationId,
            shipmentId,
            "Order confirmed successfully");
    }

    private CheckoutResult CancelAndRecord(
        Order order, CustomerProfile profile, string failedAt, string reason, long startTime)
    {
        var cancelled = order.Cancel(reason);
        PublishEvent(OrderCancelled.FromOrder(cancelled, failedAt, reason));
        RecordOutcome($"cancelled_{failedAt}", profile.Tier.ToValue(), startTime);

        _logger.LogWarning("Checkout cancelled at {FailedAt}: {Reason}", failedAt, reason);

        return new CheckoutResult(
            cancelled.Id.Value,
            "CANCELLED",
            null,
            null,
            null,
            $"Order cancelled at {failedAt}: {reason}");
    }

    private void RecordOutcome(string outcome, string tier, long startTime)
    {
        var elapsed = Stopwatch.GetElapsedTime(startTime);
        OutcomeCounter.Add(1,
            new KeyValuePair<string, object?>("outcome", outcome),
            new KeyValuePair<string, object?>("tier", tier));
        DurationHistogram.Record(elapsed.TotalSeconds,
            new KeyValuePair<string, object?>("outcome", outcome),
            new KeyValuePair<string, object?>("tier", tier));
    }

    private void PublishEvent(DomainEvent ev)
    {
        try
        {
            _events.Publish(ev);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to publish {EventType} for order {OrderId}",
                ev.EventType, ev.OrderId);
        }
    }
}
