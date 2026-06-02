---
title: Holding Update Flow — Standardized Add/Withdraw UI Across Asset Classes
type: feature-spec
category: investment-portfolio
status: proposed
version: 1.0.0
tags: [investment, portfolio, holding, update, aporte, resgate, compra, venda, ui-standardization]
ai_optimized: true
search_keywords: [holding update, add position, withdraw position, aporte, resgate, compra, venda, drawer, standardized form]
related:
  - 03-features/investment-portfolio/holding-transaction-operations.md
  - 07-frontend/patterns/form-pattern.md
  - 07-frontend/patterns/management-pattern.md
last_updated: 2026-04-25
---

# Holding Update Flow — Standardized Add/Withdraw UI Across Asset Classes

> **One-line summary**: Define the unified UX pattern and implementation backlog for adding to or withdrawing from an *existing* holding across all investment asset classes (Renda Fixa, Fundos, Ações, FIIs, Cripto), sharing a consistent visual shell while respecting per-class field differences.

---

## Background and Context

### What Already Exists

The `holding-transaction-operations.md` spec fully documents:
- The **domain model** for PURCHASE/SALE/SUBSCRIPTION transactions per asset class.
- The **backend side-effect handler chain** architecture (cash ledger, performance, pending actions).
- The **frontend `useRecordHoldingForm` hook** abstraction and `BaseTransactionFields` component.
- The **Phase 1/2/3** implementation plan for extracting shared logic.

### What This Document Adds

The existing spec focuses on *recording a new position* (user selects an asset from scratch). This document focuses on the **holding update flow**: operating on an **already-created holding** from the portfolio view. The core difference is:

| Dimension | New Position (existing spec) | Update Holding (this spec) |
|---|---|---|
| Entry point | "Add investment" action | Holding card or detail menu action |
| Asset selection | User searches/creates an asset | Asset is fixed — shown as context |
| Fixed-income config | User enters indexer, rates, dates | All pre-populated from existing holding |
| Fund config | User selects/creates fund | Fund is fixed |
| Goal | Create a new holding | Add units to / remove units from existing holding |
| Drawer title | "Registrar {classe}" | "Aportar em {nome}" / "Resgatar de {nome}" / "Comprar {ticker}" / "Vender {ticker}" |

---

## Problem Statement

Currently each asset class has its own record drawer that treats every transaction as if the asset were being configured from scratch. When a user wants to add R$ 5.000 to a CDB they already hold, they are presented with the same full form they used to create the position: indexer, rate, maturity date, coupon schedule — fields that are **irrelevant for the incremental operation** and already set on the holding.

This creates three problems:
1. **Cognitive friction**: users must re-enter or skip past holding-level metadata on every incremental operation.
2. **Data inconsistency risk**: if the user changes indexer/maturity on a follow-up aporte, the holding-level data changes silently.
3. **No visual standardization**: each drawer looks and feels different across asset classes.

---

## Goals

1. A unified drawer shell (header, direction toggle, footer) shared by all asset classes.
2. Per-class field sections that only show **transaction-level fields** (what changes per operation).
3. Holding-level metadata (asset type, indexer, maturity, fund config) shown as **read-only context**, not editable.
4. Entry points from the holding card and holding detail page.
5. The existing full-form drawers remain untouched for the *new position* flow (Phase 2 of the existing spec).

---

## Functional Requirements

### FR-1: Entry Points

The update flow is triggered from:
- **Holding card context menu**: "Aportar" / "Comprar" + "Resgatar" / "Vender" quick actions.
- **Holding detail page**: primary action buttons at the top of the page.
- **Holding detail page — transaction tab**: "Add transaction" opens the same drawer, pre-set to INFLOW.

The drawer receives `holdingId` as its sole required prop. All other data (asset, account, existing config) is loaded from the holding.

### FR-2: Shared Drawer Shell

All asset classes use the same structural components:

```
┌─────────────────────────────────────────────┐
│ [AssetTypeIcon] Asset Name      [Close ×]   │  ← Drawer.Header
│ Issuer / Fund Name · {AssetType badge}      │
├─────────────────────────────────────────────┤
│ [Aportar]  [Resgatar]  (or Comprar/Vender)  │  ← Direction toggle
├─────────────────────────────────────────────┤
│  {Transaction Fields — vary by class}        │  ← Scrollable body
│  {Read-only Context — collapsed by default} │
├─────────────────────────────────────────────┤
│ [Cancel]              [Confirmar]           │  ← Drawer.ActionButtons
└─────────────────────────────────────────────┘
```

**Direction labels by class**:

| Class | INFLOW label | OUTFLOW label |
|---|---|---|
| Renda Fixa | Aportar | Resgatar |
| Fundos | Aportar | Resgatar |
| Ações | Comprar | Vender |
| FIIs | Comprar | Vender |
| Cripto | Comprar | Vender |

### FR-3: Read-Only Context Section

All drawers include a collapsible "Detalhes do ativo" section that displays holding-level metadata as read-only chips/text. This section is collapsed by default.

**Content by class**:

- **Renda Fixa**: indexer + rate, maturity date, coupon frequency, issuer, tax exemption status, current quantity.
- **Fundos**: fund CNPJ, fund type, pricing mode, current quantity in quotas.
- **Ações / FIIs / Cripto**: ticker, exchange/custodian, current quantity, average cost.

**Rule**: No field in this section is editable from the update flow. If the user needs to change holding-level config (e.g., correct an indexer), they use a separate "Edit holding" action (outside this spec).

### FR-4: Transaction Fields by Asset Class

#### FR-4.1: Renda Fixa — Aportar / Resgatar

**INFLOW (Aportar)**:

| Field | Required | Notes |
|---|---|---|
| Data do aporte | Yes | DatePickerField, within moving window |
| Valor aplicado (grossValue) | Yes | MoneyInput — total invested amount |
| Quantidade de títulos | No | Float input; auto-calculated if grossValue and unit price known |
| IR retido | No | Defaults to 0 |
| IOF | No | Defaults to 0 |

- Indexer, rate, maturity, coupon: **NOT editable** — taken from existing holding.
- Transaction type sent to API: `PURCHASE`.

**OUTFLOW (Resgatar)**:

| Field | Required | Notes |
|---|---|---|
| Data do resgate | Yes | DatePickerField |
| Valor bruto de resgate (grossValue) | Yes | MoneyInput |
| Valor líquido (netValue) | Yes | MoneyInput — after IR |
| IR retido | Yes | Auto-calculated as grossValue − netValue; editable |
| IOF | No | Defaults to 0 |
| Quantidade de títulos | No | Auto-derived from grossValue / averageCost; editable override |

- Transaction type sent to API: `SALE`.
- Validation: derived quantity ≤ current holding quantity.

#### FR-4.2: Fundos — Aportar / Resgatar

**INFLOW (Aportar)**:

| Field | Required | Notes |
|---|---|---|
| Data da aplicação | Yes | DatePickerField |
| Valor aplicado (grossValue) | Yes | MoneyInput |
| Quantidade de cotas | No | Shown when pricingMode = MANUAL; auto-calc via grossValue / unitPrice |
| Preço da cota (unitPrice) | No | Shown when pricingMode = MANUAL |
| Data de valorização | No | Only when pricingMode = MANUAL |

- Transaction type: `SUBSCRIPTION` (all subsequent deposits after the initial position).

**OUTFLOW (Resgatar)**:

| Field | Required | Notes |
|---|---|---|
| Data do resgate | Yes | DatePickerField |
| Valor líquido (netValue) | Yes | MoneyInput — DEC-3: user always confirms |
| Quantidade de cotas | No | If pricingMode = MARKET: system suggests qty × last NAV; editable |
| IR retido | No | Defaults to 0 |

- Transaction type: `SALE`.

#### FR-4.3: Ações e FIIs — Comprar / Vender

**INFLOW (Comprar)**:

