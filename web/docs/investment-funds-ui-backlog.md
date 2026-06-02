# Investment Funds UI — Backlog

## Context

Extends the existing **`/app/personal-finance/investments`** page to support CVM investment
fund holdings alongside the existing fixed-income holdings. `RecordFixedIncomeDrawer` is the
structural reference.

Key differences from fixed income:
- The asset is looked up from the CVM catalog (autocomplete search) instead of typed manually
- No indexer, maturity date, or tax-exempt fields — replaced by quantity + price per quota
- The `mutualFunds` group in `InvestmentTable` needs expanded type coverage and fund-specific
  column rendering (quota value, quantity — not indexer / maturity)

---

## Epic 1 — Shared GraphQL Operations

**Goal:** Define reusable fund query operations consumed by the drawer autocomplete and any
future component.

| # | Task | File |
|---|------|------|
| 1.1 | Create `lib/graphql/operations/financial-data/queries.ts` with `SEARCH_INVESTMENT_FUNDS_AUTOCOMPLETE` — fields: `id`, `name`, `cnpj`, `assetType`, `isActive` | `lib/graphql/operations/financial-data/queries.ts` *(new)* |
| 1.2 | Export new operations from `lib/graphql/operations/index.ts` | `lib/graphql/operations/index.ts` |
| 1.3 | Run codegen to generate TypeScript types for the new query | `lib/graphql/types/__generated__/graphql.ts` |

> `INVESTMENT_FUNDS` (paginated) is not required for this epic — autocomplete covers the
> drawer use case. Add it only if a fund browser page is built later.

---

## Epic 2 — Asset Type Mapping & Group Expansion

**Goal:** Ensure CVM fund types flow into the correct `InvestmentTable` groups and that the
banking service receives a valid `AssetType` on creation.

| # | Task | File |
|---|------|-------|
| 2.1 | Add utility `lib/utils/mapCvmFundAssetType.ts` — maps `InvestmentFundAssetType` → banking `AssetType`: `FIXED_INCOME_FUND` → `MUTUAL_FUND`, `EQUITY_FUND` → `MUTUAL_FUND`, `MULTI_MARKET_FUND` → `MUTUAL_FUND`, `FOREX_FUND` → `MUTUAL_FUND`, `MUTUAL_FUND` → `MUTUAL_FUND`, `PGBL` → `PGBL`, `VGBL` → `VGBL` | `lib/utils/mapCvmFundAssetType.ts` *(new)* |
| 2.2 | Expand `ASSET_TYPES_BY_GROUP.mutualFunds` in `InvestmentTable` to include `'FIXED_INCOME_FUND'`, `'EQUITY_FUND'`, `'MULTI_MARKET_FUND'`, `'FOREX_FUND'` alongside the existing `'MUTUAL_FUND'` | `components/InvestmentTable.tsx` |
| 2.3 | Add `formatFundAssetType` display helper (used in chips): `FIXED_INCOME_FUND` → "Renda Fixa", `EQUITY_FUND` → "Ações", `MULTI_MARKET_FUND` → "Multimercado", `FOREX_FUND` → "Cambial", `MUTUAL_FUND` → "Misto", `PGBL` → "PGBL", `VGBL` → "VGBL" | `lib/utils/mapCvmFundAssetType.ts` (same file) |

---

## Epic 3 — RecordFundHoldingDrawer

**Goal:** Drawer that lets a user find a CVM fund by name, then record a holding (quantity +
price per quota + trade date) into one of their investment accounts. Mirrors
`RecordFixedIncomeDrawer` in structure.

### 3A — Fund autocomplete field

| # | Task | Notes |
|---|------|-------|
| 3.1 | Build `FundAutocomplete` form component — debounced 300 ms (reuse `useDebounce`), calls `SEARCH_INVESTMENT_FUNDS_AUTOCOMPLETE`; option renders fund name + CNPJ formatted + asset type chip; clears selection when input is cleared | `components/FundAutocomplete.tsx` *(new)* |
| 3.2 | CNPJ display format: `"12345678000195"` → `"12.345.678/0001-95"` — add `formatCnpj` to shared utils if not already present | `lib/utils/formatCnpj.ts` |

### 3B — Drawer form

