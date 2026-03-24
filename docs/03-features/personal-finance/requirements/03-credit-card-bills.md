# Credit Card Bills (Faturas) — Requirements Document

**Service:** Invistus Banking Service
**Module:** Personal Finance — Credit Card Bills
**Document date:** 2026-03-24

---

## 1. Overview

The Credit Card Bills (Faturas) feature allows users to manage the billing cycle of their credit card accounts (`CREDIT_CARD` and `CHARGE_CARD` subtypes). A *bill* (fatura) is a first-class entity that groups all purchases charged within a billing cycle. Users can:

- View a list of all bills for a card account, ordered by closing date (most recent first).
- Navigate between billing cycles using a carousel (Bill mode), as opposed to the calendar-month navigation used for bank and cash accounts.
- See the bill total amount (net outstanding), closing date, due date, status, available credit limit, and a visual utilization bar.
- Pay a bill in full or partially via an explicit payment action, optionally selecting a source bank account for the funds transfer.
- Import credit card statement PDFs for banks that do not offer OFX/CSV export; review extracted transactions before committing them.

The billing cycle is derived from per-account settings (`closingDay`, `dueDay` on `CardData`). Bills are auto-created on the first transaction written for a cycle; they are never manually created by users.

---

## 2. Bill Model

**Table:** `banking.credit_card_bills` (migration `V0_5`)

| Field | Type | Source | Meaning |
|---|---|---|---|
| `id` | UUID (DB-generated) | DB | Primary key |
| `financialAccountId` | UUID FK → `banking.financial_accounts` | Set on creation | The card account this bill belongs to |
| `accountId` | UUID FK → `security.accounts` | Set on creation | Tenant isolation |
| `closingDate` | DATE NOT NULL | Computed from `closingDay` | Last day of the billing cycle; identifies the cycle uniquely within an account |
| `dueDate` | DATE NOT NULL | Computed from `dueDay` relative to `closingDate` | Payment due date |
| `totalAmount` | NUMERIC(18,4) | Recomputed on every mutation | Net outstanding = SUM(DEBIT) − SUM(CREDIT) from non-deleted transactions with this `bill_id` |
| `minimumPayment` | NUMERIC(18,4)? | Manual or Open Finance | Optional minimum payment; not computed from transactions |
| `status` | TEXT NOT NULL | Recomputed on every mutation | See lifecycle below |
| `providerBillId` | TEXT? | Reserved | Future use for Open Finance bill identifier |
| `deletedAt` | TIMESTAMPTZ? | Soft-delete | All queries filter `deleted_at IS NULL` |
| `createdAt` | TIMESTAMPTZ | DB | |
| `updatedAt` | TIMESTAMPTZ | DB | |

**Unique constraint:** `uq_credit_card_bills_account_closing (financial_account_id, closing_date)` — one bill per cycle per account.

**Transaction linkage:** `banking.transactions.bill_id` (UUID FK, nullable) — set for every non-`BALANCE_ADJUSTMENT` transaction on card accounts; null otherwise.

---

## 3. Bill Lifecycle

### Status values

| Status | Condition | UI Color |
|---|---|---|
| `OPEN` | `closingDate >= today` — cycle still accumulating purchases | Green |
| `CLOSED` | `closingDate < today AND dueDate >= today` — awaiting payment | Amber |
| `OVERDUE` | `dueDate < today` AND not PAID | Red |
| `PAID` | Explicitly set after full payment is registered | Blue |

### Status computation

Status is recomputed synchronously inside `DefaultCreditCardBillRepository.recomputeTotal()` after every transaction mutation that touches the bill:

```
if current == PAID AND newTotal <= 0:
    → preserve PAID            // credits fully cover debits; payment stands
else:
    if closingDate >= today → OPEN
    elif dueDate < today    → OVERDUE
    else                    → CLOSED
```

Key invariants:
- `PAID` is **preserved** when `totalAmount <= 0` (net credits equal or exceed debits).
- `PAID` **reverts** to `CLOSED` or `OVERDUE` when `totalAmount > 0` — a payment transaction was deleted.
- `OPEN`, `CLOSED`, `OVERDUE` are **always derived from dates** — they cannot be set manually.

### Status transitions