| Field | Required | Notes |
|---|---|---|
| Data da operação | Yes | DatePickerField |
| Data de liquidação | No | Defaults D+2; editable |
| Quantidade | Yes | Integer input |
| Preço unitário | Yes | MoneyInput |
| Corretagem | No | MoneyInput (brokerageFee) |
| Taxas de liquidação | No | MoneyInput (settlementFee) |
| Outras taxas | No | MoneyInput |
| IR retido na fonte | No | MoneyInput (withholdingTax) |

- grossValue auto-calculated as quantity × unitPrice.
- netValue auto-calculated as grossValue + total fees.
- Transaction type: `PURCHASE`.

**OUTFLOW (Vender)**:

| Field | Required | Notes |
|---|---|---|
| Data da operação | Yes | DatePickerField |
| Data de liquidação | No | Defaults D+2; editable |
| Quantidade | Yes | Integer input; validated ≤ current qty |
| Preço de venda | Yes | MoneyInput |
| Corretagem | No | MoneyInput |
| Taxas de liquidação | No | MoneyInput |
| IRRF | No | MoneyInput |

- Transaction type: `SALE`.

#### FR-4.4: Cripto — Comprar / Vender

**INFLOW (Comprar)**:

| Field | Required | Notes |
|---|---|---|
| Data da operação | Yes | DatePickerField |
| Quantidade | Yes | Float with high precision (8 decimal places) |
| Preço unitário | Yes | MoneyInput |

- No settlement date, no brokerage fees.
- grossValue = quantity × unitPrice.

**OUTFLOW (Vender)**:

| Field | Required | Notes |
|---|---|---|
| Data da operação | Yes | DatePickerField |
| Quantidade | Yes | Float (8 dec places); validated ≤ current qty |
| Preço de venda | Yes | MoneyInput |

### FR-5: Field Summary Matrix

| Field | Renda Fixa | Fundos | Ações/FIIs | Cripto |
|---|:---:|:---:|:---:|:---:|
| Direction toggle | ✓ | ✓ | ✓ | ✓ |
| Trade date | ✓ | ✓ | ✓ | ✓ |
| Settlement date | — | — | ✓ (D+2) | — |
| grossValue | ✓ | ✓ | auto | auto |
| netValue | ✓ (outflow) | ✓ (outflow) | auto | auto |
| Quantity | optional | optional | **required** | **required** |
| Unit price | — | optional (MANUAL) | **required** | **required** |
| IR / IRRF | optional | optional | optional | — |
| IOF | optional | — | — | — |
| Corretagem | — | — | optional | — |
| Settlement fee | — | — | optional | — |
| Read-only context | ✓ | ✓ | ✓ | ✓ |

### FR-6: Post-Submit Behavior

After a successful submission:
1. Toast success message: "Operação registrada com sucesso."
2. Drawer closes.
3. Refetch: `INVESTMENT_HOLDINGS`, `INVESTMENT_CASH_BALANCE`, `INVESTMENT_PORTFOLIO_SUMMARY`.
4. If on holding detail page: also refetch `INVESTMENT_TRANSACTIONS` for the holding.

### FR-7: Error Handling

- `<ErrorAlert error={mutationError} />` below the form body (above ActionButtons).
- Field-level validation errors inline via `react-hook-form` + `zodResolver`.
- Quantity exceeding current position: client-side Zod validation with specific message ("Quantidade maior que a posição atual de {qty}").

---

## Architecture Recommendation

### Overview

The update flow is a **thin wrapper** on top of the infrastructure being built in Phases 1 and 2 of `holding-transaction-operations.md`. It does not introduce new GraphQL mutations — it calls `RECORD_INVESTMENT_TRANSACTION` with the exact same inputs, just with fewer editable fields because the asset context is pre-loaded.

### Component Tree

```
UpdateHoldingDrawer                    ← new: orchestrator, receives holdingId
├── useHolding(holdingId)              ← existing query hook
├── useUpdateHoldingForm               ← new: thin wrapper over useRecordHoldingForm
│   └── useRecordHoldingForm           ← Phase 2 shared hook (existing spec)
└── HoldingUpdateDrawerShell           ← new: shared visual shell
    ├── HoldingContextHeader           ← new: asset name, issuer, badge
    ├── DirectionToggle                ← new: shared component
    ├── {AssetClass}UpdateFields       ← new: 4 focused field components
    │   ├── FixedIncomeUpdateFields
    │   ├── FundUpdateFields
    │   ├── EquityUpdateFields
    │   └── CryptoUpdateFields
    ├── HoldingContextCollapsible      ← new: read-only metadata
    └── Drawer.ActionButtons           ← existing shared component
```

### Key Design Decisions

**DEC-U1: Single `UpdateHoldingDrawer` entry point with internal branching**

Rather than four separate drawers (`UpdateFixedIncomeDrawer`, `UpdateFundDrawer`, etc.), use a single `UpdateHoldingDrawer` that branches on `holding.asset.assetType` to render the correct `{AssetClass}UpdateFields`. This keeps routing and entry-point logic in one place and ensures the shell is always identical.

```typescript
// UpdateHoldingDrawer.tsx
function UpdateHoldingDrawer({ holdingId, open, onClose }) {
  const { holding } = useHolding(holdingId)
  const gridClass = assetTypeToGridClass(holding.asset.assetType)

  return (
    <HoldingUpdateDrawerShell holding={holding} onClose={onClose}>
      {gridClass === 'fixed-income' && <FixedIncomeUpdateFields />}
      {gridClass === 'funds'        && <FundUpdateFields />}
      {gridClass === 'stocks'       && <EquityUpdateFields />}
      {gridClass === 'reits'        && <EquityUpdateFields />}
      {gridClass === 'crypto'       && <CryptoUpdateFields />}
    </HoldingUpdateDrawerShell>
  )
}
```

**DEC-U2: `useUpdateHoldingForm` delegates to `useRecordHoldingForm`**

`useUpdateHoldingForm` is a thin adapter — it pre-fills `financialAccountId` and `assetId` from the loaded holding and builds the `RecordInvestmentTransactionInput` with the holding's fixed fields merged in:

```typescript
function useUpdateHoldingForm(holding, direction) {
  return useRecordHoldingForm({
    schema: schemaForClass(holding.asset.assetType, direction),
    financialAccountId: holding.financialAccountId,
    onBuildInput: (data) => ({
      ...buildBaseInput(data, holding),          // merges holding fixed fields
      ...buildClassSpecificInput(data, holding), // class-specific overrides
    }),
  })
}
```

**DEC-U3: Holding metadata is read-only — never sent back on update**

The drawer must not re-send holding-level fields (indexer, maturityDate, couponFrequency, pricingMode) on INFLOW transactions. They are stripped from the input object before submission. The API will not change them unless explicitly included.

**DEC-U4: `buildBaseInput` sets `assetId` from holding — no asset autocomplete**

The `UpdateHoldingDrawer` never renders an asset search field. `assetId` is always `holding.asset.id`. This is what differentiates the update flow from the new-position flow.

**DEC-U5: Quantity validation is client-side with server-side fallback**

On OUTFLOW, Zod schema validates `quantity <= holding.quantity`. The API also validates this and returns a structured error if the race condition occurs (another device submitted a partial sale first). Map this GraphQL error to an inline `ErrorAlert`.

---

## Detailed Implementation Backlog

> Prerequisites: `useRecordHoldingForm` and `BaseTransactionFields` from Phase 2 of `holding-transaction-operations.md` should be completed first, as this feature depends on them. If they are not yet ready, Phase 2 tasks from that spec can be done in parallel or immediately before this backlog.

---

### TICKET-U0: Prerequisites Check (0.5d)

**Goal**: Verify Phase 2 of `holding-transaction-operations.md` is complete before starting.

**Acceptance Criteria**:
- [ ] `hooks/useRecordHoldingForm.ts` exists and is tested.
- [ ] `components/BaseTransactionFields.tsx` exists.
- [ ] If not complete, implement Phase 2 first or create stubs.

---

### TICKET-U1: Data Layer — `useHolding` Query Hook (0.5d)

**Goal**: Lightweight hook to fetch a single holding by ID with the fields needed for the update drawer.

**Files to create**:
- `web/src/app/personal-finance/investments/hooks/useHolding.ts`

