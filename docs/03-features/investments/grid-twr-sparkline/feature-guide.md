---
title: Investment Grid — TWR Sparkline
type: feature-guide
module: banking
feature_slug: grid-twr-sparkline
status: in-progress
version: 1.1.0
tags: [investments, portfolio, performance, twr, sparkline, chart]
search_keywords: [twr, sparkline, investment grid, quota, performance chart, mini chart]
related:
  - docs/07-frontend/patterns/graphql-query-pattern.md
  - docs/05-backend/patterns/service-pattern.md
  - docs/05-backend/patterns/repository-pattern.md
---

# Investment Grid — TWR Sparkline

> **One-line summary**: Replace the investment grid card mini-charts (currently showing gross value in BRL) with contribution-adjusted TWR (Time-Weighted Return) percentage curves, giving users an honest view of performance independent of deposits and withdrawals.

---

## Overview

### Problem Context

The investment grid cards (`InvestmentCard`) display a background sparkline chart. That chart currently plots the monthly `grossValue` (absolute BRL amount) for each asset class. This is misleading for two reasons:

1. **Contributions inflate the curve.** A month where the user deposits R$50k shows a spike that looks like performance but is just new money.
2. **Cross-card comparison is meaningless.** A R$200k fixed-income card and a R$10k crypto card use completely different Y-scales; the sparkline shape cannot be compared.

### Goals

- The sparkline on each grid card shows the **TWR return percentage** over the full available history for that asset class.
- The shape of the curve reflects **investment performance**, not portfolio balance.
- The metric is consistent and comparable across cards (same unit: %).

### What Is Already Built

| Component | Location | Status |
|---|---|---|
| Daily TWR quota calculation | `PortfolioQuotaCalculationService` | ✅ Exists — computes per-group quota value daily |
| Recalculation engine | `PortfolioRecalculationService` | ✅ Exists — walks every day, writes quota rows |
| Quota DB table | `investment_portfolio_quota` (`V0_16__...sql`) | ✅ Exists — `dimension_type`, `dimension_key`, `quota_value`, `quota_date` |
| TWR period summary | `PortfolioPerformanceQueryService.getAssetGroupPerformance()` | ✅ Exists — returns a single scalar TWR per period |
| Grid card chart | `InvestmentCard` + `MetricSparkline` | ✅ Exists — wired to `grossValue` time series today |

### What Is Missing

1. The quota table is written at `AssetGroupMapping` sub-group granularity (`bankFixedIncome`, `creditFixedIncome`, `mutualFunds`, `pension`, `variableIncome`, `others`). It does **not** have quota rows at the grid-card level (`fixed-income`, `funds`, `stocks`, `reits`, `crypto`).
2. There is no GraphQL endpoint that returns a TWR time series per grid card class.
3. The `INVESTMENT_GRID_DATA` GQL query fetches `grossValue` per point; no quota data is fetched.

### The Granularity Problem

The grid card class ≠ the existing ASSET_GROUP dimension key. The mapping:

| Grid card key | Asset types in card | Existing quota dimension keys |
|---|---|---|
| `cash` | — | `cash` (1:1, already correct) |
| `fixed-income` | CDB, LCI, LCA, LF, LIG, RDB, LC, ETF_FIXED_INCOME, DEBENTURES, CRI, CRA, TREASURY | `bankFixedIncome` + `creditFixedIncome` (split, needs merge at card level) |
| `funds` | MUTUAL_FUND, PGBL, VGBL | `mutualFunds` + `pension` (split, needs merge at card level) |
| `stocks` | STOCK, STOCK_RIGHTS, BDR, ETF, ETF_INTERNATIONAL | part of `variableIncome` (too broad) |
| `reits` | REAL_ESTATE_FUND, AGRIBUSINESS_FUND | part of `variableIncome` (too broad) |
| `crypto` | CRYPTO | part of `others` (too broad, mixed with OPTIONS, DERIVATIVES) |

**Consequence**: we need to track quotas at grid-card class granularity in addition to sub-group granularity. This requires:
- A new `dimension_type` value `GRID_CLASS` (or a new constraint value in the existing CHECK), OR extending the existing `ASSET_GROUP` check to accept grid class keys.
- The recalculation service to compute and persist quota rows for each grid class key.

---

## Functional Requirements

### FR-1: Quota tracking at grid-card class level

- **Description**: `PortfolioRecalculationService` must compute and persist TWR quota rows for each of the 6 grid class keys (`cash`, `fixed-income`, `funds`, `stocks`, `reits`, `crypto`) alongside existing sub-group rows.
- **Priority**: Critical
- **Acceptance Criteria**:
  - After a portfolio recalculation completes, `investment_portfolio_quota` contains rows with `dimension_type = 'GRID_CLASS'` for each of the 6 grid class keys where the user has holdings.
  - The `quota_value` for `fixed-income` on any given day is computed from the combined daily value of all holdings whose asset type maps to `bankFixedIncome` or `creditFixedIncome`.
  - The `quota_value` for `funds` is computed from holdings mapped to `mutualFunds` or `pension`.
  - The `quota_value` for `stocks` is computed from holdings mapped to asset types `STOCK`, `STOCK_RIGHTS`, `BDR`, `ETF`, `ETF_INTERNATIONAL`.
  - The `quota_value` for `reits` is computed from holdings mapped to asset types `REAL_ESTATE_FUND`, `AGRIBUSINESS_FUND`.
  - The `quota_value` for `crypto` is computed from holdings of type `CRYPTO` only (not the full "others" group).
  - The `quota_value` for `cash` reuses the value already computed for the existing `cash` ASSET_GROUP quota (no separate calculation needed — same holdings).
  - No flow events are subtracted for group-level quotas — the same flow calculation logic used in `PortfolioQuotaCalculationService.calculateGroupQuota()` applies.
  - Existing `ASSET_GROUP` and `PORTFOLIO` rows are unaffected.

