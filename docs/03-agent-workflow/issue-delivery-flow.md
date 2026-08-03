---
title: Issue Delivery Flow
type: agent-workflow
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - .agents/skills/neotool-open-issue/SKILL.md
  - .agents/skills/neotool-solution-design/SKILL.md
  - .agents/skills/neotool-backlog-gen/SKILL.md
  - .agents/skills/neotool-implement/SKILL.md
  - .agents/skills/neotool-review/SKILL.md
  - .agents/skills/neotool-close/SKILL.md
  - .agents/skills/neotool-spec-audit/SKILL.md
load_when:
  - delivering a tracked GitHub issue end-to-end
  - choosing the next skill in the delivery chain
supersedes:
  - docs/08-workflows/
  - docs/workflows/
schema: docs-metadata-v1
---

# Issue Delivery Flow

Purpose: map the current skill-based path from issue to closeout.

## When to load

Load when working an issue-tracked change.

## Inputs

Skills (canonical names under `.agents/skills/`):

1. `neotool-open-issue` — capture the change as a GitHub issue when needed.
2. `neotool-solution-design` — technical plan before coding when solution is undefined.
3. `neotool-backlog-gen` — break an agreed solution into tasks.
4. `neotool-implement` — implement the issue/backlog.
5. `neotool-review` — structured review against issue + conventions.
6. `neotool-close` — closeout, durable memory updates, optional retro.
7. `neotool-spec-audit` — in a fresh session after close, verify the updated `spec/features` entry
   against the issue and diff (faithfulness check, not a rewrite).
8. Supporting: `neotool-qa-e2e-branch-validation`, `neotool-retro`, stack skills (`neotool-kotlin-service`, `neotool-nextjs-app`, `neotool-expo-mobile`).

## Rules

1. Require an issue number for implement/review/close/backlog/solution-design flows that declare it mandatory.
2. Do not skip solution design when the approach is still ambiguous.
3. Implement against the issue and backlog, not against undocumented chat intent alone.
4. Run review before merge-oriented closeout.
5. Use stack skills together with `neotool-conventions` for cross-cutting auth/error/i18n rules.
6. Run `neotool-spec-audit` in a new session after `neotool-close` updates `spec/features`; route
   recurring gaps it finds to `neotool-retro` instead of patching the spec ad hoc.

## Validation

- Issue reference exists for issue-tracked work.
- Skill sequence matches the stage (design → backlog → implement → review → close).
- Completion checks from the active skill are satisfied.

## Related

- [Review and closeout](review-and-closeout.md)
- [Context contract](context-contract.md)
- [Testing](../02-development/testing-strategy.md)
