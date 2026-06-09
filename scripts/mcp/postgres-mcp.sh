#!/usr/bin/env bash
# Linux equivalent of postgres-mcp.ps1 — runs crystaldba/postgres-mcp via Docker.
# Reads DB credentials from .env.local (preferred) or .env in the repo root.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Load env file (prefer .env.local; fall back to .env)
load_env() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  while IFS= read -r line; do
    [[ "$line" =~ ^[[:space:]]*$ || "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" =~ ^[[:space:]]*([^=]+)[[:space:]]*=[[:space:]]*(.*)[[:space:]]*$ ]] || continue
    local key="${BASH_REMATCH[1]// /}"
    local val="${BASH_REMATCH[2]}"
    # Strip surrounding quotes
    val="${val%\"}" ; val="${val#\"}"
    val="${val%\'}" ; val="${val#\'}"
    export "$key=$val"
  done < "$file"
}

load_env "$REPO_ROOT/.env"
load_env "$REPO_ROOT/.env.local"  # overrides .env

DB_URL="${DB_URL:-jdbc:postgresql://127.0.0.1:5555/zeromail?sslmode=disable}"
DB_USER="${DB_USER:-zeromail}"
DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD is required}"

# Convert jdbc: URL → plain postgresql: URL
PG_URL="${DB_URL#jdbc:}"
# localhost/127.0.0.1 on the host → zeromail-postgres is reachable directly on the
# zeromail-internal network; only remap host-only loopback addresses.
PG_URL="$(echo "$PG_URL" | sed 's|//localhost:5555/|//zeromail-postgres:5432/|g; s|//127\.0\.0\.1:5555/|//zeromail-postgres:5432/|g')"
# Inject credentials
PG_URL="$(echo "$PG_URL" | sed "s|postgresql://|postgresql://${DB_USER}:${DB_PASSWORD}@|")"

exec docker run -i --rm \
  --network=zeromail-internal \
  -e DATABASE_URI="$PG_URL" \
  crystaldba/postgres-mcp \
  --access-mode=unrestricted
