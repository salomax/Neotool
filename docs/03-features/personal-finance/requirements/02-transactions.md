# Transactions — Requirements Document

**Service:** Invistus Banking Service
**Module:** Personal Finance — Transactions
**Document date:** 2026-03-24

---

## 1. Overview

Transactions are the sole source of truth for every account balance in Invistus. Balances are never stored as mutable fields; they are derived by summing the transaction ledger at query time (with a monthly-snapshot cache for performance). This design mirrors Open Finance Brasil Phase 2 and the Pluggy aggregator API.

Users can:

- Record income, expenses, transfers, and balance-adjustments manually.
- Categorize and tag each transaction.
- Attribute a transaction to any account member (by user ID or free-form name).
- Edit any previously created transaction, including applying the same changes to other transactions with the same description.
- Soft-delete their own manually entered transactions.
- Pay a credit card bill from a linked bank account.
- Import transactions from OFX files, Nubank CSV exports, and NFC-e QR codes (Phase 2.5).
- View a month-level summary (income, expenses, opening/closing balance) and a category/tag breakdown.
- Filter the transaction list by account, month, category, tag, bill, and direction.
- Scroll through paginated results using Relay-style forward cursor pagination.

---

## 2. Transaction Model

### 2.1 Core entity — `banking.transactions`

Source: `Transaction.kt`, `V0_7__create_transactions.sql`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID v7 PK | Yes | Generated server-side |
| `financial_account_id` | UUID FK → `banking.financial_accounts` | Yes | The account this transaction belongs to |
| `account_id` | UUID | Yes | Tenant (resolved from JWT) |
| `created_by_user_id` | UUID | Yes | Authenticated user at creation time |
| `belongs_to_user_id` | UUID? | Conditional | The member who owns this spend; mutually exclusive with `belongs_to_name` |
| `belongs_to_name` | TEXT? | Conditional | Free-form attribution label; mutually exclusive with `belongs_to_user_id` |
| `credit_debit_type` | `CreditDebitType` | Yes | Direction of flow (`CREDIT` or `DEBIT`) |
| `type` | `TransactionType` | Yes | Transaction type (see 2.2) |
| `status` | `TransactionStatus` | Yes | Default `POSTED` |
| `source` | `TransactionSource` | Yes | Entry channel; always `MANUAL` for user-created |
| `amount` | NUMERIC(18,4) | Yes | Always positive; direction carried by `credit_debit_type` |
| `currency_code` | CHAR(3) | Yes | Default `BRL` |
| `amount_brl` | NUMERIC(18,4)? | No | BRL equivalent for FX transactions; null for BRL |
| `date` | DATE | Yes | Posting date (data de lançamento) |
| `transaction_datetime` | TIMESTAMPTZ? | No | Exact datetime when available (e.g. Open Finance) |
| `description` | TEXT | Yes | User-visible label |
| `description_raw` | TEXT? | No | Original institution text; immutable after OFB sync |
| `notes` | TEXT? | No | Free-form user notes |
| `tags` | TEXT[] (via `banking.transaction_tags`) | No | Free-form, account-scoped |
| `category_id` | UUID FK → `banking.transaction_categories`? | No | Null = uncategorized |
| `category_source` | `CategorySource`? | No | How category was assigned (see 2.5) |
| `merchant_id` | UUID FK → `banking.merchants`? | No | Resolved merchant entity |
| `counterparty_name` | TEXT? | No | PIX/TED recipient or sender name |
| `counterparty_document` | TEXT? | No | CPF/CNPJ |
| `counterparty_bank_code` | CHAR(3)? | No | ISPB / COMPE code |
| `counterparty_account_number` | TEXT? | No | |
| `payment_reason` | TEXT? | No | PIX end-to-end reason |
| `transfer_peer_transaction_id` | UUID? | No | ID of the mirroring transaction for internal transfers |
| `bill_id` | UUID FK → `banking.credit_card_bills`? | No | Set automatically for CREDIT_CARD / CHARGE_CARD accounts |
| `installment_number` | SMALLINT? | No | 1-based; >= 1 |
| `total_installments` | SMALLINT? | No | >= 1 |
| `payee_mcc` | INT? | No | Merchant Category Code |
| `card_identifier` | TEXT? | No | Last 4 digits of card |
| `bill_post_date` | DATE? | No | OFB bill posting date |
| `provider_transaction_id` | TEXT? | No | External ID from institution or OFB |
| `provider_account_id` | TEXT? | No | External account ID |
| `import_fingerprint` | TEXT? | No | SHA-256 for dedup on poor-FITID imports |
| `import_job_id` | UUID FK → `banking.document_import_jobs`? | No | Set for imported transactions |
| `source_data` | JSONB? | No | Original provider/import values at creation; immutable |
| `deleted_at` | TIMESTAMPTZ? | No | Soft delete timestamp |
| `created_at` | TIMESTAMPTZ | Yes | |
| `updated_at` | TIMESTAMPTZ | Yes | |

