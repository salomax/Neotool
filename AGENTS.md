# Agent instructions — Invistus

## RTK (Rust Token Killer) — shell output discipline

This DevPod installs [RTK](https://github.com/rtk-ai/rtk) to compress shell/CLI output before it
enters the model context (typically 60–90% less bash noise).

**Claude Code and Cursor** get Tier-1 hooks (commands are rewritten automatically).
**Codex / ChatGPT** rely on this rules file — always prefer RTK-prefixed shell commands:

```bash
rtk git status
rtk ./gradlew :banking:testUnit --console=plain
rtk gh issue view 474
```

Meta / escape hatch (raw output when the filter hides a needed line):

```bash
rtk proxy <command>
rtk gain
```

Codex savings are **best-effort** (rules-based). Claude Code / Cursor savings are enforced by
hooks once `rtk init` has run in the container (`postCreate` → `.devcontainer/setup-rtk.sh`).

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
