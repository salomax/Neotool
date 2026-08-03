---
title: Architecture Docs
type: index
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - package.json
  - service/kotlin/settings.gradle.kts
  - infra/
load_when:
  - understanding system shape or service boundaries
  - choosing an architecture leaf doc
supersedes:
  - docs/02-architecture/
  - docs/01-overview/
schema: docs-metadata-v1
---

# Architecture Docs

Purpose: index for durable system architecture guidance rebuilt from current repo evidence.

## Authority

- Module and package manifests under the repo root, `service/kotlin/`, `web/`, `mobile/`, and `infra/`.
- Leaf docs cite concrete evidence paths; stale architecture prose is supporting memory only.

## Rules

1. Rebuild claims from code/config/tests before using stale wording.
2. Keep feature behavior out of this section.
3. One concept per leaf file; link instead of restating.

## Documents

| Doc | Purpose |
|---|---|
| [system.md](system.md) | End-to-end system shape |
| [service-boundaries.md](service-boundaries.md) | Module/service boundaries and layer rules |
| [data-and-persistence.md](data-and-persistence.md) | Persistence and schema standards |
| [graphql-and-contracts.md](graphql-and-contracts.md) | GraphQL and API contracts |
| [security.md](security.md) | Security architecture |
| [observability.md](observability.md) | Observability architecture |
| [infrastructure.md](infrastructure.md) | Runtime/infrastructure shape |

## Related

- [../README.md](../README.md)
- [../index.md](../index.md)
- [../02-development/README.md](../02-development/README.md)
- [../04-decisions/README.md](../04-decisions/README.md)
- [../05-runbooks/README.md](../05-runbooks/README.md)
