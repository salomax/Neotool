---
title: ADR-0009 Resource Ownership Strategy
type: adr
status: superseded
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/account/FinancialAccount.kt
  - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/account/FinancialAccountGuard.kt
  - service/kotlin/security/src/main/resources/db/migration/V0_24__backfill_personal_accounts_and_memberships.sql
load_when:
  - understanding why resource_ownership ADR no longer matches code
  - designing tenant scoping for new entities
supersedes:
schema: docs-metadata-v1
---

# ADR-0009: Resource Ownership Strategy

Purpose: record the durable decision and consequences for resource ownership strategy.

## Status

superseded

## Context

Legacy ADR-0009 chose a pure per-service `resource_ownership` table for all ownership edges.

## Decision

**Superseded by current implementation.** Do not implement new features against a mandatory `resource_ownership` table.

Current verified pattern:

- Tenant scope is `accountId` on domain rows.
- Guards such as `FinancialAccountGuard.requireOwnership` check entity id + `accountId`.
- Security migrations reference `resource_ownership` only conditionally (“when the table exists”); no active `CREATE TABLE resource_ownership` was found in current service migrations.

## Consequences

- Treat the legacy pure-table decision as historical.
- Use [ADR-0019](0019-accountid-tenant-boundary.md) as the replacement decision
  record for current tenant scoping.
- If product needs sharing models beyond account membership + `accountId`
  columns, write a new ADR instead of reviving the legacy pure-table model.

## Evidence

- `FinancialAccount` documents ownership as `accountId` (tenant).
- Banking repositories state queries are scoped by `accountId`.
- Only conditional SQL touches `security.resource_ownership` if present.

## Related

- [README](README.md)
- [ADR-0019](0019-accountid-tenant-boundary.md)
- [Security architecture](../01-architecture/security.md)
- 
