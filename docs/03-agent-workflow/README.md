---
title: Agent Workflow Docs
type: index
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - AGENTS.md
  - RTK.md
  - .agents/skills/
load_when:
  - operating as an agent in this repo
  - choosing search, delivery, review, or closeout guidance
supersedes:
  - docs/08-workflows/
  - docs/workflows/
schema: docs-metadata-v1
---

# Agent Workflow Docs

Purpose: index for how agents load context, search code, deliver issues, review, and close out.

## Authority

- `AGENTS.md`, `RTK.md`, and current local skills under `.agents/skills/` / `.claude/skills/`.
- Do not preserve outdated prompt lore from stale AI context docs.

## Rules

1. Align workflows with tools actually present (RTK, Semble, Graphify, skills).
2. Keep entry conditions and completion checks explicit.
3. Exclude generic AI-writing advice and historical implementation transcripts.

## Documents

| Doc | Purpose |
|---|---|
| [context-contract.md](context-contract.md) | What to load and when |
| [issue-delivery-flow.md](issue-delivery-flow.md) | Issue-to-delivery flow |
| [code-search-and-token-discipline.md](code-search-and-token-discipline.md) | Semble/RTK/token rules |
| [review-and-closeout.md](review-and-closeout.md) | Review and closeout checks |

## Related

- [../README.md](../README.md)
- [../02-development/README.md](../02-development/README.md)
- [../04-decisions/README.md](../04-decisions/README.md)
