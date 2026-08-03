---
title: Agent Context Contract
type: agent-workflow
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - AGENTS.md
  - docs/README.md
  - docs/index.md
  - .agents/skills/neotool-conventions/SKILL.md
load_when:
  - deciding which docs and skills to load
  - avoiding oversized context
supersedes:
schema: docs-metadata-v1
---

# Agent Context Contract

Purpose: define what agents should load for Invistus tasks and what to exclude.

## When to load

Load at session start or when context is ballooning.

## Inputs

- `AGENTS.md` and `RTK.md` for repo operating rules.
- Matching skill under `.agents/skills/` / `.claude/skills/` for the task type.
- One or few `docs/` leaf docs via [index](../index.md).
- Code/config/tests cited by those docs.

## Rules

1. Prefer narrow leaf docs over loading the whole docs tree.
2. Load the narrowest skill that matches the task (`neotool-implement`, `neotool-review`, `neotool-kotlin-service`, …).
3. Do not recreate feature memory inside `docs/`.
4. Keep durable claims tied to code/config/test paths.

## Validation

- Context names concrete skills/docs actually opened.
- No reliance on stale planning/prompt transcripts.
- Task can proceed without loading the entire docs tree.

## Related

- [Code search](code-search-and-token-discipline.md)
- [Issue delivery](issue-delivery-flow.md)
- [Docs index](../index.md)
