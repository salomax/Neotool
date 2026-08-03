---
title: Backend Kotlin Standards
type: development
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/settings.gradle.kts
  - service/kotlin/build.gradle.kts
  - scripts/cli/gradlew-dev.sh
  - docs/01-architecture/service-boundaries.md
  - docs/01-architecture/data-and-persistence.md
load_when:
  - implementing Kotlin/Micronaut services
  - adding Flyway migrations or GraphQL resolvers
supersedes: []
schema: docs-metadata-v1
---

# Backend Kotlin Standards

Purpose: short standards for Kotlin backend work that match current modules and layering.

## When to load

Load for any change under `service/kotlin/`.

## Authority

Gradle settings/build files, architecture docs for boundaries/persistence, and `gradlew-dev.sh`.

## Rules

1. Add modules only by updating `settings.gradle.kts` and following existing module layout.
2. Keep dependencies inward: resolver/API → service → repository → domain/entity.
3. Enforce authz in backend code; never rely on UI gates.
4. Own schema via Flyway in the service; use UUID v7 for new primary keys.
5. Scope tenant reads/writes by `accountId` where the domain uses account tenancy.
6. Prefer `gradlew-dev.sh` for local `:module:run` to avoid Gradle lock contention.

## Commands

```bash
cd service/kotlin
./gradlew :banking:test --console=plain
../scripts/cli/gradlew-dev.sh :security:run
```

## Related

- [Service boundaries](../01-architecture/service-boundaries.md)
- [Data and persistence](../01-architecture/data-and-persistence.md)
- [Repo commands](repo-commands.md)
- [ADR-0003](../04-decisions/0003-kotlin-micronaut-backend.md)
