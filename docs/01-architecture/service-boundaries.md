---
title: Service Boundaries
type: architecture
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/settings.gradle.kts
  - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingGraphQLFactory.kt
  - service/kotlin/common/src/main/kotlin/io/github/salomax/neotool/common/security/authorization/PermissionChecker.kt
load_when:
  - placing new domain logic in the correct module/layer
  - reviewing cross-module dependencies
supersedes: []
schema: docs-metadata-v1
---

# Service Boundaries

Purpose: define module ownership and inward layer dependencies for Kotlin services.

## Context

Each Gradle module owns a bounded capability. Shared cross-cutting code lives in `common*` modules. API entrypoints must not invert dependencies into outer layers.

## Authority

- Module list: `service/kotlin/settings.gradle.kts`.
- Current Gradle modules and resolver/service/repository implementations.
- Example subgraph factory: `BankingGraphQLFactory`.
- Shared authz: `PermissionChecker` in common.

## Rules

1. Dependencies point inward: GraphQL/API → Service → Repository → Entity/Domain.
2. Services must not depend on resolvers or HTTP controllers.
3. Keep resolvers thin; put mapping in dedicated mapper/converter types.
4. Enforce authorization in the backend (`PermissionChecker` / security module). Frontend gates are non-authoritative.
5. Prefer service-to-service calls with audience-scoped tokens (ADR-0008) over trusting the network.
6. Do not invent modules absent from `settings.gradle.kts`.
7. Tenant data access is scoped by `accountId` in current banking code; do not revive superseded ADR-0009 table assumptions.

## Related

- [System](system.md)
- [Security](security.md)
- [Backend standards](../02-development/README.md)
- [ADR-0008](../04-decisions/0008-interservice-security.md)
- [ADR-0009 superseded](../04-decisions/0009-resource-ownership-strategy.md)
