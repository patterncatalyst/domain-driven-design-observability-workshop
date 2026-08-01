#!/usr/bin/env bash
# =============================================================================
# post-create.sh — runs once after the dev container is created
# =============================================================================
set -euo pipefail

echo "==> Installing Newman (Postman CLI) for API contract tests..."
npm install -g newman 2>/dev/null || echo "    (npm not available — skip newman)"

echo "==> Restoring .NET solution packages..."
dotnet restore DddWorkshop.sln

echo "==> Pre-pulling infrastructure images (background)..."
docker pull confluentinc/cp-kafka:7.7.1 &
docker pull otel/opentelemetry-collector-contrib:0.111.0 &
docker pull grafana/grafana:latest &
docker pull grafana/tempo:latest &
docker pull grafana/loki:latest &
docker pull prom/prometheus:latest &
docker pull postgres:16-alpine &
wait

echo "==> Post-create setup complete."
