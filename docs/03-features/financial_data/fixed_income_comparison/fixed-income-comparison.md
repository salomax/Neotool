---
title: Fixed Income Comparison
type: feature
category: financial-data
status: draft
version: 1.0.0
tags: [feature, financial-data, fixed-income, cdb, lci, lca, lc]
search_keywords: [fixed income simulator, cdb vs lci, lca, lc, net equivalent yield, tax regression]
related:
  - 03-features/financial_data/brazilian_economic_indicators/brazilian-economic-indicators.md
  - 03-features/financial_data/fixed_income_comparison/implementation-backlog.md
---

# Fixed Income Comparison

> **Purpose**: Provide a clear and explainable comparison between taxable and tax-exempt fixed income products (CDB, LC, LCI, and LCA), including net-equivalent yield and value evolution over time.

## Overview

This feature allows users to simulate one investment scenario and instantly compare equivalent outcomes across:

- Product type: CDB, LC, LCI, LCA
- Rate model: `% of CDI`, `Fixed annual rate`, `IPCA + spread`
- Net result at maturity (after tax when applicable)

The UI should make the tax impact explicit and answer a practical question:
`What contracted rate is required in another product/rate model to match the same net value at maturity?`

## Goals

- Make taxable vs tax-exempt product comparison understandable in one screen.
- Show gross value, tax amount, and net value with transparent assumptions.
- Provide equivalent-rate outputs for decision support.
- Show value evolution during the full investment period.

## Functional Requirements

### FR-1: Simulation Input Form

- **Description**: Users can define a base scenario to run the comparison.
- **Priority**: Critical
- **Inputs**:
  - Product type (`CDB`, `LC`, `LCI`, `LCA`)
  - Initial amount (BRL)
  - Start date
  - Maturity date
  - Rate model (`%CDI`, `Fixed`, `IPCA+`)
  - Contracted rate value
  - Market projection for CDI (annual, auto-filled from `EconomicIndicatorsLatest` and editable)
  - Market projection for IPCA (annual, auto-filled from `EconomicIndicatorsLatest` and editable)
- **Acceptance Criteria**:
  - Form supports all product and rate-model combinations.
  - User can switch product/rate type without losing unrelated field values.
  - On first load, CDI/IPCA defaults are fetched from economic indicators API when available.
  - "Update Simulation" runs calculation and refreshes all widgets.

### FR-2: Product Tax Classification

- **Description**: The engine applies income tax only to taxable products.
- **Priority**: Critical
- **Rules**:
  - Taxable: `CDB`, `LC`
  - Tax-exempt: `LCI`, `LCA` (for individual investor simulation context)
- **Acceptance Criteria**:
  - Tax is `0` for tax-exempt products.
  - Taxable products show IR amount and net value.
  - Table and summary always display tax treatment per scenario.

### FR-3: Regressive Income Tax (IR) Application

- **Description**: Apply Brazilian regressive IR table based on holding period.
- **Priority**: Critical
- **IR Brackets**:
  - Up to 180 calendar days: `22.5%`
  - 181 to 360 calendar days: `20.0%`
  - 361 to 720 calendar days: `17.5%`
  - Above 720 calendar days: `15.0%`
- **Acceptance Criteria**:
  - Bracket is selected from start-to-maturity calendar day count.
  - IR is applied only to profits of taxable products.
  - Selected bracket is visible in explanation area.

### FR-4: Rate Model Calculation Engine

- **Description**: Compute gross and net outcomes for all supported rate models.
- **Priority**: Critical
- **Supported models**:
  - `%CDI`: uses projected CDI annual rate and contracted percentage
  - `Fixed`: uses contracted fixed annual rate
  - `IPCA+`: combines projected IPCA annual rate and contracted spread
- **Acceptance Criteria**:
  - Engine converts each scenario into an effective annual return basis.
  - Value at maturity is computed with compound growth.
  - Methodology panel documents formulas in business language.

### FR-5: Net Equivalent Yield Output

- **Description**: Show which contracted rate in an alternative product/rate model matches the base net value at maturity.
- **Priority**: High
- **Acceptance Criteria**:
  - A summary card displays the net-equivalent rate (e.g., "To match this LCI at 95% CDI, CDB must yield X% CDI").
  - Equivalent result is available for each comparison row in the table.
  - Equivalent rates are recalculated every simulation run.

### FR-6: Comparative Performance Table

- **Description**: Present a side-by-side comparison for base and equivalent scenarios.
- **Priority**: High
- **Columns**:
  - Investment label
  - Contracted rate
  - Gross value
  - Income tax (IR)
  - Net value
  - Real gain (%)
- **Acceptance Criteria**:
  - Table contains base scenario and at least equivalent rows for `%CDI`, `Fixed`, and `IPCA+`.
  - Currency and percentage formatting follow pt-BR display format.
  - Tax-exempt rows show clear "Exempt" indicator in IR column.

### FR-7: Profitability Evolution Chart

- **Description**: Show value progression from start date to maturity for compared scenarios.
- **Priority**: High
- **Acceptance Criteria**:
  - Chart displays a time series for base and selected equivalent scenarios.
  - Final chart points match table net values at maturity.
  - Time axis and value axis use consistent date/currency formatting.

### FR-8: Methodology and Transparency Panel

- **Description**: Display assumptions and tax rules used by calculations.
- **Priority**: Medium
- **Acceptance Criteria**:
  - Panel includes day-count convention (`252 business days`) and tax bracket table.
  - Panel is always visible on the comparison screen.
  - Users can understand why taxable and tax-exempt outcomes differ.

### FR-9: Validation and Error Handling

- **Description**: Prevent invalid simulations and provide clear user feedback.
- **Priority**: High
- **Validation Rules**:
  - Initial amount must be greater than zero.
  - Maturity date must be after start date.
  - Contracted rate must be within configured min/max bounds.
  - CDI projection must have a value for `%CDI` calculations (API default or manual input).
  - IPCA projection must have a value for `IPCA+` calculations (API default or manual input).
- **Acceptance Criteria**:
  - Invalid fields show inline messages.
  - Simulation action is blocked while required fields are invalid.
  - Errors do not clear previously valid simulation results until a new valid run.

## Business Rules

- **BR-1: Day Count Convention**
  - Return accrual uses `252 business days` convention.
  - IR bracket uses calendar-day holding period.

- **BR-2: Profit Taxation Scope**
  - IR is applied on profit (`gross value - principal`) only for taxable products.

- **BR-3: Projection-Driven Scenario**
  - `%CDI` and `IPCA+` outputs are deterministic projections based on user inputs, not market guarantees.

- **BR-4: Equivalence Target**
  - "Equivalent" means same net value at maturity as the selected base scenario.

## User Flows

### Flow 1: Compare CDB/LC Against LCI/LCA

1. User enters amount, dates, rate model, and contracted rate.
2. User sets CDI/IPCA projections.
3. User runs simulation.
4. System shows equivalent net rate, comparison table, and evolution chart.

### Flow 2: Switch Rate Model

1. User changes from `%CDI` to `Fixed` or `IPCA+`.
2. User updates required inputs for the new model.
3. System recalculates and refreshes all outputs.

## Out of Scope (Initial Version)

- Product-level fees, custody costs, spread to benchmark, and issuer default risk modeling.
- Historical backtesting using real daily CDI/IPCA paths.
- Portfolio-level simulation with multiple positions.

## Implementation Notes

- Prefer implementing the calculation engine as a pure domain service with deterministic inputs and outputs.
- Keep UI components stateless where possible and derive all values from simulation response.
- Persist no user scenario by default in V1 (session-only interaction).
