# Portfolio Page — Requirements Document

**Service:** Invistus Banking Service + Frontend
**Module:** Investment Portfolio — Portfolio Page
**Document date:** 2026-04-20

---

## 1. Overview

The investments page (`/personal-finance/investments`) is the main entry point for the investment portfolio module. It provides:

- **Sidebar** — account selector listing all `INVESTMENT` and `BENEFIT` accounts. Allows switching between a single account and a consolidated view.
- **InvestmentSummary** — portfolio metrics panel above the grid: total portfolio value, gross gain, cash balance, and allocation breakdown by asset group.
- **InvestmentGrid** — the main holdings view, organized into `AssetGroupCard` sections, one per asset class with holdings in that account. Also shows a cash movements section.
- **PendingActionsDrawer** — opened via `?pendingActions=1` query param; lists pending events (maturity, coupon, come-cotas).
- **Empty states** — when no accounts exist, or when the selected account has no holdings.

Route: `web/src/app/personal-finance/investments/page.tsx`

---

## 2. Page Layout

```
┌─ Sidebar ──────────────────┐  ┌─ Main Content ──────────────────────────────────────┐
│                            │  │                                                      │
│ [XP Investimentos]         │  │  ┌── InvestmentSummary ──────────────────────────┐   │
│ [BTG Pactual]              │  │  │  Portfólio Total: R$ 1.250.432,50             │   │
│ [Todas as contas]          │  │  │  Rendimento: +R$ 87.320  (+7,5%)              │   │
│                            │  │  │  Total investido: R$ 1.162.000               │   │
│ [+ Criar conta]            │  │  │  Caixa: R$ 12.112                             │   │
│                            │  │  │  Alocação: [breakdown by group]               │   │
│                            │  │  └──────────────────────────────────────────────┘   │
│                            │  │                                                      │
│                            │  │  [Busca]            [Registrar ▼] [Ações Pendentes] │
│                            │  │                                                      │
│                            │  │  ┌── AssetGroupCard: Caixa ───────────────────────┐ │
│                            │  │  ├── AssetGroupCard: Renda Fixa ──────────────────┤ │
│                            │  │  ├── AssetGroupCard: Renda Variável ──────────────┤ │
│                            │  │  └── ...                                           │ │
└────────────────────────────┘  └──────────────────────────────────────────────────────┘
```

---

## 3. Account Selector (Sidebar)

- Loads all `FinancialAccount` with `type = INVESTMENT` or `type = BENEFIT` via `financialAccounts` query.
- Displays each account with its issuer name (or custom name).
- First account is selected by default when accounts exist.
- A "Todas as contas" option enables consolidated view (`selectedAccountId = null`).
- A "+ Criar conta" button opens `CreateInvestmentAccountDrawer`.
- `selectedAccountId` is stored in local React state; it is passed as `financialAccountId` to `InvestmentSummary` and `InvestmentGrid` queries.
- Bank accounts (`type = BANK`) are loaded separately to populate cash destination selectors in transaction drawers.

---

## 4. InvestmentSummary

Backend query: `investmentPortfolioSummary(financialAccountId: ID): InvestmentPortfolioSummary!`

Source: `InvestmentPortfolioSummaryService.kt`

### 4.1 Metrics

| Field | Description |
|---|---|
| `totalPortfolioValue` | `totalGrossValue + cashBalance` — primary metric displayed as h3 |
| `grossGain` | `totalGrossValue − totalCostBasis` (signed, BRL) |
| `grossGainPercent` | `grossGain / totalCostBasis` |
| `netGain` | `totalNetValue − totalCostBasis` |
| `netGainPercent` | `netGain / totalCostBasis` |
| `totalCostBasis` | Σ `(quantity × averageCost)` across all active holdings |
| `totalGrossValue` | Σ `grossValue` after valuation (provider, calculated, or estimated) |
| `cashBalance` | Ledger balance of manual cash transactions on the account |
| `hasEstimatedValues` | `true` if any holding uses `ESTIMATED` or `UNAVAILABLE` valuation source |
| `valuationReferenceDate` | Most recent indicator date used across all estimated holdings |

### 4.2 Allocation Breakdown

`byAssetGroup` contains one entry per asset group with holdings. Groups:

