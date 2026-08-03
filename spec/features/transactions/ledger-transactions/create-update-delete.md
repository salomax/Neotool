---
id: feature.transactions.ledger-transactions.create-update-delete
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/account/FinancialAccountGuard.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/shared/DateWindowPolicy.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/shared/BankingPermissions.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_7__create_transactions.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionServiceTest.kt
    - web/tests/e2e/personal-finance/transactions.e2e.spec.ts
    - web/tests/e2e/personal-finance/transactions-advanced.e2e.spec.ts
  frontend:
    - web/src/lib/graphql/operations/banking/mutations.ts
    - web/src/app/personal-finance/finance-transactions/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.ledger-transactions.transaction-model
  - feature.transactions.financial-accounts.tenant-ownership-guard
  - feature.transactions.financial-accounts.account-crud
schema: spec-metadata-v1
---

# Ledger Transactions — Create / Update / Delete

## Glossary

- `accountId` — security tenant from JWT.
- `financialAccountId` — banking account the mutation posts against.

## Behavior

### Create (`createTransactionOutcome` / `create`)

- Requires ownership of `financialAccountId` for tenant `accountId` via `FinancialAccountGuard`.
- Validates amount > 0; installment number/total consistency when either is set.
- Date must be within moving window (`DateWindowPolicy.MAX_PAST_YEARS` = 10 years back); non–`BALANCE_ADJUSTMENT` cannot precede the financial account’s initial-balance date.
- Resolves `belongsToUserId` XOR `belongsToName` (default authenticated user); membership check when user id provided.
- For credit/charge cards, resolves `billId` from command or `CreditCardBillService.resolveBillForTransaction`.
- Manual create may run categorization rule engine unless `skipRuleEngine` or investment cash-leg; returns `CreateTransactionPayload` with `transaction` + `appliedRules`.
- After save: recompute bill (if any) and monthly snapshot chain from tx month.

### Update

- Loads by `(transactionId, accountId)` or `TRANSACTION_NOT_FOUND`.
- `BALANCE_ADJUSTMENT` updates are restricted (amount-focused path; propagation/similar-edit blocked).
- `BILL_PAYMENT` direction rules: CREDIT on card accounts, DEBIT on bank-side.
- Date changes re-validate window / initial-balance constraints; bill + snapshot recompute after update.

### Delete

- Soft-delete by `(id, accountId)` with provenance origin; recomputes bill + snapshots when deleted.
- GraphQL returns `DeleteTransactionResult { deleted, pendingAction }`.

### GraphQL / agent path

- Mutations accept optional `idempotencyKey`; resolver uses `idempotencyService.executeIfAgent` (full idempotency/MCP caps → Batch 2).
- Permissions: create=`banking:transaction:create`, update=`banking:transaction:update`, delete=`banking:transaction:delete`, views=`banking:transaction:view`.

## Invariants

- Tenant always from principal; never client-authoritative `accountId`.
- Cross-tenant financial account or transaction id fails.
- Amount positive; direction separate.
- Soft delete only at this API.

## Permissions

Backend enforcement: BankingWiringFactory permission wrappers + service ownership/date/type validators.

UX hints: web mutations under `web/src/lib/graphql/operations/banking/mutations.ts` do not send `idempotencyKey` (UI path); gates in UI are UX-only.

## Data And Contracts

- Mutations: `createTransaction`, `updateTransaction`, `deleteTransaction`.
- Bruno: Create / Update / Delete / List / Get Transaction under `Banking/Transactions/`.
- Web E2E: `transactions.e2e.spec.ts`, `transactions-advanced.e2e.spec.ts`.

## Impact Surface

- Backend: TransactionService, TransactionResolver, guard, DateWindowPolicy
- GraphQL + Bruno + E2E + finance-transactions UI
- Cross: bill recompute, snapshot chain, rule engine (categorization batch), optional investment cash-leg skip

## Decisions

- Separate create/update/delete permission strings for transactions (unlike financial-account mutations sharing create).
- Initial balance is a special `BALANCE_ADJUSTMENT` row with stricter edit rules.
- Schema comments say “manual transaction”; service soft-deletes any owned row by id (imports included) — comment is aspirational UX wording, not a hard source filter on delete.

## Known Gaps

- Full transfer pairing, installment generation UX, and idempotency/MCP semantics deferred (Batch 2).
- Dedicated cross-tenant negative E2E for transaction mutations may be thin vs unit tests.
- Web client omits `idempotencyKey` (agent/MCP concern).

## Legacy Notes

- None used as authority.