| # | Task | Notes |
|---|------|-------|
| 3.3 | Create `RecordFundHoldingDrawer` — two-step flow identical to `RecordFixedIncomeDrawer`: step 1 account selection list (when `financialAccountId` not pre-set), step 2 asset form | `components/RecordFundHoldingDrawer.tsx` *(new)* |
| 3.4 | Step 2 fields: `FundAutocomplete` (required), `quantity` number input (number of quotas, positive), `unitPrice` money input (price per quota in BRL), `tradeDate` date picker | same file |
| 3.5 | Zod schema: fund required, quantity > 0, unitPrice > 0, tradeDate required | same file |
| 3.6 | `onSubmit`: call `CREATE_INVESTMENT_ASSET` with `{ name: fund.name, assetType: mapCvmFundAssetType(fund.assetType), issuerName: fund.cnpj, currencyCode: 'BRL' }`, then `RECORD_INVESTMENT_TRANSACTION` with `{ financialAccountId, assetId, direction: Inflow, type: Purchase, quantity, unitPrice, grossValue: quantity * unitPrice, tradeDate, settlementDate: tradeDate }`; refetch `INVESTMENT_HOLDINGS` + `ALL_INVESTMENT_HOLDINGS` on success | same file |
| 3.7 | Toast success message + close drawer on completion (same pattern as fixed income drawer) | same file |

---

## Epic 4 — InvestmentTable: Fund-Specific Rendering

**Goal:** The `mutualFunds` group renders columns appropriate for funds instead of showing
indexer / maturity date columns (which are irrelevant for funds).

| # | Task | Notes |
|---|------|-------|
| 4.1 | Add per-group column configuration in `InvestmentTable` — define a `FUND_GROUP_KEYS` set (`mutualFunds`, `pension`) that uses a different column set | `components/InvestmentTable.tsx` |
| 4.2 | Fund row columns: **Asset name**, **Fund type** (chip using `formatFundAssetType`), **Quantity** (number of quotas), **Gross value** (BRL), **Gain** (`GrowthChip`) — no Indexer column, no Maturity Date column | same file |
| 4.3 | `AssetGroupCard` for `mutualFunds` — title "Fundos de Investimento", summary shows count + total gross value; add "Registrar fundo" action button in the card header that opens `RecordFundHoldingDrawer` pre-scoped to the current account | `components/AssetGroupCard.tsx` (prop addition) or inline in `InvestmentTable` |

---

## Epic 5 — Page Integration

**Goal:** Wire the new drawer into `page.tsx` and expose a top-level action button for it.

| # | Task | File |
|---|------|------|
| 5.1 | Import and render `RecordFundHoldingDrawer` in `page.tsx`; add `recordFundOpen` / `setRecordFundOpen` state | `page.tsx` |
| 5.2 | Add "Registrar fundo" `Button` next to the existing "Registrar renda fixa" button in the top action row | `page.tsx` |
| 5.3 | Pass `onRecordFund` callback down through `InvestmentTable` → `AssetGroupCard` so the mutualFunds card header button opens the drawer (same pattern as `onRecordHolding` for fixed income) | `page.tsx` → `InvestmentTable.tsx` |
| 5.4 | Add "Fundos de Investimento" to the trackable assets empty state icon row (alongside Crypto, REITs, Stocks, Fixed Income) | `page.tsx` |

---

## Epic 6 — i18n

**Goal:** All user-visible strings externalized.

| # | Task |
|---|------|
| 6.1 | Fund drawer: drawer title, section headers ("Fundo", "Detalhes da aplicação"), field labels (fund, quantity, price per quota, trade date), validation messages, submit/cancel labels, success toast |
| 6.2 | Fund group card: group title "Fundos de Investimento", action button label "Registrar fundo" |
| 6.3 | Fund type chip labels (via `formatFundAssetType`): Renda Fixa, Ações, Multimercado, Cambial, Misto, PGBL, VGBL |
| 6.4 | Column headers for fund rows: Tipo, Qtd. Cotas, Valor Bruto, Rentabilidade |
| 6.5 | Empty state trackable assets label: "Fundos" |

---

## Suggested Build Order

```
Epic 1 → Epic 2 → Epic 3A → Epic 3B → Epic 4 → Epic 5 → Epic 6
```

Epics 3 and 4 can be parallelized once Epics 1 and 2 are done.