| `groupKey` | Asset types included |
|---|---|
| `variableIncome` | `STOCK`, `STOCK_RIGHTS`, `BDR`, `ETF`, `ETF_INTERNATIONAL` |
| `reits` | `REAL_ESTATE_FUND`, `AGRIBUSINESS_FUND` |
| `bankFixedIncome` | `CDB`, `RDB`, `LCI`, `LCA`, `LC`, `LF`, `LIG` |
| `creditFixedIncome` | `DEBENTURES`, `CRI`, `CRA` |
| `treasury` | `TREASURY` |
| `funds` | `MUTUAL_FUND`, `PGBL`, `VGBL` |
| `crypto` | `CRYPTO` |
| `cash` | Virtual group — `cashBalance`; `isCash = true` |

Each group row shows: asset type icon + label + `LinearProgress` allocation bar + allocation % + gain %.

Up to 5 groups inline; a "Ver todos os grupos" button opens a drawer with the full list.

### 4.3 States

- **Loading**: Skeleton placeholders for all values.
- **Error**: Retry state with icon and message.
- **Estimated values**: When `hasEstimatedValues = true`, a warning chip "estimado" appears next to the total.

### 4.4 Single account vs. consolidated

- `financialAccountId != null` → summary scoped to that account.
- `financialAccountId = null` → aggregated across all `INVESTMENT` and `BENEFIT` accounts of the user.

---

## 5. InvestmentGrid

The grid is organized into `AssetGroupCard` sections, one per asset class that has holdings in the selected account.

### 5.1 Asset Group Cards

Each `AssetGroupCard` contains:
- Group header (icon + label + count + total value)
- Holdings table specific to the group (see class-specific requirement docs for columns)
- An "Add" button scoped to that class

Group ordering: fixed canonical order matching the sidebar breakdown.

### 5.2 Cash Movements Section

A dedicated section (`CashMovementsSection`) at the top of the grid shows recent manual cash transactions (deposits, withdrawals, adjustments) on the selected investment account. Each row has inline edit/delete actions.

### 5.3 Holding Row Actions

Each holding row has a context menu (`InvestmentHoldingActionsMenu`) with actions relevant to the asset class:
- View detail drawer (`InvestmentHoldingDetailDrawer`)
- Record transaction (Aporte/Compra, Resgate/Venda, and class-specific events)
- Transfer custody
- Delete (soft-delete — only for `source = MANUAL`)

### 5.4 Holding Detail Drawer

`InvestmentHoldingDetailDrawer` opens on holding row click or via the actions menu. Content:
- For fixed income: `FixedIncomeHoldingDetailContent` (contractual terms + coupon schedule)
- For funds: `FundHoldingDetailContent` (fund metadata + valuation mode)
- For all: transaction history (`TimelineEvents`) + performance chart (`HoldingPerformanceChart`)

### 5.5 InvestmentEventSelectorDrawer

When the "Registrar ▼" button is clicked, `InvestmentEventSelectorDrawer` opens — a selection menu showing available operation types for the selected account's assets (filtered by `InvestmentHoldingAssetClass`).

---

## 6. Empty States

**Scenario A — No investment accounts**
Page shows a full-page empty state. Sidebar is not shown. Primary CTA: "Criar conta de investimento". Secondary link: learn more.

**Scenario B — Account exists but has no holdings**
Grid shows an empty state scoped to the account. CTA: "Registrar ativo". Opens the first-holding flow (asset creation + PURCHASE transaction).

**Scenario C — Search returns no results**
Grid shows "Nenhum ativo encontrado" with the current search query.

---

## 7. Pending Actions

- Pending actions badge shows count of visible pending actions for the user.
- Query param `?pendingActions=1` opens `PendingActionsDrawer`.
- Drawer lists pending events grouped by type: `INVESTMENT_MATURED`, `COUPON_PAYMENT_DUE`.
- Clicking a pending action opens the relevant confirm drawer pre-populated with holding data.

---

## 8. Search

`holdingSearchQuery` state is passed to `InvestmentGrid`. It filters holdings client-side by asset name or ticker across all visible groups.

---

## 9. Open Questions / Gaps

**9.1 Group filter interaction between summary and grid is deferred (RF-S07).**
The `onGroupClick` prop on `InvestmentSummary` and the `activeGroupFilter` state are defined but not wired up. Clicking an asset group in the summary does not filter the grid yet.

**9.2 Period selector for performance is visible but not fully wired.**
`PeriodSelector` and `DetailPerformancePeriodSelector` components exist but period-based performance series is a separate feature from the current summary.

**9.3 Consolidated view performance accuracy.**
When `selectedAccountId = null`, `InvestmentPortfolioSummaryService` iterates all INVESTMENT/BENEFIT accounts and sums their metrics. This may include closed holdings (quantity = 0, grossValue = 0) from multiple accounts, which are correctly excluded. Cross-account transfers (custody transfers) may temporarily double-count until settlement — not handled specially.
