#!/usr/bin/env bash
# =============================================================================
# setup-semble.sh — Wire Semble MCP for DevPod agents (Claude Code, Cursor, Codex).
# Called from postCreateCommand. Idempotent. Requires `uv` + `semble` on PATH
# (Dockerfile installs pinned versions).
#
# MCP only here — AGENTS.md / conventions docs in the repo carry usage guidance
# so we do not let `semble install --type instructions` rewrite project files.
# =============================================================================

set -euo pipefail

SEMBLE_VERSION="${SEMBLE_VERSION:-0.5.2}"

if ! command -v uv >/dev/null 2>&1; then
  echo "▶ [setup-semble] uv not on PATH — skip (rebuild DevPod image to install)"
  exit 0
fi

if ! command -v semble >/dev/null 2>&1; then
  echo "▶ [setup-semble] semble not on PATH — installing pinned tool"
  uv tool install "semble[mcp]==${SEMBLE_VERSION}" || {
    echo "  ~ failed to install semble; skip"
    exit 0
  }
fi

echo "▶ [setup-semble] $(semble --help >/dev/null && echo ok) — configuring MCP (idempotent)"

# Unattended multi-agent MCP wiring (no interactive prompts)
semble install --agent claude cursor codex --type mcp --yes 2>&1 || {
  echo "  ~ semble install reported an error (continuing)"
}

# Project-scoped Cursor MCP (committed) is authoritative for this repo; ensure
# the pin matches the image when developers open the folder outside DevPod.
PROJECT_MCP="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.cursor/mcp.json"
if [ -f "$PROJECT_MCP" ]; then
  echo "  ~ project MCP config present: ${PROJECT_MCP#$HOME/}"
fi

echo "✓ [setup-semble] done — prefer Semble MCP over grep+read for diagnosis"
echo "  CLI smoke: semble search \"IdempotencyService\" /workspaces/invistus --top-k 3"
