using System.Diagnostics;
using System.Diagnostics.Metrics;
using NotificationService.Domain;
using SharedObservability;

namespace NotificationService.Application;

/// <summary>
/// Determines the notification kind from an inbound order event, creates
/// a <see cref="Notification"/>, records OTel attributes, and increments
/// the notifications-sent counter.
/// </summary>
public sealed class SendNotificationUseCase
{
    internal static readonly ActivitySource Source = new("notification-service");
    private static readonly Meter ServiceMeter = new("notification-service");
    private static readonly Counter<long> NotificationCounter =
        ServiceMeter.CreateCounter<long>(
            "notifications_sent_total",
            description: "Total notifications sent by kind and customer tier");

    private readonly ILogger<SendNotificationUseCase> _logger;

    public SendNotificationUseCase(ILogger<SendNotificationUseCase> logger)
    {
        _logger = logger;
    }

    public Notification Send(InboundOrderEvent orderEvent, string customerTier)
    {
        using var activity = Source.StartActivity("Notification.Send");

        var kind = orderEvent switch
        {
            InboundOrderPlaced => NotificationKind.PlacedAck,
            InboundOrderConfirmed => NotificationKind.Confirmation,
            InboundOrderCancelled => NotificationKind.Cancellation,
            _ => throw new InvalidOperationException(
                $"Unknown event type: {orderEvent.GetType().Name}"),
        };

        var customerId = orderEvent switch
        {
            InboundOrderPlaced e => e.CustomerId,
            InboundOrderConfirmed e => e.CustomerId,
            InboundOrderCancelled e => e.CustomerId,
            _ => "unknown",
        };

        var notification = Notification.Send(
            kind,
            orderEvent.OrderId,
            customerId,
            customerTier,
            channel: "email");

        using var ctx = new DomainContext(_logger,
            NotificationContextKey.NotificationId.Of(notification.Id.Value));

        activity?.SetTag("notification.id", notification.Id.Value);
        activity?.SetTag("notification.kind", kind.ToLabel());
        activity?.SetTag("notification.channel", notification.Channel);
        activity?.SetTag("customer.tier", customerTier);
        activity?.SetTag("order.id", orderEvent.OrderId);
        activity?.SetTag("customer.id", customerId);

        // Event-specific span attributes
        if (orderEvent is InboundOrderConfirmed confirmed)
        {
            activity?.SetTag("reservation.id", confirmed.ReservationId);
            activity?.SetTag("authorization.id", confirmed.AuthorizationId);
            activity?.SetTag("shipment.id", confirmed.ShipmentId);
        }
        else if (orderEvent is InboundOrderCancelled cancelled)
        {
            activity?.SetTag("order.failed_at", cancelled.FailedAt);
        }

        _logger.LogInformation(
            "Sent notification {NotificationId} ({Kind}) for order {OrderId}",
            notification.Id.Value, kind.ToLabel(), orderEvent.OrderId);

        NotificationCounter.Add(1,
            new KeyValuePair<string, object?>("kind", kind.ToLabel()),
            new KeyValuePair<string, object?>("tier", customerTier));

        return notification;
    }
}