**GraphQL query fields needed**:
```graphql
query GetHolding($holdingId: ID!) {
  investmentHolding(id: $holdingId) {
    id
    financialAccountId
    quantity
    averageCost
    pricingMode
    indexer
    indexerPercentage
    fixedAnnualRate
    maturityDate
    couponFrequency
    couponDayOfMonth
    isTaxExempt
    asset {
      id
      name
      ticker
      assetType
      issuerName
      currencyCode
      logoAsset { url }
    }
  }
}
```

**Notes**: If the `investmentHolding(id)` resolver doesn't exist, it can be derived from `investmentHoldings` by filtering — or add the resolver (backend TICKET-U1-BE).

**Acceptance Criteria**:
- [ ] Hook returns `{ holding, loading, error }`.
- [ ] TypeScript types fully derived from GraphQL codegen.

---

### TICKET-U1-BE: Backend — `investmentHolding(id)` Query Resolver (0.5d)

**Goal**: Add a single-holding-by-ID resolver if it doesn't already exist.

**Files**: GraphQL schema + resolver in banking service.

**Acceptance Criteria**:
- [ ] `query { investmentHolding(id: "...") { ... } }` returns the holding for the authenticated user's tenant.
- [ ] Returns 404/null for holdings belonging to other tenants.

---

### TICKET-U2: Shell Components (1d)

**Goal**: Build the shared visual shell for the update drawer.

**Files to create**:
- `web/src/app/personal-finance/investments/components/update/HoldingUpdateDrawerShell.tsx`
- `web/src/app/personal-finance/investments/components/update/HoldingContextHeader.tsx`
- `web/src/app/personal-finance/investments/components/update/HoldingContextCollapsible.tsx`
- `web/src/app/personal-finance/investments/components/update/DirectionToggle.tsx`

**`HoldingContextHeader`** renders:
- Left: `AssetTypeIcon` + asset name (bold) + issuer/fund name (muted).
- Right: `AssetTypeBadge` chip.

**`DirectionToggle`** renders:
- MUI `ToggleButtonGroup` with two options (labels injected as props).
- Controlled component: `value`, `onChange`.
- `size="medium"` matching form field size per form pattern.

**`HoldingContextCollapsible`** renders:
- MUI `Accordion` collapsed by default.
- Summary: "Detalhes do ativo ▶"
- Detail content: read-only key/value pairs for class-specific metadata (see FR-3).

**Acceptance Criteria**:
- [ ] Shell renders with correct header for a fixed-income holding stub.
- [ ] Direction toggle fires onChange with correct direction value.
- [ ] Collapsible expands/collapses without error.

---

### TICKET-U3: Zod Schemas for Each Asset Class (0.5d)

**Goal**: Define four Zod schemas for the update-flow fields.

**Files to create**:
- `web/src/app/personal-finance/investments/components/update/schemas.ts`

**Schemas**:

```typescript
// Base — shared across all OUTFLOW schemas
const baseOutflowSchema = z.object({
  direction: z.literal('OUTFLOW'),
  tradeDate: z.date(),
})

// fixedIncomeInflowSchema, fixedIncomeOutflowSchema
// fundInflowSchema, fundOutflowSchema
// equityInflowSchema, equityOutflowSchema
// cryptoInflowSchema, cryptoOutflowSchema
```

Each schema in `React.useMemo` when using `t()` for locale-aware messages (per form pattern).

**Acceptance Criteria**:
- [ ] All schemas have TypeScript types exported.
- [ ] Quantity > 0 validated on inflow; quantity ≤ holding.quantity validated on outflow.
- [ ] Locale-aware decimal parsing for money fields.

---

### TICKET-U4: `useUpdateHoldingForm` Hook (0.5d)

**Goal**: Adapter hook wrapping `useRecordHoldingForm` with holding-pre-filled data.

**File to create**:
- `web/src/app/personal-finance/investments/hooks/useUpdateHoldingForm.ts`

**Signature**:
```typescript
function useUpdateHoldingForm<TFields>(
  holding: InvestmentHolding,
  direction: InvestmentTransactionDirection,
  options: {
    schema: ZodSchema<TFields>
    onBuildInput: (data: TFields, holding: InvestmentHolding) => RecordInvestmentTransactionInput
    onSuccess?: () => void
  }
): UseRecordHoldingFormReturn<TFields>
```

**Notes**:
- Injects `financialAccountId` and `assetId` from holding automatically.
- Passes `refetchQueries` including `INVESTMENT_TRANSACTIONS` (needed for holding detail page).

**Acceptance Criteria**:
- [ ] Hook calls `useRecordHoldingForm` with correct config.
- [ ] `onBuildInput` receives both form data and the holding object for merging fixed fields.

---

### TICKET-U5: Fixed Income Update Fields Component (1d)

**Goal**: Transaction-only form fields for Renda Fixa.

**File to create**:
- `web/src/app/personal-finance/investments/components/update/FixedIncomeUpdateFields.tsx`

**Props**:
```typescript
interface FixedIncomeUpdateFieldsProps {
  form: UseFormReturn<FixedIncomeUpdateFormData>
  direction: InvestmentTransactionDirection
  holding: InvestmentHolding
}
```

**INFLOW fields**: tradeDate, grossValue, quantity (optional), IR (optional), IOF (optional).

**OUTFLOW fields**: tradeDate, grossValue, netValue, IR (auto-calculated, editable), IOF (optional), quantity (optional, with max hint showing current holding.quantity).

**Acceptance Criteria**:
- [ ] All fields use `size="medium"` per form pattern.
- [ ] `FormStack` / `FormRowStack` layout.
- [ ] Outflow shows current quantity as helper text: "Posição atual: {qty} títulos".
- [ ] No holding-level fields (indexer, maturity, coupon) are rendered.

---

### TICKET-U6: Fund Update Fields Component (1d)

**Goal**: Transaction-only form fields for Fundos.

**File to create**:
- `web/src/app/personal-finance/investments/components/update/FundUpdateFields.tsx`

**INFLOW fields**:
- tradeDate.
- grossValue.
- If `holding.pricingMode === 'MANUAL'`: show unitPrice (cota price) + quantity (auto-calculated) + valuedAt date.
- If `holding.pricingMode === 'MARKET'`: show only grossValue (quantity derived server-side by CVM NAV).

**OUTFLOW fields**:
- tradeDate.
- netValue (required — DEC-3).
- If `holding.pricingMode === 'MARKET'`: show suggested quantity (computed from last CVM NAV), editable.
- IR (optional).

**Acceptance Criteria**:
- [ ] Pricing-mode-aware field visibility.
- [ ] Transaction type `SUBSCRIPTION` sent for INFLOW (not `PURCHASE`).

---

### TICKET-U7: Equity Update Fields Component (1d)

**Goal**: Transaction-only form fields for Ações and FIIs.

**File to create**:
- `web/src/app/personal-finance/investments/components/update/EquityUpdateFields.tsx`

**INFLOW fields**: tradeDate, settlementDate (D+2 default), quantity, unitPrice, brokerageFee, settlementFee, otherFees, withholdingTax.

**OUTFLOW fields**: tradeDate, settlementDate (D+2 default), quantity (max = holding.quantity), salePrice (unitPrice), brokerageFee, settlementFee, IRRF.

**Notes**:
- grossValue auto-calculated from quantity × unitPrice (display-only field).
- netValue auto-calculated from grossValue ± fees.
- Brokerage fields in a collapsible "Taxas de corretagem" sub-section, collapsed by default.

**Acceptance Criteria**:
- [ ] Quantity validation with max hint: "Posição atual: {qty} ações".
- [ ] Fee sub-section collapses cleanly.
- [ ] Works for both STOCK/BDR/ETF (stocks) and REAL_ESTATE_FUND/AGRIBUSINESS_FUND (FIIs).

---

### TICKET-U8: Crypto Update Fields Component (0.5d)

**Goal**: Transaction-only form fields for Cripto.

**File to create**:
- `web/src/app/personal-finance/investments/components/update/CryptoUpdateFields.tsx`

**INFLOW fields**: tradeDate, quantity (8 decimal places), unitPrice.

