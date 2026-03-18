# Phase 4 v3 — Credit Card Installment Display Refactor

## Problem Statement

The current import and display logic for installment purchases has two conceptual issues:

1. **Wrong year inference**: Dates parsed from PDF bills without an explicit year (e.g. "30 Set") were being assigned the current year, placing transactions incorrectly in the future.
2. **Cross-bill deduplication**: The dedup key was based on `(date, description, amount)` globally, causing subsequent installments from different bills (e.g. 5/6 and 6/6) to be silently ignored on import.
3. **Display date conflict**: Using `transaction.date` (original purchase date) for month filtering caused installments purchased in past months to disappear from the current month's view, even though they belong to the current bill.

---

## Design Decisions

### Transaction date stays as purchase date

`transaction.date` always reflects the original purchase date as printed on the bill. This is consistent with the benchmark (Nubank, Itaú, Bradesco) and gives the user a clear "when did I buy this" reference.

### Billing month — derived at query time, not stored

No new column is added to the transactions table. The billing month is computed via SQL using a LEFT JOIN on `credit_card_bills`, which is already required for other query needs.

Derivation rule based on installment position:

| Condition | Effective month |
|---|---|
| No installment, or `installment_number == 1` | `month(transaction.date)` |
| `installment_number > 1` | `month(bill.due_date)` |

SQL expression:
```sql
DATE_TRUNC('month',
  CASE
    WHEN t.installment_number > 1 THEN b.due_date
    ELSE t.date
  END
) = DATE_TRUNC('month', :targetDate)
```

Rationale: the first installment (or any non-installment purchase) represents the original buying decision — it belongs to the month it was made. Subsequent installments are future cash flow events — they belong to the month they are actually due.

### Deduplication scope is per-bill

Import deduplication key: `(financial_account_id, bill_id, normalized_description, amount)`

Each bill is a separate scope. Importing bill 5/6 and bill 6/6 creates two independent transactions, as they belong to different bills.

### Display grouping in TransactionTable

See dedicated section below.

---

## What Needs to Change

### 1. PDF Parser / Import Layer

**Year inference**
- When parsing a date without an explicit year (e.g. "30 Set"), infer the year from the bill's closing date context.
- Rule: if the parsed month is after the bill's closing month → year = bill year - 1.
- Reference: `phase-4-pdf-bill-import.md`, `phase-4-v2-import-rules.md`

**Deduplication key**
- Change from global `(date, description, amount)` to scoped `(financial_account_id, bill_id, normalized_description, amount)`.
- This allows the same installment description and amount to appear in multiple bills without being dropped.

---

### 2. Transaction Domain & Persistence

**New field: `billing_month`**
- Type: `YearMonth` (stored as year + month columns or a date column)
- Derivation rule:
  - Has `bill_id` → `billing_month = month(bill.due_date)`
  - No `bill_id` → `billing_month = month(transaction.date)`
- Must be populated on create and updated if `bill_id` or `transaction.date` changes.
- DB migration required.

**References**
- `domain/transaction/Transaction.kt`
- `model/transaction/TransactionEntity.kt`
- `repo/TransactionEntityRepository.kt`
- New Flyway migration: `V0_12__add_billing_month_to_transactions.sql`

---

### 3. MonthSummary / Snapshot Aggregation

**Change filter from `transaction.date` to `billing_month`**
- All aggregations in `SnapshotComputationService` that currently filter by month of `date` must switch to filtering by `billing_month`.
- This ensures installment amounts are correctly counted in the month the user actually pays them, not the month they were purchased.
- `totalExpenses` and `totalIncome` in `MonthSummary` must reflect `billing_month`.

**References**
- `domain/transaction/SnapshotComputationService.kt`
- `domain/transaction/MonthSummaryQueryService.kt`
- `repo/MonthlyBalanceSnapshotEntityRepository.kt` (native SQL queries)

---

### 4. GraphQL API

**Expose `billingMonth` on transaction type**
- Add `billingMonth: YearMonth` (or `billingYear: Int` + `billingMonth: Int`) to the `Transaction` GraphQL type.
- Frontend needs this to implement carousel filtering client-side or to pass as a filter argument.

**Transaction query filter**
- The query that feeds the `TransactionTable` should support filtering by `billingMonth` in addition to (or instead of) date range.
- This replaces the current month-of-date filter for credit card accounts.

**References**
- `graphql/dto/TransactionDTO.kt`
- `graphql/resolver/TransactionResolver.kt`
- `schema.graphqls`
- `BankingWiringFactory`

---

### 5. TransactionTable — Frontend UX

#### Display date rule (both views)

Each transaction has a `display_date` used for day-grouping headers:

| Condition | `display_date` |
|---|---|
| No installment, or `installment_number == 1` | `transaction.date` |
| `installment_number > 1` | `bill.due_date` |

This means subsequent installments naturally cluster together under the bill's due date in the day-grouped list.

#### Consolidated view (all accounts)

**Filter:** `billing_month == selected month` (across all accounts)

**Day-grouping:** by `display_date`, chronological descending.

```
Thursday, March 1             ← bill due date (installments group here)
  - Airbnb (5/6) · bought 30 Oct 2025     R$ 119,93
  - Amazon (4/8) · bought 15 Sep 2025     R$  89,00

Sunday, March 22              ← regular transactions by purchase date
  - Supermarket · Checking account        R$ 150,00

Saturday, March 21
  - Salary received                      R$ 5.000,00
```

