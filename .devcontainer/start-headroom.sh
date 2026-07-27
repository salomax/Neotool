#!/usr/bin/env bash
# =============================================================================
# start-headroom.sh — Run Headroom proxy on 127.0.0.1:8787 inside the DevPod.
# Idempotent. Called from start-infra.sh (postStart). Requires `headroom` on PATH
# (Dockerfile installs pinned headroom-ai[proxy]).
# =============================================================================

set -euo pipefail

PORT="${HEADROOM_PORT:-8787}"
HOST="${HEADROOM_HOST:-127.0.0.1}"
LOG="${HEADROOM_LOG:-/tmp/headroom-proxy.log}"
PIDFILE="${HEADROOM_PIDFILE:-/tmp/headroom-proxy.pid}"

ready() {
  curl -sf --connect-timeout 1 "http://${HOST}:${PORT}/readyz" >/dev/null 2>&1
}

if ready; then
  echo "✓ [headroom] already healthy on ${HOST}:${PORT}"
  exit 0
fi

if ! command -v headroom >/dev/null 2>&1; then
  echo "⚠ [headroom] binary not on PATH — skip (rebuild DevPod image to install)"
  exit 0
fi

# Stale pidfile / dead process
if [ -f "$PIDFILE" ]; then
  old_pid="$(cat "$PIDFILE" 2>/dev/null || true)"
  if [ -n "${old_pid:-}" ] && kill -0 "$old_pid" 2>/dev/null; then
    echo "▶ [headroom] process $old_pid running but not ready yet — waiting"
  else
    rm -f "$PIDFILE"
  fi
fi

if ! ready; then
  echo "▶ [headroom] starting proxy on ${HOST}:${PORT}"
  # Learn from session history is optional; skip quietly when homes are empty (fresh DevPod).
  if [ "${HEADROOM_LEARN_ON_START:-0}" = "1" ]; then
    headroom learn --verbosity --apply >>"$LOG" 2>&1 || true
  fi
  nohup headroom proxy --host "$HOST" --port "$PORT" >>"$LOG" 2>&1 &
  echo $! >"$PIDFILE"
fi

# Wait briefly for readiness (best-effort; do not block infra forever)
for _ in $(seq 1 30); do
  if ready; then
    echo "✓ [headroom] healthy on ${HOST}:${PORT} (log: $LOG)"
    exit 0
  fi
  sleep 1
done

echo "⚠ [headroom] started but /readyz not ready yet — check $LOG"
exit 0
