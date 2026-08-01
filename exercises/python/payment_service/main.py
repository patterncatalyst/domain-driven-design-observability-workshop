"""Payment Service - Payment authorization and processing."""

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi import FastAPI
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor

from shared_observability import configure_otel

# ---------------------------------------------------------------------------
# OTel bootstrap
# ---------------------------------------------------------------------------
configure_otel(service_name="payment-service")

# ---------------------------------------------------------------------------
# FastAPI application
# ---------------------------------------------------------------------------
app = FastAPI(
    title="Payment Service",
    version="1.0.0",
    description="Authorizes and processes payments for the checkout saga.",
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
# POST /api/payments/authorize  (Module 1)
# POST /api/payments/refund     (Module 1)


# ---------------------------------------------------------------------------
# Entrypoint
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8082, reload=True)
