---
id: feature.transactions.financial-accounts.tenant-ownership-guard
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/account/FinancialAccountGuard.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/account/FinancialAccountService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/account/FinancialAccountResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionResolver.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database: []
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/account/FinancialAccountServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionServiceTest.kt
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.financial-accounts.account-crud
  - feature.transactions.ledger-transactions.create-update-delete
schema: spec-metadata-v1
---

# Financial Accounts — Tenant Ownership Guard

## Glossary

- `accountId` — security tenant/workspace from JWT (`current_account`).
- `financialAccountId` — banking financial account id.

## Behavior

- `FinancialAccountGuard.requireOwnership(financialAccountId, accountId)` loads the financial account for that tenant pair and fails if missing/soft-deleted/wrong tenant.
- Used by ledger `TransactionService` (and related banking paths) before mutating or reading money against a financial account.
- Financial-account CRUD itself scopes via repository methods that take both ids (e.g. find/update/delete by `(financialAccountId, accountId)`).
- GraphQL resolvers take tenant only from authenticated principal (`resolveAccountId()`), never from client input as authoritative tenant.

## Invariants

- Cross-tenant access by guessing `financialAccountId` must fail (404 / domain error path as implemented).
- Client cannot override tenant by sending another `accountId`.

## Permissions

Backend enforcement: guard + repository tenant filters + GraphQL permission annotations.

UX hints: frontend filters lists by session only — not enforcement.

## Data And Contracts

- No dedicated GraphQL type; behavioral cross-cutting rule on account and transaction APIs.

## Impact Surface

- Backend: FinancialAccountGuard, FinancialAccountService, TransactionService, resolvers
- Tests: account/transaction service unit tests covering ownership failures

## Decisions

- Ownership is always `(financialAccountId, tenant accountId)` together.
- Backend is sole enforcement; web must not be treated as a security boundary.

## Known Gaps

- Dedicated cross-tenant negative E2E may be thin (unit/IT coverage stronger).

## Legacy Notes

- None used as authority.
