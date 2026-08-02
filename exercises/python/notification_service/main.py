"""Notification Service - Kafka consumer that sends customer notifications."""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from contextlib import asynccontextmanager

from fastapi import FastAPI
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

from infrastructure.kafka_consumer import OrderEventConsumer
from shared_observability import configure_otel

# ---------------------------------------------------------------------------
# OTel bootstrap
# ---------------------------------------------------------------------------
configure_otel(service_name="notification-service")

# ---------------------------------------------------------------------------
# Kafka consumer (started/stopped via lifespan)
# ---------------------------------------------------------------------------
_kafka_bootstrap = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
_consumer = OrderEventConsumer(bootstrap_servers=_kafka_bootstrap)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Manage Kafka consumer lifecycle alongside the FastAPI application."""
    _consumer.start()
    yield
    _consumer.stop()


# ---------------------------------------------------------------------------
# FastAPI application
# ---------------------------------------------------------------------------
app = FastAPI(
    title="Notification Service",
    version="1.0.0",
    description="Consumes order events from Kafka and dispatches customer notifications.",
    lifespan=lifespan,
)

FastAPIInstrumentor.instrument_app(app)

# ---------------------------------------------------------------------------
# Health endpoints (Quarkus-compatible paths)
# ---------------------------------------------------------------------------


@app.get("/q/health/ready")
async def readiness():
    return {"status": "UP"}


@app.get("/q/health/live")
async def liveness():
    return {"status": "UP"}


# ---------------------------------------------------------------------------
# No REST business endpoints -- this service is purely event-driven.
# ---------------------------------------------------------------------------


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8084, reload=True)
