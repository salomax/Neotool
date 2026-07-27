#!/usr/bin/env bash
# =============================================================================
# setup-agent-homes.sh — Seed container-local ~/.claude and ~/.codex from
# portable templates. Called from postCreateCommand before setup-rtk/semble.
# Idempotent: never overwrites existing files (preserves OAuth / local edits).
# =============================================================================

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATES="$REPO_ROOT/.devcontainer/agent-home"
SKILLS_SRC="$REPO_ROOT/.agents/skills"

seed_file() {
  local src="$1"
  local dest="$2"
  if [ -f "$dest" ]; then
    echo "  ~ ${dest#$HOME/} (já existe — mantido)"
  elif [ -f "$src" ]; then
    mkdir -p "$(dirname "$dest")"
    cp "$src" "$dest"
    echo "  ✓ ${dest#$HOME/}"
  fi
}

echo "▶ [setup-agent-homes] Seeding container-local agent homes..."

mkdir -p "$HOME/.claude" "$HOME/.codex/skills"

seed_file "$TEMPLATES/claude/settings.json" "$HOME/.claude/settings.json"
seed_file "$TEMPLATES/codex/config.toml" "$HOME/.codex/config.toml"

# Codex skills → workspace .agents/skills (agentskills.io source of truth).
# Claude discovers skills via the committed symlink .claude/skills -> ../.agents/skills.
if [ -d "$SKILLS_SRC" ]; then
  echo "  ▶ linking Codex skills → ${SKILLS_SRC}"
  for skill_dir in "$SKILLS_SRC"/neotool-*; do
    [ -d "$skill_dir" ] || continue
    name="$(basename "$skill_dir")"
    link="$HOME/.codex/skills/$name"
    if [ -L "$link" ] || [ -e "$link" ]; then
      rm -rf "$link"
    fi
    ln -s "$skill_dir" "$link"
    echo "  ✓ ~/.codex/skills/$name"
  done
else
  echo "  ~ skills source missing: $SKILLS_SRC"
fi

echo "✓ [setup-agent-homes] done — OAuth still required: claude login && codex login"
echo "  (re-login after --recreate; stop/up without recreate keeps container FS)"
