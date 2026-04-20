# Investment Performance — Requirements Document

**Service:** Invistus Banking Service
**Module:** Investment Portfolio — Performance Calculation
**Document date:** 2026-04-20

---

## 1. Overview

Investment performance tracks how the portfolio has evolved over time, factoring in deposits, withdrawals, dividends, and asset price changes. The system maintains a time-series of portfolio flow events and uses a **quota system (cotas)** to compute TWR (Time-Weighted Return) — the industry standard used by ANBIMA and Brazilian platforms like Gorila and Kinvo.

The performance system is comprised of:

- **`PortfolioFlowEventService`** — persists and manages portfolio flow events (`investment_portfolio_flow_event` table).
- **`PortfolioFlowEventHandler`** (lifecycle handler) — registers flow events as side effects of investment transactions.
- **`PortfolioQuotaCalculationService`** — computes the quota series (TWR) from flow events + daily valuation snapshots.
- **`PortfolioPerformanceQueryService`** — serves performance data for GraphQL queries.
- **`PortfolioPerformanceSeriesService`** — builds the performance time series for chart display.
- **`InvestmentPortfolioSummaryService`** — computes current-state portfolio summary metrics.

---

## 2. Portfolio Flow Events

Every investment transaction generates a portfolio flow event as a side effect (via `PortfolioFlowEventHandler.onRecord`). The event type determines how it affects the TWR calculation.

| Investment Transaction Type | Flow Event Type | TWR Effect |
|---|---|---|
| `PURCHASE`, `SUBSCRIPTION` | `DEPOSIT` | Cash enters → new quotas issued at current cota price |
| `SALE`, `MATURITY` | `WITHDRAWAL` | Cash exits → quotas redeemed at current cota price |
| `DIVIDEND`, `INTEREST_PAYMENT`, `AMORTIZATION`, `PRIZE`, `STOCK_LENDING` | `DIVIDEND` | Cash enters → recorded as income, not new quotas |
| `WITHHOLDING_TAX` | `INCOME_TAX` | Tax reduces portfolio value |
| All other types | `requestRecalculation(tradeDate)` | Triggers recalculation from `tradeDate` forward |

On transaction **update**: the old flow event is removed and a new one is created (replay).
On transaction **delete**: the flow event is removed.

---

## 3. Quota System (TWR)

The portfolio is modeled as a fictitious investment fund:

```
Initial quota value = R$ 1.00 (on the date of the first flow event)

Each market day:
  new_quota = previous_quota × (1 + daily_return)
  daily_return = (end_value − start_value − net_flows_of_day) / start_value

On DEPOSIT (PURCHASE/SUBSCRIPTION):
  new_quotas = deposited_amount / quota_of_day
  (quota price unchanged; quantity of quotas increases)

On WITHDRAWAL (SALE/MATURITY):
  quotas_redeemed = withdrawn_amount / quota_of_day

TWR for any period:
  return = (quota_end / quota_start) − 1
```

**Advantage**: TWR eliminates the distortion caused by timing and size of deposits/withdrawals. A new R$50k deposit does not dilute the historical strategy return. This makes performance comparable to CDI, Ibovespa, and other benchmarks.

---

## 4. Portfolio Summary (Current State)

Backend service: `InvestmentPortfolioSummaryService.kt`
GraphQL query: `investmentPortfolioSummary(financialAccountId: ID): InvestmentPortfolioSummary!`

### 4.1 Fields

| Field | Calculation |
|---|---|
| `totalCostBasis` | Σ `(quantity × averageCost)` across active holdings |
| `totalGrossValue` | Σ `grossValue` after valuation (provider, calculated, or estimated) |
| `totalNetValue` | Σ `netValue` (after provisioned IR/IOF) |
| `totalIncomeTax` | Σ provisioned `incomeTax` |
| `totalIof` | Σ provisioned `iof` |
| `grossGain` | `totalGrossValue − totalCostBasis` |
| `grossGainPercent` | `grossGain / totalCostBasis` |
| `netGain` | `totalNetValue − totalCostBasis` |
| `netGainPercent` | `netGain / totalCostBasis` |
| `cashBalance` | Net ledger balance of the investment account |
| `totalPortfolioValue` | `totalGrossValue + cashBalance` |
| `hasEstimatedValues` | `true` if any holding has `valuationSource ∈ {ESTIMATED, UNAVAILABLE}` |
| `valuationReferenceDate` | Most recent indicator date used across estimated holdings |

### 4.2 Single Account vs. Consolidated

- `financialAccountId != null` → only holdings on that account.
- `financialAccountId = null` → sums across all `INVESTMENT` and `BENEFIT` accounts of the user.

### 4.3 Calculation is On-Read

There is no materialised snapshot for the summary. It is calculated at query time from live holdings + current valuations + ledger balance. This means it always reflects the latest data but may become slow with large portfolios. Materialization is deferred to a future optimization milestone.

---

## 5. Asset Group Mapping

Holdings are grouped by `AssetGroupKey` in both the backend summary and the frontend grid. The mapping is defined server-side in `AssetGroupMapping.kt` and mirrored client-side in `investmentTableHelpers.ts`.

