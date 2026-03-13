---
title: Fixed Income Holding Valuation — Implementation Backlog
type: backlog
category: investment-portfolio
status: draft
version: 1.0.0
tags: [investment, fixed-income, valuation, backlog, cdi, ipca, ir, iof]
ai_optimized: true
search_keywords: [fixed income valuation backlog, estimated gross value, ir iof calculation backlog, holding valuation stories]
related:
  - 03-features/investment-portfolio/fixed-income-valuation.md
  - 03-features/financial_data/brazilian_economic_indicators/brazilian-economic-indicators.md
  - 08-workflows/feature-development.md
last_updated: 2026-03-13
---

# Fixed Income Holding Valuation — Implementation Backlog

> **Purpose**: Deliver estimated current values (gross, net, IR, IOF) for manually recorded fixed income holdings, computed in the banking service using contractual terms and current economic indicators.

---

## Hierarchy

```
Feature: Fixed Income Holding Valuation
├── Epic 1: Calculation Engine (banking service)
├── Epic 2: GraphQL Schema & Resolver
├── Epic 3: Indicator Integration
└── Epic 4: Frontend Display
```

---

## Epic 1: Calculation Engine

**Goal**: Implement a pure, testable calculation service in the banking service that computes estimated gross value, IR, and IOF given a holding's contractual terms and an indicator rate map.

---

### Story 1.1 — Accrued Gross Value Calculator

**As a** system component,
**I want** to compute the accrued gross value of a fixed income holding,
**So that** I can display an estimated current value when no provider-synced value is available.

**Acceptance Criteria**:
- Handles all supported indexers: `PRE_FIXADO`, `CDI`, `DI`, `SELIC`, `IPCA`, `IGP_M`, `IGP_DI`, `INPC`, `TR`.
- Returns null for `OUTROS`, `BCP`, `TLC`, or when required indicator is missing.
- Caps `t` (business days elapsed) at `maturityDate` when the holding is past maturity.
- Returns `principal` when `t = 0`.
- All formulas use 252 business days per year convention.

**Tasks**:

- [ ] **T1.1.1** Create `FixedIncomeValuationService` in `banking` service under `domain/investment/`.
  - Input: `Holding`, `Map<Indexer, BigDecimal>` (annual rates keyed by indexer)
  - Output: `HoldingValuationResult(grossValue, incomeTax, iof, netValue, valuationSource)`

- [ ] **T1.1.2** Implement `businessDaysBetween(start: LocalDate, end: LocalDate): Int`.
  - Use 252 bd/year approximation: `(calendarDays / 365.0) × 252`
  - Alternatively, if a business day calendar is available in the codebase, use it.

- [ ] **T1.1.3** Implement accrual formula for `PRE_FIXADO`.
  - `grossValue = principal × (1 + preFixedRate) ^ (t / 252)`

- [ ] **T1.1.4** Implement accrual formula for `CDI` / `DI`.
  - `cdi_daily = (1 + cdi_annual) ^ (1/252) - 1`
  - `grossValue = principal × (1 + cdi_daily × indexerPercentage) ^ t`

- [ ] **T1.1.5** Implement accrual formula for `SELIC`.
  - Same as CDI formula using SELIC annual rate.

- [ ] **T1.1.6** Implement accrual formula for `IPCA` (with optional spread).
  - If `preFixedRate` is null or zero: `grossValue = principal × (1 + ipca_annual) ^ (t / 252)`
  - If `preFixedRate > 0`: `annual_effective = (1 + ipca_annual) × (1 + preFixedRate) - 1`

- [ ] **T1.1.7** Implement accrual formula for `IGP_M`, `IGP_DI`, `INPC`, `TR`.
  - Same pattern as IPCA with respective indicator rate.

- [ ] **T1.1.8** Return null for unsupported indexers (`OUTROS`, `BCP`, `TLC`) or missing indicator data.

---

### Story 1.2 — IR Calculation

**As a** system component,
**I want** to compute the provisioned income tax on a fixed income gain,
**So that** the estimated net value reflects the actual take-home amount.

**Acceptance Criteria**:
- Uses the regressive IR table based on calendar days (not business days).
- Returns zero for `isTaxExempt = true` holdings.
- Returns zero when `gain <= 0`.
- IOF is deducted from the gain before applying IR (correct Brazilian tax order).

**Tasks**:

- [ ] **T1.2.1** Implement `irRate(calendarDays: Int): BigDecimal` in `FixedIncomeValuationService`.
  - ≤ 180 days → 22.5%
  - 181–360 days → 20.0%
  - 361–720 days → 17.5%
  - > 720 days → 15.0%

