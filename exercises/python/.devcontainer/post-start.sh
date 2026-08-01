#!/usr/bin/env bash
# =============================================================================
# post-start.sh - runs each time the devcontainer starts
# =============================================================================
set -euo pipefail

COMPOSE_DIR="$(dirname "$0")/.."

echo "==> Starting observability stack..."
cd "$COMPOSE_DIR"
docker compose up -d kafka postgres otel-collector tempo prometheus loki grafana

echo "==> Waiting for infrastructure health..."
docker compose ps

echo "==> Post-start complete. Run 'docker compose up -d' to start all services."
