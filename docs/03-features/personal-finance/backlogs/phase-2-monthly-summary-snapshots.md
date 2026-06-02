---
title: Phase 2 — Monthly Summary & Balance Snapshots
type: feature-spec
category: personal-finance
status: planned
version: 1.0.0
tags: [feature, personal-finance, transactions, balance, monthly-summary, snapshots, performance]
ai_optimized: true
search_keywords: [monthly summary, balance snapshot, entradas, saídas, saldo total, closing balance, opening balance, dirty flag, eager recompute]
related:
  - 03-features/personal-finance/phase-2-transactions.md
  - 03-features/personal-finance/personal-finance-overview.md
last_updated: 2026-03-02
---

# Phase 2 — Monthly Summary & Balance Snapshots

> **Purpose**: Define the monthly summary header (entradas, saídas, saldo total) shown above the transaction list, and the balance snapshot strategy that keeps those values consistent and performant without ever serving stale data.

---

## Overview

Every transaction list view is scoped to a selected month. Above the list, a summary header shows three key figures:

| Label (PT-BR) | Field | Description |
|---|---|---|
| **Entradas** | `totalIncome` | Total credits for the month, excluding transfers and balance adjustments |
| **Saídas** | `totalExpenses` | Total debits for the month, excluding transfers and balance adjustments |
| **Saldo total** | `closingBalance` | Full derived balance as of the last day of the selected month |

The challenge: `closingBalance` requires summing **all transactions since account creation** up to the end of the selected month. This scan grows unboundedly as history accumulates. The solution is a pre-computed monthly snapshot table used as a read cache, updated synchronously on every write.

---

## Problem Statement

### Why naive on-demand computation doesn't scale long-term

```sql
-- Correct but O(n) over all historical transactions:
SELECT SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT')
     - SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT')
FROM banking.transactions
WHERE financial_account_id = $1
  AND date <= '2026-02-28'
  AND deleted_at IS NULL
  AND status IN ('POSTED', 'PENDING');
```

For an account with 3 years of history (~1,000 transactions), this runs in <5 ms with a proper index. At 10 years (~4,000 transactions), still fast. The real concern is consistency: when a user edits a past transaction, the summary shown on screen must reflect the change **immediately**, not after a background job runs.

### Why naive async doesn't work for UX

If snapshot recomputation is async (background job):

```
14:00 — user edits a February transaction
14:01 — user looks at February summary → stale data (snapshot is dirty)
02:00 — nightly background job runs → data corrected
```

This is unacceptable for financial data. The user just made a change and immediately sees wrong numbers.

---

## Industry Research

### How major fintechs and PFM tools handle this

| Tool | Approach |
|---|---|
| **YNAB** | No snapshot. Computes running balance in-memory client-side by walking transactions backward. Works because personal account history is small (<10K rows). |
| **Quicken** | Snapshot via manual user reconciliation. User confirms balance against bank statement, creating a checkpoint. Past reconciled periods are soft-locked. |
| **Modern Treasury** (B2B payments) | Stored balance cache updated synchronously on every write, inside the same DB transaction. Works because ledger entries are immutable — no cascade problem. |
| **Blnk Finance** (open-source ledger) | Snapshot per account + dirty flag. Most recent clean snapshot + delta replay for historical queries. Falls back to full genesis replay if no snapshot exists. |
| **Accounting ERPs** (QuickBooks, SAP, NetSuite) | Hard period close: at month end, terminal balance is stored and the period is locked for edits. New entries require formal period-reopening workflow. Too rigid for PFM. |
| **Nubank** | Uses Datomic (immutable temporal DB). Point-in-time queries (`as-of`) are native — no application-level snapshots needed. Infrastructure-heavy, not appropriate for early-stage PFM. |

### Chosen pattern: checkpoint + eager synchronous recompute

The pattern used by Blnk Finance — adapted for PFM:
- `monthly_balance_snapshots` table stores one row per (account, year, month).
- On any transaction write, affected month and all subsequent months are recomputed **synchronously within the same service request**.
- `is_dirty` flag is a safety net only — used if synchronous recompute fails partway through.
- Category/tag breakdowns are always computed on-demand (no snapshot needed at MVP scale).

This gives zero stale-data window, no async complexity in the read path, and correct data immediately after every edit.

---

## Data Model

### `banking.monthly_balance_snapshots`

Pre-computed monthly summary cache. One row per (financial account, year, month). This is a **read cache** — the transactions table remains the authoritative source of truth.

