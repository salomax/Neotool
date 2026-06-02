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
    ├── 4.1 Update GraphQL Query
    ├── 4.2 Display Estimated Value (valuation source indicators)
    ├── 4.3 Expired Holdings Display ("Expirado" badge + nudge)
    ├── 4.4 Gain/Loss Display
    └── 4.5 Fixed Income Comparison Page (optional)
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

- [x] **T1.1.1** Create `FixedIncomeValuationService` in `banking` service under `domain/investment/`.
  - Input: `Holding`, `Map<Indexer, BigDecimal>` (annual rates keyed by indexer)
  - Output: `HoldingValuationResult(grossValue, netValue, incomeTax, iof, valuationSource, valuationReferenceDate)`
  - Note: these fields map directly onto the existing `InvestmentHolding` fields — no separate `estimated*` fields.

- [x] **T1.1.2** Implement `businessDaysBetween(start: LocalDate, end: LocalDate): Int`.
  - Use 252 bd/year approximation: `(calendarDays / 365.0) × 252`
  - Alternatively, if a business day calendar is available in the codebase, use it.

- [x] **T1.1.3** Implement accrual formula for `PRE_FIXADO`.
  - `grossValue = principal × (1 + preFixedRate) ^ (t / 252)`

- [x] **T1.1.4** Implement accrual formula for `CDI` / `DI`.
  - `cdi_daily = (1 + cdi_annual) ^ (1/252) - 1`
  - `grossValue = principal × (1 + cdi_daily × indexerPercentage) ^ t`

- [x] **T1.1.5** Implement accrual formula for `SELIC`.
  - Same as CDI formula using SELIC annual rate.

- [x] **T1.1.6** Implement accrual formula for `IPCA` (with optional spread).
  - If `preFixedRate` is null or zero: `grossValue = principal × (1 + ipca_annual) ^ (t / 252)`
  - If `preFixedRate > 0`: `annual_effective = (1 + ipca_annual) × (1 + preFixedRate) - 1`

- [x] **T1.1.7** Implement accrual formula for `IGP_M`, `IGP_DI`, `INPC`, `TR`.
  - Same pattern as IPCA with respective indicator rate.

- [x] **T1.1.8** Return null for unsupported indexers (`OUTROS`, `BCP`, `TLC`) or missing indicator data.

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

- [x] **T1.2.1** Implement `irRate(calendarDays: Int): BigDecimal` — extracted to `BrazilianFixedIncomeTaxRules`.
  - ≤ 180 days → 22.5%
  - 181–360 days → 20.0%
  - 361–720 days → 17.5%
  - > 720 days → 15.0%

- [x] **T1.2.2** Implement IR computation — extracted to `BrazilianFixedIncomeTaxRules.computeIr`.
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

- [x] **T1.3.1** Implement `iofRate(calendarDays: Int): BigDecimal` — extracted to `BrazilianFixedIncomeTaxRules`.
  - Day 1 → 96%, Day 2 → 93%, ..., Day 29 → 3%, Day 30+ → 0%

- [x] **T1.3.2** Implement IOF computation — extracted to `BrazilianFixedIncomeTaxRules.computeIof`.
  ```
  gain = grossValue - principal
  estimatedIof = if (calendarDays >= 30 || gain <= 0) 0 else gain × iofRate(calendarDays)
  ```

---

### Story 1.4 — Unit Tests for Calculation Engine

**Tasks**:

- [x] **T1.4.1** Test `PRE_FIXADO` formula with known values (e.g., R$10.000 at 12% a.a. for 252 bd → R$11.200).
- [x] **T1.4.2** Test `CDI` formula at 110% of 13.25% a.a. for 126 bd.
- [x] **T1.4.3** Test `IPCA+` formula with 6.5% IPCA + 6% spread for 252 bd.
- [x] **T1.4.4** Test IR brackets at boundaries (day 180, 181, 360, 361, 720, 721) — in `BrazilianFixedIncomeTaxRulesTest`.
- [x] **T1.4.5** Test IOF at day 1, 15, 29, and 30+ — in `BrazilianFixedIncomeTaxRulesTest`.
- [x] **T1.4.6** Test combined IR + IOF for a holding on day 10.
- [x] **T1.4.7** Test `isTaxExempt = true` → IR = 0 — in `BrazilianFixedIncomeTaxRulesTest`.
- [x] **T1.4.8** Test past-maturity holding: value caps at maturity date.
- [x] **T1.4.9** Test null/missing indicator → returns null gracefully.

---

## Epic 2: GraphQL Schema & Resolver

**Goal**: Expose estimated valuation fields on `InvestmentHolding` so the frontend can consume them without knowing about calculation logic.

---

### Story 2.1 — Schema Extension

