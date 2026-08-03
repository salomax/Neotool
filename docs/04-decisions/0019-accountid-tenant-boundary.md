---
title: ADR-0019 AccountId Tenant Boundary
type: adr
status: accepted
updated: 2026-08-03
owners: [platform]
source_of_truth:
  - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/account/FinancialAccount.kt
  - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/account/FinancialAccountGuard.kt
  - service/kotlin/security/src/main/resources/db/migration/V0_24__backfill_personal_accounts_and_memberships.sql
  - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/RecurringPaymentEntityRepository.kt
load_when:
  - designing ownership or tenancy for new entities
  - replacing references to legacy resource_ownership patterns
supersedes:
  - 0009-resource-ownership-strategy.md
schema: docs-metadata-v1
---

# ADR-0019: AccountId Tenant Boundary

Purpose: record the current accepted tenancy boundary used by the repo.

## Status

accepted

## Context

Legacy ADR-0009 described a generic `resource_ownership` strategy. Current code no
longer uses that as the primary ownership model for product entities. The active
pattern is account-scoped tenancy.

## Decision

Use `accountId` as the default tenant boundary for product entities and access
checks.

Implementation rules:

- New tenant-scoped domain rows should carry `accountId`.
- Ownership guards should validate `entity_id + accountId`.
- Membership in `security.accounts` / `security.account_memberships` defines who
  acts within an account.
- `resource_ownership` is historical/conditional only; do not design new
  features around it unless a new explicit ADR introduces that requirement.

## Consequences

- Repository queries and guards stay simple and local to the owning service.
- Service-layer authorization can rely on account membership plus row-level
  `accountId` checks.
- Shared-resource or cross-account collaboration models require a new ADR rather
  than stretching this pattern implicitly.

## Evidence

- `FinancialAccount` documents ownership as `accountId` tenant scope.
- `FinancialAccountGuard.requireOwnership` checks `existsByIdAndAccountId(...)`.
- security migration `V0_24` backfills `accounts` and `account_memberships`.
- banking repositories such as `RecurringPaymentEntityRepository` query by
  `accountId`.

## Related

- [0009-resource-ownership-strategy.md](0009-resource-ownership-strategy.md)
- [README.md](README.md)
- [../01-architecture/security.md](../01-architecture/security.md)
- [../01-architecture/service-boundaries.md](../01-architecture/service-boundaries.md)
