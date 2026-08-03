---
id: feature.transactions.ledger-transactions.transfers-and-installments
domain: transactions
status: draft
confidence: partial
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/Transaction.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionInputParsing.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/TransactionRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultTransactionRepository.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_7__create_transactions.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_37__mark_installment_snapshots_dirty.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/TransactionServiceTest.kt
    - web/tests/e2e/personal-finance/transactions-advanced.e2e.spec.ts
  frontend:
    - web/src/app/personal-finance/finance-transactions/transaction/InstallmentProgress.tsx
    - web/src/app/personal-finance/finance-transactions/transaction/TransactionRow.tsx
    - web/src/app/personal-finance/finance-transactions/transaction/TransactionMobileRow.tsx
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.ledger-transactions.transaction-model
  - feature.transactions.ledger-transactions.create-update-delete
schema: spec-metadata-v1
---

# Ledger Transactions — Transfers And Installments

## Glossary

- `accountId` — security tenant from JWT.
- `financialAccountId` — banking financial account for each ledger leg.
- Transfer (PF) — two (or more) ledger rows of type `TRANSFER` linked by `transferPeerTransactionId`, not investment custody transfer.

## Behavior

### Transfers (model + producers)

- Domain rule: transfers are **two rows** linked by `transferPeerTransactionId` (`Transaction.kt` design comments).
- Repository helpers: `findByTransferPeerTransactionId`, `findByTransferPeerTransactionIds` (tenant-scoped).
- **No** GraphQL `createTransfer` / paired-transfer mutation for personal finance. `CreateTransactionInput` / `TransactionInputParsing` do **not** accept `transferPeerTransactionId`.
- Manual GraphQL create may set `type: TRANSFER` as a single-leg classification only (peer link not settable via public create input).
- Internal producers that do set peer links (deps — not full specs here):
  - `TransactionService.payBill`: optional bank DEBIT `TRANSFER` then card CREDIT `TRANSFER` with `transferPeerTransactionId = bankTxId` (asymmetric: bank leg may have null peer).
  - Bill import / statement import paths can create/link transfer peers (imports batch).
  - Investment custodian cash handlers also use peer links (**investment-portfolio** bridge — out of this product surface).
- Soft-delete of one leg does **not** automatically delete or clear the peer (delete is single-id).
- Month summary aggregates `transfersIn` / `transfersOut` separately from income/expense (summaries batch).

### Installments

- Columns `installmentNumber` / `totalInstallments` on `banking.transactions` (CHECK >= 1); partial index when `installmentNumber > 1`.
- Create validation: if either field is set, both required; `1 <= installmentNumber <= totalInstallments` (`validateCreateCommand`).
- GraphQL `CreateTransactionInput` exposes both ints; create persists **one** row with those metadata fields — **no** backend expansion into N monthly rows on manual create.
- Display: web `InstallmentProgress` / table rows show `current/total` when present.
- Import/bill-import may populate installment metadata on rows (imports / bills batches).
- E2E `TX-A-12` probes an `installments: 3` generate-all-months behavior and soft-skips if unsupported; `TX-A-13` treats legs as independent deletes.

## Invariants

- Peer linkage is tenant-owned via each row’s `accountId`.
- Installment metadata is per-row; deleting one installment does not cascade to siblings.
- Investment `transferCustody` UI/mutation is **not** this feature.

## Permissions

Backend enforcement: same as ledger CRUD / pay-bill / import producers (`banking:transaction:*` etc.).

UX hints: installment badge display only; no paired-transfer wizard found under finance-transactions.

## Data And Contracts

- DB: `transfer_peer_transaction_id`, installment columns + indexes (V0_7); installment snapshot dirty fix (V0_37).
- GraphQL: `Transaction.transferPeerTransactionId`, `installmentNumber`, `totalInstallments`; Bruno Get Transaction selects peer + installment fields.
- Not on GraphQL create/update inputs: `transferPeerTransactionId`.

## Impact Surface

- Backend: Transaction model/service/repo; payBill; import/bill-import (dep)
- GraphQL: Transaction type + CreateTransactionInput installment fields
- Frontend: installment progress display
- Tests: installment validation unit tests; advanced E2E exploratory

## Decisions

- Paired transfers are data + internal producers, not a first-class PF GraphQL create API today.
- Installments are metadata (and import-generated rows), not an auto-scheduler on manual create.

## Known Gaps

- No atomic bidirectional link API for manual account-to-account transfers.
- `payBill` peer link is one-directional (card → bank).
- No dedicated transfer unit/IT for PF peer pairing; E2E installment generation expectation exceeds current create API.
- Confidence **partial**: model + validation verified; paired create UX/API incomplete.

## Legacy Notes

- None used as authority.