### FR-2: New GraphQL query — grid class TWR series

- **Description**: Expose a new GraphQL query that returns the historical TWR quota series for a given grid class key.
- **Priority**: Critical
- **Acceptance Criteria**:
  - New query: `gridClassTwrSeries(financialAccountId: ID, gridClassKey: String!): [GridClassTwrPoint!]!`
  - `GridClassTwrPoint` has fields: `date: Date!`, `quotaValue: Float!`
  - The series contains one entry per available date (daily granularity from the quota table).
  - The query returns an empty list if no quota data exists for the given class.
  - `gridClassKey` must be one of: `cash`, `fixed-income`, `funds`, `stocks`, `reits`, `crypto`. Invalid keys return an empty list (same as no data).
  - The query is scoped to the authenticated user's account. `financialAccountId` narrows to a single custodian when provided; omitting it consolidates across all investment accounts.
  - For the consolidated (no `financialAccountId`) case: the series represents the sum of `totalPortfolioValue` across accounts per date, then recomputes relative quota from that summed series.

### FR-3: Update `INVESTMENT_GRID_DATA` query to fetch TWR series

- **Description**: The frontend `INVESTMENT_GRID_DATA` GQL query must include the new `gridClassTwrSeries` for all 6 grid class keys.
- **Priority**: Critical
- **Acceptance Criteria**:
  - Six `gridClassTwrSeries` aliases are added to `INVESTMENT_GRID_DATA` (one per card: `twrCash`, `twrFixedIncome`, `twrFunds`, `twrStocks`, `twrReits`, `twrCrypto`).
  - Each fetches `{ date quotaValue }`.
  - The existing `perfBankFixedIncome`, `perfCreditFixedIncome`, etc. aliases remain in the query (still needed for the `includedHoldingsCount` and other summary fields used elsewhere). Only the sparkline data source changes.

### FR-4: Sparkline shows normalized TWR percentage

- **Description**: `investmentGridChart.ts` must build the sparkline series from the quota series instead of `grossValue`.
- **Priority**: Critical
- **Acceptance Criteria**:
  - The sparkline value at each point is `(quotaValue[i] / quotaValue[0]) - 1`, i.e., the cumulative return relative to the start of the available history.
  - The first point is always `0` (= 0% return at start).
  - The value is expressed as a decimal fraction (e.g., `0.12` = +12%), matching the existing `PercentageGain` component convention.
  - If the TWR series for a card has fewer than 2 points, the card renders no sparkline (same behavior as today when there is no gross value series).
  - The color of the sparkline continues to be driven by the card's `percentageGain` sign (positive → green, negative → red) — no change to `InvestmentCard`.

### FR-5: Trigger recalculation for existing users

- **Description**: Existing users have no `GRID_CLASS` quota rows. Adding the new dimension must trigger a recalculation.
- **Priority**: High
- **Acceptance Criteria**:
  - A data migration or application startup job enqueues a recalculation job for every `financial_account_id` that has portfolio quota data but no `GRID_CLASS` rows.
  - Alternatively, a DB migration detects accounts needing recalculation and inserts `PENDING` jobs. Preference: DB migration script.
  - The recalculation is idempotent — running it multiple times produces the same result.

---

## Business Rules

### BR-1: Grid class quota does not subtract cross-class flows

- **When**: Computing daily grid-class quota.
- **Then**: Use the same flow-free calculation as `calculateGroupQuota()` — only the portfolio-level quota subtracts flow events. Per-group and per-grid-class quotas use the raw daily return `(endValue − startValue) / startValue` without flow adjustment.
- **Why**: Group-level quotas don't track which flows belong to which sub-group. This is consistent with the existing ASSET_GROUP quota calculation.

### BR-2: Crypto maps to CRYPTO asset type only, not "others"

- **When**: Computing the `crypto` grid class quota.
- **Then**: Include only `AssetType.CRYPTO` holdings. `OPTIONS`, `DERIVATIVES`, and `OTHER` are excluded.
- **Why**: The `InvestmentGrid` card for crypto only shows CRYPTO assets. The broader "others" group would pollute the series.

### BR-3: Stocks and REITs are tracked separately despite sharing ASSET_GROUP `variableIncome`

- **When**: Computing the `stocks` and `reits` grid class quotas.
- **Then**: `stocks` includes `STOCK`, `STOCK_RIGHTS`, `BDR`, `ETF`, `ETF_INTERNATIONAL`. `reits` includes `REAL_ESTATE_FUND`, `AGRIBUSINESS_FUND`. These are disjoint sets.
- **Why**: The grid displays them as separate cards with separate performance indicators.

### BR-4: `cash` grid class quota reuses `cash` ASSET_GROUP quota

- **When**: Computing the `cash` grid class quota.
- **Then**: The recalculation service can write the same `quota_value` to both `ASSET_GROUP/cash` and `GRID_CLASS/cash`, or the query layer can read from `ASSET_GROUP/cash` when `gridClassKey = "cash"`. Preferably: write a `GRID_CLASS/cash` row to keep queries uniform.

### BR-5: TWR series for consolidated view uses value-weighted re-normalization

- **When**: `financialAccountId` is null (consolidated across all custodian accounts).
- **Then**: Sum `totalPortfolioValue` per date across all accounts for the given grid class. Re-compute the relative quota from that summed series as `(summedValue[i] / summedValue[0])`. This is an approximation of TWR for the consolidated view.
- **Why**: Proper multi-account TWR would require tracking flows between accounts, which is out of scope.

---

## Non-Functional Requirements

### Performance

- The `gridClassTwrSeries` query must be fast enough to not slow down the grid page load. The quota table is indexed by `(financial_account_id, dimension_type, dimension_key, quota_date DESC)` — this index covers the query pattern.
- The `INVESTMENT_GRID_DATA` query adds 6 new sub-queries. Each must hit the index. No full table scans.
- Expected data volume: ~1 year of daily rows = ~365 rows per grid class per account. Query should complete in <10ms for a single account.

