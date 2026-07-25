@RTK.md

# Agent instructions — Invistus

## RTK (Rust Token Killer) — shell output discipline

See `@RTK.md` (repo root). Prefer RTK-prefixed shell commands when `rtk` is on PATH.

**Claude Code and Cursor** get Tier-1 hooks inside the DevPod (`postCreate` →
`.devcontainer/setup-rtk.sh`). **Codex / ChatGPT** use this project `AGENTS.md` + root
`RTK.md` (best-effort / model compliance) — not `~/.codex/RTK.md`, so agents outside the
sandbox can resolve the file.

```bash
rtk git status
rtk ./gradlew :banking:testUnit --console=plain
rtk proxy <command>   # raw escape hatch
rtk gain
```

## Semble — semantic code search

Prefer [Semble](https://github.com/MinishLab/semble) over exploratory grep+read when locating
code (~98% fewer tokens). DevPod pins `semble[mcp]==0.5.2` and wires the MCP server on
`postCreate` (`.devcontainer/setup-semble.sh`). Cursor also has `.cursor/mcp.json`.

```bash
semble search "how is MCP write idempotency claimed" .
semble find-related path/to/File.kt 42 .
```

Details: `.agents/skills/neotool-conventions/references/semble-search.md`.

## Shared workflow

Token / validation rules: `.agents/skills/neotool-conventions/references/token-discipline.md`.
