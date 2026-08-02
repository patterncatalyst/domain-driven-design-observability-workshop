"""Shipping Service - Shipment scheduling and tracking."""

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi import FastAPI
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

from shared_observability import configure_otel

# ---------------------------------------------------------------------------
# OTel bootstrap
# ---------------------------------------------------------------------------
configure_otel(service_name="shipping-service")

# ---------------------------------------------------------------------------
# FastAPI application
# ---------------------------------------------------------------------------
app = FastAPI(
    title="Shipping Service",
    version="1.0.0",
    description="Schedules and tracks shipments for fulfilled orders.",
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
# Service routes
# ---------------------------------------------------------------------------
from infrastructure.routes import router  # noqa: E402

app.include_router(router)


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8083, reload=True)