### 2.2 `TransactionType`

Source: `TransactionEnums.kt`, `V0_7__create_transactions.sql`

| Value | Group | Description |
|---|---|---|
| `PIX` | Payment | Brazilian instant payment |
| `TED` | Payment | Electronic wire transfer |
| `DOC` | Payment | Legacy wire transfer |
| `BOLETO` | Payment | Payment slip |
| `TRANSFER` | Neutral | Internal transfer between own accounts; excluded from income/expense totals |
| `SALARY` | Income | Payroll credit |
| `DEPOSIT` | Income | Bank deposit |
| `CASHBACK` | Income | Cashback reward |
| `INVESTMENT_RETURN` | Income | Return from investment |
| `OTHER_INCOME` | Income | Catch-all |
| `CARD` | Expense | Card purchase |
| `WITHDRAWAL` | Expense | ATM or cash withdrawal |
| `FEE` | Expense | Bank or service fee |
| `INTEREST` | Expense | Interest charge |
| `LOAN_PAYMENT` | Expense | Loan amortization |
| `TAX` | Expense | Tax payment |
| `OTHER_EXPENSE` | Expense | Catch-all (default for manual inline entry) |
| `BALANCE_ADJUSTMENT` | Neutral | Sets opening balance or reconciles ledger; excluded from income/expense totals |

### 2.3 `CreditDebitType`

| Value | Meaning |
|---|---|
| `CREDIT` | Money in (income, incoming transfer, bill payment credit) |
| `DEBIT` | Money out (expense, outgoing transfer, withdrawal) |

### 2.4 `TransactionStatus`

| Value | Balance impact |
|---|---|
| `POSTED` | Included in `closingBalance` and all totals |
| `PENDING` | Included in `closingBalance` and all totals (same as POSTED in snapshots) |
| `FUTURE` | Excluded from `closingBalance`; included only in `projectedClosingBalance` |

### 2.5 `TransactionSource`

| Value | Origin |
|---|---|
| `MANUAL` | Typed by the user via the app |
| `OPEN_FINANCE` | Via Open Finance Brasil API (aggregator) |
| `OFX` | Uploaded OFX statement file |
| `CSV` | Uploaded CSV file (bank-specific parser) |
| `PDF` | Uploaded PDF document |
| `NFC_E` | NFC-e QR code scan via SEFAZ |
| `RECEIPT_OCR` | Receipt photo processed via OCR/AI |

### 2.6 `CategorySource`

| Value | Meaning |
|---|---|
| `MANUAL` | User explicitly selected the category |
| `RULE` | Auto-categorization rule matched the description |
| `IMPORTED` | Category provided by the import source |
| `AI` | Future: AI-assigned |

### 2.7 Tags

Tags are stored in `banking.transaction_tags` (one row per tag per transaction). They are account-scoped (all members share the same tag vocabulary within a tenant). Tags are used for free-form grouping independently of categories.

---

## 3. Business Rules

### 3.1 Amount direction

`amount` is always positive. Direction is expressed exclusively via `credit_debit_type`. (DB CHECK `amount > 0`.)

### 3.2 Balance derivation

Account balances are never stored as mutable fields. The source of truth is always `banking.transactions`. Monthly summaries are cached in `banking.monthly_balance_snapshots` and updated synchronously on every write.

Balance formula per snapshot:
```
closingBalance = openingBalance + SUM(CREDIT where status IN (POSTED, PENDING)) - SUM(DEBIT where status IN (POSTED, PENDING))
totalIncome    = SUM(CREDIT) WHERE type NOT IN (TRANSFER, BALANCE_ADJUSTMENT)
totalExpenses  = SUM(DEBIT)  WHERE type NOT IN (TRANSFER, BALANCE_ADJUSTMENT)
```