### Correctness

- The TWR computation in `PortfolioQuotaCalculationService.calculateGroupQuota()` must not be modified. The new grid-class quotas use the same calculation logic.
- The quota value starts at `1.00000000` for the first row and evolves from there. The normalization `(quotaValue[i] / quotaValue[0]) - 1` happens in the frontend, not the backend.

---

## Definitions

### Domain Terms

- **TWR (Time-Weighted Return)**: Return metric that eliminates the distortion caused by capital flows (deposits/withdrawals). Calculated by chain-linking sub-period returns. In this codebase, implemented via the daily quota method.
- **Quota Value**: A unit-based representation of the TWR. Starts at 1.0 and evolves daily: `quotaValue(t) = quotaValue(t-1) × (1 + dailyReturn)` where `dailyReturn = (endValue − startValue) / startValue` (no flow adjustment at group level).
- **Grid Class Key**: One of `cash`, `fixed-income`, `funds`, `stocks`, `reits`, `crypto` — the six cards displayed in the investment grid.
- **ASSET_GROUP**: Existing portfolio dimension tracked at sub-group level (`bankFixedIncome`, `creditFixedIncome`, `mutualFunds`, `pension`, `variableIncome`, `others`, `cash`).
- **GRID_CLASS**: New portfolio dimension to be tracked at grid card level.
- **Sparkline**: The background mini-chart rendered inside each `InvestmentCard`.

### Asset Type → Grid Class Mapping

```
GRID_CLASS "cash"         ← (cash balance, no holding asset type)
GRID_CLASS "fixed-income" ← CDB, RDB, LCI, LCA, LC, LF, LIG, ETF_FIXED_INCOME,
                             DEBENTURES, CRI, CRA, TREASURY
GRID_CLASS "funds"        ← MUTUAL_FUND, PGBL, VGBL
GRID_CLASS "stocks"       ← STOCK, STOCK_RIGHTS, BDR, ETF, ETF_INTERNATIONAL
GRID_CLASS "reits"        ← REAL_ESTATE_FUND, AGRIBUSINESS_FUND
GRID_CLASS "crypto"       ← CRYPTO
```

---

## Implementation Details

### Layer 1 — Database

**No schema migration needed** for the quota table itself — the existing `CHECK (dimension_type IN ('PORTFOLIO', 'ASSET_GROUP'))` must be extended to also accept `'GRID_CLASS'`.

File to modify: `invistus/service/kotlin/banking/src/main/resources/db/migration/`

Create: `V0_XX__investment_portfolio_quota_grid_class.sql`

```sql
-- Extend the dimension_type CHECK to accept GRID_CLASS rows.
ALTER TABLE banking.investment_portfolio_quota
  DROP CONSTRAINT IF EXISTS investment_portfolio_quota_dimension_type_check;

ALTER TABLE banking.investment_portfolio_quota
  ADD CONSTRAINT investment_portfolio_quota_dimension_type_check
  CHECK (dimension_type IN ('PORTFOLIO', 'ASSET_GROUP', 'GRID_CLASS'));

-- Existing indexes already cover the query pattern for the new dimension_type.
-- No new indexes needed.
```

**Recalculation trigger migration** (separate file or same file):

```sql
-- Enqueue recalculation for every financial account that has quota data
-- but no GRID_CLASS rows yet. Uses the recalculation job table.
INSERT INTO banking.investment_portfolio_recalculation_job (
    id,
    financial_account_id,
    account_id,
    status,
    recalculate_from,
    triggered_by,
    created_at,
    updated_at
)
SELECT
    uuidv7(),
    fa.id,
    fa.account_id,
    'PENDING',
    (SELECT MIN(quota_date) FROM banking.investment_portfolio_quota q WHERE q.financial_account_id = fa.id),
    'grid_class_migration_V0_XX',
    NOW(),
    NOW()
FROM banking.financial_accounts fa
WHERE fa.type = 'INVESTMENT'
  AND EXISTS (
      SELECT 1 FROM banking.investment_portfolio_quota q
      WHERE q.financial_account_id = fa.id
  )
  AND NOT EXISTS (
      SELECT 1 FROM banking.investment_portfolio_quota q
      WHERE q.financial_account_id = fa.id
        AND q.dimension_type = 'GRID_CLASS'
  )
  AND NOT EXISTS (
      SELECT 1 FROM banking.investment_portfolio_recalculation_job j
      WHERE j.financial_account_id = fa.id
        AND j.status IN ('PENDING', 'PROCESSING')
  );
```

### Layer 2 — Domain / Backend

#### 2a. `PortfolioDimensionType.kt`

Add `GRID_CLASS` to the enum:

```kotlin
enum class PortfolioDimensionType {
    PORTFOLIO,
    ASSET_GROUP,
    GRID_CLASS,   // ← new
}
```

File: `invistus/service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/portfolio/PortfolioDimensionType.kt`

#### 2b. `GridClassMapping.kt` (new file)

Create a new mapping object parallel to `AssetGroupMapping`:

```kotlin
// invistus/service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/investment/GridClassMapping.kt

object GridClassMapping {
    const val CASH = "cash"
    const val FIXED_INCOME = "fixed-income"
    const val FUNDS = "funds"
    const val STOCKS = "stocks"
    const val REITS = "reits"
    const val CRYPTO = "crypto"

    val ALL_KEYS = setOf(CASH, FIXED_INCOME, FUNDS, STOCKS, REITS, CRYPTO)

    private val GRID_CLASS_BY_ASSET_TYPE: Map<AssetType, String> = mapOf(
        AssetType.CDB to FIXED_INCOME,
        AssetType.RDB to FIXED_INCOME,
        AssetType.LCI to FIXED_INCOME,
        AssetType.LCA to FIXED_INCOME,
        AssetType.LC to FIXED_INCOME,
        AssetType.LF to FIXED_INCOME,
        AssetType.LIG to FIXED_INCOME,
        AssetType.ETF_FIXED_INCOME to FIXED_INCOME,
        AssetType.DEBENTURES to FIXED_INCOME,
        AssetType.CRI to FIXED_INCOME,
        AssetType.CRA to FIXED_INCOME,
        AssetType.TREASURY to FIXED_INCOME,
        AssetType.MUTUAL_FUND to FUNDS,
        AssetType.PGBL to FUNDS,
        AssetType.VGBL to FUNDS,
        AssetType.STOCK to STOCKS,
        AssetType.STOCK_RIGHTS to STOCKS,
        AssetType.BDR to STOCKS,
        AssetType.ETF to STOCKS,
        AssetType.ETF_INTERNATIONAL to STOCKS,
        AssetType.REAL_ESTATE_FUND to REITS,
        AssetType.AGRIBUSINESS_FUND to REITS,
        AssetType.CRYPTO to CRYPTO,
        // Options, derivatives, COE, OTHER → no grid class (excluded from sparkline)
    )

    fun gridClassFor(assetType: AssetType?): String? = GRID_CLASS_BY_ASSET_TYPE[assetType]
}
```

#### 2c. `PortfolioRecalculationService` — compute GRID_CLASS quotas

In `PortfolioRecalculationService.process()`, after computing and saving `dayQuotas` (which currently produces `PORTFOLIO` and `ASSET_GROUP` rows), also compute and save `GRID_CLASS` quotas.

The daily loop in `process()` already calls `mergeGroupValuations(...)` which returns `holdingsValueByGroup: Map<String, DailyGroupValuation>` keyed by ASSET_GROUP keys.

**Change**: Add a second aggregation pass that re-groups the same holdings by grid class. The `HistoricalHoldingTimeline` already has the asset type via `timeline.asset.assetType`.

Approach: modify `mergeGroupValuations` or add a new `mergeGridClassValuations` that groups timeline values by `GridClassMapping.gridClassFor(assetType)`.

```kotlin
// In PortfolioRecalculationService.process() daily loop:

val holdingsValueByGridClass: Map<String, DailyGroupValuation> =
    mergeGridClassValuations(
        timelines = simulationInput.timelines,
        date = date,
        rateMap = simulationInput.rateMap,
        monthlySeries = simulationInput.monthlySeries,
        pricesByAsset = simulationInput.pricesByAsset,
    )

val gridClassQuotas =
    portfolioQuotaCalculationService.calculateGridClassDayQuotas(
        financialAccountId = financialAccountId,
        date = date,
        previousGridClassQuotas = previousGridClassQuotas,
        holdingsValueByGridClass = holdingsValueByGridClass,
    )

portfolioQuotaRepository.batchUpsert(gridClassQuotas).forEach { saved ->
    previousGridClassQuotas[saved.dimensionKey.orEmpty()] = saved
}
```

The `cash` grid class: handled separately. The cash balance is not a holding — it's `investmentCashBalance`. The existing ASSET_GROUP `cash` quota already tracks it. For GRID_CLASS `cash`, the same logic applies: use the portfolio-level flow events filtered to cash-related ones, or simply mirror what ASSET_GROUP `cash` computes. Check how ASSET_GROUP `cash` is currently computed in the recalculation service and replicate at GRID_CLASS level.

**`previousGridClassQuotas`**: new cursor map, initialized from `anchorQuotas[PortfolioDimensionType.GRID_CLASS]` (same pattern as `previousGroupQuotas`).

#### 2d. `PortfolioQuotaCalculationService` — new method for GRID_CLASS

Add `calculateGridClassDayQuotas()` that wraps per-class calls to `calculateGroupQuota()` but writes rows with `dimensionType = PortfolioDimensionType.GRID_CLASS`:

```kotlin
fun calculateGridClassDayQuotas(
    financialAccountId: UUID,
    date: LocalDate,
    previousGridClassQuotas: Map<String, InvestmentPortfolioQuota>,
    holdingsValueByGridClass: Map<String, DailyGroupValuation>,
): List<InvestmentPortfolioQuota>
```

Internally calls `calculateGroupQuota()` for each entry in `holdingsValueByGridClass`, substituting `PortfolioDimensionType.GRID_CLASS` as the dimension type.

#### 2e. `PortfolioQuotaRepository` — reads for new dimension

The existing `findByAccountAndDateRange` and `findLatestBefore` methods accept `dimensionType` as a parameter. No new repository methods are needed — callers pass `PortfolioDimensionType.GRID_CLASS`.

Verify the existing query implementations accept the new enum value correctly (check JDBC/Micronaut Data mapping for enum-to-string).

#### 2f. `InvestmentDTO.kt` — new DTO

```kotlin
data class GridClassTwrPointDTO(
    val date: LocalDate,
    val quotaValue: Double,
)
```

#### 2g. `PortfolioPerformanceQueryService` — new query method

```kotlin
fun getGridClassTwrSeries(
    financialAccountId: UUID?,
    accountId: UUID,
    gridClassKey: String,
): List<GridClassTwrPointDTO>
```

Implementation for scoped (single `financialAccountId`):
1. Validate `gridClassKey` is in `GridClassMapping.ALL_KEYS` → return empty if not.
2. Query `portfolioQuotaRepository.findByAccountAndDateRange(financialAccountId, GRID_CLASS, gridClassKey, from=epoch, to=today)`.
3. Map each quota row to `GridClassTwrPointDTO(date = quota.quotaDate, quotaValue = quota.quotaValue.toDouble())`.
4. Return sorted ascending by date.