```
OPEN → CLOSED   (automatic when closingDate passes)
CLOSED → OVERDUE (automatic when dueDate passes)
CLOSED → PAID   (explicit payBill with amount >= totalAmount)
OVERDUE → PAID  (explicit payBill with amount >= totalAmount)
PAID → CLOSED / OVERDUE  (automatic revert when payment transaction deleted and totalAmount > 0)
```

There is no scheduled status sweep — status is always recalculated on demand relative to `LocalDate.now()`.

---

## 4. Cycle Arithmetic

### `computeClosingDate(transactionDate, closingDay)`

```
effectiveDay = min(closingDay, transactionDate.lengthOfMonth())   // month-end clamp

if transactionDate.dayOfMonth <= effectiveDay:
    closingDate = transactionDate.withDayOfMonth(effectiveDay)    // this month's cycle
else:
    nextMonth = transactionDate.plusMonths(1)
    closingDate = nextMonth.withDayOfMonth(min(closingDay, nextMonth.lengthOfMonth()))
```

| Transaction date | `closingDay` | Closing date | Reason |
|---|---|---|---|
| Feb 7 | 7 | Feb 7 | On closing day — belongs to this cycle |
| Feb 8 | 7 | Mar 7 | After closing day — next cycle |
| Jan 31 | 31 | Jan 31 | Exact match |
| Feb 28 | 31 | Feb 28 | Clamped (February has ≤ 29 days) |

### `computeDueDate(closingDate, dueDay)`

```
candidate = closingDate.withDayOfMonth(min(dueDay, closingDate.lengthOfMonth()))

if candidate > closingDate:
    dueDate = candidate              // due date in same month as closing
else:
    nextMonth = closingDate.plusMonths(1)
    dueDate = nextMonth.withDayOfMonth(min(dueDay, nextMonth.lengthOfMonth()))
```

| `closingDay` | `dueDay` | Closing date | Due date | Reason |
|---|---|---|---|---|
| 7 | 15 | Feb 7 | Feb 15 | Feb 15 > Feb 7 → same month |
| 25 | 10 | Mar 25 | Apr 10 | Mar 10 ≤ Mar 25 → next month |
| 28 | 5 | Feb 28 | Mar 5 | Feb 5 ≤ Feb 28 → next month |

Both `closingDay` and `dueDay` must be non-null on the account's `CardData`. If either is missing, `resolveBillForTransaction` returns null and no bill is created.

---

## 5. Business Rules

### 5.1 Which accounts use bills

Only `CREDIT_CARD` and `CHARGE_CARD` subtypes. `PREPAID_CARD`, `VOUCHER_CARD`, `BANK`, `CASH`, and all other subtypes never have `bill_id` set.

### 5.2 BALANCE_ADJUSTMENT exclusion

Transactions with `type = BALANCE_ADJUSTMENT` are **never** assigned to a bill, even on card accounts. Enforced in the service layer, not the DB.

### 5.3 Total amount computation

```sql
total_amount =
    COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'), 0) -
    COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT'), 0)
FROM banking.transactions
WHERE bill_id = :billId
  AND deleted_at IS NULL
```

- DEBIT transactions (purchases) **increase** the outstanding amount.
- CREDIT transactions (refunds, payment credits) **decrease** it.
- `totalAmount = 0` → bill fully covered. `totalAmount < 0` → credits exceed debits (overpayment).

### 5.4 Bill auto-creation

1. `computeClosingDate(transactionDate, account.cardData.closingDay)`
2. `computeDueDate(closingDate, account.cardData.dueDay)`
3. `INSERT INTO credit_card_bills … ON CONFLICT DO NOTHING` (safe for concurrent writes)
4. Re-read by `(financialAccountId, closingDate)` → obtain `id`
5. Set `transaction.billId = bill.id`
6. Call `recomputeTotal(bill.id)` → update `totalAmount` and `status`

### 5.5 Bill recompute on transaction mutation

All recomputes are synchronous within the same request (no background queue).

| Mutation | Bills recomputed |
|---|---|
| Create | Assigned bill |
| Update (date change, same cycle) | Assigned bill |
| Update (date change, different cycle) | Old bill (total decreases) AND new bill (total increases) |
| Delete (soft) | The bill the transaction belonged to |

