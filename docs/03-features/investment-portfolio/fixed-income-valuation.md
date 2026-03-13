---
title: Fixed Income Holding Valuation
type: feature
category: investment-portfolio
status: draft
version: 1.0.0
tags: [investment, fixed-income, valuation, cdi, ipca, ir, iof, manual-holdings]
search_keywords: [fixed income current value estimation, cdi holding value, ipca holding valuation, ir iof calculation, custo médio, estimated value]
related:
  - 03-features/investment-portfolio/investment-portfolio-blueprint.md
  - 03-features/financial_data/brazilian_economic_indicators/brazilian-economic-indicators.md
  - 03-features/financial_data/fixed_income_comparison/fixed-income-comparison.md
  - 03-features/investment-portfolio/fixed-income-valuation-backlog.md
last_updated: 2026-03-13
---

# Fixed Income Holding Valuation

> **Purpose**: Compute an estimated current value (gross value, net value, IR, and IOF) for fixed income holdings that have no provider-synced valuation, using contractual terms and current economic indicators already available in the financial data service.

---

## Problem Statement

When a user manually records a fixed income holding (source: `MANUAL`), the fields `grossValue`, `netValue`, `incomeTax`, and `iof` are null because they are only populated via Open Finance sync. The holding has all contractual data (`indexer`, `indexerPercentage`, `preFixedRate`, `issueDate`, `averageCost`, `quantity`) but no current value to display.

Showing `averageCost × quantity` (cost basis) as the "current value" is misleading after any time has passed. The backend should estimate the current value using the contractual rate and the latest indicator values from the financial data service.

---

## Design Principles

- **Backend-only**: All calculation happens in the banking service resolver. Frontend consumes results without any calculation logic.
- **Non-destructive**: Estimated values are exposed as separate fields (`estimatedGrossValue`, etc.) and never overwrite the provider-synced `grossValue`/`netValue`.
- **Transparent to the user**: The `isEstimated` flag allows the UI to clearly distinguish estimated from synced values.
- **Provider wins**: If `grossValue` is set (from Open Finance or file import), the provider value is used and no estimation is performed.
- **Graceful degradation**: For indexers with no available indicator data (e.g., `OUTROS`), the estimated fields are null and `isEstimated` is false.

---

## New GraphQL Fields

Add to `InvestmentHolding`:

```graphql
type InvestmentHolding {
  # ... existing fields ...

  """
  Estimated current gross value computed from contractual terms and latest indicators.
  Null if grossValue is already provided by the data provider, or if the indexer
  cannot be estimated (e.g., OUTROS, missing indicator data).
  """
  estimatedGrossValue: Float

  """
  Estimated net value after IR and IOF (estimatedGrossValue - estimatedIncomeTax - estimatedIof).
  """
  estimatedNetValue: Float

  """
  Estimated income tax (IR) on the gain. Zero for isTaxExempt holdings.
  """
  estimatedIncomeTax: Float

  """
  Estimated IOF on the gain. Zero after 30 calendar days from issueDate.
  """
  estimatedIof: Float

  """
  True when estimatedGrossValue/estimatedNetValue are populated and grossValue is null.
  False when provider-synced values are in use or when estimation is not possible.
  """
  isEstimated: Boolean!
}
```

---

## Calculation Rules

### Prerequisites

For any estimation to run, the holding must have:
- `issueDate` is not null
- `averageCost > 0` and `quantity > 0`
- `indexer` is a supported value (see table below)

The invested principal is:

```
principal = quantity × averageCost
```

---

### 1. Accrued Gross Value by Indexer

All formulas use **business days** (`t`) from `issueDate` to today (capped at `maturityDate` if in the past).
Business days use the Brazilian calendar (252 per year convention for accrual).

#### PRE_FIXADO

Fully deterministic — no external indicator needed.

```
grossValue = principal × (1 + preFixedRate) ^ (t / 252)
```

Where `preFixedRate` is an annual rate stored as a decimal (e.g., `0.12` = 12% a.a.).

**Confidence**: HIGH

---

#### CDI / DI

Uses the latest annual CDI rate from the financial data service.

```
cdi_daily = (1 + cdi_annual) ^ (1 / 252) - 1
grossValue = principal × (1 + cdi_daily × indexerPercentage) ^ t
```

Where `indexerPercentage` is stored as a fraction (e.g., `1.10` = 110% CDI).

**Confidence**: MEDIUM (CDI is relatively stable but changes with COPOM decisions)

---

#### SELIC

Same structure as CDI, using the latest annual SELIC rate.

```
selic_daily = (1 + selic_annual) ^ (1 / 252) - 1
grossValue = principal × (1 + selic_daily × indexerPercentage) ^ t
```

**Confidence**: MEDIUM

---

#### IPCA (pure inflation-linked)

Uses latest available annual IPCA rate.

If `preFixedRate` is null or zero (pure IPCA, no spread):

```
grossValue = principal × (1 + ipca_annual) ^ (t / 252)
```

If `preFixedRate > 0` (IPCA + spread, e.g., "IPCA + 6% a.a."):

```
annual_effective = (1 + ipca_annual) × (1 + preFixedRate) - 1
grossValue = principal × (1 + annual_effective) ^ (t / 252)
```

**Confidence**: MEDIUM (IPCA is published monthly with ~1 month lag; latest known value is used)

---

#### IGP_M

Same structure as IPCA, substituting IGP-M annual rate.

```
annual_effective = (1 + igpm_annual) × (1 + preFixedRate) - 1   # preFixedRate may be 0
grossValue = principal × (1 + annual_effective) ^ (t / 252)
```