**OUTFLOW fields**: tradeDate, quantity (max = holding.quantity, 8 decimal places), unitPrice (sale price).

**Notes**:
- No fees, no settlement date.
- grossValue auto-calculated display.

**Acceptance Criteria**:
- [ ] Quantity field accepts up to 8 decimal places without truncation.
- [ ] Direction toggle labels: "Comprar" / "Vender".

---

### TICKET-U9: `UpdateHoldingDrawer` Orchestrator (1d)

**Goal**: Top-level drawer component that assembles all pieces.

**File to create**:
- `web/src/app/personal-finance/investments/components/update/UpdateHoldingDrawer.tsx`

**Props**:
```typescript
interface UpdateHoldingDrawerProps {
  holdingId: string
  open: boolean
  onClose: () => void
  initialDirection?: InvestmentTransactionDirection
}
```

**Internal logic**:
1. `useHolding(holdingId)` — loads holding.
2. `assetTypeToGridClass(holding.asset.assetType)` — resolves asset class.
3. Renders `HoldingUpdateDrawerShell`.
4. Renders the correct `{AssetClass}UpdateFields` based on grid class.
5. `useUpdateHoldingForm` wired to the correct schema and `onBuildInput`.
6. `Drawer.ActionButtons` with `isLoading`, `onCancel`.
7. `<ErrorAlert error={mutationError} />`.

**Acceptance Criteria**:
- [ ] Drawer opens and closes correctly.
- [ ] Loading skeleton shown while `useHolding` is fetching.
- [ ] Error state shown if holding fails to load.
- [ ] All four asset classes render their correct fields.
- [ ] Submit calls mutation and closes on success with toast.

---

### TICKET-U10: Entry Points — Holding Card Context Menu (0.5d)

**Goal**: Wire the holding card's context menu to open `UpdateHoldingDrawer`.

**Files to modify**:
- `web/src/app/personal-finance/investments/components/table/InvestmentTable.tsx` (or grid card component).
- Add `UpdateHoldingDrawer` import and state: `[updateHoldingId, setUpdateHoldingId]`.

**Actions to add**:
- "Aportar" / "Comprar" → opens drawer with `initialDirection="INFLOW"`.
- "Resgatar" / "Vender" → opens drawer with `initialDirection="OUTFLOW"`.

**Label logic**: use `gridClass` to determine correct labels (Aportar vs. Comprar, Resgatar vs. Vender).

**Acceptance Criteria**:
- [ ] Context menu shows correct labels per asset class.
- [ ] Clicking action opens `UpdateHoldingDrawer` with correct `holdingId`.
- [ ] Only one drawer open at a time.

---

### TICKET-U11: Entry Points — Holding Detail Page (0.5d)

**Goal**: Add primary action buttons on the holding detail page.

**Files to modify**:
- `web/src/app/personal-finance/investments/[holdingId]/page.tsx` (or equivalent detail page).

**Actions to add**:
- Primary action button (MUI `Button` variant="contained"): "Aportar" / "Comprar" → `initialDirection="INFLOW"`.
- Secondary action button (MUI `Button` variant="outlined"): "Resgatar" / "Vender" → `initialDirection="OUTFLOW"`.

**Acceptance Criteria**:
- [ ] Buttons render with correct labels.
- [ ] Drawer opens from the detail page.
- [ ] After submit, `INVESTMENT_TRANSACTIONS` query refetches and table updates.

---

### TICKET-U12: i18n Strings (0.5d)

**Goal**: Add all new UI strings to the i18n files.

**Keys to add** (namespace `investments`):

```json
{
  "updateHolding": {
    "direction": {
      "inflow": "Aportar",
      "outflow": "Resgatar",
      "inflowEquity": "Comprar",
      "outflowEquity": "Vender"
    },
    "contextSection": "Detalhes do ativo",
    "currentPosition": "Posição atual: {{qty}} {{unit}}",
    "quantityExceeds": "Quantidade maior que a posição atual de {{qty}} {{unit}}",
    "success": "Operação registrada com sucesso.",
    "fields": {
      "tradeDate": "Data da operação",
      "settlementDate": "Data de liquidação",
      "grossValue": "Valor bruto",
      "netValue": "Valor líquido",
      "quantity": "Quantidade",
      "unitPrice": "Preço unitário",
      "salePrice": "Preço de venda",
      "incomeTax": "IR retido",
      "iof": "IOF",
      "brokerageFee": "Corretagem",
      "settlementFee": "Emolumentos",
      "otherFees": "Outras taxas"
    }
  }
}
```

**Acceptance Criteria**:
- [ ] All strings present in `pt-BR` locale.
- [ ] No hardcoded Portuguese strings in components (all via `t()`).

---

### TICKET-U13: E2E Smoke Tests (1d)

**Goal**: Basic automated coverage for the happy path of each asset class.

**Tests to write**:
- Fixed Income INFLOW: open drawer → fill tradeDate + grossValue → submit → toast visible.
- Fixed Income OUTFLOW: fill tradeDate + grossValue + netValue → submit.
- Equity INFLOW: fill quantity + unitPrice → submit.
- Equity OUTFLOW: fill quantity (at max) → submit.
- Cripto INFLOW: fill quantity with decimals + unitPrice → submit.

**Acceptance Criteria**:
- [ ] All 5 smoke tests pass in CI.
- [ ] Mutation input captured by mock matches expected shape.

---

## Implementation Order and Estimates

| Ticket | Description | Estimate | Dependencies |
|---|---|---|---|
| TICKET-U0 | Prerequisites check | 0.5d | — |
| TICKET-U1-BE | Backend: single holding resolver | 0.5d | — |
| TICKET-U1 | `useHolding` query hook | 0.5d | U1-BE |
| TICKET-U2 | Shell components | 1d | U0 |
| TICKET-U3 | Zod schemas | 0.5d | — |
| TICKET-U4 | `useUpdateHoldingForm` hook | 0.5d | U0, U3 |
| TICKET-U5 | Fixed income update fields | 1d | U2, U3, U4 |
| TICKET-U6 | Fund update fields | 1d | U2, U3, U4 |
| TICKET-U7 | Equity update fields | 1d | U2, U3, U4 |
| TICKET-U8 | Crypto update fields | 0.5d | U2, U3, U4 |
| TICKET-U9 | `UpdateHoldingDrawer` orchestrator | 1d | U1, U5, U6, U7, U8 |
| TICKET-U10 | Entry points: holding card menu | 0.5d | U9 |
| TICKET-U11 | Entry points: holding detail page | 0.5d | U9 |
| TICKET-U12 | i18n strings | 0.5d | U2–U8 |
| TICKET-U13 | E2E smoke tests | 1d | U9, U10, U11 |
| **Total** | | **~9.5d** | |

**Parallel execution opportunities**:
- TICKET-U1-BE + TICKET-U2 + TICKET-U3 can run in parallel.
- TICKET-U5 through TICKET-U8 can run in parallel (separate developers) once U2/U3/U4 are done.

---

## Out of Scope (V1)

The following are intentionally excluded from this spec:

1. **Editing existing transactions**: the update flow only adds new transactions. Editing is handled by opening the transaction in `InvestmentEventSelectorDrawer` (already exists).
2. **Bulk operations**: adding to multiple holdings at once.
3. **Editing holding-level metadata** (indexer, maturity date, coupon): separate "Edit holding" action, different drawer, different spec.
4. **Corporate events** (dividends, amortization, stock splits): handled by `RecordCorporateEventDrawer` (already exists).
5. **Crypto `RecordCryptoDrawer` standalone extraction** (Phase 3 of `holding-transaction-operations.md`): tracked there, not here.

---

## Decisions

**DEC-U1: Single drawer, internal branching (not four separate drawers)**
One `UpdateHoldingDrawer` with internal `assetType` branching keeps entry-point logic simple and guarantees the shell is always identical. Four separate components would risk shell divergence over time.

**DEC-U2: No asset selection in update flow**
`assetId` is always derived from the holding. This is the fundamental invariant that distinguishes update from new-position. Enforced in `useUpdateHoldingForm` — the field is never rendered and never configurable.

