#!/usr/bin/env bash
set -uo pipefail

GREEN=$'\033[0;32m'; RED=$'\033[0;31m'; BLUE=$'\033[0;34m'; YELLOW=$'\033[0;33m'; RESET=$'\033[0m'
step() { printf "\n${BLUE}==>${RESET} %s\n" "$1"; }
ok()   { printf "    ${GREEN}✓${RESET} %s\n" "$1"; }
warn() { printf "    ${YELLOW}!${RESET} %s\n" "$1"; }

FAILED_TOOLS=()
BIN_DIR="${HOME}/.local/bin"
mkdir -p "$BIN_DIR"
case ":$PATH:" in *":$BIN_DIR:"*) ;; *) echo "export PATH=\"$BIN_DIR:\$PATH\"" >> "${HOME}/.bashrc" ;; esac
export PATH="$BIN_DIR:$PATH"

step "Installing Newman (test runner)"
if command -v newman >/dev/null 2>&1; then
  ok "newman already installed"
elif npm install -g newman 2>/dev/null; then
  ok "newman installed"
else
  warn "newman install failed — you can install it later: npm install -g newman"
  FAILED_TOOLS+=("newman")
fi

step "Setting script permissions"
find tests -name "*.sh" -type f -exec chmod +x {} \; 2>/dev/null
find .devcontainer -name "*.sh" -type f -exec chmod +x {} \; 2>/dev/null
ok "scripts are executable"

step "Pre-pulling infrastructure images"
if docker info >/dev/null 2>&1; then
  docker compose -f exercises/quarkus/compose.yaml pull --quiet 2>/dev/null && ok "images cached" || warn "some images failed to pull"
else
  warn "Docker not ready — images will pull on first compose up"
fi

step "Building Quarkus services (this takes 3-5 minutes)"
if docker info >/dev/null 2>&1; then
  docker compose -f exercises/quarkus/compose.yaml build --quiet 2>/dev/null && ok "services built" || warn "build had issues — will retry on compose up"
else
  warn "Docker not ready — services will build on first compose up"
fi

cat <<'EOF'

================================================================================
  Workshop environment ready (Quarkus/Java).

  Next steps:
    1. The observability stack starts automatically.
    2. Wait ~60 seconds for all services to initialize.
    3. Run: tests/verify.sh
    4. Open Grafana: http://localhost:3000 (admin / admin)
    5. Tutorial: https://patterncatalyst.github.io/domain-driven-design-observability-workshop/

================================================================================
EOF

if [ ${#FAILED_TOOLS[@]} -gt 0 ]; then
  printf "\n${YELLOW}Note:${RESET} Failed to install: %s (optional)\n" "${FAILED_TOOLS[*]}"
fi
exit 0
