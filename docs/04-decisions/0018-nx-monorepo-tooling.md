---
title: ADR-0018 Nx Monorepo Tooling
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - package.json
  - nx.json
load_when:
  - changing monorepo task runner/caching
  - debugging affected CI tasks
supersedes:
schema: docs-metadata-v1
---

# ADR-0018: Nx Monorepo Tooling

Purpose: record the durable decision and consequences for nx monorepo tooling.

## Status

accepted

## Context

Duplicate legacy number `0003` also described Nx as the monorepo build system. Kotlin/Micronaut already occupies ADR-0003.

## Decision

Use Nx as the monorepo task runner and caching layer for JS apps and orchestrated checks. This ADR is renumbered to **0018** to eliminate the duplicate `0003` collision. Backend module builds remain Gradle-owned inside `service/kotlin/`.

## Consequences

- Clear numbering: 0003 = Kotlin/Micronaut, 0018 = Nx tooling.
- Root scripts delegate to `nx run` / `nx run-many`.

## Evidence

- `nx` and `@nx/next` / `@nx/gradle` appear in root `package.json`.
- `nx.json` configures cache and plugins.

## Related

- [README](README.md)
- [ADR-0001](0001-monorepo-architecture.md)
- [ADR-0003](0003-kotlin-micronaut-backend.md)
