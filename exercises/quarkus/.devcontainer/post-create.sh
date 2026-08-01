#!/usr/bin/env bash
# =============================================================================
# post-create.sh - One-time setup after devcontainer creation
# =============================================================================
set -uo pipefail

GREEN=$'\033[0;32m'
RED=$'\033[0;31m'
BLUE=$'\033[0;34m'
YELLOW=$'\033[0;33m'
RESET=$'\033[0m'

step() { printf "\n${BLUE}==>${RESET} %s\n" "$1"; }
ok()   { printf "    ${GREEN}✓${RESET} %s\n" "$1"; }
warn() { printf "    ${YELLOW}!${RESET} %s\n" "$1"; }
err()  { printf "    ${RED}✗${RESET} %s\n" "$1"; }

FAILED_TOOLS=()

BIN_DIR="${HOME}/.local/bin"
mkdir -p "$BIN_DIR"
case ":$PATH:" in
  *":$BIN_DIR:"*) ;;
  *) echo "export PATH=\"$BIN_DIR:\$PATH\"" >> "${HOME}/.bashrc" ;;
esac
export PATH="$BIN_DIR:$PATH"

# Install hey
step "Installing hey (REST load generator)"
if command -v hey >/dev/null 2>&1; then
  ok "hey already installed"
elif curl -fsSL --max-time 60 \
        "https://storage.googleapis.com/hey-releases/hey_linux_amd64" \
        -o "$BIN_DIR/hey" 2>/dev/null; then
  chmod +x "$BIN_DIR/hey"
  ok "hey installed to $BIN_DIR/hey"
else
  err "hey download failed. Install manually later if needed."
  FAILED_TOOLS+=("hey")
fi

# Install ghz
step "Installing ghz (gRPC load generator)"
if command -v ghz >/dev/null 2>&1; then
  ok "ghz already installed"
else
  GHZ_VERSION="0.120.0"
  TMPDIR=$(mktemp -d)
  if curl -fsSL --max-time 60 \
        "https://github.com/bojand/ghz/releases/download/v${GHZ_VERSION}/ghz-linux-x86_64.tar.gz" \
        -o "$TMPDIR/ghz.tar.gz" 2>/dev/null \
     && tar -xzf "$TMPDIR/ghz.tar.gz" -C "$TMPDIR" 2>/dev/null \
     && mv "$TMPDIR/ghz" "$BIN_DIR/ghz" 2>/dev/null; then
    chmod +x "$BIN_DIR/ghz"
    ok "ghz installed to $BIN_DIR/ghz"
  else
    err "ghz download failed."
    FAILED_TOOLS+=("ghz")
  fi
  rm -rf "$TMPDIR"
fi

# Install grpcurl
step "Installing grpcurl (manual gRPC testing)"
if command -v grpcurl >/dev/null 2>&1; then
  ok "grpcurl already installed"
else
  GRPCURL_VERSION="1.9.1"
  TMPDIR=$(mktemp -d)
  if curl -fsSL --max-time 60 \
        "https://github.com/fullstorydev/grpcurl/releases/download/v${GRPCURL_VERSION}/grpcurl_${GRPCURL_VERSION}_linux_x86_64.tar.gz" \
        -o "$TMPDIR/grpcurl.tar.gz" 2>/dev/null \
     && tar -xzf "$TMPDIR/grpcurl.tar.gz" -C "$TMPDIR" 2>/dev/null \
     && mv "$TMPDIR/grpcurl" "$BIN_DIR/grpcurl" 2>/dev/null; then
    chmod +x "$BIN_DIR/grpcurl"
    ok "grpcurl installed to $BIN_DIR/grpcurl"
  else
    err "grpcurl download failed."
    FAILED_TOOLS+=("grpcurl")
  fi
  rm -rf "$TMPDIR"
fi

# Script permissions
step "Setting script permissions"
find .devcontainer -name "*.sh" -type f -exec chmod +x {} \; 2>/dev/null
ok ".devcontainer/*.sh are executable"

# Pre-pull infrastructure images
step "Pre-pulling infrastructure images (this takes 2-3 minutes)"
if docker info >/dev/null 2>&1; then
  if docker compose -f compose.yaml pull --quiet 2>/dev/null; then
    ok "Infrastructure images cached"
  else
    warn "Some images failed to pre-pull. They'll be retried on first compose up."
  fi
else
  warn "Docker daemon not yet ready - images will pull on first 'docker compose up'"
fi

cat <<'EOF'

================================================================================

  Workshop environment ready.

  Next steps:

    1. Start the infrastructure + application stack:
         docker compose up -d

    2. Open Grafana (it should auto-open in a browser tab):
         http://localhost:3000     (login: admin / admin)

================================================================================
EOF

if [ ${#FAILED_TOOLS[@]} -gt 0 ]; then
  printf "\n${YELLOW}Note:${RESET} The following tools failed to install: %s\n" "${FAILED_TOOLS[*]}"
  printf "      The environment is still usable.\n\n"
fi

exit 0