**Tasks**:

- [x] **T2.1.1** Add `ValuationSource` enum to `banking/schema.graphqls`:
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

- [x] **T2.1.2** Add new fields to `InvestmentHolding` in `banking/schema.graphqls`:
  ```graphql
  valuationSource: ValuationSource!
  "Date of the most recent indicator used in the calculation (null when source is PROVIDER or UNAVAILABLE)."
  valuationReferenceDate: Date
  "True when maturityDate != null && maturityDate < today."
  isExpired: Boolean!
  ```
  Note: `grossValue`, `netValue`, `incomeTax`, `iof` already exist — no new value fields needed.

- [x] **T2.1.3** Regenerate supergraph schema / run federation compose to verify no breaking changes. (Run `./neotool graphql generate --all` from repo root; supergraph composes successfully with banking `valuationSource`, `valuationReferenceDate`, `isExpired`.)

- [x] **T2.1.4** Add fields to `InvestmentHoldingDTO` in `InvestmentDTO.kt`.

---

### Story 2.2 — Resolver Integration

**Tasks**:

- [x] **T2.2.1** In `InvestmentResolver`, inject `FixedIncomeValuationService` and `IndicatorRateProvider` (new — see Epic 3).

- [x] **T2.2.2** In `allInvestmentHoldings` resolver: after mapping holdings to DTOs, for each holding:
  - If `grossValue != null` (provider-synced) → set `valuationSource = PROVIDER`, leave value fields as-is.
  - If `grossValue == null` → call `FixedIncomeValuationService.estimate(holding, rateMap)` and copy result fields (`grossValue`, `netValue`, `incomeTax`, `iof`, `valuationSource`, `valuationReferenceDate`) onto the DTO.

- [x] **T2.2.3** Batch indicator fetch: fetch the rate map **once per request** (not once per holding) before the holdings loop.

- [x] **T2.2.4** Handle service errors gracefully: if indicator fetch fails, log a warning and set all estimated fields to null without failing the query.

---

## Epic 3: Indicator Integration

**Goal**: Create a rate provider in the banking service that fetches the latest indicator values from the financial data service via inter-service call.

---

### Story 3.1 — Indicator Rate Provider

**As a** banking service component,
**I want** to fetch the latest CDI, SELIC, IPCA, IGP-M, TR, and other rates from the financial data service,
**So that** the valuation engine always uses up-to-date rates.

**Tasks**:

- [x] **T3.1.1** Create `IndicatorRateProvider` interface and implementation in `banking` service.
  - Method: `fun getLatestRates(): Map<Indexer, BigDecimal>`
  - Calls `indicatorsLatest` GraphQL query on financial-data service (or direct DB read if services share DB / use internal API).

- [x] **T3.1.2** Map financial-data indicator labels to `Indexer` enum values per the mapping table in the feature spec.

- [x] **T3.1.3** Add in-memory TTL cache (1 hour) on the rate map; 1-minute retry TTL on failure.

- [x] **T3.1.4** Write unit test with mocked indicator response covering: CDI present, IPCA present, unknown label ignored, service unavailable → empty map.

---

## Epic 4: Frontend Display

**Goal**: Surface estimated values on the investment portfolio page with a clear visual indicator that the value is estimated.

---

### Story 4.1 — Update GraphQL Query

**Tasks**:

- [x] **T4.1.1** Add `valuationSource`, `valuationReferenceDate`, `isExpired` to the `ALL_INVESTMENT_HOLDINGS` query in `queries.ts`. (`grossValue`, `netValue`, `incomeTax`, `iof` are already queried.)

---

### Story 4.2 — Display Estimated Value

**As a** user,
**I want** to see an estimated current value for my manually recorded fixed income holdings,
**So that** I can track the approximate performance of my investments without waiting for a bank sync.

**Acceptance Criteria**:
- `PROVIDER`: display value normally, no indicator.
- `CALCULATED`: display value with an ⓘ icon; tooltip shows contracted rate used. No uncertainty warning needed — this is exact.
- `ESTIMATED`: display value with an ⚠ icon (amber); tooltip shows approximation warning and `valuationReferenceDate` (e.g., "CDI de 12/03/2026"). User must understand this is approximate.
- `UNAVAILABLE`: show `—`.

**Tasks**:

- [x] **T4.2.1** Update `InvestmentTable` to render the appropriate indicator per `valuationSource` alongside `grossValue`:
  - `PROVIDER` → no indicator
  - `CALCULATED` → ⓘ icon, tooltip: "Valor calculado com base na taxa pré-fixada contratada."
  - `ESTIMATED` → ⚠ icon (amber), tooltip: "Valor estimado com base na taxa contratada e nos indicadores econômicos mais recentes (referência: {valuationReferenceDate}). Pode diferir do valor real."
  - `UNAVAILABLE` → `—`