Implementation for consolidated (no `financialAccountId`):
1. Collect all investment `financialAccountId`s for the account.
2. Fetch `GRID_CLASS / gridClassKey` quota series for each account.
3. Merge by summing `totalPortfolioValue` per date across accounts.
4. Re-compute relative quota from the summed series: normalize so that first-point quota = 1.0, each subsequent point = `sum_value[i] / sum_value[0]`.
5. Return as `GridClassTwrPointDTO` list.

#### 2h. `InvestmentResolver.kt` — new GraphQL resolver method

```kotlin
@GraphQLQuery
fun gridClassTwrSeries(
    financialAccountId: ID?,
    gridClassKey: String,
    @GraphQLRootContext ctx: GraphQLContext,
): List<GridClassTwrPointDTO> {
    val accountId = ctx.accountId()
    return portfolioPerformanceQueryService.getGridClassTwrSeries(
        financialAccountId = financialAccountId?.let { UUID.fromString(it) },
        accountId = accountId,
        gridClassKey = gridClassKey,
    )
}
```

### Layer 3 — GraphQL Schema

File: `invistus/contracts/graphql/subgraphs/banking/schema.graphqls`

Add new type and query field:

```graphql
"""A single point in a grid-class TWR quota time series."""
type GridClassTwrPoint {
  date: Date!
  """Raw quota value (starts at 1.0 on first recorded day). Normalize to % return as (quotaValue / firstPoint.quotaValue) - 1."""
  quotaValue: Float!
}

extend type Query {
  """
  TWR quota time series for a grid card class (cash, fixed-income, funds, stocks, reits, crypto).
  Returns one point per recorded day, ascending. Returns empty list if no data is available.
  """
  gridClassTwrSeries(
    """Narrow to a specific custodian account. Omit to consolidate across all investment accounts."""
    financialAccountId: ID
    gridClassKey: String!
  ): [GridClassTwrPoint!]!
}
```

Regenerate supergraph after schema change.

### Layer 4 — Frontend

#### 4a. Regenerate GQL types

After schema change, run codegen to update `queries.generated.ts` and `graphql.ts`.

#### 4b. Update `INVESTMENT_GRID_DATA` query

File: `invistus/web/src/lib/graphql/operations/banking/queries.ts`

Add 6 new aliases inside `INVESTMENT_GRID_DATA`:

```graphql
twrCash: gridClassTwrSeries(
  financialAccountId: $financialAccountId
  gridClassKey: "cash"
) {
  date
  quotaValue
}
twrFixedIncome: gridClassTwrSeries(
  financialAccountId: $financialAccountId
  gridClassKey: "fixed-income"
) {
  date
  quotaValue
}
twrFunds: gridClassTwrSeries(
  financialAccountId: $financialAccountId
  gridClassKey: "funds"
) {
  date
  quotaValue
}
twrStocks: gridClassTwrSeries(
  financialAccountId: $financialAccountId
  gridClassKey: "stocks"
) {
  date
  quotaValue
}
twrReits: gridClassTwrSeries(
  financialAccountId: $financialAccountId
  gridClassKey: "reits"
) {
  date
  quotaValue
}
twrCrypto: gridClassTwrSeries(
  financialAccountId: $financialAccountId
  gridClassKey: "crypto"
) {
  date
  quotaValue
}
```

Keep existing `perfBankFixedIncome`, `perfCreditFixedIncome`, etc. aliases — they're still used for `includedHoldingsCount` and other data.

#### 4c. Update `investmentGridChart.ts`

File: `invistus/web/src/app/personal-finance/investments/components/grid/investmentGridChart.ts`

Replace `buildChartSeriesByClass` to use TWR quota data:

```typescript
type TwrPoint = { date?: unknown; quotaValue?: number | null };

export function twrPointsToSparklineSeries(
  points: readonly TwrPoint[] | null | undefined,
): TimeSeriesDataPoint[] {
  const valid = (points ?? [])
    .filter((p) => typeof p?.quotaValue === "number" && Number.isFinite(p.quotaValue))
    .map((p, idx) => ({
      quarter: p.date != null ? String(p.date) : `P${idx + 1}`,
      value: p.quotaValue as number,
    }));

  if (valid.length < 2) return [];

  const base = valid[0].value;
  if (!base || base === 0) return [];

  return valid.map((p) => ({
    quarter: p.quarter,
    value: p.value / base - 1,   // normalized TWR: 0.0 at start, e.g. 0.12 = +12%
  }));
}

export function buildChartSeriesByClass(
  gridData: InvestmentGridDataQuery | undefined | null,
): Record<InvestmentGridClassKey, TimeSeriesDataPoint[]> {
  return {
    cash: twrPointsToSparklineSeries(gridData?.twrCash),
    "fixed-income": twrPointsToSparklineSeries(gridData?.twrFixedIncome),
    funds: twrPointsToSparklineSeries(gridData?.twrFunds),
    stocks: twrPointsToSparklineSeries(gridData?.twrStocks),
    reits: twrPointsToSparklineSeries(gridData?.twrReits),
    crypto: twrPointsToSparklineSeries(gridData?.twrCrypto),
  };
}
```

Remove `mergePerformancePointGroups` and `performancePointsToSparklineSeries` if they are no longer used elsewhere. Check for other consumers before deleting.

---

## Testing Strategy

### Backend Unit Tests

#### `GridClassMappingTest`

File: new test alongside `GridClassMapping.kt`

- `gridClassFor(STOCK)` returns `"stocks"`
- `gridClassFor(REAL_ESTATE_FUND)` returns `"reits"`
- `gridClassFor(CRYPTO)` returns `"crypto"`
- `gridClassFor(OPTION)` returns `null` (not mapped)
- `gridClassFor(null)` returns `null`
- `ALL_KEYS` contains exactly the 6 expected values

#### `PortfolioQuotaCalculationServiceTest` — GRID_CLASS extension

Existing test file: `PortfolioQuotaCalculationServiceTest.kt`