### 5.6 Consolidated view and double-counting

Card expenses appear in the monthly summary in the month of the **purchase**, not the month of payment. Payment transactions use `type = TRANSFER`, which is excluded from `totalIncome`/`totalExpenses`, preventing double-counting.

---

## 6. Bill Payment Flow

### Mutation

```graphql
payBill(billId: ID!, amount: Float!, sourceAccountId: ID): CreditCardBill!
```

### Backend sequence

1. Load bill; throw if not found for tenant.
2. Reject if `bill.status == PAID`.
3. Capture `totalBeforePayment = bill.totalAmount`.
4. If `sourceAccountId` provided:
   - Validate source account belongs to same tenant.
   - Create `DEBIT TRANSFER` on source account (`description = "Pagamento fatura (cartão)"`).
5. Create `CREDIT TRANSFER` on card account:
   - `billId = billId` (reduces bill total)
   - `description = "Pagamento fatura"`
   - `transferPeerTransactionId = bankTxId` (if bank debit created)
   - `date = LocalDate.now()`
6. `recomputeBill` called automatically by `create()`, reducing `totalAmount`.
7. If `amount >= totalBeforePayment` → `setStatus(PAID)`.

### PAID threshold semantics

- `amount >= totalBeforePayment` → PAID (includes overpayment).
- `amount < totalBeforePayment` → bill stays CLOSED/OVERDUE; further `payBill` calls allowed.

### Payment reversion

If the payment credit transaction is soft-deleted: `recomputeTotal` detects `current == PAID AND newTotal > 0` → reverts to date-based status (CLOSED or OVERDUE).

### Frontend

`PayBillDialog.tsx` pre-fills amount with `bill.totalAmount`. User may change it. Source account dropdown lists all bank accounts. Amount ≤ 0 rejected client-side. "Pay Bill" button hidden when `status == 'PAID'`.

---

## 7. Bill Import (PDF)

Status: **planned** (Phase 4 — not yet implemented).

### Supported format

Digital PDF files (embedded text). Scanned/image-only PDFs are out of scope.
File size limit: ≤ 20 MB.

### Pipeline (async, Kafka-based)

```
PENDING → EXTRACTING → EXTRACTED → LLM_PROCESSING → AWAITING_REVIEW → IMPORTING → DONE
                                                                              ↓
                                                                           FAILED (any stage)
```

1. **Submit** (`submitPdfBillImport` mutation): upload to Asset Service, create `pdf_import_jobs` row, publish Kafka event.
2. **Extract** (Python worker): extract text/tables from PDF with `pdfplumber`; publish result to Kafka.
3. **AI Extraction** (LLM Service — TBD): call LLM with extracted text; validate totals (tolerance ≤ R$ 0.10) and dates; publish `PdfBillExtractionResult`.
4. **User Review** (Frontend): user reviews transactions, corrects mismatches, associates card to financial account by `card_last_four_digits`, confirms.
5. **Confirm** (`confirmPdfBillImport` mutation): dedup by fingerprint (SHA-256), insert transactions with `source = 'PDF'`, recompute affected bills.

### Bill assignment during import

`resolveBillForReviewedCycle()` trusts the `closingDate`/`dueDate` from the PDF directly (bypasses cycle arithmetic). Divergence > 2 days from account settings triggers a warning during review.

### Idempotency

Same `(account_id, file_hash)` with a non-FAILED existing job → returns existing job without reprocessing.

---

## 8. User Scenarios

**UC-01 — View current bill**
User selects a credit card account. UI switches to Bill mode. Carousel defaults to the OPEN bill; falls back to most recent (`bills[0]`). `BillSummaryHeader` shows total, closing date, due date, status, credit utilization bar, and "Pay Bill" button.

**UC-02 — Navigate between bills**
User taps left/right chevron. Left → older cycle. Right → newer cycle. Transaction list updates to show transactions for the selected `billId`.

**UC-03 — Pay a bill in full**
User taps "Pay Bill". Dialog opens with amount pre-filled. User optionally selects source bank account. Submits. Backend creates CREDIT TRANSFER (and DEBIT TRANSFER on bank). Bill becomes `PAID`. "Pay Bill" button disappears.