**DEC-U3: Holding-level metadata is read-only in this flow**
Allowing users to change indexer or maturity on a follow-up aporte would silently mutate the entire holding's configuration, affecting all historical transactions. This must be a separate, explicit "Edit holding" action with appropriate warnings.

**DEC-U4: Fund outflow always requires user to confirm netValue (DEC-3 from parent spec)**
Inherited from `holding-transaction-operations.md` DEC-3. The system may *suggest* a value but the user must always confirm it. This prevents silent errors from stale CVM NAV data.

---

## Phase 2 — UX Refinements

> **Status**: proposed  
> **Depends on**: Phase 1 fully shipped (all TICKET-U0 through TICKET-U13 complete)  
> **Goal**: Reduce cognitive load and visual inconsistency across asset classes through three targeted changes: (1) replace the collapsible context section with a direct link to the holding detail page, (2) enforce a unified two-column field grid across all asset classes, and (3) add a read-only position summary that shows the user how the current operation will affect their position — placed *before* the form fields so they have context before filling in values.

---

### P2 Overview of Changes

| Change | What changes | What is removed |
|---|---|---|
| P2-C1: Header detail link | `LaunchIcon` button added to `Drawer.Header` right side | `HoldingContextCollapsible` component removed |
| P2-C2: Unified two-column grid | All `{AssetClass}UpdateFields` refactored to shared row order | Per-class ad-hoc layouts |
| P2-C3: Position summary strip | New `PositionSummaryStrip` after direction toggle, before fields | — |

---

### P2-C1: Replace Collapsible with Header Detail Link

**Rationale**: The current `HoldingContextCollapsible` sits below the form fields, meaning the user has already finished entering data before seeing the context. It serves primarily as a navigation shortcut to the full holding detail — a job a single icon link does better with less visual weight.

**Behaviour**:
- A `LaunchIcon` `IconButton` is added to the right side of `Drawer.Header`, immediately to the left of the close button.
- Clicking it opens the holding detail page in a new browser tab (or navigates to it if the drawer is already on the detail page).
- The URL target is the holding detail route: `/personal-finance/investments/{holdingId}`.
- `HoldingContextCollapsible` is deleted entirely — the position summary strip (P2-C3) covers the remaining contextual need.

**Icon**: `import LaunchIcon from '@mui/icons-material/Launch';`

**Implementation note**: If `Drawer.Header` does not currently accept an `actions` / `endAdornment` prop, extend it to accept an optional `headerActions?: React.ReactNode` slot rendered between the title and the close button. This change must be made in `shared/components/ui/layout/Drawer`.

**Wireframe**:
```
┌─────────────────────────────────────────────────┐
│ Aportar em NuBank CDB   [↗]  [×]               │  ← LaunchIcon then Close
├─────────────────────────────────────────────────┤
```

---

### P2-C2: Unified Two-Column Field Grid

**Rule**: Every form row contains at most two fields, each in a `<Box sx={{ flex: 1, minWidth: 0 }}>` inside a `<FormRowStack>`. When a row has only one field, it still wraps in a `<FormRowStack>` but the sibling `Box` is absent — this constrains the single field to 50% of the available width, preserving visual rhythm across rows.

**Canonical row order** (rows that do not apply for a given class/direction are omitted):

| Row | Slot A | Slot B | Visibility |
|---|---|---|---|
| 1 — Dates | `tradeDate` | `settlementDate` (equity only) | Always |
| 2 — Values | `grossValue` (inflow) or `grossValue` (outflow) | `netValue` (outflow only) | Always |
| 3 — Quantity & Price | `quantity` | `unitPrice` (equity / fund MANUAL / crypto) | Always |
| 4 — Taxes | `incomeTax` | `iof` (renda fixa only; otherwise empty slot) | Outflow only |
| 5 — Fees | Collapsible accordion: `brokerageFee` \| `settlementFee` + `otherFees` | — | Equity outflow only |

**Notes per row**:

- **Row 1 — Dates**: For renda fixa and fundos, `settlementDate` is always equal to `tradeDate` and is never shown — `tradeDate` occupies slot A at half-width. For equity (both directions), `settlementDate` defaults to D+2 and is editable. Validation: `settlementDate >= tradeDate`.
- **Row 2 — Values**: For equity and cripto, both `grossValue` and `netValue` are derived (read-only display), not inputs — render them in the `PositionSummaryStrip` (P2-C3) instead of as form fields. For renda fixa inflow, `grossValue` is the sole input (half-width). For renda fixa outflow and fund outflow, both slots are inputs.
- **Row 3 — Quantity & Price**: For renda fixa, `unitPrice` is never shown — `quantity` is half-width. For fund with `pricingMode = MARKET`, `unitPrice` is also not shown. For fund with `pricingMode = MANUAL`, both fields are shown.
- **Row 4 — Taxes**: For fundos, `iof` does not apply — `incomeTax` occupies slot A at half-width. For cripto, row 4 is omitted entirely.
- **Row 5 — Fees**: Equity only. All fee fields (`brokerageFee`, `settlementFee`, `otherFees`) live inside a `<Accordion disableGutters elevation={0}>` — collapsed by default, label varies by direction ("Taxas de compra" / "Taxas de venda"). `incomeTax` for equity also lives inside this accordion (slot B alongside `otherFees`).

**Field layout per asset class × direction (complete matrix)**:

| Row | Renda Fixa — Inflow | Renda Fixa — Outflow | Fund — Inflow | Fund — Outflow | Equity — Inflow | Equity — Outflow | Cripto — Inflow | Cripto — Outflow |
|---|---|---|---|---|---|---|---|---|
| 1 | tradeDate _(½)_ | tradeDate _(½)_ | tradeDate _(½)_ | tradeDate _(½)_ | tradeDate \| settlementDate | tradeDate \| settlementDate | tradeDate _(½)_ | tradeDate _(½)_ |
| 2 | grossValue _(½)_ | grossValue \| netValue | grossValue _(½)_ | grossValue \| netValue | _(in summary strip)_ | _(in summary strip)_ | _(in summary strip)_ | _(in summary strip)_ |
| 3 | quantity _(½)_ | quantity _(½)_ | quantity \| unitPrice _(MANUAL)_ / quantity _(½, MARKET)_ | quantity \| unitPrice _(MANUAL)_ / quantity _(½, MARKET)_ | quantity \| unitPrice | quantity \| unitPrice | quantity \| unitPrice | quantity \| unitPrice |
| 4 | — | incomeTax \| iof | — | incomeTax _(½)_ | — | — _(inside accordion)_ | — | — |
| 5 | — | — | — | — | fees accordion | fees accordion | — | — |

---

### P2-C3: Position Summary Strip

**Purpose**: Give the user immediate visual context — before touching the form — showing what their position is now and what it will be after the current operation is recorded.

**Placement**: After the `DirectionToggle`, before row 1 (the date fields). It is always visible and updates reactively as the user fills in the form.

**Content**:

```
┌──────────────────────────────────────────────┐
│ Posição atual          Após esta operação    │
│ R$ 12.450,00    →      R$ 17.450,00         │  (inflow +5.000)
│ 3 títulos       →      3 títulos             │  (qty unchanged if not filled)
└──────────────────────────────────────────────┘
```

- **Left column** ("Posição atual"): reads `holding.currentValue` and `holding.quantity` directly — no form state needed.
- **Right column** ("Após esta operação"): derived reactively from `useWatch` on the form:
  - Inflow: `currentValue + grossValue`; `currentQty + (quantity ?? 0)`.
  - Outflow: `currentValue - netValue`; `currentQty - (quantity ?? 0)`.
  - For equity/crypto where `grossValue` is derived: `currentValue + quantity × unitPrice` (inflow); `currentValue - quantity × unitPrice` (outflow).
  - When form fields are empty, the right column shows `—` rather than an erroneous value.
- **Typography**: `variant="caption"` for labels, `variant="body2" fontWeight={600}` for values.
- **Arrow**: `→` as a plain character between columns, `color="text.secondary"`.
- **Container**: `Box` with `sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 1.5, display: 'grid', gridTemplateColumns: '1fr auto 1fr', gap: 1, alignItems: 'center' }}`.

