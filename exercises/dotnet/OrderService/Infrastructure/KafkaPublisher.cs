using System.Diagnostics;
using System.Text;
using System.Text.Json;
using Confluent.Kafka;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using OpenTelemetry;
using OpenTelemetry.Context.Propagation;
using OrderService.Domain;
using SharedObservability;

namespace OrderService.Infrastructure;

public sealed class OrderEventKafkaPublisher : IOrderEventPublisher, IDisposable
{
    private static readonly ActivitySource ActivitySource = new("order-service");
    private const string Topic = "order-events";

    private readonly IProducer<string, string> _producer;
    private readonly ILogger<OrderEventKafkaPublisher> _logger;

    public OrderEventKafkaPublisher(IConfiguration configuration, ILogger<OrderEventKafkaPublisher> logger)
    {
        _logger = logger;

        var bootstrapServers =
            Environment.GetEnvironmentVariable("KAFKA_BOOTSTRAP_SERVERS")
            ?? configuration["Kafka:BootstrapServers"]
            ?? "localhost:9092";

        var config = new ProducerConfig
        {
            BootstrapServers = bootstrapServers,
            Acks = Acks.All
        };

        _producer = new ProducerBuilder<string, string>(config).Build();
    }

    public void Publish(DomainEvent ev)
    {
        using var activity = ActivitySource.StartActivity("Order.Events.Publish");
        activity?.SetTag("event.type", ev.EventType);
        activity?.SetTag("order.id", ev.OrderId);

        // Build domain identifier headers
        var headerList = new List<KeyValuePair<string, byte[]>>();
        var domainIdentifiers = BuildDomainIdentifiers(ev);
        KafkaHeaderPropagator.Inject(headerList, domainIdentifiers);

        // Inject OTel trace context (traceparent, baggage) into headers
        var otelHeaders = new Dictionary<string, string>();
        Propagators.DefaultTextMapPropagator.Inject(
            new PropagationContext(
                Activity.Current?.Context ?? default,
                Baggage.Current),
            otelHeaders,
            static (carrier, key, value) => carrier[key] = value);

        foreach (var (key, value) in otelHeaders)
        {
            headerList.Add(new KeyValuePair<string, byte[]>(
                key, Encoding.UTF8.GetBytes(value)));
        }

        // Convert to Confluent.Kafka Headers
        var kafkaHeaders = new Headers();
        foreach (var (key, value) in headerList)
        {
            kafkaHeaders.Add(key, value);
        }

        // Serialize event
        var json = JsonSerializer.Serialize(ev.ToDict(), new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        });

        var message = new Message<string, string>
        {
            Key = ev.OrderId,
            Value = json,
            Headers = kafkaHeaders
        };

        _producer.Produce(Topic, message, report =>
        {
            if (report.Error.IsError)
            {
                _logger.LogError("Kafka produce error for {EventType}: {Error}",
                    ev.EventType, report.Error.Reason);
            }
        });
    }

    public void Flush(double timeoutSeconds)
    {
        _producer.Flush(TimeSpan.FromSeconds(timeoutSeconds));
    }

    public void Dispose()
    {
        _producer.Dispose();
    }

    private static IDomainIdentifier[] BuildDomainIdentifiers(DomainEvent ev)
    {
        return ev switch
        {
            OrderPlaced placed => new IDomainIdentifier[]
            {
                OrderContextKey.OrderId.Of(placed.OrderId),
                OrderContextKey.CustomerId.Of(placed.CustomerId),
                OrderContextKey.CartId.Of(placed.CartId)
            },
            OrderConfirmed confirmed => new IDomainIdentifier[]
            {
                OrderContextKey.OrderId.Of(confirmed.OrderId),
                OrderContextKey.CustomerId.Of(confirmed.CustomerId)
            },
            OrderCancelled cancelled => new IDomainIdentifier[]
            {
                OrderContextKey.OrderId.Of(cancelled.OrderId),
                OrderContextKey.CustomerId.Of(cancelled.CustomerId)
            },
            _ => new IDomainIdentifier[]
            {
                OrderContextKey.OrderId.Of(ev.OrderId)
            }
        };
    }
}