`FUTURE`-status transactions are excluded from all balance fields but contribute to `projectedClosingBalance`.

### 3.3 Date window

Transactions must fall within a rolling 5-year past window (`DateWindowPolicy.MAX_PAST_YEARS = 5`). A transaction date before `today - 5 years` is rejected with error `TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW`. Applies to both create and update. The frontend enforces the same window client-side. `BALANCE_ADJUSTMENT` transactions are exempt from the secondary check against the account's `initialBalanceDate`, but still subject to the 5-year window.

### 3.4 Date vs. initial balance date

Non-`BALANCE_ADJUSTMENT` transactions cannot have a `date` earlier than the financial account's `initialBalanceDate`. Attempting this throws `TRANSACTION_BEFORE_INITIAL_BALANCE`.

### 3.5 Soft delete

Delete sets `deleted_at = NOW()`. Deleted transactions are excluded from all balance computations and list queries (all DB indexes are partial on `deleted_at IS NULL`). Only `MANUAL`-source transactions can be deleted. Attempting to delete a non-`MANUAL` transaction throws a domain error.

### 3.6 Internal transfers

A transfer between two accounts is represented as two separate rows, both with `type = TRANSFER`, linked by `transfer_peer_transaction_id`. One row is `CREDIT` on the receiving account; the other is `DEBIT` on the source account. Transfers are excluded from `totalIncome` and `totalExpenses`.

### 3.7 Installments (Parcelamento)

Installment purchases are stored as N independent transaction rows. Each row carries `installment_number` (1-based) and `total_installments`. Both fields must be provided together. Constraints: `installment_number >= 1`, `total_installments >= 1`, `installment_number <= total_installments`. Each installment is assigned to the credit card bill of its respective billing cycle date.

### 3.8 Credit card bill assignment

When a transaction is created or its date changed for a `CREDIT_CARD` or `CHARGE_CARD` account, the system automatically resolves the correct `bill_id` via `CreditCardBillService.resolveBillForTransaction`. The bill is created if it does not yet exist for the computed cycle. `BALANCE_ADJUSTMENT` transactions are never assigned to a bill.

Billing cycle arithmetic (given `closingDay` and `dueDay`):
- `closingDate`: if `day <= closingDay` → closes in the current month; else the next month.
- `dueDate`: first occurrence of `dueDay` after the `closingDate`.

After any transaction write/delete, `CreditCardBillService.recomputeBill` recalculates `total_amount` for all affected bills.

### 3.9 Attribution (belongs-to)

Every transaction is attributed to exactly one party (DB constraint `chk_transactions_belongs_to_exactly_one`):
- `belongs_to_user_id IS NOT NULL AND belongs_to_name IS NULL`, or
- `belongs_to_user_id IS NULL AND belongs_to_name IS NOT NULL`.

On create, if neither is supplied, `belongs_to_user_id` defaults to the authenticated user's ID. A blank `belongs_to_name` is rejected. When `belongs_to_user_id` is provided, the user must be an active member of the tenant account; otherwise `CommonErrorCode.INVALID_INPUT` is thrown.

### 3.10 Category resolution on create

When saved without an explicit `categoryId`, the rule engine auto-assigns based on description and direction. For non-`MANUAL` transactions (imports/sync sources), all applicable rules may contribute. For `MANUAL` transactions, only `system` rules are evaluated during create. If a rule matches, `categorySource = RULE`. Explicit user selection sets `categorySource = MANUAL`. Uncategorized = `categoryId = null, categorySource = null`.

### 3.11 Implicit rule learning on update

When a user manually updates a `MANUAL`-source transaction's `categoryId` or `merchantId`, the rule engine records an implicit rule from description → category/merchant. Failures are logged; they do not fail the update.

### 3.12 Similar-transaction propagation

After editing a non-`MANUAL` transaction's `description`, `categoryId`, or `tags`, the service queries for other non-`MANUAL` transactions in the same account with the same base description and `credit_debit_type`. The user is offered the option to apply the same changes to those transactions and/or create a forward rule for future imports.

Base description = `descriptionRaw?.trim()` if non-empty, else `description`. `supportsFuturePropagation = false` for `MANUAL`-source transactions.

