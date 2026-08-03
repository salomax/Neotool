---
title: ADR-0001 Monorepo Architecture
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - package.json
  - nx.json
  - service/kotlin/settings.gradle.kts
  - web/package.json
  - mobile/package.json
load_when:
  - choosing repository layout
  - adding a new app or service to the repo
supersedes:
schema: docs-metadata-v1
---

# ADR-0001: Monorepo Architecture

Purpose: record the durable decision and consequences for monorepo architecture.

## Status

accepted

## Context

Invistus needs one repository for backend services, web, mobile, workers, contracts, infra, and agent tooling so shared contracts and CI stay aligned.

## Decision

Use a single Nx-powered monorepo. Current top-level layout (verified):

- `web/` — Next.js app
- `mobile/` — Expo app
- `service/kotlin/` — Micronaut modules
- `workers/` — batch/cron workers (including Go)
- `contracts/`, `infra/`, `docs/`, `docs/`, `libs/`

Do not split into multiple product repos for these layers.

## Consequences

- Shared GraphQL contracts and Nx affected checks stay in one place.
- Contributors must learn polyglot tooling in one tree.
- Directory names differ from early NeoTool `apps/` / `services/` sketches; use current paths.

## Evidence

- Root `package.json` scripts: `check:api`, `check:workers`, `check:web`, `check:mobile`, `check:all`.
- `nx.json` with `@nx/next` and Gradle-oriented target defaults.
- `service/kotlin/settings.gradle.kts` includes common, security, assets, banking, mcp, and related modules.

## Related

- [README](README.md)
- [ADR-0018 Nx tooling](0018-nx-monorepo-tooling.md)
- [Architecture system](../01-architecture/README.md)