| Column | Type | Required | Notes |
|---|---|---|---|
| `financial_account_id` | UUID FK → banking.financial_accounts | Yes | PK component |
| `account_id` | UUID FK → security.accounts | Yes | Tenant isolation |
| `year` | INT NOT NULL | Yes | Calendar year, e.g. 2026. PK component |
| `month` | INT NOT NULL CHECK (month BETWEEN 1 AND 12) | Yes | 1–12. PK component |
| `opening_balance` | NUMERIC(18,4) NOT NULL | Yes | Balance at start of month = prior month's `closing_balance`, or 0 if first month |
| `closing_balance` | NUMERIC(18,4) NOT NULL | Yes | Balance at end of month = `opening_balance` + net of all transactions in month |
| `total_income` | NUMERIC(18,4) NOT NULL DEFAULT 0 | Yes | SUM of CREDIT excl. TRANSFER and BALANCE_ADJUSTMENT |
| `total_expenses` | NUMERIC(18,4) NOT NULL DEFAULT 0 | Yes | SUM of DEBIT excl. TRANSFER and BALANCE_ADJUSTMENT |
| `is_dirty` | BOOLEAN NOT NULL DEFAULT false | Yes | Safety net: true = needs recompute due to a partial failure during write |
| `computed_at` | TIMESTAMPTZ | No | Timestamp of last successful recompute; NULL = never populated |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | Yes | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT NOW() | Yes | |

**Primary Key:** `(financial_account_id, year, month)`

**Indexes:**
```sql
-- Already covered by PK. Add a partial index for dirty sweeper:
CREATE INDEX ON banking.monthly_balance_snapshots (financial_account_id, year, month)
  WHERE is_dirty = true;
```

---

## Business Rules

### 1. Definitions: totalIncome, totalExpenses, closingBalance

```
totalIncome =
  SUM(amount)
  WHERE credit_debit_type = 'CREDIT'
    AND type NOT IN ('TRANSFER', 'BALANCE_ADJUSTMENT')
    AND status IN ('POSTED', 'PENDING')
    AND deleted_at IS NULL
    AND date BETWEEN first_day_of_month AND last_day_of_month

totalExpenses =
  SUM(amount)
  WHERE credit_debit_type = 'DEBIT'
    AND type NOT IN ('TRANSFER', 'BALANCE_ADJUSTMENT')
    AND status IN ('POSTED', 'PENDING')
    AND deleted_at IS NULL
    AND date BETWEEN first_day_of_month AND last_day_of_month

closingBalance =
  opening_balance (from prior month snapshot)
  + SUM(CREDIT amounts in month, status IN (POSTED, PENDING), deleted_at IS NULL)
  - SUM(DEBIT  amounts in month, status IN (POSTED, PENDING), deleted_at IS NULL)
```

**Why TRANSFER and BALANCE_ADJUSTMENT are excluded from income/expenses:**
- Transfers are reallocations between own accounts, not real cash flow. Including them would double-count in "all accounts" view.
- BALANCE_ADJUSTMENT is a reconciliation entry with no economic meaning.
- Both types still affect `closingBalance` because they change the real account balance.

**Why FUTURE is excluded from closingBalance:**
- Same rule as current balance: FUTURE transactions represent intent, not commitment. They appear in the list but not in the balance.
- A separate `projectedClosingBalance` field optionally includes FUTURE for a "projeção" indicator.

### 2. "All accounts" aggregated view

When `financialAccountId` is null in the `monthSummary` query:
- `totalIncome` = SUM across all accounts (TRANSFER excluded — no double-counting between own accounts).
- `totalExpenses` = SUM across all accounts (TRANSFER excluded).
- `closingBalance` = SUM of `closingBalance` of all debit/payment/cash accounts MINUS SUM of `outstanding` of all credit card accounts. This gives the user's **net worth** at month-end.

### 3. Snapshot recompute logic (single month)