### 3.13 Snapshot recomputation chain

After every create, update, or delete, `SnapshotComputationService.recomputeChain` is called from the earliest affected month through the current month. Failed mid-chain recomputes set `is_dirty = true` on affected snapshots. A `@Scheduled PT30M` sweeper heals dirty rows. The `monthSummary` query also heals on demand.

### 3.14 Category hierarchy

Categories support at most two levels (root + one subcategory level). `parent_id` may only reference a root category. System categories (`is_system = true`) cannot be deleted. Category filter in `listTransactions`: a root category filter includes all its subcategories automatically.

### 3.15 Bill payment

`payBill(billId, amount, sourceAccountId)` creates a `CREDIT / TRANSFER` on the card account. If `sourceAccountId` is provided, also creates a `DEBIT / TRANSFER` on that account. Both linked via `transfer_peer_transaction_id`. If `amount >= bill.totalAmount`, bill status becomes `PAID`. Already-`PAID` bills cannot be paid again.

---

## 4. User Scenarios

**US-1 — Create a manual transaction**
1. User opens inline form, selects direction, enters description, amount, date, optionally category and tags.
2. System validates amount > 0, date within 5-year window, date >= account initial balance date.
3. Resolves `belongs_to_user_id` to authenticated user if not specified.
4. Skips transaction-rule auto-categorization because source is `MANUAL`.
5. For CREDIT_CARD/CHARGE_CARD accounts, resolves and auto-creates the bill.
6. Saves; recomputes bill totals; recomputes snapshot chain.

**US-2 — Edit a transaction**
1. User clicks edit; inline form opens pre-filled.
2. User modifies fields. If date changed on a card account, new bill cycle is resolved.
3. If editing a non-`MANUAL` transaction's description/category/tags, service previews similar transactions.
4. `SimilarEditDialog` shown if matches found; user chooses to apply to existing/future/dismiss.
5. Snapshot chain recomputed from `min(old_date, new_date)`.

**US-3 — Delete a transaction**
1. User clicks delete; confirmation dialog shown.
2. On confirm: `deleted_at = NOW()`. Bill total recomputed if had `billId`. Snapshot chain recomputed.
3. Non-`MANUAL` transactions rejected.

**US-4 — Categorize a transaction (single)**
1. User clicks category chip → hierarchical picker → selects category.
2. `updateTransaction` called; `categorySource = MANUAL`. Implicit rule learning fires.

**US-5 — Bulk categorize**
1. User selects multiple rows → "Categorize selected" → picks category.
2. `bulkCategorize` mutation called. `categorySource = MANUAL` for all. No rule learning.

**US-6 — Tag a transaction**
1. User edits transaction; tags input shows account-scoped tags as autocomplete.
2. Tags normalized on save (trimmed, deduplicated). Blank tags rejected.

**US-7 — Create an installment purchase**
1. User creates N transactions manually with `installmentNumber` (1..N) and `totalInstallments = N`.
2. Each installment is an independent ledger entry assigned to its cycle's bill.
3. No automatic replication — each row must be created individually.

**US-8 — Record a transfer between own accounts**
1. User creates `DEBIT / TRANSFER` on source account.
2. User creates `CREDIT / TRANSFER` on destination account with `transferPeerTransactionId`.
3. Neither transaction contributes to `totalIncome` / `totalExpenses`.

**US-9 — Pay a credit card bill**
1. User opens bill view → "Pay bill" → enters amount, optionally selects source bank account.
2. `payBill` mutation: creates `CREDIT / TRANSFER` on card; optionally `DEBIT / TRANSFER` on source.
3. If `amount >= totalAmount`, bill becomes `PAID`.

**US-10 — View monthly summary**
1. User navigates to month/year. `monthSummary` query returns header.
2. Fields: `openingBalance`, `closingBalance`, `totalIncome`, `totalExpenses`, `transfersIn`, `transfersOut`, `projectedClosingBalance`, `isCurrentMonth`, `isFutureMonth`, `expensesByCategory`, `incomeByCategory`, `tagsSummary`.
3. When `financialAccountId = null`, aggregates across all accounts in the tenant.

---

## 5. Import Flow

### 5.1 Supported formats