- [ ] **T1.2.2** Implement IR computation.
  ```
  gain = grossValue - principal
  iof_amount = computeIof(gain, calendarDays)
  taxable_gain = gain - iof_amount
  estimatedIncomeTax = if (isTaxExempt || taxable_gain <= 0) 0 else taxable_gain × irRate
  ```

---

### Story 1.3 — IOF Calculation

**As a** system component,
**I want** to compute the IOF tax for holdings redeemed within 30 calendar days of issue,
**So that** very recent investments show the correct early-redemption cost.

**Acceptance Criteria**:
- Returns zero for `calendarDays >= 30`.
- Uses the 30-entry daily regressive IOF table.
- Applied to gross gain (before IR).

**Tasks**:

- [ ] **T1.3.1** Implement `iofRate(calendarDays: Int): BigDecimal` with the full 30-day table.
  - Day 1 → 96%, Day 2 → 93%, ..., Day 29 → 3%, Day 30+ → 0%

- [ ] **T1.3.2** Implement IOF computation.
  ```
  gain = grossValue - principal
  estimatedIof = if (calendarDays >= 30 || gain <= 0) 0 else gain × iofRate(calendarDays)
  ```

---

### Story 1.4 — Unit Tests for Calculation Engine

**Tasks**:

- [ ] **T1.4.1** Test `PRE_FIXADO` formula with known values (e.g., R$10.000 at 12% a.a. for 252 bd → R$11.200).
- [ ] **T1.4.2** Test `CDI` formula at 110% of 13.25% a.a. for 126 bd.
- [ ] **T1.4.3** Test `IPCA+` formula with 6.5% IPCA + 6% spread for 252 bd.
- [ ] **T1.4.4** Test IR brackets at boundaries (day 180, 181, 360, 361, 720, 721).
- [ ] **T1.4.5** Test IOF at day 1, 15, 29, and 30+.
- [ ] **T1.4.6** Test combined IR + IOF for a holding on day 10.
- [ ] **T1.4.7** Test `isTaxExempt = true` → IR = 0.
- [ ] **T1.4.8** Test past-maturity holding: value caps at maturity date.
- [ ] **T1.4.9** Test null/missing indicator → returns null gracefully.

---

## Epic 2: GraphQL Schema & Resolver

**Goal**: Expose estimated valuation fields on `InvestmentHolding` so the frontend can consume them without knowing about calculation logic.

---

### Story 2.1 — Schema Extension

**Tasks**:

- [ ] **T2.1.1** Add `ValuationSource` enum to `banking/schema.graphqls`:
  ```graphql
  """
  Indicates the origin and reliability of a holding's current value.
  """
  enum ValuationSource {
    "Value provided by the data provider (Open Finance / file import). Authoritative."
    PROVIDER

    "Value computed from the contracted rate with no external variables (PRE_FIXADO only). Exact."
    CALCULATED

    "Value projected using latest economic indicators (CDI, IPCA, etc.). Approximate."
    ESTIMATED

    "Value cannot be determined (unsupported indexer, missing data, or no issueDate)."
    UNAVAILABLE
  }
  ```

- [ ] **T2.1.2** Add computed valuation fields to `InvestmentHolding` in `banking/schema.graphqls`:
  ```graphql
  estimatedGrossValue: Float
  estimatedNetValue: Float
  estimatedIncomeTax: Float
  estimatedIof: Float
  valuationSource: ValuationSource!
  ```

- [ ] **T2.1.3** Regenerate supergraph schema / run federation compose to verify no breaking changes.

- [ ] **T2.1.4** Add fields to `InvestmentHoldingDTO` in `InvestmentDTO.kt`.

---

### Story 2.2 — Resolver Integration

**Tasks**:

- [ ] **T2.2.1** In `InvestmentResolver`, inject `FixedIncomeValuationService` and `IndicatorRateProvider` (new — see Epic 3).

- [ ] **T2.2.2** In `allInvestmentHoldings` resolver: after mapping holdings to DTOs, for each holding:
  - If `grossValue != null` → `valuationSource = PROVIDER`, leave estimated fields null.
  - If `grossValue == null` → call `FixedIncomeValuationService.estimate(holding, rateMap)`, populate estimated fields, set `valuationSource` from result.

- [ ] **T2.2.3** Batch indicator fetch: fetch the rate map **once per request** (not once per holding) before the holdings loop.

- [ ] **T2.2.4** Handle service errors gracefully: if indicator fetch fails, log a warning and set all estimated fields to null without failing the query.

---

