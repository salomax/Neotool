---
id: feature.transactions.imports.bill-import-review-and-confirm
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/billimport/BillImportService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/billimport/BillImportCommitPreparer.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/billimport/AssistantBillReviewBuilder.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/billimport/BillImportReview.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/CreditCardBillService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/dedup/ImportFingerprintService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/billimport/BillImportResolver.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_34__add_credit_card_bill_reference_month.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_35__credit_card_bill_reference_month_unique.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/billimport/BillImportServiceTest.kt
    - web/tests/e2e/personal-finance/bill-import.e2e.spec.ts
    - web/tests/e2e/personal-finance/bill-import-reimport.e2e.spec.ts
  frontend:
    - web/src/app/personal-finance/finance-transactions/bill/BillImportDrawer.tsx
    - web/src/lib/graphql/operations/banking/mutations.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.imports.bill-pdf-import-pipeline
  - feature.transactions.imports.pdf-extractor-worker
  - feature.transactions.credit-card-bills.reference-month-and-identity
  - feature.transactions.credit-card-bills.bill-lifecycle
  - feature.transactions.summaries.monthly-balance-snapshots
schema: spec-metadata-v1
---

# Imports — Bill Import Review And Confirm

## Glossary

- `accountId` — tenant on the job and created ledger rows.
- `financialAccountId` — card FA chosen per review card at confirm (may differ from submit).
- `closingDate` / `dueDate` — reviewed cycle dates; `referenceMonth` = first day of **due** month (`CreditCardBillService.computeReferenceMonth`).
- `reviewCardId` — stable id for a card bucket in `reviewPayload` (required on confirm input).

## Behavior

### Review payload (job `AWAITING_REVIEW`)

- Stored on job; GraphQL `BillImportReview`:
  - `reviewMode`: `CARD_BUCKET_MATCHING` (default) or `TX_CARD_ASSIGNMENT` (flat assignment when assistant has last-four pattern but cards lack last-four).
  - Cards: last four, holder, closing/due, totals, txs, match candidates / matched FA.
  - Txs: date, description, amount, credit/debit, installment, `isBillPayment`, optional category hints.
- Banking enriches matches and categorization **suggestions** before UX; user confirm is authoritative for FA + included txs + overrides.

### Confirm (`confirmBillImport`)

- Permission: `banking:transaction:create`.
- Only `CREDIT_CARD_BILL` jobs; status must be `AWAITING_REVIEW` (idempotent no-op return if already `DONE`).
- Sets `IMPORTING` → processes cards → `DONE` on success.
- Re-import: soft-deletes prior txs for same `importJobId` before inserting.

#### Cycle / bill identity

- Effective closing/due from confirm command overrides → review card → statement period / card `dueDay` (`BillImportCommitPreparer`).
- `referenceMonth` derived from effective `dueDate`.
- Bill via `resolveBillForReviewedCycle(closingDate, dueDate)` — trusts reviewed dates (does not re-derive from card settings); still keys identity by `referenceMonth`.

#### Created artifacts

| Artifact | Rule |
|---|---|
| Charges | `TransactionType.CARD`, `source=PDF`, linked `billId`, installment fields, `importJobId` |
| Installments | number/total; date normalized to due/closing cycle; `billPostDate` = purchase date |
| Bill payments (`isBillPayment`) | `TRANSFER` CREDIT on card; **`billId` null by design**; optional DEBIT peer on linked bank FA + `transferPeerTransactionId` |
| Fingerprints | Charges with bill: `computeForBill(FA, referenceMonth, …)`; payments / no bill: date-based `compute(...)`. Duplicate fingerprint → **skip** row silently |
| Rules | Preload on confirm; empty category re-runs rules; manual category may `learnFromManualAssignment` |
| Snapshots | `recomputeChain` per affected FA |
| Bills | `recomputeBill` + soft-delete orphan bills previously affected |

### Partial failure

- `confirmBillImport` is `@Transactional`. On exception: attempts `markFailed` then rethrows. With default TX rollback, **FAILED may not persist** and job can remain `AWAITING_REVIEW` / pre-confirm while GraphQL errors — underspecified by tests.
- Duplicate fingerprints are skips, not job failure.

### UX

- Drawer steps: upload → processing → card-matching (or flat assignment) → tx-review → result/error.
- Confirm sends `reviewCardId` + FA + included txs + optional closing/due overrides.

## Invariants

- Backend owns bill linking, dedup, peers, snapshots; frontend only assembles confirm input.
- Reviewed due date drives `referenceMonth` identity (see credit-card-bills specs).
- Bill-payment transfers are not charges on the bill (`billId` null).

## Permissions

- Confirm: `banking:transaction:create` (same as submit).
- Reading review payload: via `billImportJob` view permission.

## Data And Contracts

- GraphQL: `ConfirmBillImportInput`, `BillImportReview*`, `Duplicate`-style skip is silent (no resolveImportDuplicate for bills).
- Bruno Confirm uses obsolete `confirmPdfBillImport` / `lastFourDigits` instead of `reviewCardId`.
- E2E: `bill-import.e2e.spec.ts`; reimport E2E leans CSV deterministic path more than live PDF worker.

## Impact Surface

- Backend confirm/preparer/review builders + CreditCardBillService + fingerprint + snapshots
- GraphQL + BillImportDrawer + Bruno (stale) + unit/E2E
- Cross: bill lifecycle; Batch 6 fingerprint service shared helpers; no statement resolve-duplicate flow

## Decisions

- Explicit user confirm before ledger writes (unlike statement async auto-commit of non-conflicts).
- Fingerprint keyed by `referenceMonth` for bill charges so re-imports of same cycle dedup correctly.
- Bill payments modeled as transfers, not `BILL_PAYMENT` type / not bill line items.

## Known Gaps

- Confirm failure vs transactional rollback of `FAILED` status — tracked in [#589](https://github.com/invistus/invistus/issues/589) (persist `FAILED` while rolling back ledger writes).
- Bruno confirm request shape diverges from schema.
- Web accepts `COMPLETED` as success alias though enum is `DONE`.

## Legacy Notes

- None used as authority.
