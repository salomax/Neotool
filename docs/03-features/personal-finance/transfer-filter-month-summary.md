# Feature: Transfer Filter in Month Summary Sidebar

## Overview

Add a "Transfers" section to the `MonthSummary` sidebar component, between the volume bar (Income/Expenses) and the category breakdowns. The section shows internal transfer amounts (`transfersIn` / `transfersOut`) and is clickable to filter the transaction table to show only `TRANSFER`-type transactions.

---

## Context

### What already exists

- `MonthSummaryData` already contains `transfersIn: number` and `transfersOut: number` (GraphQL fields already returned).
- The backend `transactions` GraphQL query already accepts `types: [TransactionType!]` for filtering by transaction type (see `contracts/graphql/subgraphs/banking/schema.graphqls`).
- The filter toggle pattern is encapsulated in `useScopedToggleFilter` (`finance-transactions/hooks/useScopedToggleFilter.ts`).
- The credit/debit filter (`creditDebitFilter`) is the closest analog: it uses a scoped toggle, passes through the sidebar, wires into the GQL query, and renders an active filter chip.

### What does NOT exist yet

- A `types` variable in the frontend `TRANSACTIONS` GQL query.
- Any UI representation of `transfersIn` / `transfersOut` in the sidebar.
- A filter state or active chip for transfer type.

---

## UI Design

### New section in MonthSummary

Position: **between the volume bar and the category breakdowns** (i.e., after the green/red split bar at the bottom of the Income/Expenses block, before the `Divider` that opens the "Expenses by Category" section).

Render two rows using the existing `SummaryMetricRow` component:

| Row | Icon | Label | Value | Color |
|-----|------|-------|-------|-------|
| 1 | `SwapHorizIcon` | `t('financeTransactions.monthSummary.transfersIn')` | `summary.transfersIn` | `theme.palette.info.main` |
| 2 | `SwapHorizIcon` | `t('financeTransactions.monthSummary.transfersOut')` | `summary.transfersOut` | `theme.palette.secondary.main` (or `warning.main`) |

- Both rows share the same `onActivate` callback: `() => onTransferClick()`.
- Neither row has a `percentage` prop (no `% of total` caption needed).
- Both rows are `dimmed` when `activeCreditDebitTypeFilter !== null` (the user is viewing income-only or expenses-only — transfers are irrelevant in that view).
- Both rows are hidden (do not render) when `summary.transfersIn === 0 && summary.transfersOut === 0` and not loading.
- The section is wrapped in a `<Box sx={{ mt: 2 }}>` with a visual separator (a `<Divider sx={{ mb: 2 }} />` before the rows).

### Active filter chip

When the transfer filter is active, an active filter chip appears in `FinanceTransactionsActiveFilters` (alongside the existing credit/debit and category chips):

- Icon: `SwapHorizIcon`
- Label: `t('financeTransactions.monthSummary.filteringByTransfer')`
- Accent color: `theme.palette.info.main`
- onDelete: clears the transfer filter

### Dimming behavior summary

| Active filter | Income row | Expenses row | Transfer rows |
|---|---|---|---|
| `creditDebitType = Credit` | normal | dimmed | dimmed |
| `creditDebitType = Debit` | dimmed | normal | dimmed |
| `transfer = true` | normal | normal | normal (active) |
| none | normal | normal | normal |

Transfer rows and credit/debit filters are **independent** — the user can combine them (the backend will AND them). No auto-clearing between filters.

---

## Implementation Plan

### 1. i18n — `web/src/app/personal-finance/i18n/locales/en.json` and `pt.json`

Add the following keys inside the existing `"monthSummary"` object (after `"clearCreditDebitTypeFilter"`):

**en.json:**
```json
"transfersIn": "Transfers In",
"transfersOut": "Transfers Out",
"filteringByTransfer": "Internal transfers only",
"clearTransferFilter": "Clear transfer filter"
```

**pt.json:**
```json
"transfersIn": "Transferências recebidas",
"transfersOut": "Transferências enviadas",
"filteringByTransfer": "Somente transferências internas",
"clearTransferFilter": "Remover filtro de transferência"
```

