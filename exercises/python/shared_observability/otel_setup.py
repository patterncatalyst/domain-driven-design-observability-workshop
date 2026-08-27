"""Configure the OpenTelemetry SDK for workshop services."""

from __future__ import annotations

import logging
import os

import structlog
from opentelemetry import metrics, trace
from opentelemetry._logs import set_logger_provider
from opentelemetry.exporter.otlp.proto.grpc._log_exporter import OTLPLogExporter
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import SERVICE_NAME, SERVICE_NAMESPACE, Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor


def configure_otel(service_name: str | None = None) -> None:
    """Bootstrap TracerProvider, MeterProvider, LoggerProvider, and structlog.

    Reads ``OTEL_SERVICE_NAME`` and ``OTEL_EXPORTER_OTLP_ENDPOINT`` from
    the environment if not supplied explicitly. Safe to call once at
    application startup.

    Logs are exported over OTLP to the Collector (and on to Loki) *and*
    rendered to the console, so structured log lines carry the same domain
    context (``order.id``, ``customer.id``, ...) in Loki as the traces and
    metrics do -- matching the Quarkus and C# implementations.

    Args:
        service_name: Override for the service name resource attribute.
            Falls back to the ``OTEL_SERVICE_NAME`` env var.
    """
    svc = service_name or os.environ.get("OTEL_SERVICE_NAME", "unknown-service")
    endpoint = os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317")

    resource = Resource.create(
        {SERVICE_NAME: svc, SERVICE_NAMESPACE: "workshop"}
    )

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

    # -- Logs (OTLP -> Collector -> Loki) -------------------------------------
    logger_provider = LoggerProvider(resource=resource)
    logger_provider.add_log_record_processor(
        BatchLogRecordProcessor(OTLPLogExporter(endpoint=endpoint, insecure=True))
    )
    set_logger_provider(logger_provider)

    # Bridge Python's stdlib logging to OTLP. structlog emits through the stdlib
    # root logger, so this handler ships every log line to the Collector; a
    # StreamHandler keeps human-readable output on the console.
    otel_handler = LoggingHandler(level=logging.NOTSET, logger_provider=logger_provider)
    root = logging.getLogger()
    root.setLevel(logging.INFO)
    root.handlers.clear()
    root.addHandler(logging.StreamHandler())
    root.addHandler(otel_handler)

    # -- Structured logging (structlog over stdlib) ---------------------------
    # Render each event to a JSON string that becomes the log body. The bound
    # context fields (order.id, customer.id, cart.id, ...) ride along in the
    # JSON, so in Loki `| json | order_id="ord_..."` filters work the same way
    # they do for the Quarkus and C# services. The stdlib root logger fans the
    # line out to both the console and the OTLP handler (-> Collector -> Loki).
    structlog.configure(
        processors=[
            structlog.contextvars.merge_contextvars,
            structlog.processors.add_log_level,
            structlog.processors.TimeStamper(fmt="iso"),
            structlog.processors.JSONRenderer(),
        ],
        wrapper_class=structlog.stdlib.BoundLogger,
        context_class=dict,
        logger_factory=structlog.stdlib.LoggerFactory(),
        cache_logger_on_first_use=True,
    )