```
function recomputeSnapshot(accountId, year, month):

  prior = getSnapshot(accountId, year, month - 1)   // handles year rollover
  opening_balance = prior?.closing_balance ?? 0      // 0 = first month ever

  rows = SELECT credit_debit_type, type, SUM(amount) as total
         FROM banking.transactions
         WHERE financial_account_id = accountId
           AND EXTRACT(YEAR  FROM date) = year
           AND EXTRACT(MONTH FROM date) = month
           AND deleted_at IS NULL
           AND status IN ('POSTED', 'PENDING')
         GROUP BY credit_debit_type, type

  credit_total = SUM(rows WHERE credit_debit_type = 'CREDIT')
  debit_total  = SUM(rows WHERE credit_debit_type = 'DEBIT')

  closing_balance = opening_balance + credit_total - debit_total

  total_income   = SUM(rows WHERE credit_debit_type = 'CREDIT'
                              AND type NOT IN ('TRANSFER', 'BALANCE_ADJUSTMENT'))
  total_expenses = SUM(rows WHERE credit_debit_type = 'DEBIT'
                              AND type NOT IN ('TRANSFER', 'BALANCE_ADJUSTMENT'))

  UPSERT monthly_balance_snapshots SET
    opening_balance = opening_balance,
    closing_balance = closing_balance,
    total_income    = total_income,
    total_expenses  = total_expenses,
    is_dirty        = false,
    computed_at     = NOW()
  WHERE (financial_account_id, year, month) = (accountId, year, month)
```

### 4. Snapshot chain: recompute from affected month to present

When a transaction changes, the `closing_balance` of the affected month shifts, which shifts `opening_balance` of the next month, which shifts its `closing_balance`, and so on through to the current month.

```
function recomputeChain(accountId, affectedYear, affectedMonth):

  cursor = (affectedYear, affectedMonth)
  today  = current (year, month)

  while cursor <= today:
    recomputeSnapshot(accountId, cursor.year, cursor.month)
    cursor = nextMonth(cursor)
```

For a typical user with 2 years of history, this recomputes ~24 snapshots. Each snapshot is 2–3 SQL aggregate queries over one month's transactions. Total time: **< 100 ms** with the existing `(financial_account_id, date DESC)` index.

---

## Write Flow: editing a transaction

Full sequence for `updateTransaction(id, input)`:

```
1. Validate input, check permissions (existing logic)

2. Load current transaction to determine:
   - old_date = transaction.date (before update)
   - new_date = input.date       (after update, may be same)

3. UPDATE banking.transactions SET ... WHERE id = $id
   (standard mutation, no changes here)

4. Determine affected_month:
   - If date changed: affected_month = min(old_date, new_date)   ← earlier of the two
   - If date unchanged: affected_month = month(transaction.date)

5. recomputeChain(transaction.financial_account_id,
                  affected_month.year, affected_month.month)
   - On any error mid-chain:
     * Mark remaining months is_dirty = true
     * Log error (sweeper job will fix)
     * DO NOT fail the mutation — return success to client

6. Return:
   {
     transaction: <updated transaction>,
     monthSummary: <recomputed snapshot for the month in the current UI view>
   }
```

Same pattern applies to `createTransaction` and `deleteTransaction`:
- **Create**: `affected_month = month(input.date)`
- **Delete**: `affected_month = month(transaction.date)`
- **Transfer** (two legs): `affected_month = min(debit_leg.date, credit_leg.date)` — recompute chains for both accounts

---

## Read Flow: monthSummary query

```
function monthSummary(financialAccountId, year, month):

  snapshot = SELECT * FROM monthly_balance_snapshots
             WHERE financial_account_id = financialAccountId
               AND year = year AND month = month

  if snapshot IS NULL:
    // First time this month is ever accessed
    recomputeChain(financialAccountId, year, month)   // computes from genesis if needed
    snapshot = SELECT * FROM monthly_balance_snapshots ...  // now exists

  else if snapshot.is_dirty:
    // Safety net: a prior write failed partway through
    recomputeChain(financialAccountId, year, month)
    snapshot = SELECT * FROM monthly_balance_snapshots ...

  // snapshot is clean — return it
  return {
    year:           snapshot.year,
    month:          snapshot.month,
    totalIncome:    snapshot.total_income,
    totalExpenses:  snapshot.total_expenses,
    openingBalance: snapshot.opening_balance,
    closingBalance: snapshot.closing_balance,
    // computed on-demand (not stored in snapshot):
    transfersIn:    <query for CREDIT TRANSFER in month>,
    transfersOut:   <query for DEBIT TRANSFER in month>,
    projectedClosingBalance: <closingBalance + SUM(FUTURE transactions in month)>,
    isCurrentMonth: (year, month) == current calendar month
  }
```

**Navigating to a past month the user has never opened:**
The snapshot does not exist. `recomputeChain` walks back to the nearest clean snapshot (or genesis) and forward to the requested month, creating all missing snapshots. After this first access, subsequent navigations to the same month are O(1) PK lookups.

---

## Category & Tag Breakdowns