| Channel | Format | Dedup key |
|---|---|---|
| OFX file upload | `source = OFX` | `provider_transaction_id` (FITID); fallback to `import_fingerprint` |
| Nubank CSV | `source = CSV` | `provider_transaction_id` (Nubank identifier) |
| NFC-e QR code | `source = NFC_E` | `provider_transaction_id` (44-digit SEFAZ chNFe) |
| Receipt photo | `source = RECEIPT_OCR` | `import_fingerprint` (SHA-256) |
| Open Finance / Pluggy | `source = OPEN_FINANCE` | `provider_transaction_id` (OFB transactionId) |

### 5.2 Import lifecycle

```
Upload / scan
  → Create import job (file_hash, status=PENDING)
  → Level 3 check: same file_hash + COMPLETED → return existing job
  → Publish to Kafka: banking.import.file-requested
  → Return ImportResult { id, status=PENDING }

Kafka consumer (async):
  status = PROCESSING
  For each row:
    Level 1: provider_transaction_id + source already in DB → SKIP
    Level 1 fallback: import_fingerprint match → SKIP
    Level 2: same (account, date, amount), different source → HOLD for review
    No conflict → INSERT
  All rows processed:
    duplicate_review_count > 0 → status = REVIEW_REQUIRED
    else → status = COMPLETED
```

NFC-e is processed synchronously (single SEFAZ HTTP call, 5 s timeout).

### 5.3 Duplicate resolution options (Level 2)

| Option | Effect |
|---|---|
| `KEEP_BOTH` | Both transactions committed independently |
| `USE_INCOMING` | Existing soft-deleted; incoming committed; fields merged |
| `KEEP_EXISTING` | Incoming discarded; existing unchanged |

### 5.4 Status notifications

Frontend polls import job status via SSE (`/api/imports/{jobId}/stream`). Falls back to a single polling query if SSE connection fails.

---

## 6. Validations & Restrictions

| Field | Rule | Error |
|---|---|---|
| `amount` | Must be > 0 | `"amount must be > 0"` |
| `date` | Within 5 years of today | `TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW` |
| `date` | >= account `initialBalanceDate` (except `BALANCE_ADJUSTMENT`) | `TRANSACTION_BEFORE_INITIAL_BALANCE` |
| `installment_number` | >= 1, required with `total_installments` | Validation error |
| `total_installments` | >= 1, required with `installment_number` | Validation error |
| `installment_number` | <= `total_installments` | Validation error |
| `belongs_to_user_id` | Must be active tenant member | `INVALID_INPUT` |
| `belongs_to_user_id` + `belongs_to_name` | Mutually exclusive; exactly one required | DB constraint + service |
| `belongs_to_name` | Non-blank after trimming | DB constraint + service |
| `tags` | Each non-blank after trimming | `"tags cannot contain blank values"` |
| `category_id` | System category or same tenant | Service validation |
| `financial_account_id` | Must belong to authenticated tenant | Service validation |
| Delete | Only `MANUAL`-source | Domain error |
| `payBill` | Bill must not already be `PAID` | Domain error |
| `payBill` | Amount must be positive | Service validation |

---

## 7. Open Questions / Gaps

1. **Installment auto-generation** — no bulk-create mutation for N installments from a single input; each row must be created individually.

2. **Status transitions** — no state machine guards; a `POSTED` transaction can be downgraded to `FUTURE` via update. Whether this is intentional is not documented.

3. **Transfer creation UX** — the frontend inline form shows a "Transfer" option (not a real `CreditDebitType`) but the backend expects two separate rows. A dedicated transfer mutation may be needed.

4. **Non-MANUAL delete / hide** — the service rejects deletes of non-`MANUAL` transactions but there is no hide/archive mechanism for OFX- or Open Finance-sourced transactions.

5. **Recurring transactions** — explicitly out of scope. No recurring entry model exists.

6. **Attachment backend implementation** — `AttachmentSection` exists in the frontend but the backend scope is not in the transaction requirements backlog.

7. **`FUTURE`-status projected balance query scope** — aggregation query for FUTURE transactions is not fully documented in the backlog.

8. **CSV format coverage** — currently only Nubank CSV. Other banks deferred to Phase 3. The `TransactionSource` enum uses a single `CSV` value while the import backlog enumerates bank-specific values; reconciliation needed before implementation.