**New component**: `PositionSummaryStrip` — located at:
`web/src/app/personal-finance/investments/components/update/PositionSummaryStrip.tsx`

**Props**:
```typescript
interface PositionSummaryStripProps {
  currentValue: number | null;        // holding.currentValue (may be null)
  currentQuantity: number;            // holding.quantity
  projectedValue: number | null;      // derived from form watch — null when inputs empty
  projectedQuantity: number | null;   // derived from form watch — null when inputs empty
  currencyCode: string;
  quantityUnit: string;               // "títulos" / "cotas" / "ações" / "unidades"
  locale: string;
}
```

---

### Phase 2 Implementation Backlog

#### TICKET-P2-1: Extend `Drawer.Header` with `headerActions` slot (0.5d)

**File to modify**: `web/src/shared/components/ui/layout/Drawer.tsx` (or wherever `Drawer.Header` is defined)

**Change**: Add optional `headerActions?: React.ReactNode` prop to `Drawer.Header`. Render it between the title and the close `IconButton`.

**Acceptance Criteria**:
- [ ] Existing usages of `Drawer.Header` without `headerActions` are unaffected.
- [ ] When `headerActions` is provided, the node renders flush right of the title.
- [ ] Close button remains the rightmost element.

---

#### TICKET-P2-2: Add LaunchIcon link to `HoldingUpdateDrawerShell` (0.5d)

**File to modify**: `web/src/app/personal-finance/investments/components/update/HoldingUpdateDrawerShell.tsx`

**Change**:
1. Accept a `holdingDetailPath: string` prop.
2. Render an `IconButton` with `LaunchIcon` as `headerActions` in `Drawer.Header`.
3. `href={holdingDetailPath}` + `target="_blank"` (MUI `IconButton` does not support `href` — wrap in `<Link href={...} target="_blank">` from Next.js with `passHref legacyBehavior`).
4. Delete the `<HoldingContextCollapsible>` import and usage.

**File to delete**: `web/src/app/personal-finance/investments/components/update/HoldingContextCollapsible.tsx`

**`holdingDetailPath` value**: `/personal-finance/investments/{holdingId}` — constructed in `UpdateHoldingDrawer` and passed down.

**Acceptance Criteria**:
- [ ] `LaunchIcon` button visible in drawer header.
- [ ] Clicking opens holding detail page in new tab.
- [ ] `HoldingContextCollapsible` no longer rendered anywhere.
- [ ] No TypeScript errors after deletion.

---

#### TICKET-P2-3: `PositionSummaryStrip` component (1d)

**File to create**: `web/src/app/personal-finance/investments/components/update/PositionSummaryStrip.tsx`

**Requirements**: See P2-C3 spec above. The component is purely presentational — it receives already-computed `projectedValue` and `projectedQuantity` from its parent; it does not call `useWatch` itself.

**Computed props are derived in**: each `{AssetClass}UpdateFields` component via `useWatch`, then passed to `PositionSummaryStrip`.

**Acceptance Criteria**:
- [ ] Shows `—` in the "Após" column when projected values are null.
- [ ] Renders correctly for all four asset classes with their respective `quantityUnit` labels.
- [ ] Formatting matches locale (BRL, pt-BR decimal separator).

---

#### TICKET-P2-4: Refactor `FixedIncomeUpdateFields` to unified grid + add summary strip (1d)

**File to modify**: `web/src/app/personal-finance/investments/components/update/FixedIncomeUpdateFields.tsx`

**Layout changes** (see P2-C2 matrix above):
- Row 1: `tradeDate` (half-width) — wrap in `<FormRowStack><Box sx={{ flex: 1, minWidth: 0 }}>`.
- Row 2 inflow: `grossValue` (half-width).
- Row 2 outflow: `<FormRowStack>` with `grossValue` | `netValue`.
- Row 3: `quantity` (half-width).
- Row 4 outflow: `<FormRowStack>` with `incomeTax` | `iof`.

**Summary strip**: Add `useWatch` for `grossValue`, `netValue`, `quantity` → compute `projectedValue` and `projectedQuantity` → pass to `<PositionSummaryStrip>` rendered as the first child inside `<FormStack>` (before row 1).

**Props to add**:
```typescript
currentValue: number | null;
currencyCode: string;
locale: string;
```

**Acceptance Criteria**:
- [ ] Inflow and outflow layouts match the P2-C2 matrix.
- [ ] Summary strip updates as user types.
- [ ] Existing IR auto-calc side-effect (grossValue − netValue) preserved.

---

#### TICKET-P2-5: Refactor `FundUpdateFields` to unified grid + add summary strip (1d)

**File to modify**: `web/src/app/personal-finance/investments/components/update/FundUpdateFields.tsx`

**Layout changes**:
- Row 1: `tradeDate` (half-width).
- Row 2 inflow: `grossValue` (half-width).
- Row 2 outflow: `grossValue` | `netValue`.
- Row 3 inflow (MANUAL): `quantity` | `unitPrice`.
- Row 3 inflow (MARKET): `quantity` (half-width).
- Row 3 outflow (MANUAL): `quantity` | `unitPrice`.
- Row 3 outflow (MARKET): `quantity` (half-width).
- Row 4 outflow: `incomeTax` (half-width).
- `valuedAt` date (MANUAL inflow only): half-width, placed between row 3 and row 4.

**Summary strip**: Watch `grossValue`, `netValue`, `quantity` → same pattern as fixed income.

**Acceptance Criteria**:
- [ ] MANUAL vs. MARKET pricing mode shows correct fields.
- [ ] `valuedAt` placement matches spec.

---

#### TICKET-P2-6: Refactor `EquityUpdateFields` to unified grid + add summary strip (1d)

**File to modify**: `web/src/app/personal-finance/investments/components/update/EquityUpdateFields.tsx`

**Layout changes**:
- Row 1: `tradeDate` | `settlementDate` (both directions).
- Row 3: `quantity` | `unitPrice`.
- Rows 2 (grossValue/netValue): **removed as form fields** — values move to `PositionSummaryStrip`.
- Row 5 fees: existing accordion preserved; move `incomeTax` into the accordion alongside `otherFees` (slot B in the second fee row).

**Summary strip**: Watch `quantity`, `unitPrice`, `brokerageFee`, `settlementFee`, `otherFees` → recompute `grossValue` and `netValue` → pass to strip. Show both gross and net values on separate lines in the "Após" column.

**Acceptance Criteria**:
- [ ] `grossValue` and `netValue` no longer rendered as editable fields.
- [ ] Accordion still collapses cleanly; `incomeTax` inside accordion.
- [ ] Summary strip shows both gross and net.

---

#### TICKET-P2-7: Refactor `CryptoUpdateFields` to unified grid + add summary strip (0.5d)

**File to modify**: `web/src/app/personal-finance/investments/components/update/CryptoUpdateFields.tsx`

**Layout changes**:
- Row 1: `tradeDate` (half-width).
- Row 3: `quantity` | `unitPrice`.
- `grossValue` display: moves to `PositionSummaryStrip` (derived from `quantity × unitPrice`).

**Acceptance Criteria**:
- [ ] `grossValue` no longer a standalone read-only box.
- [ ] Summary strip shows projected position.

---

#### TICKET-P2-8: Wire `holdingDetailPath` in `UpdateHoldingDrawer` (0.5d)

**File to modify**: `web/src/app/personal-finance/investments/components/update/UpdateHoldingDrawer.tsx`

**Change**: Compute `holdingDetailPath` as `` `/personal-finance/investments/${holding.id}` `` and pass it to `HoldingUpdateDrawerShell`.

**Note**: If the drawer is already open *from* the holding detail page, the link may navigate to the same page — this is acceptable behaviour for V2.

**Acceptance Criteria**:
- [ ] Prop threaded correctly; no runtime error.
- [ ] TypeScript types updated.

---

### Phase 2 Implementation Order and Estimates

