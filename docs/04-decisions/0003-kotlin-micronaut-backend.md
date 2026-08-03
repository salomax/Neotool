---
title: ADR-0003 Kotlin Micronaut Backend
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/settings.gradle.kts
  - service/kotlin/build.gradle.kts
  - service/kotlin/banking
  - service/kotlin/security
load_when:
  - choosing backend language/framework
  - adding a new Kotlin service module
supersedes:
schema: docs-metadata-v1
---

# ADR-0003: Kotlin Micronaut Backend

Purpose: record the durable decision and consequences for kotlin micronaut backend.

## Status

accepted

## Context

Backend services need a cloud-native JVM stack with GraphQL, strong typing, and fast container startup.

## Decision

Use Kotlin + Micronaut + Gradle for backend modules under `service/kotlin/`. Persist with Micronaut Data/JPA patterns and Flyway where the module owns schema.

## Consequences

- One backend stack across security, banking, assets, assistant, mcp, and related modules.
- JVM/Gradle expertise required.
- Nx ADR tooling is separate (ADR-0018); this ADR owns the backend runtime choice only.

## Evidence

- `settings.gradle.kts` includes `:common`, `:security`, `:assets`, `:banking`, `:mcp`, and other modules.
- Module `application.yml` files configure PostgreSQL JDBC.

## Related

- [README](README.md)
- [ADR-0018](0018-nx-monorepo-tooling.md)
- [Development backend](../02-development/README.md)
