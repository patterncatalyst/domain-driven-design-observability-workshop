#!/usr/bin/env bash
# =============================================================================
# post-start.sh - Runs on every devcontainer start
# =============================================================================
set -uo pipefail

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon not yet ready - skipping infrastructure auto-start."
  echo "Once Docker is up, run: docker compose up -d"
  exit 0
fi

echo "Starting infrastructure and application stack..."
docker compose up -d --quiet-pull 2>&1 | tail -5

echo ""
echo "Stack running. Useful URLs (forwarded to your browser):"
echo "  Grafana    http://localhost:3000  (admin / admin)"
echo "  Prometheus http://localhost:9090"
echo "  Tempo      http://localhost:3200"
echo ""