| Ticket | Description | Estimate | Dependencies |
|---|---|---|---|
| TICKET-P2-1 | Extend `Drawer.Header` with `headerActions` | 0.5d | — |
| TICKET-P2-2 | LaunchIcon link + remove collapsible | 0.5d | P2-1 |
| TICKET-P2-3 | `PositionSummaryStrip` component | 1d | — |
| TICKET-P2-4 | Refactor `FixedIncomeUpdateFields` | 1d | P2-3 |
| TICKET-P2-5 | Refactor `FundUpdateFields` | 1d | P2-3 |
| TICKET-P2-6 | Refactor `EquityUpdateFields` | 1d | P2-3 |
| TICKET-P2-7 | Refactor `CryptoUpdateFields` | 0.5d | P2-3 |
| TICKET-P2-8 | Wire `holdingDetailPath` in orchestrator | 0.5d | P2-2 |
| **Total** | | **~6d** | |

**Parallel execution**: P2-1 and P2-3 can run in parallel. P2-4 through P2-7 can run in parallel once P2-3 is done.

---

---

## Phase 3 — Edit Existing Transactions

> **Status**: proposed
> **Depends on**: Phase 1 (TICKET-U0 → U13) fully shipped. Phase 2 is independent — Phase 3 does not require Phase 2.
> **Goal**: Allow users to edit the fields of an already-recorded transaction using the same `UpdateHoldingDrawer` shell and per-class field components built in Phase 1. This replaces the current edit path that repurposes the old full-form drawers (`RecordFixedIncomeDrawer`, `RecordFundHoldingDrawer`, `RecordEquityHoldingDrawer`) for editing.

---

### P3 Background

The backend already exposes `updateInvestmentTransaction(id, input: UpdateInvestmentTransactionInput)` and `useRecordHoldingForm` already branches between `RECORD_INVESTMENT_TRANSACTION` (create) and `UPDATE_INVESTMENT_TRANSACTION` (edit) based on whether an `editTransaction` object is passed. The infrastructure is ready — Phase 3 is purely a frontend wiring task.

**Current edit paths (to be replaced)**:
- `InvestmentEventSelectorDrawer` → user clicks a transaction row → opens a stacked sub-drawer (`RecordFixedIncomeDrawer` / `RecordFundHoldingDrawer` / `RecordEquityHoldingDrawer` in edit mode).
- Holding detail page `TimelineEvents` → user clicks the context menu "Edit" on a transaction row → the parent page opens one of the same old drawers.

**Target state after Phase 3**:
- Both paths open `UpdateHoldingDrawer` with the transaction pre-filled.
- No stacked sub-drawers. No old `Record*Drawer` usage for edit mode.

---

### P3 Design Decisions

**DEC-P3-1: Pass the full transaction object, not an ID**

Both current entry points (`InvestmentEventSelectorDrawer`, `TimelineEvents`) already hold the full `InvestmentTransaction` object in memory from a prior list query. Passing the object directly avoids a second round-trip fetch. An `editTransactionId` prop is not needed in Phase 3.

**DEC-P3-2: Direction is locked in edit mode**

Changing the direction of an existing transaction (INFLOW → OUTFLOW) would create an accounting reversal, not an edit. The direction toggle must be disabled (read-only, visually muted) when `editTransaction` is provided.

**DEC-P3-3: Position summary strip is hidden in edit mode**

The strip relies on `holding.quantity` and `holding.currentValue` which already include the transaction being edited. Showing a projected delta would be misleading. In edit mode the strip is omitted entirely. This simplification is acceptable for Phase 3; a corrected delta view (position excluding the edited transaction) is a Phase 4 concern.

**DEC-P3-4: `UpdateInvestmentTransactionInput` mirrors `RecordInvestmentTransactionInput`**

The update input has no `assetId` or `financialAccountId` (those are immutable), but all transaction-level fields (`tradeDate`, `grossValue`, `netValue`, `quantity`, `unitPrice`, `incomeTax`, `iof`, `settlementDate`, `type`, `direction`, etc.) are identical. `buildRecordInput` already produces all these fields — it can be reused to build the update input directly.

---

### Phase 3 Implementation Backlog

---

#### TICKET-P3-1: `mapTransactionToFormValues` utility (0.5d)

**Goal**: Pure function that converts an `InvestmentTransaction` API object to `Partial<UpdateHoldingFormValues>` for use as `defaultValues` when pre-filling the edit form.

**File to create**:
`web/src/app/personal-finance/investments/components/update/mapTransactionToFormValues.ts`

**Mapping rules**:

| Form field | Transaction field | Notes |
|---|---|---|
| `tradeDate` | `transaction.tradeDate` | Parse `"YYYY-MM-DD"` string → `Date` (use `dayjs(value).toDate()`) |
| `settlementDate` | `transaction.settlementDate` | Same date parsing; may be null |
| `grossValue` | `transaction.grossValue` | Number passthrough |
| `netValue` | `transaction.netValue ?? undefined` | Nullable |
| `quantity` | `transaction.quantity ?? undefined` | Nullable |
| `unitPrice` | `transaction.unitPrice ?? undefined` | Nullable |
| `incomeTax` | `transaction.incomeTax ?? 0` | Default 0 |
| `iof` | `transaction.iof ?? 0` | Default 0 |
| `brokerageFee` | `0` | Not stored per transaction; default 0 |
| `settlementFee` | `0` | Same |
| `otherFees` | `0` | Same |
| `valuedAt` | `undefined` | Fund MANUAL only; not available from list query |

**Signature**:
```typescript
export function mapTransactionToFormValues(
  transaction: Pick<
    InvestmentTransaction,
    'tradeDate' | 'settlementDate' | 'grossValue' | 'netValue' |
    'quantity' | 'unitPrice' | 'incomeTax' | 'iof'
  >
): Partial<UpdateHoldingFormValues>
```

**Acceptance Criteria**:
- [ ] All date strings parse to local midnight `Date` objects (no UTC offset shift).
- [ ] Function is pure — no side effects, no imports from React.
- [ ] Exported and unit-tested with at least one fixture per asset class.

---

#### TICKET-P3-2: Extend `useUpdateHoldingForm` with edit-mode support (0.5d)

**Goal**: Thread `editTransaction` through `useUpdateHoldingForm` so that the underlying `useRecordHoldingForm` calls `UPDATE_INVESTMENT_TRANSACTION` instead of `RECORD_INVESTMENT_TRANSACTION`.

**File to modify**:
`web/src/app/personal-finance/investments/hooks/useUpdateHoldingForm.ts`

**Changes**:

1. Add `editTransaction?: Pick<InvestmentTransaction, 'id' | 'direction' | 'type' | 'tradeDate' | 'settlementDate' | 'grossValue' | 'netValue' | 'quantity' | 'unitPrice' | 'incomeTax' | 'iof'> | null` to `UseUpdateHoldingFormOptions`.
2. Pass `editTransaction` and `editHoldingId: holding.id` to `useRecordHoldingForm`.
3. When `editTransaction` is present, provide `onBuildUpdateInput` that reuses the same `onBuildInput` result cast as `UpdateInvestmentTransactionInput` (the field shapes are identical; `assetId`/`financialAccountId` are simply not included in the update input).

**Key note**: `useRecordHoldingForm` already handles the mutation branch — no changes needed there.

**Acceptance Criteria**:
- [ ] When `editTransaction` is provided, the hook calls `UPDATE_INVESTMENT_TRANSACTION` on submit.
- [ ] When `editTransaction` is absent, behaviour is unchanged (calls `RECORD_INVESTMENT_TRANSACTION`).
- [ ] TypeScript types compile without `as unknown` casts for the update input.

---

#### TICKET-P3-3: Extend `UpdateHoldingDrawer` with edit mode (1d)

**Goal**: Add edit-mode support to the orchestrator component so it can be opened with a pre-filled transaction.

**File to modify**:
`web/src/app/personal-finance/investments/components/update/UpdateHoldingDrawer.tsx`

**New prop on `UpdateHoldingDrawerProps`**:
```typescript
editTransaction?: Pick<
  InvestmentTransaction,
  'id' | 'direction' | 'type' | 'tradeDate' | 'settlementDate' |
  'grossValue' | 'netValue' | 'quantity' | 'unitPrice' | 'incomeTax' | 'iof'
> | null;
```

