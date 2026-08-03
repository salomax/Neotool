---
id: feature.transactions.credit-card-bills.bill-lifecycle
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/CreditCardBillService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultCreditCardBillRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/CreditCardBillEntityRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/CreditCardBill.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/BillStatus.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_3__create_banking_accounts.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_7__create_transactions.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/CreditCardBillServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/CreditCardBillLifecycleIT.kt
    - web/tests/e2e/personal-finance/credit-card-bills.e2e.spec.ts
  frontend:
    - web/src/app/personal-finance/finance-transactions/bill/
    - web/src/lib/graphql/operations/banking/queries.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.credit-card-bills.reference-month-and-identity
  - feature.transactions.credit-card-bills.pay-bill
  - feature.transactions.financial-accounts.account-model
  - feature.transactions.ledger-transactions.create-update-delete
schema: spec-metadata-v1
---

# Credit Card Bills — Bill Lifecycle

## Glossary

- `accountId` — security tenant owning the bill and card.
- `financialAccountId` — CREDIT_CARD / CHARGE_CARD financial account (not VOUCHER_CARD).
- Bill — one `banking.credit_card_bills` row per billing cycle for that card.

## Behavior

### Scope

- Bill logic applies only when account subtype ∈ `{CREDIT_CARD, CHARGE_CARD}` and `cardData.closingDay` + `dueDay` are set (`CreditCardBillService.CARD_SUBTYPES`).
- Bills are not created for BANK/CASH/VOUCHER etc., nor for types `BALANCE_ADJUSTMENT` / `BILL_PAYMENT` via `resolveBillForTransaction`.

### Create path (via ledger)

- On `TransactionService.create` for a card account: resolve `billId` from command or `resolveBillForTransaction(date, type)` → `findOrCreate` cycle → set on tx → `recomputeBill`.
- On update: may re-resolve bill when date/type changes; recompute old and new bill ids.
- On delete: soft-delete tx then `recomputeBill` for its `billId`.
- Snapshot recompute still runs for the financial account (summaries batch).

### Totals and status

- `totalAmount` = `SUM(DEBIT) - SUM(CREDIT)` of non-deleted txs with that `bill_id` (sync on recompute).
- Status after recompute (`computeStatus`):
  - Keep `PAID` only if still `PAID` **and** `newTotal <= 0`.
  - Else: `OPEN` if `closingDate >= today`; `OVERDUE` if `dueDate < today`; else `CLOSED`.
- Explicit `markAsPaid` sets `PAID` without recomputing total (used by payBill after full cover).
- Deleting a payment that leaves positive net total drops `PAID` back to date-derived status.

### Queries

- `creditCardBills(financialAccountId)` — ownership guard; list non-deleted (ordered by reference month desc in product/tests).
- `creditCardBill(financialAccountId, referenceMonth?)` — by reference month, or first with `closingDate >= today`, else most recent.

### Account delete coupling

- Deleting a financial account is blocked while bills in `OPEN`/`CLOSED` exist (`FINANCIAL_ACCOUNT_HAS_OPEN_BILLS`); remaining bills soft-deleted as part of account delete path (account-crud).

## Invariants

- Every bill belongs to `(financialAccountId, accountId)`.
- Totals always derived from linked ledger rows, not client input.
- Soft-delete supported; orphan soft-delete helper exists (`softDeleteIfOrphan` — used by import paths).

## Permissions

Backend enforcement: list/get `banking:transaction:view`. Lifecycle mutations ride ledger create/update/delete permissions.

UX hints: bill summary header / month picker in finance-transactions; not enforcement.

## Data And Contracts

- DB: `credit_card_bills` (V0_3); txs `bill_id` FK (V0_7).
- GraphQL `CreditCardBill`, `BillStatus`, list/get queries.
- Bruno: List / Get Credit Card Bill, List Transactions By Bill (Get uses stale arg — see divergences in identity spec).

## Impact Surface

- Backend: CreditCardBillService/repo, TransactionService hooks, resolvers
- GraphQL + Lifecycle IT + E2E `credit-card-bills.e2e.spec.ts`
- Frontend bill summary UI
- Cross: pay-bill; reference-month identity; bill PDF import (imports Batch B)

## Decisions

- Bills are materialised cycle rows created lazily on first eligible charge.
- Status is mostly date + total driven; `PAID` is sticky only while net ≤ 0.

## Known Gaps

- `minimumPayment` / `providerBillId` reserved (Open Finance) — not computed in this path.
- TransactionService delete does not call `softDeleteIfOrphan` (orphans cleaned mainly via import).

## Legacy Notes

- None used as authority.