**UC-04 — Pay a bill partially**
User reduces amount in dialog. Backend creates CREDIT TRANSFER. Bill remains CLOSED/OVERDUE. User can pay the remainder later.

**UC-05 — Bill closes automatically**
At midnight on `closingDate`, the next `recomputeTotal` call transitions bill from OPEN to CLOSED.

**UC-06 — Bill becomes overdue**
At midnight after `dueDate`, the next `recomputeTotal` call transitions bill from CLOSED to OVERDUE. Overdue warning shown in `BillSummaryHeader`.

**UC-07 — Transaction added to card account**
Backend computes `closingDate`/`dueDate`, calls `findOrCreate`, links the transaction, recomputes bill total. Bill auto-created with status OPEN if new.

**UC-08 — Transaction date edited across billing cycles**
Backend resolves new bill, moves `billId`, recomputes both old and new bills.

**UC-09 — Transaction deleted**
Soft-delete recomputes bill. If deleted transaction was a payment credit and bill was PAID, status reverts to CLOSED/OVERDUE.

**UC-10 — Import a credit card bill PDF**
User uploads PDF. Async pipeline: extract → AI → review. User confirms. Transactions committed with `source = 'PDF'`.

**UC-11 — Same PDF submitted twice**
SHA-256 match with non-FAILED job → returns existing job immediately.

**UC-12 — `closingDay = 31` in February**
Transaction Feb 15, `closingDay = 31`. `effectiveDay = min(31, 28) = 28`. Feb 15 ≤ 28 → `closingDate = 2026-02-28`.

**UC-13 — Pay without source account**
Only CREDIT TRANSFER on card created. No DEBIT TRANSFER.

**UC-14 — Attempt to pay an already-paid bill**
Backend rejects: "Bill is already paid." Frontend hides the button preemptively.

**UC-15 — BALANCE_ADJUSTMENT on card account**
`resolveBillForTransaction` returns null. No bill created or updated. `bill_id = NULL` on the transaction.

---

## 9. Validations & Restrictions

### Bill creation
- Only for `CREDIT_CARD` or `CHARGE_CARD` accounts.
- `closingDay` and `dueDay` must both be non-null; otherwise no bill is created.
- `BALANCE_ADJUSTMENT` transactions never receive a bill assignment.

### Payment
- `amount` must be positive (client-side validation).
- Bill must belong to calling user's tenant.
- Bill must not already be `PAID`.
- If `sourceAccountId` provided, it must belong to the same tenant.

### PDF import
- File size ≤ 20 MB; MIME type must be `application/pdf`.
- User review is mandatory before committing.
- Scanned PDFs not supported.
- Duplicate submission detected by `(account_id, file_hash)`.

### Status
- `OPEN`, `CLOSED`, `OVERDUE` cannot be set directly — always computed from dates.
- `PAID` only set via `payBill` when `amount >= totalBeforePayment`.
- `PAID` reverts automatically when `totalAmount > 0` after recompute.

---

## 10. Open Questions / Gaps

1. **Status staleness between mutations** — bill status is only recalculated on transaction mutations. A bill can display stale status (e.g., still `OPEN` after `closingDate`) if no transaction is written after that date. Consider a nightly or on-read recalculation.

2. **`minimumPayment` field** — no defined population path beyond "manual or Open Finance." No UI for editing. Not used in PAID threshold logic.

3. **Payment date always today** — `payBill` uses `LocalDate.now()`. Users cannot specify a historical payment date for reconciliation.

4. **Minimum payment floor not enforced** — no validation that amount ≥ `minimumPayment` even when set.

5. **Overpayment** — `totalAmount < 0` after overpayment is not surfaced distinctly. Consider an overpayment warning.

6. **Empty bill state** — when a card account has no bills at all, the carousel returns null silently. No empty state is shown.

7. **LLM Service for PDF import** — listed as "TBD — planned as separate feature." No implementation timeline.

8. **Multi-card PDFs** — card association at review relies on `card_last_four_digits`. If the column is missing on the account record, auto-association fails silently.

9. **`findBill` default when multiple open cycles** — when `closingDate` is null and multiple bills have `closingDate >= today`, the most recent future bill is returned first (DESC order). This edge case is not explicitly handled.

10. **`providerBillId`** — reserved for Open Finance sync but always null; no sync feature is currently planned.