Add test cases:
- `calculateGridClassDayQuotas` with an empty map returns empty list
- `calculateGridClassDayQuotas` with a single grid class produces a row with `dimensionType = GRID_CLASS`
- First day for a class (no previous quota): quota_value = `INITIAL_QUOTA_VALUE` (1.0)
- Subsequent day with no daily change: quota_value stays the same
- Day with +10% value increase: quota_value increases by factor 1.10
- Day with -5% value decrease: quota_value decreases by factor 0.95
- Multiple grid classes: each class gets its own quota row

#### `PortfolioRecalculationServiceTest` / `PortfolioRecalculationTwrAfterHoldingDeleteTest`

Existing test files — extend to verify GRID_CLASS rows:
- After `process()`, assert `GRID_CLASS/fixed-income` rows exist for accounts with fixed-income holdings
- After deleting a holding and re-running `process()`, assert GRID_CLASS rows are recalculated from `recalculateFrom`
- Assert that the quota evolution is consistent: if a holding gains 10% then loses 10%, the final GRID_CLASS quota_value ≈ 0.99 (not 1.0) — TWR property

#### `PortfolioPerformanceQueryServiceTest` — new `getGridClassTwrSeries` tests

Existing test file: `PortfolioPerformanceQueryServiceTest.kt`

Add:
- Empty result when no `GRID_CLASS` quota rows exist for the account
- Empty result for unknown `gridClassKey`
- Returns correct points sorted ascending by date
- Returns correct points for scoped `financialAccountId`
- Consolidated view (no `financialAccountId`): sums values across accounts and re-normalizes
- Consolidated view with two accounts: result reflects combined portfolio TWR

### Frontend Unit Tests

#### `investmentGridChart.test.ts`

File: adjacent to `investmentGridChart.ts` (create if not exists)

Test `twrPointsToSparklineSeries`:
- Empty input → empty array
- Single point → empty array (need at least 2 for a series)
- Two points with same quota → `[0, 0]`
- Two points where quota doubled → `[0, 1.0]` (= 100% return)
- First quota value is 0 → empty array (division by zero guard)
- Non-finite quota values are filtered out
- Points with `null` quotaValue are filtered out
- Normalization: first value is always 0.0

Test `buildChartSeriesByClass`:
- With null `gridData` → all 6 keys return `[]`
- With all `twr*` fields null → all 6 keys return `[]`
- With `twrFixedIncome` having 3 valid points → returns normalized series for `fixed-income`

---

## Scenarios

### Scenario: Fixed income card with 12 months of history

- **Type**: Happy Path
- **Context**: User has 2 CDB holdings and 1 LCI holding, all in the same financial account. Portfolio recalculation has completed. 12 months of `GRID_CLASS/fixed-income` quota rows exist.
- **Action**: User opens the investments page.
- **Expected Behavior**: The fixed-income card shows a sparkline with 12 data points. The curve starts at 0% and shows the cumulative TWR over 12 months. If the total return is ~10%, the last point is near +0.10.
- **Validation Points**:
  - `twrFixedIncome` in `INVESTMENT_GRID_DATA` response has 12 non-null points.
  - `investmentGridChart.buildChartSeriesByClass` returns 12 normalized points for `"fixed-income"`.
  - The `MetricSparkline` renders a curve starting at 0 and ending at ~0.10.

### Scenario: New user with no history

- **Type**: Edge Case
- **Context**: User has just added their first holding. No recalculation has completed yet; no quota rows exist.
- **Expected Behavior**: All 6 cards render with no sparkline (empty card area). No error is thrown.
- **Validation Points**:
  - `twrFixedIncome` returns `[]`.
  - `twrPointsToSparklineSeries([])` returns `[]`.
  - `InvestmentCard` receives `chartData = []`, renders without the mini-chart section.

### Scenario: Crypto card — only CRYPTO assets contribute, not OPTIONS

- **Type**: Business Rule Validation
- **Context**: User has 1 CRYPTO holding and 1 OPTION holding. Both share the same custodian.
- **Expected Behavior**: The `crypto` grid class quota is computed only from the CRYPTO holding. The OPTION holding is excluded.
- **Validation Points**:
  - `GRID_CLASS/crypto` quota rows exist and reflect only the CRYPTO holding value.
  - No `GRID_CLASS` row exists for OPTIONS (they have no mapped grid class key).

### Scenario: Mid-year purchase increases portfolio value but not TWR

- **Type**: Business Rule — TWR Correctness
- **Context**: User has R$10k in a CDB (yield: +10%). In month 6, they deposit R$50k into a new CDB at the same rate. By year end, grossValue grew by ~R$56k (mostly from the new deposit), but the actual yield was still ~10%.
- **Expected Behavior**: The sparkline's final point is near +0.10 (≈ +10%), not a multiple of that. The sparkline curve does not spike sharply in month 6 despite the large value jump.
- **Validation Points**:
  - The GRID_CLASS quota for `fixed-income` on month 6 reflects only the daily return of existing holdings, not the new deposit.
  - The normalized TWR sparkline ends near +0.10, regardless of the deposit size.

### Scenario: Account with two custodians, consolidated view

- **Type**: Consolidated View
- **Context**: User has stocks in Broker A and stocks in Broker B. `financialAccountId` is null (consolidated mode).
- **Expected Behavior**: The stocks card sparkline reflects the combined portfolio.
- **Validation Points**:
  - `getGridClassTwrSeries(financialAccountId=null, gridClassKey="stocks")` queries both accounts, sums `totalPortfolioValue` per date, and re-normalizes.
  - The result is a single series, not two.

---

## Code References

**Key Files to Modify**:

| File | Change |
|---|---|
| `service/kotlin/banking/src/main/kotlin/.../domain/portfolio/PortfolioDimensionType.kt` | Add `GRID_CLASS` enum value |
| `service/kotlin/banking/src/main/kotlin/.../service/investment/GridClassMapping.kt` | **New file** — asset type → grid class key mapping |
| `service/kotlin/banking/src/main/kotlin/.../service/investment/PortfolioRecalculationService.kt` | Compute and persist `GRID_CLASS` quotas in daily loop |
| `service/kotlin/banking/src/main/kotlin/.../service/investment/PortfolioQuotaCalculationService.kt` | Add `calculateGridClassDayQuotas()` method |
| `service/kotlin/banking/src/main/kotlin/.../service/investment/PortfolioPerformanceQueryService.kt` | Add `getGridClassTwrSeries()` method |
| `service/kotlin/banking/src/main/kotlin/.../graphql/investment/InvestmentDTO.kt` | Add `GridClassTwrPointDTO` |
| `service/kotlin/banking/src/main/kotlin/.../graphql/investment/InvestmentResolver.kt` | Add `gridClassTwrSeries` query resolver |
| `contracts/graphql/subgraphs/banking/schema.graphqls` | Add `GridClassTwrPoint` type and `gridClassTwrSeries` query |
| `service/kotlin/banking/src/main/resources/db/migration/V0_XX__...sql` | **New file** — extend CHECK, trigger recalculation jobs |
| `web/src/lib/graphql/operations/banking/queries.ts` | Add `twr*` aliases to `INVESTMENT_GRID_DATA` |
| `web/src/app/personal-finance/investments/components/grid/investmentGridChart.ts` | Replace `buildChartSeriesByClass` to use quota data |

**Key Files to Read Before Implementing**:

| File | Why |
|---|---|
| `service/kotlin/banking/src/main/kotlin/.../service/investment/PortfolioRecalculationService.kt` | Understand the daily loop and cursor state before adding GRID_CLASS |
| `service/kotlin/banking/src/main/kotlin/.../service/investment/recalculation/HistoricalHoldingTimeline.kt` | Understand how holdings are valued per day |
| `service/kotlin/banking/src/main/kotlin/.../service/investment/PortfolioQuotaCalculationService.kt` | Copy the `calculateGroupQuota` logic for the new method |
| `service/kotlin/banking/src/main/kotlin/.../service/investment/AssetGroupMapping.kt` | Reference for mapping pattern and constant naming |
| `service/kotlin/banking/src/main/resources/db/migration/V0_16__investment_performance_history_tables.sql` | Understand existing quota table schema |
| `web/src/app/personal-finance/investments/components/grid/investmentGridChart.ts` | Full file — replace its logic |
| `web/src/app/personal-finance/investments/components/grid/useInvestmentGrid.ts` | Understand how `chartSeriesByKey` flows to cards |

---

## Open Questions

- [ ] **Cash grid class quota**: The `cash` balance is tracked differently from holdings (no individual asset types). Verify how `ASSET_GROUP/cash` is currently computed in `PortfolioRecalculationService` and replicate exactly for `GRID_CLASS/cash`. If they are identical, consider reading from `ASSET_GROUP/cash` in the query layer and skipping a separate `GRID_CLASS/cash` write.
- [ ] **`mergeGroupValuations` function**: This function in `PortfolioRecalculationService` aggregates by ASSET_GROUP. Determine if it can be reused with a different mapping function, or if a new `mergeGridClassValuations` is cleaner.
- [ ] **Supergraph regeneration**: After schema change, both `supergraph.graphql` and `supergraph.local.graphql` and `supergraph.staging.graphql` need to be regenerated. Confirm the regeneration process (Rover CLI or CI pipeline) before implementing.
- [ ] **Frontend codegen**: Run `pnpm codegen` (or equivalent) after the GQL query change to regenerate `queries.generated.ts`. Confirm the codegen command.

---

## References

- `docs/05-backend/patterns/service-pattern.md` — service layer patterns
- `docs/05-backend/patterns/repository-pattern.md` — repository patterns
- `docs/07-frontend/patterns/graphql-query-pattern.md` — frontend GQL query patterns
- `service/kotlin/banking/src/test/kotlin/.../service/investment/PortfolioRecalculationTwrAfterHoldingDeleteTest.kt` — reference test for TWR correctness after mutations
- `service/kotlin/banking/src/test/kotlin/.../service/investment/PortfolioPerformanceQueryServiceTest.kt` — reference test for query service

---

---

## Phase 2 — Sparkline Response Size Optimization

### Problem

With daily TWR quota rows going back to the account's first transaction, `gridClassTwrSeries` can return thousands of points per grid class. In practice, fixed income with ~2 years of history returns ~1,827 points, weighing ~21 KB in JSON. For a sparkline rendered at ~120 px wide, this is pure waste:

- A sparkline cannot display more resolution than ~1 point per pixel — ~120 useful points maximum.
- 6 classes × ~1,800 points × multiple investment accounts = several hundred KB on the initial grid load.
- At 10 years of history the problem gets proportionally worse.

### Solution

Two independent levers, both should be applied:

#### Lever 1 — Window limit (mandatory, implement first)

Cap the series to a fixed lookback window. For a grid card sparkline, **1 year** is the right default: it shows trend without loading years of irrelevant history, and matches what users expect from a "how am I doing?" widget.

This is a backend-only change. The GQL query gets a `period` argument; the resolver passes it as a `from` date to the repository query.

#### Lever 2 — Downsampling (optional, implement if needed)

Even within 1 year, 252 daily trading points is more than a 120px sparkline can render. Weekly sampling (last closing value per week) reduces to ~52 points per year with identical visual output. This can be done either in the repository (SQL `GROUP BY` weekly bucket) or in the service layer.

**Decision**: implement Lever 1 now (hardcoded 1-year default). If response sizes are still a concern after that, add Lever 2.

---

### FR-6: Window limit for `gridClassTwrSeries`