Category totals, tag totals, and per-counterparty breakdowns are **always computed on-demand**. They are not stored in the snapshot table.

**Reason:** The combination space (month × account × category × tag) is too large to pre-cache. A single-month GROUP BY query over the transactions table is fast (< 5 ms for typical account history) and does not require a cache.

```sql
-- Category breakdown for a given month (example)
SELECT
  c.id,
  c.name,
  c.icon,
  c.color,
  t.credit_debit_type,
  SUM(t.amount) AS total
FROM banking.transactions t
JOIN banking.transaction_categories c ON t.category_id = c.id
WHERE t.financial_account_id = $1
  AND t.date BETWEEN $2 AND $3
  AND t.deleted_at IS NULL
  AND t.status IN ('POSTED', 'PENDING')
GROUP BY c.id, c.name, c.icon, c.color, t.credit_debit_type
ORDER BY total DESC;
```

If category breakdowns become slow at scale, the right solution is a separate `monthly_category_snapshots` table — a future optimization, not an MVP concern.

---

## GraphQL Schema

```graphql
type MonthSummary {
  year: Int!
  month: Int!

  # Core summary header (served from snapshot)
  totalIncome: Float!             # Entradas — CREDIT excl. TRANSFER & BALANCE_ADJUSTMENT
  totalExpenses: Float!           # Saídas — DEBIT excl. TRANSFER & BALANCE_ADJUSTMENT
  closingBalance: Float!          # Saldo total — full derived balance at end of month (excl. FUTURE)
  openingBalance: Float!          # Balance at start of month (prior month's closingBalance)

  # Optional: transfer breakdown for single-account view
  transfersIn: Float!             # CREDIT of type TRANSFER in the month
  transfersOut: Float!            # DEBIT of type TRANSFER in the month

  # Optional: projected balance including FUTURE transactions
  projectedClosingBalance: Float  # closingBalance + FUTURE transactions

  isCurrentMonth: Boolean!        # True if (year, month) equals current calendar month
}

# Query
extend type Query {
  monthSummary(
    financialAccountId: ID        # null = aggregate all accounts owned by the tenant
    year: Int!
    month: Int!
  ): MonthSummary!
}
```

The `monthSummary` query is separate from `transactions(dateFrom, dateTo)` so the frontend can fetch the summary header and the transaction list independently (parallel requests, different loading states).

---

## Background Sweeper Job

The sweeper is a safety net — not part of the normal write flow.

**Trigger:** Scheduled (e.g., every 15 minutes) or on application startup.

**Query:**
```sql
SELECT DISTINCT financial_account_id, year, month
FROM banking.monthly_balance_snapshots
WHERE is_dirty = true
ORDER BY year ASC, month ASC;
```

**Action:** For each dirty snapshot, call `recomputeChain`. This heals any snapshot chain that was partially updated due to a failed write.

The sweeper does not need to run frequently for UX correctness — because the read path already heals dirty snapshots on demand. The sweeper exists to pre-heal months the user hasn't navigated to yet, keeping the system clean.

---

## Implementation Plan

> This plan is sequenced for implementation by a single developer or LLM. Each step is independently testable. Steps within a phase can be parallelized if needed.

### Phase A — Database

**A1. Flyway migration: create `banking.monthly_balance_snapshots`**

```sql
CREATE TABLE banking.monthly_balance_snapshots (
  financial_account_id UUID        NOT NULL REFERENCES banking.financial_accounts(id),
  account_id           UUID        NOT NULL REFERENCES security.accounts(id),
  year                 INT         NOT NULL,
  month                INT         NOT NULL CHECK (month BETWEEN 1 AND 12),
  opening_balance      NUMERIC(18,4) NOT NULL,
  closing_balance      NUMERIC(18,4) NOT NULL,
  total_income         NUMERIC(18,4) NOT NULL DEFAULT 0,
  total_expenses       NUMERIC(18,4) NOT NULL DEFAULT 0,
  is_dirty             BOOLEAN     NOT NULL DEFAULT false,
  computed_at          TIMESTAMPTZ,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (financial_account_id, year, month)
);

CREATE INDEX ON banking.monthly_balance_snapshots (financial_account_id, year, month)
  WHERE is_dirty = true;
```

No data seeding needed — table starts empty; snapshots are created on first access.

---

### Phase B — Service Layer (Kotlin)

**B1. `MonthlyBalanceSnapshotRepository`**