---

### 2. GQL query — `web/src/lib/graphql/operations/banking/queries.ts`

In the `TRANSACTIONS` query (exported as `export const TRANSACTIONS = gql\`...\``), add `$types: [TransactionType!]` to the variable declarations and pass it through:

```graphql
query Transactions(
  # ... existing variables ...
  $types: [TransactionType!]
) {
  transactions(
    # ... existing args ...
    types: $types
  ) {
    # unchanged
  }
}
```

---

### 3. `MonthSummary.tsx` — `web/src/app/personal-finance/finance-transactions/summary/MonthSummary.tsx`

#### 3a. Add props

```typescript
interface MonthSummaryProps {
  // ... existing props ...
  /** Called when user clicks a transfer row to filter transactions by TRANSFER type. */
  onTransferClick?: () => void;
  /** When true, transfer rows are highlighted as the active filter. */
  activeTransferFilter?: boolean;
}
```

#### 3b. Add import

Add `SwapHorizIcon` to the MUI icon imports:
```typescript
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
```

#### 3c. Render transfer rows

After the volume bar block (the `{(totalVolume > 0 || ...) && <Box ...>...</Box>}` section), before the `{summary && !loading && expensesByCategory !== undefined && ...}` categories block, add:

```tsx
{summary && !loading && (summary.transfersIn > 0 || summary.transfersOut > 0) && (
  <Box sx={{ mt: 2 }}>
    <Divider sx={{ mb: 2 }} />
    <Stack spacing={1.2}>
      <SummaryMetricRow
        icon={<SwapHorizIcon />}
        color={theme.palette.info.main}
        label={t('financeTransactions.monthSummary.transfersIn')}
        value={summary.transfersIn}
        isLoading={false}
        formatCurrency={formatCurrency}
        totalSuffix={totalSuffix}
        dimmed={activeCreditDebitTypeFilter !== null}
        onActivate={onTransferClick}
      />
      <SummaryMetricRow
        icon={<SwapHorizIcon />}
        color={theme.palette.secondary.main}
        label={t('financeTransactions.monthSummary.transfersOut')}
        value={summary.transfersOut}
        isLoading={false}
        formatCurrency={formatCurrency}
        totalSuffix={totalSuffix}
        dimmed={activeCreditDebitTypeFilter !== null}
        onActivate={onTransferClick}
      />
    </Stack>
  </Box>
)}
```

> Note: `SummaryMetricRow`'s `interactive` flag is `Boolean(onActivate) && !dimmed && !isLoading`, so when `onTransferClick` is provided and the row isn't dimmed, it becomes clickable automatically. No additional changes needed inside `SummaryMetricRow`.

---

### 4. `FinanceTransactionsSidebar.tsx` — `web/src/app/personal-finance/finance-transactions/FinanceTransactionsSidebar.tsx`

#### 4a. Add `transferFilter` to `CalendarSummaryState`

```typescript
interface CalendarSummaryState {
  // ... existing fields ...
  transferFilter: {
    value: boolean | null;
    onToggle: () => void;
  };
}
```

#### 4b. Pass to `MonthSummary`

```tsx
<MonthSummary
  // ... existing props ...
  onTransferClick={calendarSummary.transferFilter.onToggle}
  activeTransferFilter={calendarSummary.transferFilter.value === true}
/>
```

---

### 5. `page.tsx` — `web/src/app/personal-finance/finance-transactions/page.tsx`

#### 5a. Add import

Add `TransactionType` to the graphql types import:
```typescript
import { CreditDebitType, FinancialAccount, FinancialAccountType, TransactionType } from '@/lib/graphql/types/__generated__/graphql';
```

#### 5b. Add transfer filter state

After the existing filter hooks (`categoryFilter`, `tagFilter`, `creditDebitFilter`):
```typescript
const transferFilter = useScopedToggleFilter<true>(filterScopeKey);
```

