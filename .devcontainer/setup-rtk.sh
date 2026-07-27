#!/usr/bin/env bash
# =============================================================================
# setup-rtk.sh — Install agent hooks for RTK (Rust Token Killer) in the DevPod.
# Called from postCreateCommand after setup-agent-homes.sh. Idempotent.
# Requires `rtk` on PATH (Dockerfile). Agent homes are container-local
# (/home/dev/.claude, /home/dev/.codex) — not bind-mounted from the host.
#
# Agents:
#   - Claude Code / Copilot: Tier-1 PreToolUse hook (rtk init -g --auto-patch)
#   - Cursor:                Tier-1 preToolUse hook (rtk init -g --agent cursor)
#   - Codex / ChatGPT:       project-root RTK.md + AGENTS.md (NOT ~/.codex/) so
#                            agents outside the DevPod sandbox can resolve the file
# =============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

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

# Codex — project-local RTK.md + AGENTS.md @RTK.md (committed in the repo).
# Do NOT use `rtk init -g --codex`: that pins @/home/dev/.codex/RTK.md, which breaks
# agents running outside the DevPod sandbox.
# Do NOT re-run `rtk init --codex` when files already exist — it overwrites RTK.md
# with the stock template and would drop Invistus-specific notes.
if [ -f "$REPO_ROOT/RTK.md" ] && grep -qE '^@RTK\.md$' "$REPO_ROOT/AGENTS.md" 2>/dev/null; then
  echo "  ~ Codex: using committed $REPO_ROOT/RTK.md (skip rtk init --codex)"
else
  rtk init --codex 2>&1 || {
    echo "  ~ Codex project init reported an error (continuing)"
  }
fi

# Scrub stale absolute references left by older global Codex inits
CODEX_AGENTS="${HOME}/.codex/AGENTS.md"
if [ -f "$CODEX_AGENTS" ] && grep -qE '@/.*/\.codex/RTK\.md' "$CODEX_AGENTS" 2>/dev/null; then
  echo "  ~ scrubbing absolute ~/.codex/RTK.md refs from $CODEX_AGENTS"
  tmp="$(mktemp)"
  # Drop broken absolute @…/.codex/RTK.md lines; keep everything else
  grep -vE '@/.*/\.codex/RTK\.md' "$CODEX_AGENTS" >"$tmp" || true
  # Point global Codex instructions at the project RTK.md (absolute path works for
  # in-container agents; out-of-sandbox agents use the repo AGENTS.md @RTK.md instead)
  if ! grep -qF "@${REPO_ROOT}/RTK.md" "$tmp" 2>/dev/null && ! grep -qE '^@RTK\.md$' "$tmp" 2>/dev/null; then
    {
      echo "@${REPO_ROOT}/RTK.md"
      echo
      cat "$tmp"
    } >"$CODEX_AGENTS"
  else
    mv "$tmp" "$CODEX_AGENTS"
  fi
  rm -f "$tmp"
fi

echo "✓ [setup-rtk] done — Codex uses ${REPO_ROOT}/RTK.md (project root)"
echo "  verify: test -f RTK.md && head -1 AGENTS.md && rtk init --show && rtk gain"
rtk init --show 2>&1 || true
