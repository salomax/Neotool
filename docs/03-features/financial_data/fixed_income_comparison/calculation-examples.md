---
title: Fixed Income Comparison — Calculation Examples and Golden Vectors
type: reference
category: financial-data
status: draft
version: 1.0.0
tags: [financial-data, fixed-income, golden-vectors, precision, FIC-S011]
related:
  - 03-features/financial_data/fixed_income_comparison/fixed-income-comparison.md
  - 03-features/financial_data/fixed_income_comparison/implementation-backlog.md
---

# Fixed Income Comparison — Calculation Examples and Golden Vectors

This document provides the **precision policy** and **golden test vectors** for the Fixed Income Comparison engine. It is the single source of truth for QA and implementation (FIC-S012). Formulas and conventions are defined in the [Fixed Income Comparison](fixed-income-comparison.md) feature spec (Formula contract section); this file focuses on measurable precision rules and deterministic input/output examples.

---

## 1. Precision Policy (FIC-S011-T03)

### 1.1 Internal precision

- **Monetary amounts (BRL)**: All intermediate and final values (principal, gross, tax, net) must be computed with **10 decimal places**.
  - Implementation: use `BigDecimal` with scale **10** and `RoundingMode.HALF_UP` for all monetary operations.
- **Rates and percentages**: Effective annual rate, contracted rate, bracket rate, and equivalent-rate solver results must use **10 decimal places** internally.
  - Example: `0.1200000000` for 12% effective annual rate.

### 1.2 Display rounding

- **Currency (BRL)**: Round to **2 decimal places** for UI and reports (e.g. `R$ 11.162,96`).
- **Percentages**: Round to **4 decimal places** for display (e.g. `12,5000%`).
- **Real gain (%)**: Same as percentages, 4 decimal places.

Rounding mode for display: **half away from zero** (equivalent to `HALF_UP` for positive values).

### 1.3 Automated test tolerance

Tests that assert against golden vectors must use the following tolerances so that small rounding differences do not cause false failures:

| Quantity           | Tolerance (absolute) | Notes                          |
|--------------------|----------------------|--------------------------------|
| Monetary (BRL)     | `0.01`               | 1 cent; use for gross, IR, net |
| Rate (decimal)     | `0.0001`             | 1 basis point (0.01%)          |
| Percentage display| `0.0001`             | 1 bp when comparing % values   |

- Prefer **absolute** tolerance for amounts and rates.
- If comparing percentages stored as decimals (e.g. `0.12` for 12%), use rate tolerance `0.0001`.
- Golden vectors below list expected values rounded to 2 decimals (currency) or 4 decimals (rates) for readability; the engine must produce values that fall within the stated tolerance of these expected values.

---

## 2. Calculation Assumptions for Golden Vectors

The following conventions are used so that every vector is deterministic. They must match the Formula contract in the feature spec.

- **Business days from calendar days**:  
  `business_days = round(calendar_days × 252 / 365)`
- **Growth factor**:  
  `gross = principal × (1 + effective_annual_rate)^(business_days / 252)`
- **%CDI**:  
  `effective_annual_rate = (CDI_annual_pct / 100) × (contracted_pct / 100)`  
  (e.g. CDI 12%, 100% contracted → 12% effective.)
- **FIXED**:  
  `effective_annual_rate = fixed_annual_rate` (as decimal, e.g. 10% → 0.10).
- **IPCA+**:  
  `effective_annual_rate = (1 + IPCA_annual) × (1 + spread_annual) − 1`  
  (e.g. IPCA 6%, spread 5% → (1.06)(1.05) − 1 = 11.3%.)
- **IR bracket** (calendar days):  
  - 1–180 → 22.5%  
  - 181–360 → 20.0%  
  - 361–720 → 17.5%  
  - Above 720 → 15.0%
- **Tax**:  
  `profit = gross − principal`; `tax = profit × bracket_rate` for **taxable** (CDB, LC); **exempt** (LCI, LCA): `tax = 0`.
- **Net**:  
  `net = gross − tax`.

All golden scenarios use **principal = 10.000,00 BRL** and the same start date for simplicity (only calendar-day delta matters for IR and accrual).

---

## 3. Golden Test Vectors (FIC-S011-T02)

At least six scenarios are provided, covering all rate models (%CDI, FIXED, IPCA_PLUS), both tax classes (taxable and exempt), and **boundary** holding periods (180/181, 360/361, 720/721).

### 3.1 Scenario 1 — CDB %CDI, 360 days (taxable, 20% bracket)

| Input / Output | Value |
|----------------|--------|
| **Product** | CDB |
| **Rate model** | %CDI |
| **Principal (BRL)** | 10.000,00 |
| **Calendar days** | 360 |
| **CDI annual (%)** | 12,0000 |
| **Contracted % CDI** | 100,0000 |
| **IR bracket** | 20,0% (181–360 days) |
| **Gross (BRL)** | 11.162,96 |
| **IR (BRL)** | 232,59 |
| **Net (BRL)** | 10.930,37 |

*Tags: taxable, %CDI, 360d, core*

---

### 3.2 Scenario 2 — LCI %CDI, 360 days (exempt)

