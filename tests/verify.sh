#!/usr/bin/env bash
# =============================================================================
# verify.sh - workshop environment health check
# =============================================================================
# Checks every component of the observability stack and reports green or red.
# Intended to be safe to run repeatedly. Used by:
#   - The pre-workshop checklist (Prerequisites page)
#   - Module 1 setup verification
#   - The devcontainer postCreateCommand
# =============================================================================

set -uo pipefail

# Colors (no-op if NO_COLOR is set or stdout isn't a tty)
if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  RED=$'\033[0;31m'
  GREEN=$'\033[0;32m'
  YELLOW=$'\033[0;33m'
  BLUE=$'\033[0;34m'
  RESET=$'\033[0m'
else
  RED=""; GREEN=""; YELLOW=""; BLUE=""; RESET=""
fi

PASS=0
FAIL=0
WARN=0

ok()    { printf "  ${GREEN}✓${RESET} %s\n" "$1"; PASS=$((PASS+1)); }
fail()  { printf "  ${RED}✗${RESET} %s\n" "$1"; FAIL=$((FAIL+1)); }
warn()  { printf "  ${YELLOW}!${RESET} %s\n" "$1"; WARN=$((WARN+1)); }
header(){ printf "\n${BLUE}== %s ==${RESET}\n" "$1"; }

# -----------------------------------------------------------------------------
# Helpers
# -----------------------------------------------------------------------------
http_check() {
  local name=$1 url=$2 expected=${3:-200}
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$url")
  if [[ "$code" == "$expected" ]]; then
    ok "$name reachable ($url -> $code)"
  else
    fail "$name unreachable ($url -> $code, expected $expected)"
  fi
}

# -----------------------------------------------------------------------------
# Docker
# -----------------------------------------------------------------------------
header "Docker"
if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then
    ok "Docker daemon responding"
  else
    fail "Docker installed but daemon not responding"
  fi
else
  fail "Docker not installed"
fi

# -----------------------------------------------------------------------------
# Containers
# -----------------------------------------------------------------------------
header "Workshop containers"
expected_containers=(
  workshop-kafka
  workshop-postgres
  workshop-otel-collector
  workshop-tempo
  workshop-prometheus
  workshop-loki
  workshop-grafana
)
for c in "${expected_containers[@]}"; do
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${c}$"; then
    ok "$c running"
  else
    fail "$c not running (try: docker compose -f infrastructure/_infra/compose-*.yaml up -d)"
  fi
done

# -----------------------------------------------------------------------------
# Observability stack endpoints
# -----------------------------------------------------------------------------
header "Observability endpoints"
http_check "Grafana"    "http://localhost:3000/api/health"
http_check "Prometheus" "http://localhost:9090/-/ready"
http_check "Tempo"      "http://localhost:3200/ready"
http_check "Loki"       "http://localhost:3100/ready"
http_check "OTel Collector (zPages)" "http://localhost:8888/metrics"

# -----------------------------------------------------------------------------
# Grafana datasources & dashboards
# -----------------------------------------------------------------------------
header "Grafana provisioning"
ds_response=$(curl -s -u admin:admin --max-time 5 http://localhost:3000/api/datasources 2>/dev/null || echo "[]")
for expected_ds in Prometheus Tempo Loki; do
  if echo "$ds_response" | grep -q "\"name\":\"${expected_ds}\""; then
    ok "Datasource provisioned: $expected_ds"
  else
    fail "Datasource missing: $expected_ds"
  fi
done

dash_response=$(curl -s -u admin:admin --max-time 5 "http://localhost:3000/api/search?type=dash-db" 2>/dev/null || echo "[]")
for expected_dash in workshop-service-health workshop-checkout-saga workshop-trace-explorer workshop-observability-cost workshop-transport-comparison; do
  if echo "$dash_response" | grep -q "\"uid\":\"${expected_dash}\""; then
    ok "Dashboard provisioned: $expected_dash"
  else
    warn "Dashboard not yet loaded: $expected_dash (Grafana may still be starting)"
  fi
done

# -----------------------------------------------------------------------------
# Kafka
# -----------------------------------------------------------------------------
header "Kafka"
if docker exec workshop-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; then
  ok "Kafka broker responsive"
else
  fail "Kafka broker not responsive"
fi

# -----------------------------------------------------------------------------
# Postgres
# -----------------------------------------------------------------------------
header "Postgres"
if docker exec workshop-postgres pg_isready -U appuser >/dev/null 2>&1; then
  ok "Postgres accepting connections"
else
  fail "Postgres not accepting connections"
fi

# -----------------------------------------------------------------------------
# Workshop services (expected after deliverable #3)
# -----------------------------------------------------------------------------
header "Workshop services"
service_ports=(
  "order-service:8080"
  "inventory-service:8081"
  "payment-service:8082"
  "shipping-service:8083"
  "notification-service:8084"
)
for svc in "${service_ports[@]}"; do
  name=${svc%:*}
  port=${svc#*:}
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 2 "http://localhost:${port}/q/health/ready")
  if [[ "$code" == "200" ]]; then
    ok "$name healthy on :$port"
  elif [[ "$code" == "000" ]]; then
    warn "$name not running on :$port (expected if you haven't started services yet)"
  else
    fail "$name unhealthy on :$port (HTTP $code)"
  fi
done

# -----------------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------------
header "Summary"
printf "  ${GREEN}%d passed${RESET}, ${RED}%d failed${RESET}, ${YELLOW}%d warnings${RESET}\n" "$PASS" "$FAIL" "$WARN"

if [[ $FAIL -gt 0 ]]; then
  printf "\n${RED}Environment is not ready.${RESET} See docs/troubleshooting.md (or https://patterncatalyst.github.io/ddd-observability-workshop/troubleshooting/ once published)\n"
  exit 1
elif [[ $WARN -gt 0 ]]; then
  printf "\n${YELLOW}Environment ready with warnings.${RESET} Warnings about workshop services are expected before Module 1.\n"
  exit 0
else
  printf "\n${GREEN}All systems go.${RESET}\n"
  exit 0
fi