| `groupKey` | `AssetType` values |
|---|---|
| `variableIncome` | `STOCK`, `STOCK_RIGHTS`, `BDR`, `ETF`, `ETF_INTERNATIONAL` |
| `reits` | `REAL_ESTATE_FUND`, `AGRIBUSINESS_FUND` |
| `bankFixedIncome` | `CDB`, `RDB`, `LCI`, `LCA`, `LC`, `LF`, `LIG` |
| `creditFixedIncome` | `DEBENTURES`, `CRI`, `CRA` |
| `treasury` | `TREASURY` |
| `fixedIncomeEtf` | `ETF_FIXED_INCOME` |
| `funds` | `MUTUAL_FUND`, `PGBL`, `VGBL` |
| `crypto` | `CRYPTO` |
| `cash` | Virtual — `cashBalance` only; `isCash = true` |

---

## 6. Valuation Sources

Holdings may have their `grossValue` populated from different sources, indicated by `valuationSource`:

| `ValuationSource` | Meaning |
|---|---|
| `PROVIDER` | Set by Open Finance sync or file import — authoritative |
| `MARKET_DATA` | Computed from live price from financial-data service (equities, ETFs, FIIs) |
| `MANUAL` | User-supplied value (fund with `pricingMode = MANUAL`) |
| `CALCULATED` | Computed from contracted PRE_FIXADO rate — exact |
| `ESTIMATED` | Computed using latest economic indicator (CDI, IPCA, SELIC, etc.) — approximate |
| `UNAVAILABLE` | Cannot be determined (unsupported indexer, missing issueDate, etc.) |

Valuation precedence: `PROVIDER` wins over all computed valuations. For fixed income: `FixedIncomeValuationService`. For funds with market pricing: `FundValuationService`. For equities/FIIs/ETFs: price from `FinancialDataListedAssetMarketDataProvider`.

---

## 7. Business Rules

**BR-1: Recalculation on transaction change.**
Any CRUD operation on an `InvestmentTransaction` triggers a recalculation of portfolio flow events from `tradeDate` forward. Recalculation is synchronous within the same `@Transactional` boundary.

**BR-2: Closed holdings are excluded.**
Holdings with `quantity = 0` and `deletedAt != null` are not included in summary calculations. Holdings with `quantity = 0` but no `deletedAt` (closed positions) are included at zero value — they do not affect the totals but are retained for history.

**BR-3: Gross gain is V1 metric.**
The displayed gain is gross (`grossGainPercent = grossGain / totalCostBasis`). Net gain (after IR) is computed in `netGainPercent` but is not prominently displayed in V1. IR calculation per asset class is complex and deferred.

**BR-4: Benchmark comparison is not yet implemented.**
CDI, IBOV, IPCA, IFIX benchmark series exist in the financial-data service but are not yet consumed to display a portfolio-vs-benchmark comparison on the investments page.

---

## 8. User Scenarios

**Scenario 1 — View portfolio summary**
User opens the investments page. `InvestmentPortfolioSummaryService` runs, valuates all holdings (using their respective valuation service), computes gains, and returns the summary. The UI shows `totalPortfolioValue`, `grossGain`, `grossGainPercent`, `cashBalance`, and the group breakdown.

**Scenario 2 — Summary shows estimated values**
User has a CDI-indexed CDB. The holding's valuation is `ESTIMATED` (using the latest CDI rate). The summary shows the total with a warning chip "estimado" next to it.

**Scenario 3 — New purchase affects summary**
User records a PURCHASE of R$ 10.000 in a CDB. `PortfolioFlowEventHandler.onRecord` registers a `DEPOSIT` event. `InvestmentPortfolioSummaryService` next call returns updated `totalCostBasis`, `totalGrossValue`, and `grossGain`.

**Scenario 4 — Sale affects summary**
User records a SALE of 100 shares at R$ 50 each (net R$ 5.000). `PortfolioFlowEventHandler.onRecord` registers a `WITHDRAWAL` event. Cash balance increases by R$ 5.000 (auto-created CREDIT via `CustodianCashLedgerHandler`). Portfolio gross value decreases by the holding's gross value portion.

---

## 9. Open Questions / Gaps

**9.1 Performance chart / time series is not fully exposed in UI.**
`PortfolioPerformanceSeriesService` and `PortfolioPerformanceQueryService` exist on the backend. `HoldingPerformanceChart` component exists on the frontend. The holding detail drawer shows performance per holding. A full portfolio-level performance chart (with period selectors and benchmark comparison) is not yet wired up in the main investments page.

**9.2 Cost basis uses `unitPrice`, not `netValue / quantity` for equities.**
Per DEC-4 in `holding-transaction-operations.md`, the correct cost basis for equities with broker fees should use `netValue / quantity` (which includes fees in the cost basis, per RFB IN 1.585/2015). The current implementation uses `unitPrice` (`grossValue / quantity`). This is a known inaccuracy tracked as `COST_BASIS_NET_VALUE_FIX`.

**9.3 Benchmark comparison is missing.**
CDI, Ibovespa, IPCA, IFIX comparison is not yet implemented on the portfolio page. The data exists in the financial-data service. This is V2 scope.

**9.4 TWR quota series requires daily valuation snapshots.**
`PortfolioQuotaCalculationService` needs consistent daily valuations to compute the quota series accurately. When holdings use `ESTIMATED` or `UNAVAILABLE` valuation, the TWR accuracy degrades. This is acceptable for V1.
