# Dashboard — Dynamic Start Date for "All Period" Filter

## Context

The personal-finance dashboard has a date range filter with options: `currentMonth`, `3m`, `6m`, `1y`, `3y`, `all`.

The `all` option is currently hardcoded to **60 months (5 years)** back from today in:

`web/src/features/personal-finance/dashboard/hooks/useDashboardDateRange.ts` — line 62

```ts
: option === 'all'
  ? 60   // hardcoded — always 5 years
  : 1;
```

Since today is April 2026, this always produces `startYear: 2021, startMonth: 5` — regardless of when the user actually started using the product.

**Problems with this approach:**

1. **Fetches phantom months** — if the user only has data from 2023 onward, we request and render 2 years of empty chart data.
2. **Silently truncates history** — if the user has transactions or investments older than 5 years, "All period" misses them entirely.

---

## Goal

Replace the hardcoded 60-month offset with a **dynamic start date** derived from the user's actual earliest data point — the earliest of:

- The oldest non-deleted transaction for the selected financial account (or across all accounts when no filter is active)
- The oldest investment portfolio entry for the same scope

---

## Backend Changes

### 1. New GraphQL field on `FinancialAccount`

Extend the existing `FinancialAccount` GraphQL type (schema at `service/kotlin/banking/src/main/resources/graphql/schema.graphqls`) with a new field:

```graphql
type FinancialAccount {
  # ... existing fields ...
  dataSince: YearMonth   # nullable — null if account has no data yet
}

type YearMonth {
  year: Int!
  month: Int!
}
```

`dataSince` returns the earliest year/month across all non-deleted transactions for that account.

> If a `YearMonth` scalar/type already exists in the schema, reuse it.

### 2. Resolver implementation

In `service/kotlin/banking/src/main/graphql/resolver/` (or wherever `FinancialAccount` is resolved), add a resolver for `dataSince` that runs:

```sql
SELECT
  EXTRACT(YEAR  FROM MIN(date))::int AS year,
  EXTRACT(MONTH FROM MIN(date))::int AS month
FROM banking.transactions
WHERE financial_account_id = :accountId
  AND deleted_at IS NULL
```

Return `null` if no rows exist (account with no data).

### 3. Cross-account scope (global "All accounts" view)

When the dashboard is in "all accounts" mode (no specific `financialAccountId` selected), the query should aggregate across all accounts belonging to the tenant (`account_id`):

```sql
SELECT
  EXTRACT(YEAR  FROM MIN(date))::int AS year,
  EXTRACT(MONTH FROM MIN(date))::int AS month
FROM banking.transactions
WHERE account_id = :accountId
  AND deleted_at IS NULL
```

The resolver should detect whether a `financialAccountId` filter is active and use the appropriate query.

### 4. Investment portfolio (if applicable)

If investment portfolio entries have their own table with a reference/trade date column, include that in the MIN as well (UNION or LEAST of both MINs). Check the investments domain for the correct table/column names.

---

## Frontend Changes

### 1. Add `dataSince` to the `FINANCIAL_ACCOUNTS` query

File: `web/src/lib/graphql/operations/banking/queries.ts`

Add `dataSince { year month }` to the `FinancialAccount` fragment / `FINANCIAL_ACCOUNTS` query so the value is already available when the dashboard loads — **zero extra network requests**.

### 2. Expose `dataSince` from the dashboard store or context

The dashboard already loads financial account data. Surface the resolved `dataSince` value (or a cross-account aggregate) into the dashboard state — either via the Zustand store (`store.ts`) or directly from context.

For the cross-account case, compute `min(dataSince)` across all loaded accounts on the frontend.

### 3. Update `buildMonthRange` to accept an optional `since` date

File: `web/src/features/personal-finance/dashboard/hooks/useDashboardDateRange.ts`

```ts
export function buildMonthRange(
  option: DateRangeOption,
  now: Date = new Date(),
  since?: MonthYear,   // new optional param
): MonthYear[] {
```

When `option === 'all'` and `since` is provided, generate the full month list from `since` to the current month instead of using the hardcoded 60 count.

When `since` is `null` / not yet loaded, fall back to the current 60-month default (safe degraded behavior).

### 4. Wire `since` into the call site

Wherever `buildMonthRange` is called with `option === 'all'`, pass the resolved `dataSince` value.

---

## Acceptance Criteria

- [ ] A user whose oldest transaction is in March 2023 sees "All period" start in March 2023, not May 2021.
- [ ] A user whose oldest transaction is in January 2019 sees "All period" start in January 2019 (not capped at 5 years).
- [ ] A new account with zero transactions shows "All period" starting from the current month (graceful fallback).
- [ ] No additional network request is made — `dataSince` is resolved from the existing `financialAccounts` query payload.
- [ ] All other date range options (`3m`, `6m`, `1y`, `3y`, `currentMonth`) are unaffected.
- [ ] Existing unit tests for `buildMonthRange` still pass; new tests cover the `since` parameter.

---

## Files to Touch (summary)

| Layer | File | Change |
|---|---|---|
| GraphQL schema | `schema.graphqls` | Add `dataSince: YearMonth` to `FinancialAccount` |
| Resolver | `TransactionResolver.kt` (or account resolver) | Implement `dataSince` DB query |
| SQL / repo | new or existing repo method | `MIN(date)` query |
| GQL operation | `queries.ts` | Add `dataSince { year month }` to account query |
| Hook | `useDashboardDateRange.ts` | Add `since?: MonthYear` param to `buildMonthRange` |
| Store / context | `store.ts` or context file | Derive and expose `dataSince` |
| Call site | wherever `buildMonthRange('all', ...)` is called | Pass `since` |