Interface and JPA/JDBC implementation:
- `findByAccountAndYearMonth(financialAccountId, year, month): Snapshot?`
- `findMostRecentCleanBefore(financialAccountId, year, month): Snapshot?` — used to find the nearest valid starting point for chain recompute
- `upsert(snapshot: Snapshot)` — INSERT ON CONFLICT DO UPDATE
- `markDirtyFromMonth(financialAccountId, year, month)` — bulk UPDATE is_dirty = true WHERE (year, month) >= (y, m)

**B2. `SnapshotComputationService`**

Core computation logic. No external dependencies beyond the transactions repository.

Methods:
- `recomputeSnapshot(financialAccountId, year, month): Snapshot`
  - Loads prior month snapshot for `opening_balance`
  - Runs two aggregate SQL queries on `banking.transactions` (net balance, income/expense totals)
  - Upserts the result
- `recomputeChain(financialAccountId, startYear, startMonth)`
  - Iterates from (startYear, startMonth) to (current year, current month)
  - Calls `recomputeSnapshot` for each month in sequence
  - On any exception: marks remaining months dirty, logs error, returns (does not rethrow)

**B3. Hook `SnapshotComputationService` into transaction mutations**

Modify `TransactionService` (existing):
- After `createTransaction`: call `recomputeChain(account, month(input.date))`
- After `updateTransaction`: call `recomputeChain(account, min(old_date, new_date))`
- After `deleteTransaction`: call `recomputeChain(account, month(transaction.date))`
- After `createTransfer` (two accounts): call `recomputeChain` for each account

Important: the recompute call is **outside** the DB transaction for the mutation itself. If recompute fails, the mutation has already committed successfully — the snapshot will be healed on the next read or by the sweeper.

**B4. `MonthSummaryQueryService`**

Implements the `monthSummary` resolver logic:
- Loads snapshot by PK
- If null or dirty: calls `recomputeChain`, reloads snapshot
- Computes on-demand fields: `transfersIn`, `transfersOut`, `projectedClosingBalance`
- Handles null `financialAccountId` (all-accounts aggregation):
  - Loads snapshots for all financial accounts of the tenant for the given (year, month)
  - Aggregates: totalIncome + totalExpenses summed; closingBalance = debit accounts sum - credit card outstanding sum

**B5. Background sweeper**

Scheduled job (Spring `@Scheduled` or Quartz):
- Runs every 15 minutes
- Queries all `is_dirty = true` snapshots grouped by `financial_account_id`
- Calls `recomputeChain` per account starting from earliest dirty month
- Logs results (count of healed snapshots)

---

### Phase C — GraphQL Layer

**C1. Add `MonthSummary` type and `monthSummary` query to schema**

```graphql
# In schema.graphqls (or relevant schema file):
type MonthSummary {
  year: Int!
  month: Int!
  totalIncome: Float!
  totalExpenses: Float!
  closingBalance: Float!
  openingBalance: Float!
  transfersIn: Float!
  transfersOut: Float!
  projectedClosingBalance: Float
  isCurrentMonth: Boolean!
}

extend type Query {
  monthSummary(financialAccountId: ID, year: Int!, month: Int!): MonthSummary!
}
```

**C2. Implement `MonthSummaryResolver`**

- Delegates to `MonthSummaryQueryService`
- Applies permission check: `banking:transaction:view` on the tenant's account

---

### Phase D — Frontend

**D1. `MonthSummaryHeader` component**

Display component for the three key figures:

```
┌────────────────────────────────────────────────┐
│  Entradas        Saídas         Saldo total     │
│  R$ 8.500,00    R$ 4.320,00    R$ 12.340,00     │
└────────────────────────────────────────────────┘
```

- Green for Entradas, Red for Saídas, neutral for Saldo total
- Skeleton loader while `monthSummary` query is in flight
- Placed above the transaction list, below the month selector

**D2. Connect to `monthSummary` GraphQL query**

- Called whenever the selected month changes in the month selector
- Parameters: `financialAccountId` from account selector (null = all accounts), current year/month
- Independent from the `transactions` list query (parallel fetch, separate loading state)

**D3. Optimistic update on transaction mutation**

When a transaction is created/edited/deleted:
1. Immediately refresh the `monthSummary` query for the affected month (Apollo `refetchQueries` or cache invalidation)
2. If the transaction date changes to a different month, also refresh the original month's summary
3. The server responds with the fresh snapshot (already recomputed synchronously), so the UI updates immediately

---

### Phase E — Testing

**Unit tests (service layer)**

