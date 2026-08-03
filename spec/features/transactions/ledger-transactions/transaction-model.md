---
id: feature.transactions.ledger-transactions.transaction-model
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/Transaction.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionEnums.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_7__create_transactions.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionServiceTest.kt
  frontend:
    - web/src/app/personal-finance/finance-transactions/
    - web/src/lib/graphql/operations/banking/mutations.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.ledger-transactions.create-update-delete
  - feature.transactions.financial-accounts.account-model
  - feature.transactions.financial-accounts.tenant-ownership-guard
schema: spec-metadata-v1
---

# Ledger Transactions — Transaction Model

## Glossary

- `accountId` — tenant/workspace from security JWT (`current_account`).
- `financialAccountId` — banking financial account the ledger row posts to.
- `Transaction` — personal-finance ledger row in `banking.transactions`, not an investment-portfolio aggregate.

## Behavior

- Domain `Transaction` is the core ledger entry: ownership (`financialAccountId`, `accountId`, `createdByUserId`), optional `belongsToUserId` XOR `belongsToName`, direction via `creditDebitType`, type/status/source, positive `amount` (+ optional `amountBrl` for FX), dates, description/notes/tags, category + `categorySource`, merchant, counterparty fields, transfer peer, optional `investmentTransactionId` cash-leg pointer, recurring link, card/bill/installment fields, OFB/import identity fields, applied-rule audit, provenance `origin`, soft-delete `deletedAt`.
- Design rules in domain comments: amount always positive; balance never stored on the row; `BALANCE_ADJUSTMENT` is neutral; transfers are two rows linked by `transferPeerTransactionId`; installments tracked by `installmentNumber`/`totalInstallments`.
- Types cover OFB-aligned payments, income, expense, and neutral `BALANCE_ADJUSTMENT`. Status: POSTED / PENDING / FUTURE. Sources: MANUAL, OPEN_FINANCE, OFX, CSV, PDF, NFC_E, RECEIPT_OCR.
- GraphQL `Transaction` exposes `financialAccountId` and related fields but **not** tenant `accountId`. Includes federation helpers such as `hasNfceDocument`, `linkedInvestmentAsset`, applied-rule fields.

## Invariants

- Every row belongs to one tenant `accountId` and one `financialAccountId`.
- Direction is `creditDebitType`; amount must be > 0 at create validation.
- Running balance is derived (snapshots / queries), not a column on the transaction.
- Investment cash-leg link (`investmentTransactionId`) is a bridge pointer to investment-portfolio — not PF portfolio CRUD.

## Permissions

Backend enforcement: view/mutate via `banking:transaction:*` at GraphQL (see create-update-delete). Ownership via tenant-scoped repository + financial-account guard.

UX hints: personal-finance transaction UI; not enforcement.

## Data And Contracts

- DB: `banking.transactions` (V0_7 + later alters for bill payment type, provider soft-delete uniqueness, etc.).
- GraphQL `type Transaction` and enums `CreditDebitType`, `TransactionType`, `TransactionStatus`, `TransactionSource`, `CategorySource`.

## Impact Surface

- Backend: Transaction domain + TransactionService
- GraphQL: banking schema Transaction
- Database: V0_7(+)
- Tests: TransactionServiceTest
- Frontend: finance-transactions UI + banking GraphQL ops

## Decisions

- Positive amount + direction enum (not signed ledger amount on PF create).
- Soft delete with provenance origin.
- Tenant id is JWT-scoped storage, not a GraphQL field on Transaction.

## Known Gaps

- Transfers / installments / MCP change-log / full idempotency semantics deferred to later Batch 2 specs.
- Open Finance sync product depth not mapped in Batch 1.
- `investmentTransactionId` / `linkedInvestmentAsset` are bridge fields — detail lives in investment-portfolio.

## Legacy Notes

- None used as authority.
