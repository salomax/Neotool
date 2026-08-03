---
title: ADR-0005 PostgreSQL Database
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/banking/src/main/resources/application.yml
  - service/kotlin/security/src/main/resources/application.yml
  - infra/postgres
load_when:
  - choosing primary datastore
  - adding schema migrations
supersedes:
schema: docs-metadata-v1
---

# ADR-0005: PostgreSQL Database

Purpose: record the durable decision and consequences for postgresql database.

## Status

accepted

## Context

Domain data requires ACID relational storage with migration discipline across services.

## Decision

Use PostgreSQL as the primary database, Flyway for migrations per owning service schema, and JDBC/Micronaut Data access from Kotlin services.

## Consequences

- Strong consistency and per-service schema ownership.
- Operational dependency on Postgres/pgbouncer infra under `infra/`.

## Evidence

- Service `application.yml` files reference PostgreSQL.
- `infra/postgres/` and `infra/pgbouncer/` exist.
- Flyway SQL migrations live under service `db/migration` trees.

## Related

- [README](README.md)
- [Data architecture](../01-architecture/README.md)