| Test | What to verify |
|---|---|
| `recomputeSnapshot` — income only month | totalIncome > 0, totalExpenses = 0, closingBalance = openingBalance + totalIncome |
| `recomputeSnapshot` — expense only month | totalExpenses > 0, totalIncome = 0, closingBalance = openingBalance - totalExpenses |
| `recomputeSnapshot` — TRANSFER excluded from income/expenses | transferIn CREDIT does not appear in totalIncome |
| `recomputeSnapshot` — BALANCE_ADJUSTMENT excluded | adjustment CREDIT does not appear in totalIncome |
| `recomputeSnapshot` — FUTURE excluded from balances | FUTURE transaction not counted in closingBalance |
| `recomputeChain` — chain propagates correctly | Edit month 2 → months 2, 3, 4 all recomputed; month 5 unchanged |
| `recomputeChain` — partial failure marks remaining dirty | Simulate error on month 4; months 4+ marked is_dirty=true |
| `monthSummary` — dirty snapshot triggers recompute on read | is_dirty=true → recomputed before return |
| `monthSummary` — missing snapshot triggers recompute on read | No row → recomputed and saved before return |
| All-accounts aggregation | Multiple accounts summed correctly; credit card outstanding subtracted |

**Integration tests**

| Test | What to verify |
|---|---|
| Create transaction → monthSummary reflects new transaction immediately | No async wait needed |
| Edit transaction amount → monthSummary shows updated totals | |
| Edit transaction date from Feb to Mar → Feb and Mar summaries both updated | |
| Delete transaction → monthSummary reflects deletion | |
| Create transfer → both accounts' summaries updated; transfer not in totalIncome/totalExpenses | |
| Navigate to past month → snapshot created and returned | First access creates snapshot |
| Sweeper job heals dirty snapshots | Set is_dirty=true manually; run sweeper; verify is_dirty=false |

**Frontend tests**

| Test | What to verify |
|---|---|
| MonthSummaryHeader renders correct values | |
| Skeleton shown during load | |
| Changing month selector triggers new query | |
| After transaction mutation, summary refreshes | |

---

## Done Criteria

- [ ] `banking.monthly_balance_snapshots` table created via Flyway migration
- [ ] `SnapshotComputationService.recomputeSnapshot` correctly implements the computation formula
- [ ] `SnapshotComputationService.recomputeChain` propagates from affected month to current month
- [ ] `TransactionService` calls `recomputeChain` after every create/update/delete/transfer
- [ ] `monthSummary` GraphQL query returns correct `totalIncome`, `totalExpenses`, `closingBalance`, `openingBalance`
- [ ] `monthSummary` excludes TRANSFER and BALANCE_ADJUSTMENT from `totalIncome`/`totalExpenses`
- [ ] `monthSummary` supports `financialAccountId = null` (all-accounts aggregation)
- [ ] Read path heals dirty/missing snapshots before returning data (zero stale data served)
- [ ] Background sweeper heals dirty snapshots on schedule
- [ ] `MonthSummaryHeader` UI component displays entradas / saídas / saldo total
- [ ] Summary header updates immediately after any transaction mutation
- [ ] All unit and integration tests passing

---

## Resolved Design Decisions

| Decision | Resolution | Rationale |
|---|---|---|
| Async (background job) vs synchronous recompute on write? | **Synchronous eager recompute on write** | Async creates a stale-data window that is unacceptable for financial UI. At MVP scale (< 36 months per user, ~5 ms per month), synchronous recompute of the full chain takes < 150 ms — within acceptable API response time. |
| What if recompute fails mid-chain? | Mark remaining months `is_dirty = true`; return success; sweeper heals later | The mutation (transaction write) has already committed. Failing the mutation due to a cache error would be wrong. `is_dirty` + read-path healing ensures eventual consistency with no data loss. |
| Store category/tag breakdowns in snapshot? | **No — always on-demand** | Too many combinations to cache. A single-month `GROUP BY` over transactions is fast (< 5 ms). Snapshot only stores the three aggregate scalars needed for the summary header. |
| `closingBalance` for "all accounts": sum or net-worth? | **Net worth**: debit accounts sum MINUS credit card outstanding | User wants to know their total financial position. Credit card balance is a liability, so it reduces net worth. |
| `transfersIn` / `transfersOut` in snapshot or on-demand? | **On-demand** (computed at query time, not stored) | These are secondary fields for the single-account view. Storing them adds columns that are only sometimes needed. |
| Background sweeper frequency? | **Every 15 minutes** | Frequent enough to heal failures quickly without unnecessary load. The read path handles user-facing correctness immediately. |
