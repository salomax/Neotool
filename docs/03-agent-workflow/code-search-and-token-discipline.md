---
title: Code Search and Token Discipline
type: agent-workflow
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - AGENTS.md
  - RTK.md
  - .agents/skills/neotool-conventions/references/token-discipline.md
  - .agents/skills/neotool-conventions/references/semble-search.md
load_when:
  - searching for unknown code
  - bounding shell and exploration tokens
supersedes:
  - docs/workflows/
schema: docs-metadata-v1
---

# Code Search and Token Discipline

Purpose: enforce Semble-first search and RTK/token discipline for agents.

## When to load

Load before exploratory search or noisy CLI loops.

## Inputs

- `AGENTS.md` sections for RTK, Semble, Graphify, Headroom.
- `RTK.md` for shell prefix rules.
- Token discipline + Semble how-to under neotool-conventions references.

## Rules

1. Before exploratory Grep/Read/find/rg, run Semble search (or MCP equivalent) first.
2. Use `semble find-related` after a known path:line hit instead of browsing siblings blindly.
3. Prefix shell commands with `rtk` when available; use `rtk proxy` only when compression hides needed lines.
4. Prefer narrowest reliable test scope before broad reruns; cap blind reruns per token-discipline.
5. Use Graphify for architecture path questions; do not replace Semble for symbol location.
6. Headroom proxy is optional infra for model traffic savings inside DevPod (`127.0.0.1:8787`).

## Validation

```bash
semble search "relevant question" .
semble find-related path/to/File.kt 42 .
rtk git status
rtk gain
```
- Exploration started with Semble when the path was unknown.
- Shell sessions used RTK when present.

## Related

- [Context contract](context-contract.md)
- [Repo commands](../02-development/repo-commands.md)
- [Local development runbook](../05-runbooks/README.md)
