using Microsoft.Extensions.Logging;

namespace SharedObservability;

/// <summary>
/// Creates a logging scope from one or more <see cref="IDomainIdentifier"/>
/// instances and sets the corresponding OTel baggage entries. Disposing the
/// context removes the baggage entries and ends the logging scope.
/// </summary>
public sealed class DomainContext : IDisposable
{
    private readonly IDisposable? _loggingScope;
    private readonly IDomainIdentifier[] _identifiers;

    public DomainContext(ILogger logger, params IDomainIdentifier[] identifiers)
    {
        _identifiers = identifiers;

        // Build a dictionary for the structured logging scope
        var scopeState = new Dictionary<string, object>();
        foreach (var id in identifiers)
        {
            scopeState[id.Key] = id.Value;
            BaggageHelpers.Set(id.Key, id.Value);
        }

        _loggingScope = logger.BeginScope(scopeState);
    }

    public void Dispose()
    {
        foreach (var id in _identifiers)
        {
            BaggageHelpers.Remove(id.Key);
        }

        _loggingScope?.Dispose();
    }
}
