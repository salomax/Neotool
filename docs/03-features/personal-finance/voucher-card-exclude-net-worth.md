# Voucher Card — Exclude from Net Worth

## Context

In Brazil, employer-provided benefit cards (Vale Alimentação, Vale Refeição, Vale Combustível, etc.)
are funded by the employer or a third party — not by the user's own money. Recording expenses on
these cards should not reduce the user's personal balance or net worth. However, users still want
to see where voucher money was spent (categories, monthly totals per card).

## Decision

Use the existing `includeInNetWorth` flag on `FinancialAccount`.

- `VOUCHER_CARD` is already a valid `FinancialAccountSubtype` in the enum and DB constraint.
- `include_in_net_worth` already exists on the `financial_accounts` table (`BOOLEAN NOT NULL DEFAULT TRUE`).
- The full plumbing exists: domain, entity, mapper, commands, GraphQL schema.

**No DB migration is required.**

The work is purely in the service layer:

1. **Smart default**: when creating a `VOUCHER_CARD` account, default `includeInNetWorth = false`
   unless the caller explicitly passes a value.

2. **Aggregate summary filter**: `MonthSummaryQueryService.getAggregateMonthlySummary` currently
   aggregates ALL accounts. It must skip accounts where `includeInNetWorth = false`.

3. **Category / tag breakdown filter**: the aggregate breakdown queries also run over all accounts.
   They must exclude transactions that belong to `includeInNetWorth = false` accounts.

---

## What does NOT change

- Per-account `monthSummary` queries are unaffected. Viewing a single voucher card still works.
- The `SnapshotComputationService` and per-account snapshot chain are unaffected.
- Transactions on voucher cards are fully tracked (dates, amounts, categories, tags).
- The user can override `includeInNetWorth` to `true` on a voucher card if they wish
  (e.g. if they want the card balance in their net worth).

---

## Files to change

### 1. `FinancialAccountService.kt`
**Path**: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/account/FinancialAccountService.kt`

In the `create` method, the current default for `includeInNetWorth` is always `true`:

```kotlin
// BEFORE
includeInNetWorth = command.includeInNetWorth ?: true,
```

Change it to default to `false` for `VOUCHER_CARD`:

```kotlin
// AFTER
includeInNetWorth = command.includeInNetWorth
    ?: (command.subtype != FinancialAccountSubtype.VOUCHER_CARD),
```

This means:
- Any other account type: default `true` (unchanged behaviour).
- `VOUCHER_CARD`: default `false` unless explicitly overridden by the caller.

No other changes in `update` are needed — the update path already preserves the existing value
when `includeInNetWorth` is absent from the command.

---

### 2. `TransactionEntityRepository.kt`
**Path**: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/TransactionEntityRepository.kt`

The current `findFinancialAccountIdsByAccount` query fetches IDs from the `transactions` table only,
without checking the account's `include_in_net_worth` flag:

```sql
-- BEFORE
SELECT DISTINCT financial_account_id
  FROM banking.transactions
 WHERE account_id = :accountId
   AND deleted_at IS NULL
```

Replace it with a version that joins `financial_accounts` and filters:

```kotlin
@Query(
    """
    SELECT DISTINCT t.financial_account_id
      FROM banking.transactions t
      JOIN banking.financial_accounts fa ON fa.id = t.financial_account_id
     WHERE t.account_id = :accountId
       AND t.deleted_at IS NULL
       AND fa.include_in_net_worth = TRUE
       AND fa.deleted_at IS NULL
    """,
    nativeQuery = true,
)
fun findFinancialAccountIdsByAccount(accountId: UUID): List<UUID>
```

**Performance note**: The join is on `fa.id` (PK — already indexed). There is no fan-out since
each transaction has exactly one `financial_account_id`. The existing index
`idx_banking_financial_accounts_active ON financial_accounts(account_id) WHERE deleted_at IS NULL`
does not cover this join, but `fa.id` is the PK so Postgres uses the PK index. No new index is
needed.

Also update the delegation in `DefaultTransactionRepository.kt`:
**Path**: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultTransactionRepository.kt`

The delegation call `entityRepository.findFinancialAccountIdsByAccount(accountId)` has the same
signature and requires no change.

---

### 3. `MonthSummaryQueryService.kt` — aggregate category/tag breakdowns
**Path**: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/MonthSummaryQueryService.kt`

After the fix in step 2, the snapshot aggregation loop in `getAggregateMonthlySummary` is already
correct (it iterates over `financialAccountIds` which now excludes voucher cards). However, the
category/tag breakdown calls are still cross-account:

```kotlin
// These currently include voucher transactions because financialAccountId = null
val expensesByCategory = getCategoryBreakdown(accountId, null, year, month, CategoryApplicableTo.EXPENSE)
val incomeByCategory   = getCategoryBreakdown(accountId, null, year, month, CategoryApplicableTo.INCOME)
val tagsSummary        = getTagBreakdown(accountId, null, year, month, null)
```

When `financialAccountId = null` the underlying dynamic query filters only by `account_id`, so
voucher transactions bleed into the breakdowns.

**Fix**: inject `FinancialAccountRepository` into `MonthSummaryQueryService` and compute the
set of included (net-worth) account IDs once, then pass them to the breakdown calls.

The cleanest approach that does not require touching the complex dynamic SQL is to filter the
breakdown result in Kotlin **only if** the caller is in aggregate mode (`financialAccountId = null`)
and there are excluded accounts. Since category/tag breakdown already supports
`financialAccountId` filtering, iterate over the included account IDs instead of passing `null`:

```kotlin
// In getAggregateMonthlySummary — AFTER fix
val financialAccountIds = transactionRepository.findFinancialAccountIdsByAccount(accountId)
// financialAccountIds now contains ONLY includeInNetWorth = true accounts (from step 2)

// For category/tag breakdown: if there is only one included account, pass its ID directly.
// If multiple, fetch the full list and SUM per-account breakdowns — OR use the existing
// null-aggregate path but add a join filter.
//
// Recommended: add a new query variant (see step 4 below) that accepts a list of
// included financial account IDs and adds WHERE financial_account_id = ANY(:ids).
```

---

### 4. New query: `getCategoryBreakdownForAccounts` / `getTagBreakdownForAccounts`
**Path**: `TransactionRepository` interface + `DefaultTransactionRepository` implementation
**Path**: the dynamic query builder in `DefaultTransactionRepository`

Add a new parameter `includedFinancialAccountIds: List<UUID>?` to `TransactionListQuery`.
When the list is non-null and non-empty, inject into the dynamic WHERE clause:

```sql
AND t.financial_account_id = ANY(CAST(:includedFinancialAccountIds AS uuid[]))
```

Then in `getAggregateMonthlySummary`, when calling the breakdowns in aggregate mode,
pass `financialAccountIds` (the already-filtered list) instead of `null`.

This is the most surgical change: the dynamic query builder already handles optional filters,
so adding one more list parameter follows the existing pattern.

**If `financialAccountIds` is empty** (no net-worth accounts), the aggregate summary already
returns early with a zero-value summary, so the breakdown calls are never reached.

---

## Affected call paths (summary)

| Call path | Change required |
|-----------|----------------|
| `createFinancialAccount(VOUCHER_CARD)` | default `includeInNetWorth = false` |
| `monthSummary(financialAccountId)` — single card | **No change** |
| `monthSummary(null)` — aggregate | Fix: exclude accounts with `includeInNetWorth = false` from snapshot loop ✅ (via step 2) |
| Category/tag breakdown — single card | **No change** |
| Category/tag breakdown — aggregate (`null`) | Fix: filter to net-worth accounts only (step 3/4) |
| `CreditCardBillService` / `SnapshotComputationService` | **No change** — operate per financial account |
| `MonthlySnapshotSweeperJob` | **No change** — heals per account regardless of flag |

---

## GraphQL / API surface

No GraphQL schema changes. The existing `FinancialAccount.includeInNetWorth: Boolean!` field is
already exposed, and clients can already toggle it via `updateFinancialAccount(includeInNetWorth: Boolean)`.

Clients should reflect this default in the UI: when creating a `VOUCHER_CARD`, pre-tick
"Excluir do patrimônio líquido" to `true` (i.e. pre-set `includeInNetWorth = false`) and explain
why with a short tooltip.

---

## Tests to write or update

### Unit tests

- `FinancialAccountServiceTest`:
  - `createVoucherCard_defaultsIncludeInNetWorthFalse`
  - `createVoucherCard_callerCanOverrideIncludeInNetWorthTrue`
  - `createCreditCard_defaultsIncludeInNetWorthTrue` (regression: other card types unchanged)

### Integration tests

- `MonthlyBalanceSnapshotRepositoryIT` (or a new `MonthSummaryQueryServiceIT`):
  - Setup: one regular checking account + one voucher card, both with transactions.
  - Assert: aggregate `monthSummary` snapshot totals and balances include only the checking account.
  - Assert: aggregate category breakdown excludes voucher transactions.

---

## Out of scope (deferred)

- UI changes beyond default `includeInNetWorth = false` (toggle label, tooltip text).
- Bill management (`CreditCardBillService`) for voucher cards — voucher cards do not have bills.
  The service already guards on `CREDIT_CARD` and `CHARGE_CARD` subtypes; `VOUCHER_CARD` is
  already excluded from bill creation.
- A dedicated "Benefits" section in the dashboard — future iteration.
