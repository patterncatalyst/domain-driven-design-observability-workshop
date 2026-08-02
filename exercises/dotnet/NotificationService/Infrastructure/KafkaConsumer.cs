using System.Diagnostics;
using System.Text;
using Confluent.Kafka;
using NotificationService.Application;
using NotificationService.Domain;
using OpenTelemetry;
using OpenTelemetry.Context.Propagation;
using SharedObservability;

namespace NotificationService.Infrastructure;

/// <summary>
/// Background service that consumes order events from Kafka, restores OTel
/// trace context and domain identifiers from message headers, and delegates
/// to <see cref="SendNotificationUseCase"/>.
/// </summary>
public sealed class OrderEventConsumer : BackgroundService
{
    private readonly ILogger<OrderEventConsumer> _logger;
    private readonly IConfiguration _configuration;
    private readonly SendNotificationUseCase _useCase;

    public OrderEventConsumer(
        ILogger<OrderEventConsumer> logger,
        IConfiguration configuration,
        SendNotificationUseCase useCase)
    {
        _logger = logger;
        _configuration = configuration;
        _useCase = useCase;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var bootstrapServers =
            Environment.GetEnvironmentVariable("KAFKA_BOOTSTRAP_SERVERS")
            ?? _configuration["Kafka:BootstrapServers"]
            ?? "localhost:9092";

        var topic = _configuration["Kafka:Topic"] ?? "order-events";

        var config = new ConsumerConfig
        {
            BootstrapServers = bootstrapServers,
            GroupId = "notification-service-v2",
            AutoOffsetReset = AutoOffsetReset.Earliest,
            EnableAutoCommit = true,
        };

        _logger.LogInformation(
            "Starting Kafka consumer: bootstrap={Bootstrap}, group={Group}, topic={Topic}",
            bootstrapServers, config.GroupId, topic);

        // Yield so the host can finish startup before we enter the blocking poll loop
        await Task.Yield();

        using var consumer = new ConsumerBuilder<string, string>(config).Build();
        consumer.Subscribe(topic);

        try
        {
            while (!stoppingToken.IsCancellationRequested)
            {
                ConsumeResult<string, string>? result;
                try
                {
                    result = consumer.Consume(TimeSpan.FromSeconds(1));
                }
                catch (ConsumeException ex)
                {
                    _logger.LogWarning(ex, "Kafka consume error");
                    continue;
                }

                if (result is null)
                    continue;

                ProcessMessage(result);
            }
        }
        finally
        {
            consumer.Close();
            _logger.LogInformation("Kafka consumer closed");
        }
    }

    private void ProcessMessage(ConsumeResult<string, string> result)
    {
        try
        {
            // ------------------------------------------------------------------
            // 1. Convert Kafka headers to a dictionary for OTel propagation
            // ------------------------------------------------------------------
            var headersDict = new Dictionary<string, string>();
            if (result.Message.Headers is not null)
            {
                foreach (var header in result.Message.Headers)
                {
                    headersDict[header.Key] = Encoding.UTF8.GetString(header.GetValueBytes());
                }
            }

            // ------------------------------------------------------------------
            // 2. Extract OTel trace context (traceparent / baggage) from headers
            // ------------------------------------------------------------------
            var propagationContext = Propagators.DefaultTextMapPropagator.Extract(
                default,
                headersDict,
                static (carrier, key) =>
                {
                    if (carrier.TryGetValue(key, out var value))
                        return new[] { value };
                    return Enumerable.Empty<string>();
                });

            Baggage.Current = propagationContext.Baggage;

            // ------------------------------------------------------------------
            // 3. Start a consumer span parented to the extracted context
            // ------------------------------------------------------------------
            using var activity = SendNotificationUseCase.Source.StartActivity(
                "Notification.Consume",
                ActivityKind.Consumer,
                propagationContext.ActivityContext);

            // ------------------------------------------------------------------
            // 4. Extract domain identifiers from Kafka headers and restore them
            // ------------------------------------------------------------------
            var kafkaHeaders = new List<KeyValuePair<string, byte[]>>();
            if (result.Message.Headers is not null)
            {
                foreach (var header in result.Message.Headers)
                {
                    kafkaHeaders.Add(new KeyValuePair<string, byte[]>(
                        header.Key, header.GetValueBytes()));
                }
            }

            var domainIds = KafkaHeaderPropagator.Extract(kafkaHeaders);
            var identifiers = domainIds
                .Select(kvp => NotificationContextKey.FromKey(kvp.Key)?.Of(kvp.Value))
                .Where(id => id is not null)
                .Cast<IDomainIdentifier>()
                .ToArray();

            using var domainCtx = new DomainContext(_logger, identifiers);

            // ------------------------------------------------------------------
            // 5. Read customer tier from OTel baggage (set by upstream service)
            // ------------------------------------------------------------------
            var customerTier = BaggageHelpers.Get("customer.tier") ?? "unknown";

            // ------------------------------------------------------------------
            // 6. Deserialize the event and dispatch to use case
            // ------------------------------------------------------------------
            var orderEvent = InboundOrderEvent.DeserializeEvent(result.Message.Value);

            activity?.SetTag("event.type", orderEvent.EventType);
            activity?.SetTag("order.id", orderEvent.OrderId);
            activity?.SetTag("customer.tier", customerTier);

            _useCase.Send(orderEvent, customerTier);
        }
        catch (Exception ex) when (ex is InvalidOperationException or System.Text.Json.JsonException)
        {
            _logger.LogWarning(ex,
                "Failed to process message at offset {Offset}, skipping",
                result.Offset.Value);
        }
    }
}
