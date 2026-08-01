namespace SharedObservability;

/// <summary>
/// Represents a domain-level identifier that can be propagated across service
/// boundaries via OpenTelemetry baggage and Kafka headers.
/// </summary>
public interface IDomainIdentifier
{
    /// <summary>
    /// The baggage/header key (e.g., "order.id", "customer.id").
    /// </summary>
    string Key { get; }

    /// <summary>
    /// The identifier value.
    /// </summary>
    string Value { get; }
}
