#!/usr/bin/env bash
# =============================================================================
# setup-graphify.sh — Wire Graphify CLI + agent skills in the DevPod.
# Called from postCreateCommand after setup-agent-homes.sh. Idempotent.
# Requires `uv` + `graphify` on PATH (Dockerfile installs pinned graphifyy[mcp]).
# Writes into container-local ~/.claude / ~/.codex / OpenCode skill dirs
# (not host bind mounts).
#
# Skill/CLI wiring only — AGENTS.md carries usage guidance so we do not let
# `graphify install --project` rewrite committed project instruction files.
# Cursor alwaysApply rule is intentionally skipped (conflicts with Semble
# as the default code-locator); use `/graphify` or the CLI when needed.
# =============================================================================

set -euo pipefail

GRAPHIFY_VERSION="${GRAPHIFY_VERSION:-0.9.30}"

if ! command -v uv >/dev/null 2>&1; then
  echo "▶ [setup-graphify] uv not on PATH — skip (rebuild DevPod image to install)"
  exit 0
fi

if ! command -v graphify >/dev/null 2>&1; then
  echo "▶ [setup-graphify] graphify not on PATH — installing pinned tool"
  uv tool install "graphifyy[mcp]==${GRAPHIFY_VERSION}" || {
    echo "  ~ failed to install graphifyy; skip"
    exit 0
  }
fi

echo "▶ [setup-graphify] wiring agent skills (idempotent)"

# Claude Code — ~/.claude/skills/graphify + CLAUDE.md registration
graphify install 2>&1 || {
  echo "  ~ graphify install (claude) reported an error (continuing)"
}

# Codex — skill + AGENTS.md section under ~/.codex (container-local)
graphify install --platform codex 2>&1 || {
  echo "  ~ graphify install (codex) reported an error (continuing)"
}

# OpenCode — ~/.config/opencode/skills/graphify
graphify install --platform opencode 2>&1 || {
  echo "  ~ graphify install (opencode) reported an error (continuing)"
}

echo "✓ [setup-graphify] done — CLI: graphify / skill: /graphify ."
echo "  build graph: /graphify .   |   query: graphify query \"…\" ."
echo "  (Semble remains the default locator; Graphify is for knowledge-graph passes)"
