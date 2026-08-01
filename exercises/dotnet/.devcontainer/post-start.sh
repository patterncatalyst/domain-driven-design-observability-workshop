#!/usr/bin/env bash
# =============================================================================
# post-start.sh — runs each time the dev container starts
# =============================================================================
set -euo pipefail

echo "==> Starting observability and infrastructure stack..."
docker compose up -d \
  kafka postgres otel-collector tempo prometheus loki grafana 2>/dev/null \
  || echo "    (docker compose not ready — start manually with 'docker compose up -d')"

echo "==> Waiting for infrastructure health..."
echo "    Grafana:    http://localhost:3000"
echo "    Prometheus: http://localhost:9090"
echo "    Tempo:      http://localhost:3200"
echo "    Loki:       http://localhost:3100"
echo ""
echo "==> To start application services: docker compose up --build -d"
echo "==> Post-start complete."
