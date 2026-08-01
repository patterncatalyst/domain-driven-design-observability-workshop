"""Notification Service - Kafka consumer that sends customer notifications."""

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi import FastAPI
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

from shared_observability import configure_otel

# ---------------------------------------------------------------------------
# OTel bootstrap
# ---------------------------------------------------------------------------
configure_otel(service_name="notification-service")

# ---------------------------------------------------------------------------
# FastAPI application
# ---------------------------------------------------------------------------
app = FastAPI(
    title="Notification Service",
    version="1.0.0",
    description="Consumes order events from Kafka and dispatches customer notifications.",
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
# Service routes - to be implemented during workshop modules
# ---------------------------------------------------------------------------
# Kafka consumer lifecycle is managed via FastAPI lifespan events.
# No REST endpoints beyond health checks - this service is event-driven.


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8084, reload=True)
