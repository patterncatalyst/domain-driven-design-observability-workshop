#!/usr/bin/env bash
# =============================================================================
# post-create.sh - runs once when the devcontainer is first created
# =============================================================================
set -euo pipefail

echo "==> Installing Newman (API test runner)..."
npm install -g newman

echo "==> Installing shared_observability package..."
pip install -e "$(dirname "$0")/.." 2>/dev/null || \
  pip install -e /workspaces/*/exercises/python 2>/dev/null || \
  echo "    (shared_observability will be installed when compose builds)"

echo "==> Installing per-service dependencies..."
for svc in order_service inventory_service payment_service shipping_service notification_service; do
  if [ -f "$(dirname "$0")/../${svc}/requirements.txt" ]; then
    echo "    -> ${svc}"
    pip install -r "$(dirname "$0")/../${svc}/requirements.txt"
  fi
done

echo "==> Pre-pulling Docker images (background)..."
images=(
  "confluentinc/cp-kafka:7.7.1"
  "postgres:16-alpine"
  "otel/opentelemetry-collector-contrib:0.111.0"
  "grafana/tempo:2.6.1"
  "prom/prometheus:v2.55.1"
  "grafana/loki:3.2.1"
  "grafana/grafana:11.3.0"
)
for img in "${images[@]}"; do
  docker pull "$img" &
done
wait

echo "==> Making scripts executable..."
find "$(dirname "$0")/../" -name "*.sh" -exec chmod +x {} \;

echo "==> Post-create complete."