## Epic 3: Indicator Integration

**Goal**: Create a rate provider in the banking service that fetches the latest indicator values from the financial data service via inter-service call.

---

### Story 3.1 — Indicator Rate Provider

**As a** banking service component,
**I want** to fetch the latest CDI, SELIC, IPCA, IGP-M, TR, and other rates from the financial data service,
**So that** the valuation engine always uses up-to-date rates.

**Tasks**:

- [ ] **T3.1.1** Create `IndicatorRateProvider` interface and implementation in `banking` service.
  - Method: `fun getLatestRates(): Map<Indexer, BigDecimal>`
  - Calls `indicatorsLatest` GraphQL query on financial-data service (or direct DB read if services share DB / use internal API).

- [ ] **T3.1.2** Map financial-data indicator labels to `Indexer` enum values per the mapping table in the feature spec.

- [ ] **T3.1.3** Add in-memory TTL cache (e.g., 1 hour) on the rate map to avoid N requests per query.

- [ ] **T3.1.4** Write unit test with mocked indicator response covering: CDI present, IPCA present, unknown label ignored, service unavailable → empty map.

---

## Epic 4: Frontend Display

**Goal**: Surface estimated values on the investment portfolio page with a clear visual indicator that the value is estimated.

---

### Story 4.1 — Update GraphQL Query

**Tasks**:

- [ ] **T4.1.1** Add `estimatedGrossValue`, `estimatedNetValue`, `valuationSource` to the `ALL_INVESTMENT_HOLDINGS` query in `queries.ts`.

---

### Story 4.2 — Display Estimated Value

**As a** user,
**I want** to see an estimated current value for my manually recorded fixed income holdings,
**So that** I can track the approximate performance of my investments without waiting for a bank sync.

**Acceptance Criteria**:
- `PROVIDER`: display value normally, no indicator.
- `CALCULATED`: display value with a small note (e.g., lock icon or "calculated" badge). No uncertainty warning needed — this is exact.
- `ESTIMATED`: display value with `~` prefix and a warning tooltip. User must understand this is approximate.
- `UNAVAILABLE`: show `—`.

**Tasks**:

- [ ] **T4.2.1** Create utility `resolveHoldingValue(holding): { value: number | null, source: ValuationSource }`:
  ```typescript
  if (holding.grossValue != null) return { value: holding.grossValue, source: 'PROVIDER' }
  if (holding.estimatedGrossValue != null) return { value: holding.estimatedGrossValue, source: holding.valuationSource }
  return { value: null, source: 'UNAVAILABLE' }
  ```

- [ ] **T4.2.2** Update `InvestmentTable` (or equivalent) to use `resolveHoldingValue` and render the appropriate indicator per `ValuationSource`:
  - `PROVIDER` → no indicator
  - `CALCULATED` → small icon (e.g., calculator or lock), tooltip: "Valor calculado com base na taxa pré-fixada contratada."
  - `ESTIMATED` → `~` prefix, tooltip: "Valor estimado com base nos indicadores econômicos mais recentes. Pode diferir do valor real. Será atualizado ao sincronizar com sua instituição."
  - `UNAVAILABLE` → `—`

- [ ] **T4.2.3** Update portfolio group totals to sum `resolveHoldingValue` across all holdings in the group.

- [ ] **T4.2.4** When a group total includes at least one `ESTIMATED` holding, show `~` on the group total as well.

---

### Story 4.3 — Fixed Income Comparison Page Integration (Optional)

**Tasks**:

- [ ] **T4.3.1** In `FixedIncomeComparisonPageClient`, pre-fill simulation fields from holding data when navigating from a specific holding context (future enhancement, not blocking V1).

---

## Definition of Done

- [ ] All calculation engine unit tests pass.
- [ ] Schema change is backward-compatible (new nullable fields only).
- [ ] `valuationSource = PROVIDER` when provider has already synced values (no regression).
- [ ] `valuationSource = CALCULATED` for PRE_FIXADO with no provider value.
- [ ] `valuationSource = ESTIMATED` for CDI/IPCA/SELIC/etc. with no provider value.
- [ ] `valuationSource = UNAVAILABLE` for OUTROS or missing data.
- [ ] Indicator service downtime does not break the holdings query.
- [ ] Frontend shows `—` for `UNAVAILABLE`.
- [ ] Frontend shows no indicator for `PROVIDER`.
- [ ] Frontend shows exact-value indicator (icon + tooltip) for `CALCULATED`.
- [ ] Frontend shows `~` + warning tooltip for `ESTIMATED`.
- [ ] Tooltip copy reviewed and approved.
