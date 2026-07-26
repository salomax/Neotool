#!/usr/bin/env bash
# Start security + MCP inside the DevPod with lowered rate limits for GH-420 k6 validation.
set -euo pipefail

ROOT="${ROOT:-/workspaces/invistus}"
cd "$ROOT/service/kotlin"

LIMIT="${OAUTH2_TOKEN_RATE_LIMIT_LIMIT:-5}"
WINDOW_MS="${OAUTH2_TOKEN_RATE_LIMIT_WINDOW_MS:-10000}"
MCP_READ_LIMIT="${MCP_RATE_LIMIT_TOKEN_READ_LIMIT:-5}"
MCP_READ_WINDOW_MS="${MCP_RATE_LIMIT_TOKEN_READ_WINDOW_MS:-10000}"
MCP_USER_LIMIT="${MCP_RATE_LIMIT_USER_LIMIT:-1000}"

mkdir -p /tmp/gh420-k6
SECURITY_LOG=/tmp/gh420-k6/security.log
MCP_LOG=/tmp/gh420-k6/mcp.log

if curl -fsS --max-time 2 http://127.0.0.1:8080/health >/dev/null 2>&1; then
  echo "security already healthy on :8080"
else
  echo "starting security with token rate-limit limit=${LIMIT} windowMs=${WINDOW_MS}"
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/service/kotlin/security/.env.local"
  set +a
  export OAUTH2_TOKEN_RATE_LIMIT_LIMIT="$LIMIT"
  export OAUTH2_TOKEN_RATE_LIMIT_WINDOW_MS="$WINDOW_MS"
  export POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
  export POSTGRES_PORT="${POSTGRES_PORT:-6432}"
  nohup ./gradlew :security:run --console=plain >"$SECURITY_LOG" 2>&1 &
  echo $! >/tmp/gh420-k6/security.pid
fi

if curl -fsS --max-time 2 http://127.0.0.1:8086/health >/dev/null 2>&1; then
  echo "mcp already healthy on :8086"
else
  echo "starting mcp with read rate-limit limit=${MCP_READ_LIMIT} windowMs=${MCP_READ_WINDOW_MS}"
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/service/kotlin/mcp/.env.local"
  set +a
  export MCP_RATE_LIMIT_TOKEN_READ_LIMIT="$MCP_READ_LIMIT"
  export MCP_RATE_LIMIT_TOKEN_READ_WINDOW_MS="$MCP_READ_WINDOW_MS"
  export MCP_RATE_LIMIT_USER_LIMIT="$MCP_USER_LIMIT"
  export MCP_RATE_LIMIT_CLEANUP_ENABLED=false
  export POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
  export POSTGRES_PORT="${POSTGRES_PORT:-6432}"
  nohup ./gradlew :mcp:run --console=plain >"$MCP_LOG" 2>&1 &
  echo $! >/tmp/gh420-k6/mcp.pid
fi

echo "waiting for health..."
for i in $(seq 1 90); do
  sec_ok=0
  mcp_ok=0
  curl -fsS --max-time 2 http://127.0.0.1:8080/health >/dev/null 2>&1 && sec_ok=1
  curl -fsS --max-time 2 http://127.0.0.1:8086/health >/dev/null 2>&1 && mcp_ok=1
  if [[ "$sec_ok" == "1" && "$mcp_ok" == "1" ]]; then
    echo "security + mcp healthy"
    exit 0
  fi
  sleep 2
done

echo "timed out waiting for services" >&2
echo "--- security log tail ---" >&2
tail -n 80 "$SECURITY_LOG" >&2 || true
echo "--- mcp log tail ---" >&2
tail -n 80 "$MCP_LOG" >&2 || true
exit 1
