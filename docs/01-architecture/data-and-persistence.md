---
title: Data and Persistence
type: architecture
status: active
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/security/src/main/resources/db/migration/V0_1__init.sql
  - service/kotlin/banking/src/main/resources/db/migration/V0_1__create_banking_schema.sql
  - service/kotlin/financialdata/src/main/resources/db/migration/V1_1__create_financial_data_schema.sql
  - infra/postgres
  - infra/pgbouncer
  - service/kotlin/banking/src/main/resources/application.yml
load_when:
  - adding Flyway migrations
  - choosing ID or schema conventions
  - working with Postgres locally
supersedes:
  - docs/04-domain/database-schema-standards.md
  - docs/04-domain/uuid-v7-standard.md
schema: docs-metadata-v1
---

# Data and Persistence

Purpose: record persistence standards verified against current migrations and infra.

## Context

PostgreSQL is the primary datastore. Each service owns its schema and Flyway history. Pooling and local DB images live under `infra/`.

## Authority

- Flyway trees under `service/kotlin/*/src/main/resources/db/migration`.
- `uuidv7()` used in security/financialdata migrations; assets domain documents UUID v7 ids.
- `infra/postgres` and `infra/pgbouncer` directories exist.
- JDBC configured in service `application.yml` files.

## Rules

1. Use PostgreSQL; do not introduce a second primary OLTP store without an ADR.
2. Own schema objects in the service schema (`security`, `banking`, `financial_data`, `assets`, …).
3. Ship schema changes only via Flyway migrations in that service.
4. Prefer UUID v7 primary keys (`DEFAULT uuidv7()` in SQL; UUID v7 in domain models).
5. Scope tenant rows with `accountId` (or equivalent account foreign key) in domain services.
6. Use pgbouncer/postgres infra paths for runtime wiring; do not hardcode unverified hostnames in docs.

## Related

- [System](system.md)
- [ADR-0005](../04-decisions/0005-postgresql-database.md)
- [Backend development](../02-development/README.md)
