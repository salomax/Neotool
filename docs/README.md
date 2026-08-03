---
title: Invistus Engineering Docs
type: index
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - docs/index.md
  - AGENTS.md
  - package.json
  - service/kotlin/settings.gradle.kts
load_when:
  - starting engineering work in this repo
  - choosing which docs section to load
  - distinguishing engineering docs from spec/
supersedes:
  - historical docs tree
schema: docs-metadata-v1
---

# Invistus Engineering Docs

Purpose: navigable entry point for durable engineering documentation under `docs/`.

## When to load

Load this file first when you need architecture, development standards, agent workflow,
ADRs, or runbooks.

## Authority

- Current code, config, and tests are source of truth for technical claims.
- `docs/` holds durable engineering guidance only.
- Feature behavior memory belongs outside this corpus (`spec/features` is owned elsewhere).

## Rules

1. Prefer one canonical doc per concept; load only what the task needs.
2. Verify claims against the paths listed in each doc's `Authority` section.
3. Do not copy feature requirements into `docs/`.
4. Do not treat backlog, sprint notes, prompts, or market research as durable docs.
5. Keep durable engineering guidance under the current `docs/` taxonomy.

## Sections

| Section | Use for |
|---|---|
| [01-architecture](01-architecture/README.md) | System shape, boundaries, GraphQL, security, observability, infra |
| [02-development](02-development/README.md) | Build/test/extend standards for backend, web, mobile |
| [03-agent-workflow](03-agent-workflow/README.md) | How agents search, deliver issues, review, and close out |
| [04-decisions](04-decisions/README.md) | Architecture Decision Records |
| [05-runbooks](05-runbooks/README.md) | Operational procedures |

Also see:

- [Full index](index.md)

## Related

- [index.md](index.md)
- [01-architecture/README.md](01-architecture/README.md)
- [02-development/README.md](02-development/README.md)
- [03-agent-workflow/README.md](03-agent-workflow/README.md)
- [04-decisions/README.md](04-decisions/README.md)
- [05-runbooks/README.md](05-runbooks/README.md)
