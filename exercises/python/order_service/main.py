"""Order Service - Saga orchestrator for the checkout flow."""

import sys
import os

# Allow importing the shared_observability package from the parent directory.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi import FastAPI
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

from shared_observability import configure_otel

# ---------------------------------------------------------------------------
# OTel bootstrap - must run before the app handles any requests
# ---------------------------------------------------------------------------
configure_otel(service_name="order-service")

# ---------------------------------------------------------------------------
# FastAPI application
# ---------------------------------------------------------------------------
app = FastAPI(
    title="Order Service",
    version="1.0.0",
    description="Saga orchestrator - coordinates the checkout flow across services.",
)

FastAPIInstrumentor.instrument_app(app)

# ---------------------------------------------------------------------------
# Health endpoints (Quarkus-compatible paths)
# ---------------------------------------------------------------------------


@app.get("/q/health/ready")
async def readiness():
    """Readiness probe - service can accept traffic."""
    return {"status": "UP"}


@app.get("/q/health/live")
async def liveness():
    """Liveness probe - process is alive."""
    return {"status": "UP"}


# ---------------------------------------------------------------------------
# Service routes - to be implemented during workshop modules
# ---------------------------------------------------------------------------
# POST /api/orders/checkout  (Module 1 - Deliverable #3)


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8080, reload=True)
