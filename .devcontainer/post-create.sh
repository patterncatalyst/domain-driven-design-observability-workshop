#!/usr/bin/env bash
set -uo pipefail

GREEN=$'\033[0;32m'; BLUE=$'\033[0;34m'; YELLOW=$'\033[0;33m'; RESET=$'\033[0m'
step() { printf "\n${BLUE}==>${RESET} %s\n" "$1"; }
ok()   { printf "    ${GREEN}✓${RESET} %s\n" "$1"; }
warn() { printf "    ${YELLOW}!${RESET} %s\n" "$1"; }

step "Installing Newman (test runner)"
npm install -g newman 2>/dev/null && ok "newman installed" || warn "newman install failed"

step "Installing Python dependencies"
pip install -r exercises/python/order_service/requirements.txt 2>/dev/null && ok "deps installed" || warn "pip install had issues"

step "Setting script permissions"
find tests -name "*.sh" -type f -exec chmod +x {} \; 2>/dev/null
find .devcontainer -name "*.sh" -type f -exec chmod +x {} \; 2>/dev/null
ok "scripts are executable"

step "Pre-pulling infrastructure images"
if docker info >/dev/null 2>&1; then
  docker compose -f exercises/python/compose.yaml pull --quiet 2>/dev/null && ok "images cached" || warn "some images failed to pull"
fi

step "Building Python services"
if docker info >/dev/null 2>&1; then
  docker compose -f exercises/python/compose.yaml build --quiet 2>/dev/null && ok "services built" || warn "build had issues"
fi

cat <<'EOF'

================================================================================
  Workshop environment ready (Python/FastAPI).

  Next steps:
    1. The observability stack starts automatically.
    2. Wait ~60 seconds for all services to initialize.
    3. Run: tests/verify.sh
    4. Open Grafana: http://localhost:3000 (admin / admin)
    5. Tutorial: https://patterncatalyst.github.io/domain-driven-design-observability-workshop/

================================================================================
EOF
exit 0
