using System.Diagnostics;
using OpenTelemetry;

namespace SharedObservability;

/// <summary>
/// Static helpers for reading and writing OpenTelemetry Baggage entries.
/// Baggage propagates domain identifiers (order ID, customer ID, etc.) across
/// service boundaries so that downstream spans and logs carry business context.
/// </summary>
public static class BaggageHelpers
{
    /// <summary>
    /// Retrieves a baggage value by key, returning null if not present.
    /// </summary>
    public static string? Get(string key)
    {
        return Baggage.GetBaggage(key);
    }

    /// <summary>
    /// Sets a baggage key-value pair on the current activity context.
    /// </summary>
    public static void Set(string key, string value)
    {
        Baggage.SetBaggage(key, value);
    }

    /// <summary>
    /// Removes a baggage entry by key.
    /// </summary>
    public static void Remove(string key)
    {
        Baggage.RemoveBaggage(key);
    }

    /// <summary>
    /// Returns all current baggage entries as key-value pairs.
    /// </summary>
    public static IReadOnlyDictionary<string, string> GetAll()
    {
        return Baggage.GetBaggage()
            .ToDictionary(kvp => kvp.Key, kvp => kvp.Value);
    }
}
