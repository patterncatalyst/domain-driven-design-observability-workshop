using System.Text;

namespace SharedObservability;

/// <summary>
/// Propagates <see cref="IDomainIdentifier"/> instances to and from Kafka
/// message headers, enabling domain context to flow through event-driven
/// communication without coupling to OTel wire format.
/// </summary>
public static class KafkaHeaderPropagator
{
    private const string HeaderPrefix = "domain.";

    /// <summary>
    /// Writes domain identifiers into Kafka headers. Each identifier is stored
    /// as a UTF-8 header with key "domain.{identifier.Key}".
    /// </summary>
    /// <param name="headers">The Kafka headers collection (list of key-value byte pairs).</param>
    /// <param name="identifiers">Domain identifiers to propagate.</param>
    public static void Inject(IList<KeyValuePair<string, byte[]>> headers, params IDomainIdentifier[] identifiers)
    {
        foreach (var id in identifiers)
        {
            var headerKey = HeaderPrefix + id.Key;
            headers.Add(new KeyValuePair<string, byte[]>(headerKey, Encoding.UTF8.GetBytes(id.Value)));
        }
    }

    /// <summary>
    /// Reads domain identifier values from Kafka headers. Returns a dictionary
    /// of identifier keys (without the "domain." prefix) to their values.
    /// </summary>
    /// <param name="headers">The Kafka headers collection.</param>
    /// <returns>Dictionary of domain identifier key-value pairs found in headers.</returns>
    public static IReadOnlyDictionary<string, string> Extract(IEnumerable<KeyValuePair<string, byte[]>> headers)
    {
        var result = new Dictionary<string, string>();

        foreach (var header in headers)
        {
            if (header.Key.StartsWith(HeaderPrefix, StringComparison.Ordinal))
            {
                var domainKey = header.Key[HeaderPrefix.Length..];
                result[domainKey] = Encoding.UTF8.GetString(header.Value);
            }
        }

        return result;
    }
}
