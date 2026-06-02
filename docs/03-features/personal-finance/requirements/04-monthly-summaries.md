# Monthly Balance Summaries & Snapshots — Requirements Document

**Service:** Invistus Banking Service
**Module:** Personal Finance — Monthly Summaries & Snapshots
**Document date:** 2026-03-24

---

## 1. Overview

The monthly summary feature provides users with a financial overview header above the transaction list for any selected month. It displays the following core metrics:

| Label (PT-BR) | Field | Description |
|---|---|---|
| Saldo total | `closingBalance` | Full derived account balance at end of month, excluding FUTURE-status transactions |
| Entradas | `totalIncome` | Total credits for the month, excluding transfers and balance adjustments |
| Saídas | `totalExpenses` | Total debits for the month, excluding transfers and balance adjustments |

Users can also see:

- `openingBalance`: the balance carried in from the prior month
- `transfersIn` / `transfersOut`: transfer amounts within the month (single-account view only)
- `projectedClosingBalance`: optional projection including FUTURE-status transactions
- `expensesByCategory` / `incomeByCategory`: per-category breakdown with percentage bars
- `tagsSummary`: per-tag breakdown with percentage bars
- An income/expense volume bar showing the relative split

The UI is implemented in `web/src/app/personal-finance/finance-transactions/summary/MonthSummary.tsx` and the month navigation carousel in `MonthYearCarousel.tsx` in the same directory.

The summary is fetched independently from the transaction list (parallel requests, separate loading states). A skeleton loader is shown while the query is in-flight. If the query fails and no cached data exists, an error state is shown with a retry button.

---

## 2. Snapshot Model

**Table:** `banking.monthly_balance_snapshots`

**Source files:**
- Domain: `domain/transaction/MonthlyBalanceSnapshot.kt`
- Entity: `model/transaction/MonthlyBalanceSnapshotEntity.kt`
- Repository: `repo/MonthlyBalanceSnapshotEntityRepository.kt`

### Composite Primary Key

`(financial_account_id, year, month)` — one row per financial account per calendar month.

### Fields

| Column | Type | Notes |
|---|---|---|
| `financial_account_id` | UUID (FK) | PK component; references `banking.financial_accounts` |
| `account_id` | UUID (FK) | Tenant isolation |
| `year` | INT NOT NULL | Calendar year, e.g. 2026. PK component |
| `month` | INT NOT NULL, CHECK (BETWEEN 1 AND 12) | 1–12. PK component |
| `opening_balance` | NUMERIC(18,4) NOT NULL | Prior month's `closing_balance`, or 0 at account genesis |
| `closing_balance` | NUMERIC(18,4) NOT NULL | Derived balance at end of month |
| `total_income` | NUMERIC(18,4) NOT NULL DEFAULT 0 | SUM of CREDIT excl. TRANSFER and BALANCE_ADJUSTMENT |
| `total_expenses` | NUMERIC(18,4) NOT NULL DEFAULT 0 | SUM of DEBIT excl. TRANSFER and BALANCE_ADJUSTMENT |
| `is_dirty` | BOOLEAN NOT NULL DEFAULT false | Safety net: true when a partial chain recompute failed |
| `computed_at` | TIMESTAMPTZ | Timestamp of last successful recompute; NULL if never populated |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |

### Dirty Flag

`is_dirty = true` means the snapshot's values may be stale due to a failed mid-chain recompute. It is not a normal operational state — it is a safety net. The read path heals dirty snapshots synchronously before serving data. The sweeper job heals them proactively in the background.

A partial index exists for efficient sweeper queries:
```sql
CREATE INDEX ON banking.monthly_balance_snapshots (financial_account_id, year, month)
  WHERE is_dirty = true;
```

### Storage Scope

- Only past and current months are persisted. Future months are never stored as snapshot rows.
- The table starts empty; rows are created on first access or first transaction write.
- `transfersIn`, `transfersOut`, `projectedClosingBalance`, `expensesByCategory`, `incomeByCategory`, and `tagsSummary` are **not stored** in the snapshot — they are computed on-demand at query time.

---

## 3. Balance Formula

### openingBalance

```
openingBalance(year, month) =
  closingBalance of the snapshot for (year, month - 1),
  OR 0 if no prior snapshot exists (first month in account history)
```

The prior month's snapshot is the nearest clean snapshot strictly before the requested month, resolved by `findLatestCleanBefore`. If no clean prior snapshot exists, the system walks back to the earliest transaction date (via `findEarliestTransactionDate`) and computes forward from there.