The toggle call will be `() => transferFilter.toggle(true)` — calling it twice clears the filter (built-in behavior of `useScopedToggleFilter`).

#### 5c. Wire into TRANSACTIONS query variables

Add to the `variables` object passed to `useQuery(TRANSACTIONS, ...)`:
```typescript
types:
  displayMode.kind === 'calendar' && transferFilter.value
    ? [TransactionType.Transfer]
    : undefined,
```

#### 5d. Add to `activeFiltersOpen` condition

```typescript
const activeFiltersOpen =
  displayMode.kind === 'calendar' &&
  Boolean(categoryFilter.value || tagFilter.value || creditDebitFilter.value || transferFilter.value);
```

#### 5e. Pass transfer filter to sidebar

```tsx
calendarSummary={{
  // ... existing fields ...
  transferFilter: {
    value: transferFilter.value,
    onToggle: () => transferFilter.toggle(true),
  },
}}
```

#### 5f. Pass transfer chip props to `FinanceTransactionsActiveFilters`

```tsx
<FinanceTransactionsActiveFilters
  // ... existing props ...
  summaryTransferFilterActive={Boolean(transferFilter.value)}
  onClearTransferFilter={transferFilter.clear}
  summaryTransferChipAccent={theme.palette.info.main}
/>
```

---

### 6. `FinanceTransactionsActiveFilters.tsx` — `web/src/app/personal-finance/finance-transactions/FinanceTransactionsActiveFilters.tsx`

#### 6a. Add props

```typescript
interface FinanceTransactionsActiveFiltersProps {
  // ... existing props ...
  summaryTransferFilterActive: boolean;
  onClearTransferFilter: () => void;
  summaryTransferChipAccent: string;
}
```

#### 6b. Add import

```typescript
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
```

#### 6c. Render transfer chip

Add after the `summaryCreditDebitTypeFilter` chip block:

```tsx
{summaryTransferFilterActive && (
  <Chip
    icon={<SwapHorizIcon sx={{ fontSize: 18 }} />}
    label={t('financeTransactions.monthSummary.filteringByTransfer')}
    onDelete={onClearTransferFilter}
    deleteIcon={
      <CloseIcon fontSize="small" aria-label={t('financeTransactions.monthSummary.clearTransferFilter')} />
    }
    variant="outlined"
    sx={{
      pl: 1.5,
      pr: 1.25,
      py: 0.75,
      bgcolor: alpha(summaryTransferChipAccent, 0.12),
      borderColor: alpha(summaryTransferChipAccent, 0.4),
      color: summaryTransferChipAccent,
      fontWeight: 600,
      '& .MuiChip-icon': {
        color: summaryTransferChipAccent,
        ml: 0.25,
        mr: 0.5,
      },
      '& .MuiChip-label': { px: 1.25 },
      '& .MuiChip-deleteIcon': {
        color: alpha(summaryTransferChipAccent, 0.75),
        '&:hover': { color: summaryTransferChipAccent },
      },
    }}
  />
)}
```

---

## File Change Summary

| File | Change |
|------|--------|
| `web/src/app/personal-finance/i18n/locales/en.json` | Add 4 keys inside `monthSummary` |
| `web/src/app/personal-finance/i18n/locales/pt.json` | Add 4 keys inside `monthSummary` |
| `web/src/lib/graphql/operations/banking/queries.ts` | Add `$types` variable to `TRANSACTIONS` query |
| `web/src/app/personal-finance/finance-transactions/summary/MonthSummary.tsx` | Add `onTransferClick`/`activeTransferFilter` props + transfer rows UI |
| `web/src/app/personal-finance/finance-transactions/FinanceTransactionsSidebar.tsx` | Add `transferFilter` to `CalendarSummaryState` + pass to `MonthSummary` |
| `web/src/app/personal-finance/finance-transactions/page.tsx` | Add `transferFilter` state, wire to query, active filters bar, and sidebar |
| `web/src/app/personal-finance/finance-transactions/FinanceTransactionsActiveFilters.tsx` | Add transfer chip |
