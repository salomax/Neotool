---
id: feature.transactions.financial-accounts.account-crud
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/account/FinancialAccountService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/account/FinancialAccountResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_3__create_banking_accounts.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/account/FinancialAccountServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/FinancialAccountResolverIT.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/FinancialAccountInitialBalanceIT.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/FinancialAccountDeleteIntegrationTest.kt
    - web/tests/e2e/personal-finance/financial-accounts.e2e.spec.ts
  frontend:
    - web/src/app/personal-finance/finance-transactions/account/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.financial-accounts.account-model
  - feature.transactions.financial-accounts.issuers-reference
  - feature.transactions.financial-accounts.tenant-ownership-guard
  - feature.transactions.ledger-transactions.create-update-delete
schema: spec-metadata-v1
---

# Financial Accounts — CRUD

## Behavior

### List / get

- `listByAccount(tenant accountId)` / `findById(financialAccountId, accountId)`.
- GraphQL list/get require `banking:account:view`; tenant from `principal.resolveAccountId()`.

### Create

- Requires exactly one of `issuerId` or `issuerName`.
- Name defaults to issuer name / issuerName when omitted; max 255 chars.
- Shape: BANK cannot have cardData; non-BANK cannot have bankData.
- Card masked number must be exactly 4 digits when present; multi-card (CREDIT/PAYMENT) clears masked number/last-4.
- Closing/due days optional but must be 1–31 when set.
- **Initial balance** only for `type=BANK`: non-zero signed amount + date within moving window / not future → creates `BALANCE_ADJUSTMENT` tx via `TransactionService.create` (“Saldo inicial”).
- TODO in code: plan quota on create (not implemented).

### Update

- Loads by `(financialAccountId, tenant accountId)` or 404.
- Cannot change type from BANK↔non-BANK.
- Same issuer exclusivity and shape rules; merges bank/card patches.
- Initial balance update only when type is BANK (create/update/clear BALANCE_ADJUSTMENT + snapshot recompute).

### Delete

- Soft-delete financial account for tenant.
- Blocked if any non-deleted transactions exist (`FINANCIAL_ACCOUNT_HAS_TRANSACTIONS`).
- Blocked if open credit-card bills exist (`FINANCIAL_ACCOUNT_HAS_OPEN_BILLS`).
- Deletes monthly snapshots for the account; soft-deletes bills; then soft-deletes account.

## Invariants

- Mutations always scoped by JWT tenant `accountId`.
- Delete refuses when ledger activity remains (except cleared snapshots/bills path above).

## Permissions

Backend enforcement:

- Create/update/delete GraphQL all use `banking:account:create` (no separate update/delete permission constants).
- View uses `banking:account:view`.

UX hints: web drawers/forms; gates if any are UX-only.

## Data And Contracts

- Mutations: `createFinancialAccount`, `updateFinancialAccount`, `deleteFinancialAccount`.
- Inputs: `CreateFinancialAccountInput`, `UpdateFinancialAccountInput`.
- Bruno: List + Create Financial Account under `Banking/Transactions/` (no Update/Delete Bruno found).

## Impact Surface

- Backend: FinancialAccountService/Resolver, BankingWiringFactory
- GraphQL + ITs + E2E financial-accounts
- Frontend account UI
- Cross: creates BALANCE_ADJUSTMENT via ledger TransactionService

## Decisions

- Soft delete only.
- Update/delete share create permission string.
- Initial balance is a ledger transaction, not a column on financial_accounts.

## Known Gaps

- No Bruno for update/delete financial account.
- Schema comments say bankData “Required when type = BANK” / cardData required for card subtypes; service only enforces mutual exclusion of bank vs card payload, not presence of bankData/cardData.
- Billing quota TODO unenforced.
- INVESTMENT account create/update may be reachable via same API — product routing to investment-portfolio still open.

## Legacy Notes

- None used as authority.