### closingBalance

```
closingBalance =
  openingBalance
  + SUM(amount WHERE credit_debit_type = 'CREDIT'
          AND status IN ('POSTED', 'PENDING')
          AND deleted_at IS NULL
          AND date IN month)
  - SUM(amount WHERE credit_debit_type = 'DEBIT'
          AND status IN ('POSTED', 'PENDING')
          AND deleted_at IS NULL
          AND date IN month)
```

FUTURE-status transactions are excluded. TRANSFER and BALANCE_ADJUSTMENT types are included (they affect the real account balance).

Source: `SnapshotComputationService` — `closingBalance = openingBalance + totals.totalCredit - totals.totalDebit`

### projectedClosingBalance

```
projectedClosingBalance =
  closingBalance
  + SUM(amount WHERE credit_debit_type = 'CREDIT' AND status = 'FUTURE' AND deleted_at IS NULL AND date IN month)
  - SUM(amount WHERE credit_debit_type = 'DEBIT'  AND status = 'FUTURE' AND deleted_at IS NULL AND date IN month)
```

`projectedClosingBalance` is `null` when there are no FUTURE transactions in the month (`totalFutureCredit = 0 AND totalFutureDebit = 0`).

### Future Month Balance

For a requested month strictly after the current calendar month:

1. The current month's snapshot `closingBalance` serves as the anchor.
2. For each intermediate month, the balance is projected forward using FUTURE transaction totals.
3. `closingBalance` for a future month equals `openingBalance` (no real transactions).
4. `projectedClosingBalance = openingBalance + totalFutureCredit - totalFutureDebit` for the requested month.
5. No snapshot rows are persisted for future months.

---

## 4. Transaction Inclusion Rules

### Status rules

| Status | closingBalance / openingBalance | totalIncome / totalExpenses | projectedClosingBalance |
|---|---|---|---|
| POSTED | Included | Included | Not included (already in closing) |
| PENDING | Included | Included | Not included (already in closing) |
| FUTURE | Excluded | Excluded | Included |

Soft-deleted transactions (`deleted_at IS NOT NULL`) are excluded from all calculations.

### Type rules for totalIncome / totalExpenses

| Type | Included in totalIncome / totalExpenses |
|---|---|
| Any type except TRANSFER and BALANCE_ADJUSTMENT | Yes |
| TRANSFER | No |
| BALANCE_ADJUSTMENT | No |

**Rationale:** Transfers are reallocations between the user's own accounts — including them would double-count in a consolidated view. BALANCE_ADJUSTMENT is a reconciliation entry with no cash-flow meaning. Both types still affect `closingBalance` because they change the real account balance.

### Type rules for closingBalance

All types (including TRANSFER and BALANCE_ADJUSTMENT) are included in the net credit/debit used to compute `closingBalance`.

---

## 5. Summary Breakdown

All breakdown fields are computed on-demand at query time. They are not stored in the snapshot table.

### expensesByCategory

- Aggregates DEBIT transactions for the month grouped by `category_id`.
- Filters: `status IN ('POSTED', 'PENDING')`, `deleted_at IS NULL`, `applicableTo = EXPENSE`.
- Transactions with no category are grouped into a single "uncategorized" row (`uncategorized = true`, `category = null`).
- Each `CategoryTotal` carries: `category` (id, name, icon, color, parentId), `uncategorized` flag, `totalAmount`, `transactionCount`, `percentage` (share of total, rounded to 2 decimal places).

### incomeByCategory

- Same structure as `expensesByCategory` but aggregates CREDIT transactions with `applicableTo = INCOME`.

### tagsSummary

- Aggregates all tagged transactions for the month grouped by tag string.
- Filters: `status IN ('POSTED', 'PENDING')`, `deleted_at IS NULL`. No `applicableTo` filter.
- Each `TagTotal` carries: `tag`, `totalAmount`, `transactionCount`, `percentage`.

### UI display rules

- Top 5 categorized items shown inline; "See All" opens a full-list drawer.
- Uncategorized is always shown after the top 5 categorized items.
- Category rows and tag rows are clickable to filter the transaction table.
- When a filter is active, non-matching rows are rendered at 55% opacity with grayscale.
- The income (Entradas) and expense (Saídas) metric rows are clickable to filter by credit/debit type.
- A proportional income/expense volume bar is shown when `totalIncome + totalExpenses > 0`.

