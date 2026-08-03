---
id: feature.transactions.credit-card-bills.reference-month-and-identity
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/CreditCardBillService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultCreditCardBillRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/CreditCardBill.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_34__add_credit_card_bill_reference_month.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_35__credit_card_bill_reference_month_unique.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_38__credit_card_bill_closing_date_unique_active.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/CreditCardBillServiceTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/integration/CreditCardBillReferenceMonthMigrationIT.kt
    - web/tests/e2e/personal-finance/credit-card-bills.e2e.spec.ts
  frontend:
    - web/src/lib/graphql/operations/banking/queries.ts
    - web/src/app/personal-finance/finance-transactions/bill/
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.credit-card-bills.bill-lifecycle
  - feature.transactions.financial-accounts.account-model
schema: spec-metadata-v1
---

# Credit Card Bills — Reference Month And Identity

## Glossary

- `closingDate` — last day of the charge cycle (from `CardData.closingDay` + tx date).
- `dueDate` — payment due date (from `CardData.dueDay` relative to closing).
- `referenceMonth` — business bill month = **first day of the due month** (`YearMonth.from(dueDate).atDay(1)`).

## Behavior

### Cycle arithmetic (`CreditCardBillService`)

- `computeClosingDate(txDate, closingDay)`: clamp day to month length; if `tx.day <= effectiveClosingDay` → close this month; else → next month (clamped).
- `computeDueDate(closingDate, dueDay)`: candidate same month; if not after closing → push to next month (clamped).
- `computeReferenceMonth(dueDate)` → identity key for UX/import.

### Identity / findOrCreate

- Primary lookup: active bill by `(financialAccountId, accountId, referenceMonth)`.
- If found with different closing/due → `updateCycleMetadata`.
- Else try undelete soft-deleted row with same `closingDate`, else insert OPEN bill.
- Uniqueness (active rows):
  - `(financial_account_id, reference_month)` unique (V0_35).
  - `(financial_account_id, closing_date)` unique active (V0_38; replaced hard UNIQUE from V0_3).

### Reviewed import cycles

- `resolveBillForReviewedCycle(closingDate, dueDate, type)` trusts reviewed dates (does not re-derive from card settings); still computes `referenceMonth` from dueDate. Depth of PDF import → imports Batch B.

### GraphQL query identity

- Canonical: `creditCardBill(financialAccountId, referenceMonth)`.
- Web queries use `referenceMonth`.
- List ordered by reference month descending (product/E2E).

## Invariants

- At most one **active** bill per card per `referenceMonth`.
- At most one **active** bill per card per `closingDate`.
- Reference month is due-month based, not closing-month based (July charges due August → `referenceMonth` August-01).

## Permissions

Same as bill queries: `banking:transaction:view`.

## Data And Contracts

- Migrations V0_34 / V0_35 / V0_38.
- GraphQL field `CreditCardBill.referenceMonth`.

## Impact Surface

- Backend date helpers + findOrCreate
- GraphQL + E2E CB-R-* / CB-H-* / CB-A-* cycle cases
- Frontend bill month selection
- Cross: bill import reconciliation uses same identity (imports Batch B)

## Decisions

- Business identity is `referenceMonth` (due month), with closing date as cycle boundary metadata.
- Soft-deleted bills may be revived by closing date when recreating a cycle.

## Known Gaps / Divergences

- Bruno `Get Credit Card Bill.bru` still queries `creditCardBill(..., closingDate: $closingDate)` — **stale vs schema** (schema expects `referenceMonth`). List/Pay Bruno are closer to current API.
- Schema comments say closing/due “derived from CardData”; reviewed-import path can set closing/due without re-deriving from card days.

## Legacy Notes

- None used as authority.
