---
id: feature.transactions.summaries.dashboard-and-cashflow
domain: transactions
status: draft
confidence: verified
source_of_truth: code
last_verified: 2026-08-03
evidence:
  backend:
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/DashboardAnalyticsService.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/DashboardAnalyticsCalculator.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/DashboardPeriodSummary.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/CashFlowDataPoint.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionResolver.kt
    - service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/config/BankingWiringFactory.kt
  graphql:
    - contracts/graphql/subgraphs/banking/schema.graphqls
  database: []
  tests:
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/DashboardAnalyticsCalculatorTest.kt
    - service/kotlin/banking/src/test/kotlin/io/github/salomax/neotool/banking/domain/transaction/DashboardAnalyticsServiceDelegationTest.kt
  frontend:
    - web/src/features/personal-finance/dashboard/
    - web/src/lib/graphql/operations/banking/queries.ts
  mobile: []
  workers: []
legacy_refs: []
related_specs:
  - feature.transactions.summaries.monthly-balance-snapshots
  - feature.transactions.summaries.month-summary-query
schema: spec-metadata-v1
---

# Summaries — Dashboard And Cashflow

## Glossary

- `accountId` — tenant scope for aggregate dashboard queries.
- `financialAccountId` — optional single-FA filter.
- Cashflow series — monthly income/expenses/closing from **persisted snapshots** (not live recompute).

## Behavior

### Queries (all `banking:transaction:view`)

- `cashFlowSeries(financialAccountId?, startYear/Month, endYear/Month)` → list of `CashFlowDataPoint` (income, expenses, closingBalance, derived net). One point per month; multi-FA aggregate folds same (year,month) by summing snapshot rows. Months without snapshot rows are **omitted**.
- `dashboardPeriodSummary(...)` → period KPIs from snapshots (sum income/expenses, opening of first month, closing cash of last month, averages, savings rate) **plus** investment portfolio opening/closing quota values and headline invested/return metrics (`InvestmentPortfolioSummaryService` — investment-portfolio bridge).
- `categoryBreakdownForRange(...)` → single range GROUP BY via `getCategoryBreakdownForRange` (default applicableTo EXPENSE).
- Related portfolio series (`portfolioNetWorthSeries` / allocation) live on investment quota tables — cite only as dashboard dependency, not PF ledger authority.

### Filters / exclusions

- Snapshot-backed cash metrics inherit snapshot income/expense rules (exclude TRANSFER / BALANCE_ADJUSTMENT / BILL_PAYMENT; FUTURE not in snapshot totals).
- Aggregate FA filter for monthSummary uses NON_CASHFLOW + `include_in_net_worth`; **dashboard `findByRangeForAccount` does not apply that type filter** — any FA snapshots for the tenant in range are summed (possible inclusion of investment FA snapshots if present).

### Dirty / heal

- Dashboard calculator is **read-only** on existing snapshot rows — it does **not** call `recomputeChain` / heal dirty flags (unlike `monthSummary`).

## Invariants

- Cashflow charts reflect snapshot cache state at query time.
- Net worth KPI mixes cash closing from snapshots with portfolio quota gross value.

## Permissions

Backend: `banking:transaction:view` on dashboard analytics fetchers.

UX: `web/src/features/personal-finance/dashboard/` widgets (`CashFlowWidget`, `MonthSummaryKpiWidget`, category/net-worth charts) via `useDashboardAnalytics`.

## Data And Contracts

- GraphQL: `CashFlowDataPoint`, `DashboardPeriodSummary`, `categoryBreakdownForRange`, cashFlowSeries, dashboardPeriodSummary.
- No Bruno dashboard/cashflow requests found (only Get Month Summary).
- Unit tests for calculator/delegation; no dedicated dashboard E2E under personal-finance.

## Impact Surface

- Backend: DashboardAnalyticsService/Calculator, resolvers
- GraphQL + dashboard React feature module
- Cross: monthly snapshots; investment portfolio quotas for net-worth KPIs

## Decisions

- Shared calculator keeps dashboard and investment widgets on one KPI source.
- Prefer snapshot series for cashflow UX performance over on-demand monthSummary per month.

## Known Gaps

- No Bruno for dashboard queries.
- No E2E covering cashFlowSeries / dashboardPeriodSummary.
- Aggregate dashboard may diverge from aggregate monthSummary FA inclusion rules (NON_CASHFLOW / include_in_net_worth).
- Dirty snapshots can skew dashboard until sweeper or a monthSummary heal runs.

## Legacy Notes

- None used as authority.