---

## 6. Write Flow

Snapshots are recomputed **synchronously and eagerly** on every transaction mutation.

### Affected month determination

| Mutation | Affected month |
|---|---|
| Create transaction | `month(input.date)` |
| Update transaction (date unchanged) | `month(transaction.date)` |
| Update transaction (date changed) | `min(old_date, new_date)` |
| Delete transaction | `month(transaction.date)` |
| Create transfer (two legs) | `min(debit_leg.date, credit_leg.date)` — both accounts recomputed independently |

### Chain recompute

After determining the affected month, `SnapshotComputationService.recomputeChain` is called:

1. Find the `openingBalance` for the affected month — use `closingBalance` of the nearest clean snapshot strictly before that month; fall back to `BigDecimal.ZERO` if none.
2. Walk forward month by month from the affected month to the current calendar month.
3. For each month: run an aggregate query over `banking.transactions`, upsert the result (`INSERT ... ON CONFLICT DO UPDATE`).
4. Each month's `closingBalance` becomes the next month's `openingBalance`.

### Error handling

If an exception is thrown mid-chain:
- `markDirtyFromMonth` bulk-sets `is_dirty = true` for all snapshots from the failure point onward.
- The transaction mutation has already committed and is not rolled back.
- The mutation returns success to the client; dirty snapshots are healed on the next read or by the sweeper.

---

## 7. Healing & Sweeper

### Dirty flag mechanism

`markDirtyFromMonth` executes:
```sql
UPDATE banking.monthly_balance_snapshots
   SET is_dirty = true, updated_at = NOW()
 WHERE financial_account_id = :financialAccountId
   AND (year > :year OR (year = :year AND month >= :month))
```

### On-demand healing at read time

`MonthSummaryQueryService.resolveSnapshot` heals before serving data:

1. No snapshot row exists → determine earliest recompute start, call `recomputeChain`, reload.
2. Snapshot exists, `isDirty = true` → same recompute-and-reload path.
3. Snapshot exists, `isDirty = false` → use cached values directly (O(1) PK lookup).

This guarantees no stale data is ever served.

### Sweeper job

**Class:** `MonthlySnapshotSweeperJob`

**Schedule:** Every 30 minutes by default. Configurable via `banking.snapshot.sweeper.fixed-delay` (ISO-8601 duration). Disabled via `banking.snapshot.sweeper.enabled: false`.

**Algorithm:**
1. Find all accounts with at least one dirty snapshot.
2. For each account: find all dirty rows ordered by `(year ASC, month ASC)`.
3. Call `recomputeChain` from the earliest dirty month.
4. Failed accounts are logged as warnings and do not stop processing of other accounts.

---

## 8. Consolidated View

When `financialAccountId` is `null` in the `monthSummary` query, `getAggregateMonthlySummary` is called.

### Algorithm

1. Find all financial account IDs for the tenant.
2. Call `getMonthSummary` independently for each account (with on-demand healing).
3. Sum all numeric fields: `totalIncome`, `totalExpenses`, `closingBalance`, `openingBalance`, `transfersIn`, `transfersOut`.
4. `projectedClosingBalance` is `null` if no account has a projection; otherwise the sum of all non-null projections.
5. Category and tag breakdowns computed via a separate cross-account SQL query (not summed from per-account results).

### Internal transfer cancellation

Because TRANSFER type is excluded from `totalIncome`/`totalExpenses`, both legs of an internal transfer cancel each other out in the summed `closingBalance` — no double-counting.

---

## 9. User Scenarios

**Scenario 1 — View monthly summary for the current month**
1. User opens the finance transactions page. Current month selected by default.
2. `monthSummary` query fetches `(financialAccountId, currentYear, currentMonth)`.
3. If a clean snapshot exists, returned immediately from cache.
4. Summary header renders: Saldo total, Entradas, Saídas, volume bar, category/tag breakdowns.

**Scenario 2 — Navigate to a past month never previously opened**
1. User navigates to a prior month via the month carousel.
2. No snapshot row exists. Read path resolves nearest clean prior snapshot, runs `recomputeChain`, persists missing rows, returns result.
3. Subsequent navigations to the same month are instant (O(1) PK lookup).

**Scenario 3 — Navigate to a future month**
1. User clicks past the current month.
2. No snapshot is persisted. Service computes projected summary on-the-fly using FUTURE transaction totals.
3. `isFutureMonth = true`. Category and tag breakdowns are empty.
4. `closingBalance` equals `openingBalance`. `projectedClosingBalance` includes FUTURE amounts.