#### Single credit card view

**Filter:** `bill.financial_account_id = card AND month(bill.due_date) = selected month`
No `billing_month` needed — the bill is the natural scope.

**Day-grouping:** by `display_date`, same rule as above.

```
Thursday, March 1             ← installments due this month
  - Airbnb (5/6) · bought 30 Oct 2025     R$ 119,93

Saturday, February 22         ← regular bill purchases by purchase date
  - Grocery store · Nubank               R$  89,00

Friday, February 21
  - Gas station · Nubank                 R$ 120,00
```

Note: regular purchases may show February dates in the March card view because the billing period spans month boundaries. This is expected and correct.

---

## Summary of New Fields

No new DB columns are required. The billing month is derived at query time via SQL CASE + LEFT JOIN on `credit_card_bills`. All display logic derives from existing fields: `transaction.date`, `installment_number`, `bill.due_date`, and `BillStatus`.

---

### 6. Manual Transactions on Credit Card Accounts (No Bill Import)

When a user manually adds a transaction to a credit card account without importing a PDF bill, the bill assignment follows the same flow as the import pipeline — no special case is needed.

**How it works:**
`TransactionService.create` already calls `CreditCardBillService.findOrCreate` for CREDIT_CARD and CHARGE_CARD accounts. Given the transaction date and the card's `closingDay` / `dueDay`, the service computes the correct bill and auto-creates it if it doesn't exist.

Example: manual expense on Feb 15 on a card with `closingDay=2`, `dueDay=10`:
- `computeClosingDate(Feb 15, 2)` → Mar 02
- `computeDueDate(Mar 02, 10)` → Mar 10
- `billing_month = 2026-03` — appears correctly in the March view

**Fallback — card without cycle configured:**
If the card has no `closingDay` / `dueDay`, no bill is created and `billing_month = month(transaction.date)`. The transaction behaves like a bank account transaction for filtering purposes. This is acceptable as a degraded experience; the user should be prompted to configure the card's billing cycle.

---

### 7. PDF Upload — Password-Protected Files

Some banks issue password-protected PDF bills. The chosen approach is **upfront detection (Option A)**: detect encryption client-side before upload and present a password field immediately, avoiding an async fail-and-retry cycle.

#### Client-side detection

Use **`pdfjs-dist`** (Mozilla PDF.js) — the industry standard for PDF handling in JavaScript/TypeScript. It is well-maintained, handles edge cases, and surfaces a `PasswordException` naturally when a document requires a password.

Detection flow:
1. User selects the PDF file.
2. Read the file as `ArrayBuffer` (no upload yet).
3. Call `pdfjsLib.getDocument({ data })`. If it throws `PasswordException`, the file is encrypted.
4. Show a password input field in the upload UI.
5. User enters the password; it is held in memory only.
6. Upload the file + password together in the same request.

`pdfjs-dist` is also useful if the frontend ever needs to render a PDF preview, so the dependency pays for itself.

#### Backend / job layer

- The upload endpoint receives an optional `password` parameter alongside the file.
- The PDF extraction job passes the password to the PDF library (e.g. PDFBox) when opening the document.
- The password is used only in memory during extraction and is never persisted.
- If the job receives an encrypted PDF without a password (edge case / direct API call), it returns error code `PDF_PASSWORD_REQUIRED` so the caller can prompt and retry.

#### Security constraints

- The password is **never stored** — not in the database, not in logs, not in job payloads.
- Transmit over HTTPS only (already required for the API).

---

### 8. LLM Output Schema — Remove `confidence` Field

The `confidence` field on card/transaction outputs caused a `SCHEMA_VALIDATION_FAILED` error that blocked the entire import, even when the actual transaction data was valid. This reveals a fragility: LLM metadata fields should never block a business-critical flow.

**Decision: remove `confidence` entirely.**

Reasons:
- It is not consumed anywhere downstream (no review queue, no quality filter, no analytics).
- LLM confidence scores are non-deterministic and format-inconsistent — the LLM may return floats, strings ("high"), or values outside [0, 1].
- If debugging is needed, the raw LLM output should be logged separately, not encoded in the schema contract.

**What to change:**
- Remove `confidence` from the LLM output schema / contract definition.
- Remove `confidence` from any DTOs, mappers, or persistence models that carry it.
- Remove the `OUTPUT_VALIDATION` check for `confidence`.
- If raw LLM output logging is desired for debugging, log it at the infrastructure level before validation — independently of the schema contract.

**Future guidance:** any LLM metadata field (confidence, category hints, flags) must be treated as optional and never included in hard validation. Only business-critical fields (`amount`, `date`, `description`) warrant blocking validation.

---

## Summary of New Fields

No new DB columns are required. The billing month is derived at query time via SQL CASE + LEFT JOIN on `credit_card_bills`. All display logic derives from existing fields: `transaction.date`, `installment_number`, `bill.due_date`, and `BillStatus`.

---

## References

- `phase-3-credit-card-bills.md` — bill domain model
- `phase-4-pdf-bill-import.md` — import pipeline
- `phase-2-monthly-summary-snapshots.md` — snapshot aggregation
- [pdfjs-dist on npm](https://www.npmjs.com/package/pdfjs-dist) — client-side PDF detection
