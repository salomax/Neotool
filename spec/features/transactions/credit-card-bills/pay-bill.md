---
id: feature.transactions.credit-card-bills.pay-bill
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/CreditCardBillService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_22__add_bill_payment_transaction_type.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/CreditCardBillLifecycleIT.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/TransactionBillPaymentResolverIT.kt
    - web/tests/e2e/personal-finance/credit-card-bills.e2e.spec.ts
  frontend:
    - web/src/app/personal-finance/finance-transactions/bill/PayBillDialog.tsx
    - web/src/lib/graphql/operations/banking/mutations.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.credit-card-bills.bill-lifecycle
  - feature.transactions.ledger-transactions.transfers-and-installments
  - feature.transactions.ledger-transactions.create-update-delete
schema: spec-metadata-v1
---

# Credit Card Bills — Pay Bill

## Glossary

- `accountId` — tenant from JWT.
- `billId` — credit card bill being paid.
- `sourceAccountId` — optional bank `financialAccountId` for the cash leg.
- `payBill` payment type — creates `TRANSFER` rows (not `BILL_PAYMENT` enum value).

## Behavior

### Mutation `payBill(billId, amount, sourceAccountId?)`

- Permission: `banking:transaction:create`.
- Loads bill via `requireBillForAccount`; rejects if already `PAID`.
- Payment date = `LocalDate.now()`.
- Captures `totalBeforePayment = bill.totalAmount` before writes.

### Ledger rows created

1. **Optional bank leg** (when `sourceAccountId` present): ownership check on source FA; `create` DEBIT `TRANSFER` on bank, description `"Pagamento fatura (cartão)"`, **no** `billId` / peer yet.
2. **Card leg** (always): CREDIT `TRANSFER` on `bill.financialAccountId`, description `"Pagamento fatura"`, **`billId` set**, `transferPeerTransactionId = bankTxId` (null if no source).
- Peer link is **asymmetric** (card → bank only). Card create triggers `recomputeBill` (CREDIT lowers total).

### Status outcome

- If `amount >= totalBeforePayment` → `markAsPaid` → status `PAID`.
- Else return refreshed bill (partial payment reduces total; status remains date-derived OPEN/CLOSED/OVERDUE).

### Related: `BILL_PAYMENT` type

- Separate ledger type (V0_22) used for manual/import semantics with direction rules (card CREDIT / bank DEBIT).
- `resolveBillForTransaction` **never** assigns bills for `BILL_PAYMENT` or `BALANCE_ADJUSTMENT`.
- `payBill` intentionally uses `TRANSFER` + explicit `billId` so the payment posts into the bill total.

## Invariants

- Cannot pay an already-PAID bill.
- Source account (if any) must belong to same tenant.
- Full payment threshold compares payment amount to pre-payment total, not post-recompute alone.

## Permissions

Backend enforcement: `banking:transaction:create` on GraphQL `payBill`.

UX hints: `PayBillDialog` amount + optional bank account select; calls `PAY_BILL` mutation.

## Data And Contracts

- GraphQL mutation comment matches service (CREDIT TRANSFER + optional DEBIT TRANSFER + PAID when full).
- Bruno: `Pay Credit Card Bill.bru` (omits `referenceMonth` in selection).
- Tests: Lifecycle IT full/partial pay; E2E credit-card-bills pay flows.

## Impact Surface

- Backend: TransactionService.payBill, CreditCardBillService.markAsPaid
- GraphQL + PayBillDialog + Bruno + IT/E2E
- Cross: transfer peer model (Batch 2); bill lifecycle recompute

## Decisions

- Payment posts as TRANSFER into the bill, not as BILL_PAYMENT type.
- Partial payments allowed; only full cover flips to PAID.

## Known Gaps

- No idempotencyKey on `payBill` (unlike some agent ledger mutations).
- Paying without source account creates only card CREDIT (no bank cash movement).
- Deleting payment txs can unstick PAID via recompute — covered in lifecycle comments/tests more than dedicated E2E.

## Legacy Notes

- None used as authority.
