---
title: Review and Closeout
type: agent-workflow
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - .agents/skills/neotool-review/SKILL.md
  - .agents/skills/neotool-close/SKILL.md
  - .agents/skills/neotool-retro/SKILL.md
  - .agents/skills/neotool-qa-e2e-branch-validation/SKILL.md
load_when:
  - reviewing a PR or branch against an issue
  - closing a delivery and updating durable memory
supersedes:
  - docs/94-validation/
  - docs/08-workflows/
schema: docs-metadata-v1
---

# Review and Closeout

Purpose: define review gates and closeout responsibilities after implementation.

## When to load

Load after implementation when preparing merge or formal close.

## Inputs

- `neotool-review` for structured code review tied to an issue.
- `neotool-close` for delivery close, durable updates, optional retro invocation.
- `neotool-qa-e2e-branch-validation` when branch E2E coverage is requested.
- `neotool-retro` when process learning is useful (including when invoked by close).

## Rules

1. Reviews need an issue number and must check requirements + project conventions.
2. Prefer mechanical checks (tests, typecheck, lint) appropriate to the changed layers.
3. Closeout updates durable engineering memory in the places the close skill specifies; do not invent parallel feature corpora under `docs/`.
4. Do not run retro automatically after every tiny task outside closeout guidance.
5. E2E validation maps branch diffs to `web/tests/e2e` when that skill is invoked.

## Validation

- Review findings are actionable and issue-grounded.
- Close skill completion checks pass.
- Spec/docs updates happen only where the governing skill allows (this migration must not edit `spec/`).

## Related

- [Issue delivery](issue-delivery-flow.md)
- [Testing strategy](../02-development/testing-strategy.md)
- [Docs README](../README.md)