- **Description**: `gridClassTwrSeries` accepts an optional `period` argument that limits how far back the series goes. The grid card hardcodes `ONE_YEAR`.
- **Priority**: High
- **Acceptance Criteria**:
  - New GraphQL argument: `period: GridClassTwrPeriod` (enum, optional).
  - Default when omitted: `ONE_YEAR` (1 year lookback from today).
  - Supported values: `SIX_MONTHS`, `ONE_YEAR`, `THREE_YEARS`, `ALL` (all available history).
  - The `from` date passed to the repository is computed as: `today.minus(period)`, or `LocalDate.MIN` for `ALL`.
  - The series always starts with the first available point on or after `from`. The normalization base is still the **first point returned**, not an artificial 0.
  - The frontend `INVESTMENT_GRID_DATA` query passes `period: ONE_YEAR` explicitly (not relying on default) so intent is clear.
  - Response for a 1-year window with daily rows: ≤ 366 points. Response size for all 6 classes: < 30 KB total.

---

### Implementation Details — Phase 2

#### Layer 2 — Backend

##### 2i. New enum `GridClassTwrPeriod`

Add to `InvestmentDTO.kt` or a new file alongside the DTO:

```kotlin
enum class GridClassTwrPeriod {
    SIX_MONTHS,
    ONE_YEAR,
    THREE_YEARS,
    ALL,
}
```

##### 2j. `PortfolioPerformanceQueryService.getGridClassTwrSeries` — add `period` param

```kotlin
fun getGridClassTwrSeries(
    financialAccountId: UUID?,
    accountId: UUID,
    gridClassKey: String,
    period: GridClassTwrPeriod = GridClassTwrPeriod.ONE_YEAR,
): List<GridClassTwrPointDTO>
```

Compute `from`:

```kotlin
val today = LocalDate.now()
val from = when (period) {
    GridClassTwrPeriod.SIX_MONTHS  -> today.minusMonths(6)
    GridClassTwrPeriod.ONE_YEAR    -> today.minusYears(1)
    GridClassTwrPeriod.THREE_YEARS -> today.minusYears(3)
    GridClassTwrPeriod.ALL         -> LocalDate.of(1970, 1, 1)
}
```

Pass `from` to `portfolioQuotaRepository.findByAccountAndDateRange(...)` instead of the hardcoded `LocalDate.of(1970, 1, 1)`.

The same `from` applies to the consolidated path inside `buildConsolidatedGridClassSeries` — pass it through as a parameter.

##### 2k. `InvestmentResolver.kt` — expose `period` on the resolver

```kotlin
fun gridClassTwrSeries(
    financialAccountId: String?,
    gridClassKey: String,
    period: GridClassTwrPeriod?,
    principal: RequestPrincipal,
): List<GridClassTwrPointDTO> =
    portfolioPerformanceQueryService.getGridClassTwrSeries(
        financialAccountId = financialAccountId?.let { UUID.fromString(it) },
        accountId = principal.accountId,
        gridClassKey = gridClassKey,
        period = period ?: GridClassTwrPeriod.ONE_YEAR,
    )
```

#### Layer 3 — GraphQL Schema

Update the Kotlin service schema at `service/kotlin/banking/src/main/resources/graphql/schema.graphqls` (this is synced to contracts — do **not** edit the contracts file directly):

```graphql
enum GridClassTwrPeriod {
  SIX_MONTHS
  ONE_YEAR
  THREE_YEARS
  ALL
}

# Update existing gridClassTwrSeries field:
gridClassTwrSeries(
  financialAccountId: ID
  gridClassKey: String!
  """Lookback window. Defaults to ONE_YEAR when omitted."""
  period: GridClassTwrPeriod
): [GridClassTwrPoint!]!
```

After editing, run `./neotool graphql all --all` to sync and recompose the supergraph, then run `pnpm codegen`.

> **Important**: Always edit `service/kotlin/banking/src/main/resources/graphql/schema.graphqls`. The file at `contracts/graphql/subgraphs/banking/schema.graphqls` is generated by the sync step and will be overwritten.

#### Layer 4 — Frontend

##### 4d. Update `INVESTMENT_GRID_DATA` query aliases

In `web/src/lib/graphql/operations/banking/queries.ts`, add `period: ONE_YEAR` to all 6 `gridClassTwrSeries` aliases:

```graphql
twrCash: gridClassTwrSeries(
  financialAccountId: $financialAccountId
  gridClassKey: "cash"
  period: ONE_YEAR
) {
  date
  quotaValue
}
# ... same for twrFixedIncome, twrFunds, twrStocks, twrReits, twrCrypto
```

No changes to `investmentGridChart.ts` — the normalization logic is period-agnostic.

---

### Testing — Phase 2

#### Backend

Add to `PortfolioPerformanceQueryServiceTest`:

- `getGridClassTwrSeries` with `period = ONE_YEAR`: only returns rows from the last year, not older ones.
- `getGridClassTwrSeries` with `period = ALL`: returns all rows regardless of age.
- `getGridClassTwrSeries` with `period = SIX_MONTHS`: only returns rows from the last 6 months.
- Normalization base: when `period = ONE_YEAR` and the first available row within that window is not the account's all-time first row, normalization still uses the first returned row as base (not quota_value = 1.0 from the account's inception).

#### Frontend

No new unit tests needed — `twrPointsToSparklineSeries` is period-agnostic and already tested. Verify in the browser that the response for `InvestmentGridData` is < 30 KB total after the change.

---

### Expected Impact

| Before | After (ONE_YEAR window) |
|---|---|
| ~1,827 points / class / year | ≤ 366 points / class |
| ~21 KB for fixed-income alone | < 5 KB for fixed-income |
| ~120+ KB for 6 grid classes on long-lived accounts | < 30 KB total payload target for all 6 classes |
| Grows unboundedly with account age | Capped permanently |

---

## Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-04-13 | — | Initial version |
| 1.1.0 | 2026-04-14 | — | Phase 2: sparkline response size optimization via period window |
