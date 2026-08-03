---
id: feature.transactions.summaries.monthly-balance-snapshots
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/SnapshotComputationService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/MonthlyBalanceSnapshot.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultTransactionRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultMonthlyBalanceSnapshotRepository.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/transaction/MonthlySnapshotSweeperJob.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/account/FinancialAccountService.kt
  graphql: []
  database:
    - service/kotlin/banking/src/main/resources/db/migration/V0_7__create_transactions.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_27__mark_snapshots_dirty_for_transfer_closing_balance_fix.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_28__mark_snapshots_dirty_for_rule_reclassification_fix.sql
    - service/kotlin/banking/src/main/resources/db/migration/V0_37__mark_installment_snapshots_dirty.sql
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/SnapshotComputationServiceTest.kt
  frontend: []
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.summaries.month-summary-query
  - feature.transactions.summaries.dashboard-and-cashflow
  - feature.transactions.ledger-transactions.create-update-delete
schema: spec-metadata-v1
---

# Summaries — Monthly Balance Snapshots

## Glossary

- `accountId` — tenant owning the snapshot row.
- `financialAccountId` — banking FA the snapshot is keyed by.
- Snapshot — derived read cache in `banking.monthly_balance_snapshots`; **source of truth remains `banking.transactions`** (no balance on the transaction row).

## Behavior

### Persisted fields

- PK `(financial_account_id, year, month)`; stores `openingBalance`, `closingBalance`, `totalIncome`, `totalExpenses`, `isDirty`, `computedAt`.
- `closingBalance = openingBalance + SUM(CREDIT) − SUM(DEBIT)` for POSTED/PENDING in month (all types).
- `totalIncome` / `totalExpenses` from `getMonthTotals`: CREDIT/DEBIT POSTED/PENDING with type **not in** `TRANSFER`, `BALANCE_ADJUSTMENT`, `BILL_PAYMENT`.
- FUTURE status excluded from closing/income/expense snapshot buckets.

### Month membership (important)

- Base rows: txs with `installment_number` null or ≤1, filtered by **transaction `date`** in month.
- Installments with `installment_number > 1` and a bill: attributed by **bill `due_date`** month.
- Installments >1 without bill: still by transaction `date`.

### Write / dirty / recompute

- `SnapshotComputationService.recomputeChain(faId, accountId, fromYear, fromMonth)` walks from that month through **current** calendar month; opening from latest clean prior snapshot or 0; on failure `markDirtyFromMonth` then rethrow.
- **Eager `recomputeChain` callers**: `TransactionService` create/update/delete; `FinancialAccountService` initial-balance updates; import/bill-import job paths (imports batch).
- **`markDirtyFromMonth` without full chain** (heal later): `TransactionRuleService.applyToHistorical`, `McpChangeLogService.revertToolCallChanges`; one-shot migrations V0_27/28/37.
- `bulkCategorize` does **not** recompute snapshots (category does not change income/expense/closing formulas).
- Account delete clears snapshots for that FA.
- `MonthlySnapshotSweeperJob` periodically heals accounts with dirty rows (default ~30m; configurable/disableable).

## Invariants

- Snapshots never invent money; they aggregate ledger rows.
- No snapshot persistence for months after “today” (future handled only in month-summary query).
- Dirty means “stale cache”; read path or sweeper must heal.

## Permissions

No direct GraphQL for snapshots; mutated as side effect of ledger/account writes under those permissions.

## Data And Contracts

- Table created in V0_7; dirty heal indexes; historical dirty-fix migrations.

## Impact Surface

- Backend: SnapshotComputationService, repos, TransactionService/FA hooks, sweeper job
- Tests: SnapshotComputationServiceTest
- Cross: month-summary heal-on-read; dashboard reads snapshots as-is

## Decisions

- Synchronous chain on ledger writes; dirty flag + sweeper as safety net.
- Installment>1 with bill uses due-date month for totals (aligns list/summary with bill cycle).

## Known Gaps

- Domain comments sometimes omit `BILL_PAYMENT` exclusion — **SQL is authoritative** (excludes it from income/expense).
- Dashboard range queries do not heal dirty snapshots (monthSummary does).

## Legacy Notes

- None used as authority.
