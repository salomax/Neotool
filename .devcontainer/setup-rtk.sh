#!/usr/bin/env bash
# =============================================================================
# setup-rtk.sh — Install agent hooks for RTK (Rust Token Killer) in the DevPod.
# Called from postCreateCommand. Idempotent. Requires `rtk` on PATH (Dockerfile).
#
# Agents:
#   - Claude Code / Copilot: Tier-1 PreToolUse hook (rtk init -g --auto-patch)
#   - Cursor:                Tier-1 preToolUse hook (rtk init -g --agent cursor)
#   - Codex / ChatGPT:       rules-file integration (rtk init -g --codex) — best-effort
# =============================================================================

set -euo pipefail

if ! command -v rtk >/dev/null 2>&1; then
  echo "▶ [setup-rtk] rtk not on PATH — skip (rebuild DevPod image to install)"
  exit 0
fi

if ! rtk gain >/dev/null 2>&1; then
  echo "▶ [setup-rtk] WARNING: rtk binary present but 'rtk gain' failed — wrong package?"
  rtk --version || true
  exit 0
fi

echo "▶ [setup-rtk] $(rtk --version) — configuring agent hooks (idempotent)"

# Claude Code — global PreToolUse hook + settings patch (no prompt)
rtk init -g --auto-patch --hook-only 2>&1 || {
  echo "  ~ Claude Code init reported an error (continuing)"
}

# Cursor — global hooks.json (RTK: Cursor hooks are global-only)
rtk init -g --agent cursor --auto-patch 2>&1 || {
  echo "  ~ Cursor init reported an error (continuing)"
}

# Codex — AGENTS.md + RTK.md instructions (no hard hook; model compliance)
rtk init -g --codex 2>&1 || {
  echo "  ~ Codex init reported an error (continuing)"
}

echo "✓ [setup-rtk] done — verify with: rtk init --show && rtk gain"
rtk init --show 2>&1 || true
