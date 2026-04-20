# Test Scenarios: Investment Performance

**Requirements ref:** `04-investment-performance.md`
**Document date:** 2026-04-20

---

## Portfolio Summary — Happy Paths

### SC-01: Summary computed correctly from holdings + cash

**Setup:** single account with:
- 100 PETR4 @ averageCost=38.50, grossValue=4200 (market price)
- CDB @ averageCost=10000, grossValue=10450 (CALCULATED)
- cashBalance=5000

**Expected `investmentPortfolioSummary` output:**

| Field               | Value                             |
|---|---|
| totalCostBasis      | 100×38.50 + 10000 = R$ 13 850    |
| totalGrossValue     | 4200 + 10450 = R$ 14 650          |
| grossGain           | 14650 − 13850 = R$ 800            |
| grossGainPercent    | 800 / 13850 = 5,78%               |
| cashBalance         | R$ 5 000                          |
| totalPortfolioValue | 14650 + 5000 = R$ 19 650          |

---

### SC-02: netGain computed after provisioned IR/IOF

**Setup:** CDB holding with provisioned `incomeTax=R$ 420`

**Asserts:**
- `totalNetValue = totalGrossValue − totalIncomeTax`
- `netGain = totalNetValue − totalCostBasis`

---

### SC-03: Consolidated summary aggregates all INVESTMENT and BENEFIT accounts

**Setup:** 3 accounts with `totalGrossValue` of R$ 50k, R$ 30k, R$ 10k

**Asserts:**
- `investmentPortfolioSummary(financialAccountId: null)` returns `totalGrossValue = 90 000`

---

### SC-04: Closed holdings (quantity=0) excluded from totals

**Setup:** one open holding (grossValue=10 000) + one closed holding (quantity=0)

**Asserts:**
- `totalGrossValue` reflects only the open holding
- Closed holding at zero value does not inflate or distort totals

---

### SC-05: `hasEstimatedValues = true` when any holding uses ESTIMATED or UNAVAILABLE

**Setup:** portfolio with one CALCULATED holding + one ESTIMATED (CDI) holding

**Asserts:**
- `hasEstimatedValues = true`
- `valuationReferenceDate` = date of the CDI indicator data used

---

### SC-06: `hasEstimatedValues = false` when all holdings are MARKET_DATA or CALCULATED

**Setup:** all holdings are equities (MARKET_DATA) or pre-fixed (CALCULATED)

**Asserts:** `hasEstimatedValues = false`; no "estimado" warning shown

---

## Asset Group Mapping

### SC-07: Holdings appear in the correct asset group

| Asset type            | Group key           |
|---|---|
| STOCK, ETF, BDR       | variableIncome      |
| ETF_INTERNATIONAL     | variableIncome      |
| REAL_ESTATE_FUND      | reits               |
| AGRIBUSINESS_FUND     | reits               |
| CDB, LCI, LCA, RDB    | bankFixedIncome     |
| DEBENTURES, CRI, CRA  | creditFixedIncome   |
| TREASURY              | treasury            |
| ETF_FIXED_INCOME      | fixedIncomeEtf      |
| MUTUAL_FUND, PGBL, VGBL | funds             |
| CRYPTO                | crypto              |
| cashBalance           | cash (virtual)      |

**Asserts:** mapping consistent between `AssetGroupMapping.kt` (backend) and `investmentTableHelpers.ts` (frontend)

---

## Valuation Sources and Precedence

### SC-08: Valuation source priority

| Source      | When applied                                          | Overrides          |
|---|---|---|
| PROVIDER    | Open Finance or file import sets `grossValue`         | Everything         |
| MARKET_DATA | Live price from financial-data service                | CALCULATED, ESTIMATED |
| CALCULATED  | PRE_FIXADO indexer — exact math                       | ESTIMATED          |
| ESTIMATED   | CDI, IPCA, SELIC, IGP-M — uses latest indicator      | UNAVAILABLE        |
| MANUAL      | Fund with `pricingMode=MANUAL`                        | Others (user-set)  |
| UNAVAILABLE | OUTROS indexer, missing `issueDate`, no data          | Nothing            |

**Asserts:** when `PROVIDER` data exists, it is used regardless of other computed values

---

### SC-09: UNAVAILABLE valuation falls back to cost basis

**Setup:** holding with `indexer=OUTROS` (or missing `issueDate`)

**Asserts:**
- `grossValue = averageCost × quantity`
- `valuationSource = UNAVAILABLE`
- No warning about external data missing required

---

## Portfolio Flow Events (TWR)

### SC-10: PURCHASE generates a DEPOSIT flow event

**Trigger:** `recordInvestmentTransaction({ type: PURCHASE, direction: INFLOW })`

**Asserts:**
- `PortfolioFlowEventHandler.onRecord` creates a `DEPOSIT` flow event
- Event stored in `investment_portfolio_flow_event`
- `PortfolioQuotaCalculationService` recalculates quota from `tradeDate` forward

---

### SC-11: SALE generates a WITHDRAWAL flow event

**Trigger:** `recordInvestmentTransaction({ type: SALE, direction: OUTFLOW })`

**Asserts:** `WITHDRAWAL` flow event created; quota series recalculated

---

### SC-12: Income events generate DIVIDEND flow events

| Transaction type   | Flow event type |
|---|---|
| DIVIDEND           | DIVIDEND        |
| INTEREST_PAYMENT   | DIVIDEND        |
| AMORTIZATION       | DIVIDEND        |
| STOCK_LENDING      | DIVIDEND        |

---

### SC-13: WITHHOLDING_TAX generates INCOME_TAX flow event

**Asserts:** flow event type = `INCOME_TAX`

---

### SC-14: Transaction update replays flow event

**Action:** edit an existing PURCHASE (change grossValue)

**Asserts:**
- Old flow event removed
- New flow event created with updated values
- Quota series recalculated from original `tradeDate` forward

---

### SC-15: Transaction delete removes flow event

**Action:** delete a PURCHASE transaction

**Asserts:**
- Corresponding `DEPOSIT` flow event deleted
- Quota series recalculated

---

## TWR Quota System — Arithmetic Checks

### SC-16: Initial quota value = R$ 1.00

**Setup:** no prior transactions; first PURCHASE recorded

**Asserts:** quota series starts at R$ 1.00 on the date of the first flow event

---

### SC-17: New deposit does not change the quota price (only increases quantity of quotas)

**Setup:** portfolio quota = R$ 1.15 today

**Action:** deposit R$ 10 000

**Asserts:**
- Quota price remains R$ 1.15
- New quotas issued = 10000 / 1.15 = 8 695,65
- Total quota count increases
- TWR historical performance NOT diluted by new money

---

### SC-18: TWR for period = (quota_end / quota_start) − 1

**Setup:** quota on 2026-01-01 = R$ 1.00; quota on 2026-04-20 = R$ 1.08

**Expected TWR:** `(1.08 / 1.00) − 1 = 8,0%`

---

## Summary — On-Read Calculation

### SC-19: Summary is recalculated on every query (no materialized snapshot)

**Asserts:**
- After recording a transaction, next query to `investmentPortfolioSummary` returns updated values
- No cache invalidation step required

---

### SC-20: Summary reflects real-time valuation

**Setup:** live price for PETR4 changes between two page loads

**Asserts:**
- Second `investmentPortfolioSummary` call returns updated `totalGrossValue` and `grossGain`
- No stale cached value served
