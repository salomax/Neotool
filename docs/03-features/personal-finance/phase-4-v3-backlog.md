# Phase 4 v3 — Implementation Backlog

Reference doc: `phase-4-v3-installment-display-refactor.md`

---

## Backend

### B1 — PDF Parser: Fix year inference
- File: PDF reader / LLM output processor
- Rule: if parsed month > bill closing month → year = bill year - 1
- Reference: `phase-4-pdf-bill-import.md`

### B2 — PDF Parser: Fix deduplication key
- Change dedup from `(date, description, amount)` to `(financial_account_id, bill_id, normalized_description, amount)`
- Allows same description/amount to appear in multiple bills (e.g. installments 5/6 and 6/6)

### B3 — LLM Schema: Remove `confidence` field
- Remove `confidence` from LLM output contract / schema definition
- Remove from any DTOs, mappers, persistence models that carry it
- Remove `OUTPUT_VALIDATION` check for `confidence`
- If debug logging is desired, log raw LLM output at infrastructure level before validation

### B4 — GraphQL: Add billing month filter to transaction query
- Update transaction list query to accept `year: Int, month: Int` filter params
- Backend applies SQL CASE filter (no new column needed):
  ```sql
  DATE_TRUNC('month',
    CASE WHEN t.installment_number > 1 THEN b.due_date ELSE t.date END
  ) = DATE_TRUNC('month', :targetDate)
  ```
- Requires LEFT JOIN on `credit_card_bills` (alias `b`) in the query
- Reference: existing `monthSummary(year: Int, month: Int)` pattern

### B5 — GraphQL: Expose `displayDate` on Transaction type
- `display_date` is a computed field returned by the API — not stored in DB
- Rule: `installment_number > 1` → `bill.due_date`, else `transaction.date`
- File: `graphql/dto/TransactionDTO.kt` — add `displayDate: Date`
- File: `schema.graphqls` — add `displayDate: Date` to `Transaction` type
- File: `graphql/resolver/TransactionResolver.kt` — compute and map
- Frontend uses this field directly for day-grouping headers

### B6 — Snapshot / MonthSummary: Aggregate using billing month CASE
- File: `domain/transaction/SnapshotComputationService.kt`
- File: `repo/MonthlyBalanceSnapshotEntityRepository.kt` (native SQL)
- Change aggregation filter from `month(date)` to the SQL CASE expression (B4)
- Ensure `totalExpenses` and `totalIncome` reflect the billing month correctly

### B7 — PDF Upload: Password support
- Upload endpoint: accept optional `password` param alongside file
- PDF extraction job: pass password to PDFBox when opening document
- Never persist the password (memory-only during extraction)
- Return error code `PDF_PASSWORD_REQUIRED` if encrypted PDF received without password

---

## Frontend

### F1 — PDF upload UI: Password detection
- Add `pdfjs-dist` dependency
- On file selection: read as `ArrayBuffer`, call `pdfjsLib.getDocument({ data })`
- If `PasswordException` thrown → show password input field
- Send file + password together in upload request

### F2 — Consolidated view: Switch filter to billing month
- Replace current month-of-date filter with `year + month` query params (B4)
- Backend handles the CASE derivation — frontend just passes year/month

### F3 — Consolidated view: Update day-grouping to use `displayDate`
- Group transaction rows by `displayDate` (returned from API — B5)
- Show original `transaction.date` as secondary info on installment rows (e.g. "bought 30 Oct 2025")

### F4 — Credit card view: Filter by bill scope
- Query: transactions where `bill.financial_account_id = card AND month(bill.due_date) = selected month`
- No billing month CASE needed — bill is the natural scope
- Backend implements directly; frontend passes `financialAccountId + year + month`

### F5 — Credit card view: Apply `displayDate` day-grouping
- Same rule as F3 — use `displayDate` from API for group headers
- Regular bill purchases group by purchase date; installments (N>1) group by due date

---

## Definition of Done

- [ ] B1–B2: Import produces correct dates and no cross-bill dedup drops
- [ ] B3: `confidence` field removed; import no longer fails on metadata validation
- [ ] B4: Transaction query accepts year/month filter using SQL CASE (no new DB column)
- [ ] B5: `displayDate` computed field exposed on Transaction GraphQL type
- [ ] B6: MonthSummary totals reflect billing month correctly via SQL CASE
- [ ] B7: Password-protected PDFs can be imported via optional password param
- [ ] F1: Password detection works client-side before upload
- [ ] F2–F5: Consolidated and card views filter and group correctly by the new rules