- [x] **T4.2.2** Update portfolio group totals to sum `grossValue` across all holdings in the group (unchanged logic, now always a single field).

- [x] **T4.2.3** When a group total includes at least one `ESTIMATED` holding, show ⚠ icon (amber) on the group total as well.

---

### Story 4.3 — Expired Holdings Display

**As a** user,
**I want** to see a clear "Expirado" indicator on holdings that have passed their maturity date,
**So that** I know which investments need attention (redemption, rollover, or removal).

**Acceptance Criteria**:
- Any holding where `isExpired = true` shows an "Expirado" badge regardless of `valuationSource`.
- For MANUAL expired holdings: show nudge — "Este ativo venceu em {maturityDate}. Atualize ou remova-o."
- For PROVIDER expired holdings: show "Expirado" badge only, no nudge (bank will reconcile via next sync).
- Value displayed is the maturity value (calculation already capped at maturity date by the engine — no frontend change needed).

**Tasks**:

- [x] **T4.3.1** Add `isExpired` and `maturityDate` to the `ALL_INVESTMENT_HOLDINGS` query in `queries.ts` (if not already present).

- [x] **T4.3.2** Render "Expirado" badge on any row where `isExpired = true`.

- [x] **T4.3.3** For MANUAL expired holdings (`valuationSource != PROVIDER`), render inline nudge with `maturityDate`.

---

### Story 4.4 — Gain/Loss Display

**As a** user,
**I want** to see the absolute and percentage gain on each holding,
**So that** I can quickly assess how each investment is performing.

**Acceptance Criteria**:
- Absolute gain: `currentValue - averageCost × quantity` (where `currentValue` is from `resolveHoldingValue`).
- Percentage gain: `(currentValue / (averageCost × quantity) - 1) × 100`.
- When `valuationSource = ESTIMATED`, gain inherits the ⚠ indicator.
- When `valuationSource = PROVIDER`, gain is shown without indicator.
- When value is unavailable, gain shows `—`.

**Tasks**:

- [x] **T4.4.1** Compute gain directly from existing fields — no utility needed:
  ```typescript
  const principal = holding.averageCost * holding.quantity
  const absolute = holding.grossValue != null ? holding.grossValue - principal : null
  const percent  = absolute != null ? (absolute / principal) * 100 : null
  ```

- [x] **T4.4.2** Add gain columns (absolute + %) to `InvestmentTable`, reusing the ⚠ indicator from `valuationSource` when `ESTIMATED`.

---

### Story 4.5 — Fixed Income Comparison Page Integration (Optional)

**Tasks**:

- [ ] **T4.5.1** In `FixedIncomeComparisonPageClient`, pre-fill simulation fields from holding data when navigating from a specific holding context (future enhancement, not blocking V1). Deferred to post-V1.

---

## Definition of Done

- [x] All calculation engine unit tests pass.
- [x] Schema change adds only new fields (`valuationSource`, `valuationReferenceDate`, `isExpired`) — backward-compatible.
- [x] No `estimated*` fields exist in schema or DTO — single `grossValue`/`netValue` used for all sources.
- [x] `valuationSource = PROVIDER` when provider has already synced values (no regression on existing data).
- [x] `valuationSource = CALCULATED` for PRE_FIXADO with no provider value.
- [x] `valuationSource = ESTIMATED` for CDI/IPCA/SELIC/etc. with no provider value.
- [x] `valuationSource = UNAVAILABLE` for OUTROS, missing data, or missing issueDate.
- [x] Indicator service downtime does not break the holdings query.
- [x] Frontend shows `—` when `grossValue` is null (`UNAVAILABLE`).
- [x] Frontend shows no indicator for `PROVIDER`.
- [x] Frontend shows ⓘ icon + tooltip for `CALCULATED`.
- [x] Frontend shows ⚠ icon (amber) + warning tooltip with `valuationReferenceDate` for `ESTIMATED`.
- [x] Tooltip copy does not reference sync — works for fully manual users.
- [x] Expired holdings (`isExpired = true`) show "Expirado" badge for both PROVIDER and MANUAL.
- [x] MANUAL expired holdings show nudge with maturity date.
- [x] Gain/loss columns visible with ⚠ inherited when `valuationSource = ESTIMATED`.

## Remaining

- [x] **T2.1.3** Federation compose verified (run `./neotool graphql generate --all`).
- [x] **T3.1.4** Unit tests for `FinancialDataIndicatorRateProvider` (cache/retry, success and failure paths).
- [ ] **T4.5.1** (Optional, deferred) Fixed Income Comparison Page: pre-fill simulation fields from holding context.