**Scenario 4 — Edit a past transaction and view corrected summary**
1. User edits a February transaction.
2. `TransactionService` calls `recomputeChain` from February through current month.
3. All snapshots from February onward are upserted correctly within the same service request.
4. UI refetches `monthSummary`; correct values returned immediately.

**Scenario 5 — Move a transaction to a different month**
1. User changes a transaction date from February to March.
2. `affected_month = min(Feb, Mar) = Feb`.
3. `recomputeChain` runs from February through current month, correcting both months.

**Scenario 6 — Filter transactions by expense category**
1. User clicks an expense category row in the summary.
2. Transaction table filters to show only transactions in that category.
3. All other category rows are dimmed.

**Scenario 7 — Filter by income type**
1. User clicks the Entradas metric row.
2. Transaction table filters to CREDIT transactions.
3. Saídas row and all expense category rows are dimmed.

**Scenario 8 — Filter transactions by tag**
1. User clicks a tag row.
2. Transaction table filters to transactions with that tag.

**Scenario 9 — Consolidated view across all accounts**
1. User selects "All accounts" (passes `financialAccountId = null`).
2. `getAggregateMonthlySummary` sums all per-account summaries.
3. Internal transfers do not inflate income/expense totals.
4. Category and tag breakdowns reflect all accounts combined.

**Scenario 10 — Back to current month**
1. When viewing a non-current month, a "Back to current month" link appears.
2. Clicking it resets the selection to `(currentMonth, currentYear)`.

---

## 10. Validations & Restrictions

- **Month value:** Must be between 1 and 12 (DB `CHECK` constraint).
- **Future months:** No snapshot rows are created. Guard in `recomputeChain`: returns immediately if `startYearMonth.isAfter(currentYearMonth)`.
- **Snapshot uniqueness:** Enforced by composite PK. All writes use `INSERT ... ON CONFLICT DO UPDATE` (UPSERT) — concurrent writes are safe.
- **Soft-deleted transactions:** Excluded from all calculations.
- **Mutation resilience:** If `recomputeChain` fails, the transaction mutation has already committed. Snapshot failure is isolated via dirty flags.
- **Sweeper can be disabled:** `banking.snapshot.sweeper.enabled: false` disables the background job. On-demand healing remains active.
- **Currency/locale:** Hardcoded to `pt-BR` / `BRL` until user preferences are wired.

---

## 11. Open Questions / Gaps

**1. Consolidated closingBalance not net-worth adjusted.**
The backlog states the consolidated `closingBalance` should be "net worth: debit accounts sum MINUS credit card outstanding." The implementation simply sums all accounts' `closingBalance` values without subtracting credit card liabilities.

**2. No migration file found for `banking.monthly_balance_snapshots`.**
Memory references `V0_11__create_monthly_balance_snapshots.sql` but the actual `V0_11` file is `V0_11__backfill_rule_merchant_links.sql`. Confirm which migration version created this table.

**3. Sweeper frequency discrepancy.**
Backlog design doc specifies 15 minutes; implementation defaults to 30 minutes (`PT30M`). Align to intended frequency.

**4. Category breakdown does not explicitly exclude TRANSFER and BALANCE_ADJUSTMENT.**
If transfers or adjustments are assigned to a category, they may appear in the breakdown. A type exclusion filter should be verified and added if needed.

**5. Tag breakdown has no type filter.**
`getTagBreakdown` call sites pass `null` for `applicableTo`, meaning all transaction types contribute to tag totals. Expected behavior for TRANSFER/BALANCE_ADJUSTMENT tags is not specified.

**6. projectedClosingBalance for consolidated future months is potentially understated.**
`getAggregateMonthlySummary` sums only non-null projected balances. Accounts with no FUTURE transactions contribute nothing, so the consolidated projection excludes their current closing balance. Intended semantics should be clarified.

**7. User preference for currency and locale is hardcoded.**
`MonthSummary.tsx` uses `pt-BR` locale and `BRL` currency as constants (`MONTH_SUMMARY_LOCALE`, `MONTH_SUMMARY_CURRENCY`). No tracking issue found.

**8. No pagination or limit on category/tag breakdowns at the backend.**
The frontend slices to top 5 for inline display, but the repository returns all rows. No backend limit is enforced for performance or API contract reasons.