**Confidence**: MEDIUM

---

#### TR

TR rate is available from the financial data service. Same structure as IPCA+spread.

```
annual_effective = (1 + tr_annual) × (1 + preFixedRate) - 1
grossValue = principal × (1 + annual_effective) ^ (t / 252)
```

**Confidence**: LOW (TR has been near zero for years but is technically variable)

---

#### IGP_DI / INPC / BCP / TLC

Apply same pattern as IPCA if the indicator is available in the financial data service.
If the indicator value is unavailable, set all estimated fields to null and `isEstimated = false`.

---

#### OUTROS

Cannot be estimated. Set all estimated fields to null. `isEstimated = false`.

---

### 2. Income Tax (IR)

Apply only when `isTaxExempt = false`.

**Tax base (gain)**:

```
gain = grossValue - principal
```

If `gain <= 0`: IR = 0 (no tax on non-positive returns).

**IR bracket** — based on **calendar days** from `issueDate` to today:

| Calendar days | IR rate |
|---|---|
| ≤ 180 | 22.5% |
| 181 – 360 | 20.0% |
| 361 – 720 | 17.5% |
| > 720 | 15.0% |

```
estimatedIncomeTax = gain × irRate
```

**Tax-exempt products** (`isTaxExempt = true`): LCI, LCA, CRI, CRA, and incentivized debentures.

```
estimatedIncomeTax = 0
```

---

### 3. IOF

IOF applies only within the first **30 calendar days** from `issueDate`.

After 30 days: `estimatedIof = 0`.

For day ≤ 30, apply the regressive IOF table against the gain:

| Day | IOF rate | Day | IOF rate |
|-----|----------|-----|----------|
| 1   | 96%      | 16  | 46%      |
| 2   | 93%      | 17  | 43%      |
| 3   | 90%      | 18  | 40%      |
| 4   | 86%      | 19  | 36%      |
| 5   | 83%      | 20  | 33%      |
| 6   | 80%      | 21  | 30%      |
| 7   | 76%      | 22  | 26%      |
| 8   | 73%      | 23  | 23%      |
| 9   | 70%      | 24  | 20%      |
| 10  | 66%      | 25  | 16%      |
| 11  | 63%      | 26  | 13%      |
| 12  | 60%      | 27  | 10%      |
| 13  | 56%      | 28  | 6%       |
| 14  | 53%      | 29  | 3%       |
| 15  | 50%      | 30  | 0%       |

```
estimatedIof = gain × iofTable[calendarDays]
```

**Note**: IOF is applied before IR in the tax order of operations. IR is levied on the gain net of IOF when both apply:

```
iof_amount   = gain × iofRate
taxable_gain = gain - iof_amount
ir_amount    = taxable_gain × irRate
```

---

### 4. Net Value

```
estimatedNetValue = estimatedGrossValue - estimatedIncomeTax - estimatedIof
```

---

### 5. isEstimated Flag

| Condition | isEstimated |
|---|---|
| `grossValue` is not null (provider-synced) | `false` |
| `indexer` is null or `OUTROS` | `false` |
| Required indicator data unavailable | `false` |
| `issueDate` is null | `false` |
| Estimation was computed successfully | `true` |

---

## Indicator Mapping

The financial data service exposes indicators via `indicatorsLatest`. The mapping between `Indexer` enum values and indicator labels:

| Indexer enum | Indicator label (financial-data service) |
|---|---|
| `CDI` | `CDI` |
| `DI` | `CDI` (DI Over ≈ CDI for this purpose) |
| `SELIC` | `SELIC` |
| `IPCA` | `IPCA` |
| `IGP_M` | `IGP-M` |
| `IGP_DI` | `IGP-DI` |
| `INPC` | `INPC` |
| `TR` | `TR` |
| `PRE_FIXADO` | — (no indicator needed) |
| `BCP`, `TLC`, `OUTROS` | — (not estimatable) |

All indicator rates from the service are **annualized** values. Use the per-day conversion:
`daily = (1 + annual)^(1/252) - 1` for CDI/SELIC/DI.
For inflation indices (IPCA, IGP-M, etc.), use the annual rate directly in the compound formula.

---

## Edge Cases

| Scenario | Behavior |
|---|---|
| `maturityDate` is in the past | Cap `t` at `maturityDate`. Value is "what it was worth at maturity". |
| `issueDate` is today | `t = 0`, `grossValue = principal`, IOF applies at day 1 rate if redeemed |
| `gain < 0` (rate went negative) | IR = 0, IOF = 0, `estimatedNetValue = estimatedGrossValue` |
| `quantity = 0` | Holding is closed (filtered by resolver). Estimation not applicable. |
| `averageCost = 0` | Data integrity issue. Skip estimation, return null. |
| Indicator service unavailable | Return null for estimated fields. Do not fail the holding query. |

---

## Interaction with Provider Sync

When Open Finance sync runs and populates `grossValue` / `netValue` / `incomeTax` / `iof` / `lastValuedAt`:
- The provider values take precedence in all displays.
- `isEstimated` becomes `false`.
- The estimated fields (`estimatedGrossValue`, etc.) continue to be computed but are ignored by the UI.

This means the transition from "estimated" to "synced" is seamless — no frontend logic change required.

---

## Out of Scope (V1)

- Daily historical series replay (using actual daily CDI/IPCA data points instead of a constant rate projection).
- Equities, ETFs, BDRs, funds — estimation requires market price feeds, handled separately.
- Multi-currency holdings — estimation is in the holding's native currency only.
- Fractional business day calculation (ANBIMA calendar) — use approximation (252 bd/year).
