#!/usr/bin/env bash
set -uo pipefail

if ! docker info >/dev/null 2>&1; then
  echo "Docker not ready — run 'docker compose -f exercises/dotnet/compose.yaml up -d' when it is."
  exit 0
fi

echo "Starting workshop stack (C#/.NET 10)..."
docker compose -f exercises/dotnet/compose.yaml up -d 2>&1 | tail -5

echo ""
echo "Workshop stack starting. Give it ~60 seconds, then:"
echo "  Grafana:    http://localhost:3000  (admin / admin)"
echo "  Verify:     tests/verify.sh"