| Input / Output | Value |
|----------------|--------|
| **Product** | LCI |
| **Rate model** | %CDI |
| **Principal (BRL)** | 10.000,00 |
| **Calendar days** | 360 |
| **CDI annual (%)** | 12,0000 |
| **Contracted % CDI** | 100,0000 |
| **IR bracket** | N/A (exempt) |
| **Gross (BRL)** | 11.162,96 |
| **IR (BRL)** | 0,00 |
| **Net (BRL)** | 11.162,96 |

*Tags: exempt, %CDI, 360d, core*

---

### 3.3 Scenario 3 — CDB FIXED, 181 days (boundary 181 → 20%)

| Input / Output | Value |
|----------------|--------|
| **Product** | CDB |
| **Rate model** | FIXED |
| **Principal (BRL)** | 10.000,00 |
| **Calendar days** | 181 |
| **Fixed annual rate (%)** | 10,0000 |
| **IR bracket** | 20,0% (181–360 days) |
| **Gross (BRL)** | 10.483,98 |
| **IR (BRL)** | 96,80 |
| **Net (BRL)** | 10.387,18 |

*Tags: taxable, FIXED, boundary 181, 20% bracket*

---

### 3.4 Scenario 4 — LCA IPCA+, 365 days (exempt)

| Input / Output | Value |
|----------------|--------|
| **Product** | LCA |
| **Rate model** | IPCA_PLUS |
| **Principal (BRL)** | 10.000,00 |
| **Calendar days** | 365 |
| **IPCA annual (%)** | 6,0000 |
| **Spread annual (%)** | 5,0000 |
| **IR bracket** | N/A (exempt) |
| **Gross (BRL)** | 11.130,00 |
| **IR (BRL)** | 0,00 |
| **Net (BRL)** | 11.130,00 |

*Tags: exempt, IPCA_PLUS, core*

---

### 3.5 Scenario 5 — CDB %CDI, 180 days (boundary 180 → 22,5%)

| Input / Output | Value |
|----------------|--------|
| **Product** | CDB |
| **Rate model** | %CDI |
| **Principal (BRL)** | 10.000,00 |
| **Calendar days** | 180 |
| **CDI annual (%)** | 12,0000 |
| **Contracted % CDI** | 100,0000 |
| **IR bracket** | 22,5% (up to 180 days) |
| **Gross (BRL)** | 10.597,24 |
| **IR (BRL)** | 134,38 |
| **Net (BRL)** | 10.462,86 |

*Tags: taxable, %CDI, boundary 180, 22,5% bracket*

---

### 3.6 Scenario 6 — CDB %CDI, 361 days (boundary 361 → 17,5%)

| Input / Output | Value |
|----------------|--------|
| **Product** | CDB |
| **Rate model** | %CDI |
| **Principal (BRL)** | 10.000,00 |
| **Calendar days** | 361 |
| **CDI annual (%)** | 12,0000 |
| **Contracted % CDI** | 100,0000 |
| **IR bracket** | 17,5% (361–720 days) |
| **Gross (BRL)** | 11.176,08 |
| **IR (BRL)** | 205,81 |
| **Net (BRL)** | 10.970,27 |

*Tags: taxable, %CDI, boundary 361, 17,5% bracket*

---

### 3.7 Scenario 7 — CDB FIXED, 720 days (boundary 720 → 17,5%)

| Input / Output | Value |
|----------------|--------|
| **Product** | CDB |
| **Rate model** | FIXED |
| **Principal (BRL)** | 10.000,00 |
| **Calendar days** | 720 |
| **Fixed annual rate (%)** | 10,0000 |
| **IR bracket** | 17,5% (361–720 days) |
| **Gross (BRL)** | 12.166,27 |
| **IR (BRL)** | 379,10 |
| **Net (BRL)** | 11.787,17 |

*Tags: taxable, FIXED, boundary 720, 17,5% bracket*

---

### 3.8 Scenario 8 — CDB FIXED, 721 days (boundary 721 → 15%)

| Input / Output | Value |
|----------------|--------|
| **Product** | CDB |
| **Rate model** | FIXED |
| **Principal (BRL)** | 10.000,00 |
| **Calendar days** | 721 |
| **Fixed annual rate (%)** | 10,0000 |
| **IR bracket** | 15,0% (above 720 days) |
| **Gross (BRL)** | 12.177,02 |
| **IR (BRL)** | 326,55 |
| **Net (BRL)** | 11.850,47 |

*Tags: taxable, FIXED, boundary 721, 15% bracket*

---

## 4. Coverage Summary

| Criterion | Covered by scenarios |
|-----------|------------------------|
| Rate models | %CDI (1, 2, 5, 6), FIXED (3, 7, 8), IPCA_PLUS (4) |
| Tax classes | Taxable: CDB (1, 3, 5, 6, 7, 8); Exempt: LCI (2), LCA (4) |
| IR boundaries | 180 days (5), 181 days (3), 360 days (1, 2), 361 days (6), 720 days (7), 721 days (8) |

---

## 5. Sign-off (before FIC-S012)

- [ ] Golden examples reviewed by at least one engineer.
- [ ] Golden examples reviewed by at least one QA.
- [ ] Formula contract in `fixed-income-comparison.md` aligned with calculation assumptions above.
- [ ] Precision and tolerance accepted for implementation and automated tests.
