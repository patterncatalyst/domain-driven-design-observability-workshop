using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using OpenTelemetry;
using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;

namespace SharedObservability;

/// <summary>
/// Extension methods to wire up OpenTelemetry tracing, metrics, and logging
/// with OTLP exporters. Call from Program.cs:
/// <code>builder.Services.AddOpenTelemetryWorkshop(builder.Configuration);</code>
/// </summary>
public static class OtelSetup
{
    public static IServiceCollection AddOpenTelemetryWorkshop(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        var serviceName = configuration["OpenTelemetry:ServiceName"] ?? "unknown-service";
        var serviceNamespace = configuration["OpenTelemetry:ServiceNamespace"] ?? "workshop";

        var resourceBuilder = ResourceBuilder.CreateDefault()
            .AddService(
                serviceName: serviceName,
                serviceNamespace: serviceNamespace);

        // --- Tracing ---
        services.AddOpenTelemetry()
            .WithTracing(tracing =>
            {
                tracing
                    .SetResourceBuilder(resourceBuilder)
                    .AddAspNetCoreInstrumentation()
                    .AddHttpClientInstrumentation()
                    .AddOtlpExporter();
            })
            .WithMetrics(metrics =>
            {
                metrics
                    .SetResourceBuilder(resourceBuilder)
                    .AddAspNetCoreInstrumentation()
                    .AddHttpClientInstrumentation()
                    .AddRuntimeInstrumentation()
                    .AddOtlpExporter();
            });

        // --- Logging ---
        services.AddLogging(logging =>
        {
            logging.AddOpenTelemetry(options =>
            {
                options.SetResourceBuilder(resourceBuilder);
                options.AddOtlpExporter();
                options.IncludeFormattedMessage = true;
                options.IncludeScopes = true;
            });
        });

        return services;
    }
}