**Behaviour changes when `editTransaction` is present**:

1. **Initial direction**: locked to `editTransaction.direction` — ignore `initialDirection` prop.
2. **Default values**: use `mapTransactionToFormValues(editTransaction)` merged over the asset-class defaults from `buildDefaultValues(assetClass)`.
3. **Direction toggle**: rendered disabled. Pass `directionLocked={true}` to `HoldingUpdateDrawerShell`.
4. **Position summary strip**: pass `hideStrip={true}` to each `{AssetClass}UpdateFields` component (or use the `editTransaction` presence as the signal).
5. **Submit text**: use `t('investments.updateHolding.saveChanges')` instead of `t('actions.save')`.
6. **Drawer title**: use `t('investments.updateHolding.titles.edit', { asset: targetLabel })`.
7. **`useUpdateHoldingForm`**: pass `editTransaction` from prop.

**Changes to `HoldingUpdateDrawerShell`** (minor):
- Add optional `directionLocked?: boolean` prop. When true, the `DirectionToggle` renders with `disabled` and `sx={{ pointerEvents: 'none', opacity: 0.5 }}` — visually communicating the lock without hiding the control entirely.

**Acceptance Criteria**:
- [ ] Drawer opens with all form fields pre-filled from `editTransaction`.
- [ ] Direction toggle is visually disabled; changing it has no effect.
- [ ] Position summary strip is not rendered.
- [ ] Submit calls `UPDATE_INVESTMENT_TRANSACTION` with the transaction ID.
- [ ] On success: toast "Operação atualizada com sucesso." + drawer closes + refetch queries fire.
- [ ] In create mode (no `editTransaction`), all existing behaviour is unchanged.

---

#### TICKET-P3-4: Replace old edit sub-drawers in `InvestmentEventSelectorDrawer` (1d)

**Goal**: Remove the three stacked `Record*Drawer` components used for editing and replace with the single `UpdateHoldingDrawer` in edit mode.

**File to modify**:
`web/src/app/personal-finance/investments/components/InvestmentEventSelectorDrawer.tsx`

**Changes**:

1. Remove imports and usage of `RecordFixedIncomeDrawer`, `RecordFundHoldingDrawer`, `RecordEquityHoldingDrawer`.
2. Remove the `fixedIncomeEditHolding`, `fundEditHolding`, `equityEditHolding` `useMemo` blocks — they are no longer needed.
3. Remove the `kind`, `financialAccountId`, `investmentAccounts` props from `InvestmentEventSelectorDrawerProps` — they were only needed to feed the old sub-drawers.
4. Add state: `[editingTransaction, setEditingTransaction]` — stores the full `EventSelectorTransaction` object (not just the ID) when a row is clicked.
5. Render a single `UpdateHoldingDrawer` below the selector `Drawer`:

```tsx
<UpdateHoldingDrawer
  holdingId={holding?.id ?? null}
  open={Boolean(editingTransaction)}
  onClose={() => setEditingTransaction(null)}
  editTransaction={editingTransaction}
  onRecorded={handleEditRecorded}
/>
```

6. Update `handleEditRecorded` to clear `editingTransaction` and call `onRecorded`.

**`EventSelectorTransaction` type**: already has all fields needed by the `editTransaction` prop (they come from `INVESTMENT_TRANSACTIONS` query which returns `InvestmentTransaction`).

**Callers to update**: wherever `InvestmentEventSelectorDrawer` is rendered, remove the now-unused `kind`, `financialAccountId`, `investmentAccounts` props.

**Acceptance Criteria**:
- [ ] Clicking a manual transaction row in the selector opens `UpdateHoldingDrawer` with pre-filled fields.
- [ ] Clicking a non-manual (Open Finance / import) transaction still shows the lock icon and is not clickable.
- [ ] After a successful edit, the selector closes and the holding data refetches.
- [ ] No `RecordFixedIncomeDrawer` / `RecordFundHoldingDrawer` / `RecordEquityHoldingDrawer` imports remain in this file.
- [ ] TypeScript compiles with no errors after prop removal.

---

#### TICKET-P3-5: Update `TimelineEvents` callers for edit mode (0.5d)

**Goal**: Wire the `onEditTransaction` callback in holding detail pages to open `UpdateHoldingDrawer` with the transaction pre-filled, instead of the old `Record*Drawer` components.

**Context**: `TimelineEvents` already emits `onEditTransaction(transactionId: string)`. The parent pages hold a list of transactions from a prior query and can look up the full transaction object by ID.

**Files to modify**: whichever page/component renders `TimelineEvents` and handles `onEditTransaction` — search for `onEditTransaction` usages in `web/src/app/personal-finance/investments/`.

**Pattern to apply in each caller**:

```tsx
// 1. Add state in the parent page
const [editTransaction, setEditTransaction] = React.useState<InvestmentTransaction | null>(null);

// 2. Pass to TimelineEvents
onEditTransaction={(id) => {
  const tx = transactions.find((t) => t.id === id) ?? null;
  setEditTransaction(tx);
}}

// 3. Render UpdateHoldingDrawer
<UpdateHoldingDrawer
  holdingId={holdingId}
  open={Boolean(editTransaction)}
  onClose={() => setEditTransaction(null)}
  editTransaction={editTransaction}
  onRecorded={async () => {
    setEditTransaction(null);
    await refetch();
  }}
/>
```

**Acceptance Criteria**:
- [ ] Clicking "Edit" in `TimelineEvents` opens `UpdateHoldingDrawer` with correct pre-filled fields.
- [ ] After a successful edit, the transaction list refreshes.
- [ ] No old `Record*Drawer` edit-mode usage remains in any holding detail page.

---

#### TICKET-P3-6: i18n strings for edit mode (0.5d)

**Goal**: Add the new keys required by edit mode.

**Keys to add** under `investments.updateHolding`:

```json
{
  "updateHolding": {
    "titles": {
      "edit": "Editar operação — {{asset}}"
    },
    "saveChanges": "Salvar alterações",
    "successEdit": "Operação atualizada com sucesso."
  }
}
```

**Acceptance Criteria**:
- [ ] All strings present in `pt-BR` locale.
- [ ] No hardcoded Portuguese strings introduced in TICKET-P3-3 through P3-5.

---

### Phase 3 Implementation Order and Estimates

| Ticket | Description | Estimate | Dependencies |
|---|---|---|---|
| TICKET-P3-1 | `mapTransactionToFormValues` utility | 0.5d | — |
| TICKET-P3-2 | Extend `useUpdateHoldingForm` for edit mode | 0.5d | P3-1 |
| TICKET-P3-3 | Extend `UpdateHoldingDrawer` with edit mode | 1d | P3-2 |
| TICKET-P3-4 | Replace old edit sub-drawers in selector | 1d | P3-3 |
| TICKET-P3-5 | Update `TimelineEvents` callers | 0.5d | P3-3 |
| TICKET-P3-6 | i18n strings | 0.5d | P3-3 |
| **Total** | | **~4d** | |

**Parallel execution**: P3-1 and P3-6 can start immediately. P3-4 and P3-5 can run in parallel once P3-3 is done.

---

### Phase 3 Out of Scope

1. **Position delta in edit mode** — showing "before/after" with the edited transaction excluded from current position. Deferred to Phase 4.
2. **Editing holding-level metadata** (indexer, maturityDate, coupon) — separate "Edit holding" action, different spec.
3. **Delete transaction** — `TimelineEvents` already has `onDeleteTransaction`; that flow is separate and not touched here.
4. **Editing non-manual transactions** (Open Finance, file import) — locked at the UI level; the API also rejects updates to non-manual transactions.

---

## Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-04-25 | salomax | Initial spec — standardized update flow, component tree, full backlog |
| 1.1.0 | 2026-04-26 | salomax | Phase 2 added — header detail link, unified two-column grid, position summary strip |
| 1.2.0 | 2026-04-27 | salomax | Phase 3 added — edit existing transactions via extended UpdateHoldingDrawer |
