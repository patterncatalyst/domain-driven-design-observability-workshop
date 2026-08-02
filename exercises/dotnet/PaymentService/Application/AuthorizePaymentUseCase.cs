using System.Diagnostics;
using System.Diagnostics.Metrics;
using PaymentService.Domain.Identifiers;
using PaymentService.Domain.Models;
using SharedObservability;

namespace PaymentService.Application;

/// <summary>
/// The authorize-payment use case. Decides whether to authorize, decline,
/// or fail a payment based on customer ID pattern matching (simulated
/// for the workshop -- no real payment gateway).
/// </summary>
public class AuthorizePaymentUseCase
{
    private static readonly ActivitySource Source = new("PaymentService");
    private static readonly Meter PaymentMeter = new("PaymentService");
    private static readonly Counter<long> AuthorizationsCounter =
        PaymentMeter.CreateCounter<long>("payment_authorizations_total",
            description: "Total payment authorization attempts by outcome and tier");

    private readonly ILogger<AuthorizePaymentUseCase> _logger;

    public AuthorizePaymentUseCase(ILogger<AuthorizePaymentUseCase> logger)
    {
        _logger = logger;
    }

    public Authorization Authorize(AuthorizePaymentCommand command)
    {
        using var activity = Source.StartActivity("Payment.Authorize");

        using var ctx = new DomainContext(_logger,
            PaymentContextKey.OrderId.Of(command.OrderId),
            PaymentContextKey.CustomerId.Of(command.CustomerId));

        var tier = BaggageHelpers.Get("customer.tier") ?? "unknown";

        activity?.SetTag("order.id", command.OrderId);
        activity?.SetTag("customer.id", command.CustomerId);
        activity?.SetTag("customer.tier", tier);
        activity?.SetTag("payment.method", command.PaymentMethod);
        activity?.SetTag("payment.amount", (double)command.Amount);
        activity?.SetTag("payment.currency", command.Currency);

        var authorization = DecideOutcome(command);

        // Enrich context with result
        BaggageHelpers.Set("authorization.id", authorization.Id.Value);
        activity?.SetTag("authorization.id", authorization.Id.Value);
        activity?.SetTag("authorization.outcome", authorization.Outcome.ToString());

        _logger.LogInformation("Authorization {Outcome}: {AuthorizationId} (amount={Amount} {Currency})",
            authorization.Outcome, authorization.Id, command.Amount, command.Currency);

        AuthorizationsCounter.Add(1,
            new KeyValuePair<string, object?>("outcome", authorization.Outcome.ToString()),
            new KeyValuePair<string, object?>("tier", tier));

        return authorization;
    }

    /// <summary>
    /// Simulate authorization outcome based on customer ID patterns.
    /// Customer IDs containing "fail" trigger FAILURE (gateway error).
    /// Customer IDs containing "decline" trigger DECLINED.
    /// Everything else is AUTHORIZED.
    /// "fail" is checked first so it takes precedence if both appear.
    /// </summary>
    private static Authorization DecideOutcome(AuthorizePaymentCommand command)
    {
        var cidLower = command.CustomerId.ToLowerInvariant();

        if (cidLower.Contains("fail"))
        {
            return Authorization.Failure(
                command.OrderId, command.Amount, command.Currency,
                $"Simulated failure for customer: {command.CustomerId}");
        }

        if (cidLower.Contains("decline"))
        {
            return Authorization.Declined(
                command.OrderId, command.Amount, command.Currency,
                $"Simulated decline for customer: {command.CustomerId}");
        }

        return Authorization.Authorized(command.OrderId, command.Amount, command.Currency);
    }
}
