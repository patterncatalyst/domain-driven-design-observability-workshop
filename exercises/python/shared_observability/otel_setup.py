"""Configure the OpenTelemetry SDK for workshop services."""

from __future__ import annotations

import os

import structlog
from opentelemetry import metrics, trace
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import SERVICE_NAME, Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor


def configure_otel(service_name: str | None = None) -> None:
    """Bootstrap TracerProvider, MeterProvider, and structured logging.

    Reads ``OTEL_SERVICE_NAME`` and ``OTEL_EXPORTER_OTLP_ENDPOINT`` from
    the environment if not supplied explicitly. Safe to call once at
    application startup.

    Args:
        service_name: Override for the service name resource attribute.
            Falls back to the ``OTEL_SERVICE_NAME`` env var.
    """
    svc = service_name or os.environ.get("OTEL_SERVICE_NAME", "unknown-service")
    endpoint = os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317")

    resource = Resource.create({SERVICE_NAME: svc})

    # -- Traces ---------------------------------------------------------------
    tracer_provider = TracerProvider(resource=resource)
    tracer_provider.add_span_processor(
        BatchSpanProcessor(OTLPSpanExporter(endpoint=endpoint, insecure=True))
    )
    trace.set_tracer_provider(tracer_provider)

    # -- Metrics --------------------------------------------------------------
    metric_reader = PeriodicExportingMetricReader(
        OTLPMetricExporter(endpoint=endpoint, insecure=True),
        export_interval_millis=10_000,
    )
    meter_provider = MeterProvider(resource=resource, metric_readers=[metric_reader])
    metrics.set_meter_provider(meter_provider)

    # -- Structured logging (structlog) ---------------------------------------
    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.processors.add_log_level,
            structlog.processors.TimeStamper(fmt="iso"),
            structlog.dev.ConsoleRenderer() if os.environ.get("LOG_FORMAT") != "json"
            else structlog.processors.JSONRenderer(),
        ],
        wrapper_class=structlog.make_filtering_bound_logger(0),
        context_class=dict,
        logger_factory=structlog.PrintLoggerFactory(),
        cache_logger_on_first_use=True,
    )
