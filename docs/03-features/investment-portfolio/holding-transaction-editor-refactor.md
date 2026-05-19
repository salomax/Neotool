---
title: Holding Transaction Editor — Refactoring Requirements
type: feature-spec
category: investment-portfolio
status: draft
version: 1.0.0
tags: [investment, portfolio, transaction, refactor, ux, drawer, editor]
ai_optimized: true
search_keywords: [transaction editor, unified drawer, entry points, buy, sell, aporte, resgate, compra, venda]
related:
  - 03-features/investment-portfolio/holding-transaction-operations.md
  - 07-frontend/patterns/form-pattern.md
last_updated: 2026-05-12
---

# Holding Transaction Editor — Refactoring Requirements

> **One-line summary**: Replace four independent transaction drawers with one unified `TransactionEditor` + merge `InvestmentHoldingDetailDrawer` into a single tabbed drawer (Resumo + Movimentações). Row hover `+`/`−` adds transactions to existing holdings. Grid/header `+` creates new holdings. The Movimentações tab handles edit and delete. No separate `HoldingManagementDrawer`.

---

## Problem Context

The portfolio currently has four transaction recording drawers: `RecordFixedIncomeDrawer` (846 lines), `RecordEquityHoldingDrawer` (916 lines, also handles FIIs and Crypto), `RecordFundHoldingDrawer` (849 lines), and `RecordCashDrawer` (72 lines). The first three each duplicate mutation wiring, cache invalidation, error handling, and base form fields — ~2,600 lines total.

On the UX side, five different entry points reach these drawers but with inconsistent pre-filling, intent signaling, and interaction models. The popover edit path is cramped and fragile. The "+ Investimento" button adds a concept that is functionally identical to a buy with an empty ticker.

**Goals**:
1. One component to maintain, one mental model for the user.
2. Form sections that reflect real structural differences between asset classes — not arbitrary drawer splits.
3. Entry points that are honest about what they do: shortcuts open the same editor, pre-filled differently.

---

## Drawer Flow Model

The drawer has two possible steps. Whether Step 1 exists depends on the asset class.

### Step 1 — Asset Selection Screen

**Applies to: Funds, Stocks, FIIs, Crypto only.**

Step 1 occupies the full drawer content area. It is a standalone selection screen — not a form section, not a field. The user picks or creates an asset; on confirmation the drawer transitions to Step 2.

| Class | Mechanism |
|---|---|
| Funds | `searchInvestmentFundsAutocomplete` (CNPJ / name) with a manual entry toggle for funds not in CVM |
| Stocks | `searchInvestmentAssetsAutocomplete` filtered to stock asset types |
| FIIs | `searchInvestmentAssetsAutocomplete` filtered to `REAL_ESTATE_FUND`, `AGRIBUSINESS_FUND` |
| Crypto | Free-text form: name + ticker + optional network (no search query) |

**When Step 1 is skipped**: for `row-buy`, `row-sell`, and `edit` intents, the asset is already known. The drawer opens directly at Step 2 with the resolved asset. Step 1 is never mounted.

**Fixed Income has no Step 1.** Its "selection" (issuer institution + asset type) is part of the transaction form itself — those fields affect validation and cannot be separated from the form lifecycle. The drawer opens directly at the form for all Fixed Income intents.

### Step 2 — Transaction Form

The form always has this layout from top to bottom:

1. **Asset Identity Block** — a non-field visual element at the top of the drawer content, above all form fields. Shows asset name, ticker, and class badge. Has an inline "Alterar" affordance that navigates back to Step 1 (only rendered when `step1Screen !== null`, i.e., not for Fixed Income).
2. **Section B — Values** — core transaction fields (direction, date, amounts, class-specific fields).
3. **"Mais detalhes" disclosure toggle** — only rendered when `SectionCFields !== null`.
4. **Section C — Transaction Costs** — inside the disclosure toggle.

The Asset Identity Block is **not** a form field. It carries no Zod schema entry. It is display-only except for the "Alterar" navigation action.

### Section B — Values

The core transaction fields. Shared structure with class-specific variants.

**Common fields across all classes**:
- Direction toggle (buy/sell — or Aportar/Resgatar for RF/Fundos): hidden when direction is locked by entry point; shown as a labelled header instead.
- Trade date (required).
- Gross value (required for RF/Fundos inflow; derived for equities from qty × unit price).
- Net value (required for RF/Fundos outflow; optional for equities).

**Class-specific fields**:

| Class | Additional fields |
|---|---|
| Fixed Income | Indexer + rate, emission date, maturity date (required), grace period, coupon frequency + day, IR exemption flag, settlement date |
| Funds | Quantity in shares + unit price (or gross value only), pricing mode (MARKET / MANUAL), valuation date (when MANUAL), settlement date |
| Stocks / FIIs | Quantity (required), unit price (required), settlement date (default D+2), price factor |
| Crypto | Quantity (required), unit price (required) |
| Cash | Amount, date, direction, notes |

### Section C — Transaction Costs

Fees and taxes. Structurally absent for some classes.

| Class | Costs |
|---|---|
| Cash | — (no Section C) |
| Fixed Income | IR withheld, IOF |
| **Funds** | **None — investment funds do not have transaction costs in this model** |
| Stocks / FIIs | Brokerage fee (corretagem), settlement fee (emolumentos), IRRF |
| Crypto | None |

Section C is rendered inside a **"Mais detalhes" progressive disclosure toggle** for classes that have it (Fixed Income, Stocks, FIIs). It is collapsed by default for new transactions and expanded in edit mode when cost fields are already populated.

---

## Entry Points

### Current State

| Entry point | Component | Behavior |
|---|---|---|
| Grid "Investir" button | `InvestmentGridDrawers` | Opens class drawer with empty fields, direction=buy |
| Row `+` button | `InvestmentHoldingDetailDrawer` | Opens `InvestmentEventSelectorDrawer` with direction=buy |
| Row `−` button | `InvestmentHoldingDetailDrawer` | Opens `InvestmentEventSelectorDrawer` with direction=sell |
| Popover "Editar" | `InvestmentHoldingDetailDrawer` transaction list | Opens edit inside detail drawer (cramped) |
| "+ Investimento" | Grid class header | Opens same class drawer as "Investir" grid button |
| "Gerenciar movimentações" | Actions menu | Opens `InvestmentEventSelectorDrawer` |

### Target State

Two distinct creation contexts determine the entry point and intent:

**Context A — New holding** (asset not yet in portfolio; Step 1 applies for applicable classes):

| Entry point | Opens | Intent |
|---|---|---|
| Grid card "Aplicar" / "Comprar" button | `TransactionEditor` Step 1 (or Step 2 for Fixed Income) | `new-buy` |
| Table header "+ Investimento" (asset class popover) | `TransactionEditor` Step 1 (or Step 2 for Fixed Income) | `new-buy` |

**Context B — Existing holding** (asset already in portfolio; Step 1 always skipped):

| Entry point | Opens | Intent |
|---|---|---|
| Row hover "+" button | `TransactionEditor` Step 2 directly | `row-buy` |
| Row hover "−" button | `TransactionEditor` Step 2 directly | `row-sell` (with `maxQty`) |
| "Movimentações" tab "+" button (inside detail drawer) | `TransactionEditor` Step 2 directly | `row-buy` |

**Edit and Delete** (always from the Movimentações tab):

| Action | Behavior |
|---|---|
| Click a transaction row in Movimentações tab | Opens `TransactionEditor` with `edit` intent layered in front of the detail drawer |
| Delete icon on a transaction row | `ConfirmationDialog` inline → `DELETE_INVESTMENT_TRANSACTION` mutation |

**Net changes from current state**: `HoldingManagementDrawer` does not exist as a separate component; detail drawer absorbs a "Movimentações" management tab; `TransactionEditor` layers on top of the open detail drawer rather than swapping; row hover `+`/`−` replace the old `InvestmentEventSelectorDrawer` path; "Gerenciar movimentações" action removed.

### Unified Detail + Transactions Drawer

`InvestmentHoldingDetailDrawer` becomes the single surface for all holding-level interactions.

```
Portfolio grid row
  └── click row (or "Visualizar" action) → InvestmentHoldingDetailDrawer (size="large")
        ├── Tab "Resumo"         → visualization: chart, performance, event timeline (unchanged)
        └── Tab "Movimentações"  → transaction table: list + add / edit / delete
              ├── "+" button          → TransactionEditor (row-buy intent, layered on top)
              ├── click transaction   → TransactionEditor (edit intent, layered on top)
              └── delete button       → ConfirmationDialog (inline, no new drawer)

Holding table row (on hover)
  ├── "+" button → TransactionEditor (row-buy intent, layered on top)
  └── "−" button → TransactionEditor (row-sell intent, layered on top)

Grid "Aplicar"/"Comprar" button  → TransactionEditor (new-buy intent, Step 1)
Table header "+ Investimento"    → TransactionEditor (new-buy intent, Step 1)
```

`TransactionEditor` always layers on top — it does NOT close the detail drawer. When the editor closes, the user returns to the Movimentações tab where they left off.

---

## Functional Requirements

### FR-1: Single `TransactionEditor` Component

- One drawer component with the two-step flow described above.
- Accepts an `intent` prop that encodes pre-filling, initial step, and operation type.

```typescript
// Shared resolved asset shape — entry points provide this; no extra query needed in the shell
interface ResolvedAsset {
  id: string
  name: string
  ticker?: string
  assetType: AssetType
}

type NewBuyIntent  = { kind: "new-buy";  assetClass: InvestmentAssetClass }
type NewSellIntent = { kind: "new-sell"; assetClass: InvestmentAssetClass }
// row-* and edit: asset is already known — shell opens at step 2, no step 1 mounted
type RowBuyIntent  = { kind: "row-buy";  assetClass: InvestmentAssetClass; holdingId: string; asset: ResolvedAsset }
type RowSellIntent = { kind: "row-sell"; assetClass: InvestmentAssetClass; holdingId: string; asset: ResolvedAsset; maxQty: number }
type EditIntent    = { kind: "edit";     assetClass: InvestmentAssetClass; holdingId: string; transactionId: string; asset: ResolvedAsset }

type TransactionEditorIntent = NewBuyIntent | NewSellIntent | RowBuyIntent | RowSellIntent | EditIntent
```

- The initial step, drawer title, locked fields, and CTA copy are derived from `intent` — they are not props.
- The step 1 screen, form fields, schema, and mutations are derived from `assetClass` via `ASSET_CLASS_CONFIGS` — they are not separate components.

### FR-2: Intent → UI Mapping

| intent.kind | Initial step | Drawer title (step 2) | Direction toggle | CTA label |
|---|---|---|---|---|
| `new-buy` | Step 1 (if class has it), else Step 2 | "Comprar {class}" / "Aportar" | Shown, default=buy | "Registrar" |
| `new-sell` | Step 1 (if class has it), else Step 2 | "Vender {class}" / "Resgatar" | Shown, default=sell | "Registrar" |
| `row-buy` | Step 2 directly | "Comprar {asset.ticker}" / "Aportar em {asset.name}" | Hidden (buy implied) | "Registrar" |
| `row-sell` | Step 2 directly | "Vender {asset.ticker}" / "Resgatar {asset.name}" | Hidden (sell implied) | "Registrar" |
| `edit` | Step 2 directly | "Editar operação" | Shown, pre-filled | "Salvar alterações" |

The drawer title is always derived from the resolved asset name/ticker once an asset is selected. In step 1 (before selection), the title reflects the asset class and action: "Selecionar fundo" / "Selecionar ativo" / etc.

### FR-3: Step 1 — Asset Selection Screen

- Applies only when `config.step1Screen !== null` (Funds, Stocks, FIIs, Crypto) AND intent is `new-buy` or `new-sell`.
- The step 1 screen takes over the full drawer content area. No form fields are mounted.
- The step 1 drawer title is action + class: "Selecionar fundo", "Selecionar ativo", "Selecionar criptomoeda".
- When the user selects or creates an asset:
  - The shell stores `selectedAsset: ResolvedAsset` in local state.
  - The drawer transitions to step 2.
  - The form is mounted and `defaultValues` are seeded (including the resolved asset id).
- No "back" button is shown in step 1 — the entry point controls opening/closing the drawer.

**Per-class step 1 behavior**:

- **Stocks / FIIs**: search field (`searchInvestmentAssetsAutocomplete`) with class-appropriate type filter. Selecting from results confirms immediately and transitions to step 2.
- **Crypto**: free-text form (name + ticker + optional network) with a "Confirmar" button — no search query.
- **Funds**: see FR-3c below.

### FR-3c: Funds — Unlisted Fund Escalation

Fund search must handle two sources and one fallback:

1. **CVM-indexed funds**: queried via `searchInvestmentFundsAutocomplete` (by name or CNPJ).
2. **User's previously created manual funds**: included in the same search results, visually distinguished (e.g., a "Seu portfólio" section or a badge). These are funds the user created manually in a prior transaction.
3. **Not found — create manually**: always present at the bottom of the results list as a secondary action item "Registrar fundo manualmente". When the search returns zero results it becomes the primary prompt: "Nenhum fundo encontrado — Registrar manualmente".

Tapping "Registrar manualmente" expands an **inline sub-form within Step 1** (no screen navigation):
- Fund name (required)
- CNPJ (optional — foreign funds may not have one)
- Confirmar button → produces `ResolvedAsset` with `source: "manual"`, transitions to Step 2

**Pricing mode rule**: when `selectedAsset.source === "manual"`, step 2 defaults `pricingMode` to `MANUAL` and makes the field non-editable. There is no market data source for unlisted funds, so MARKET pricing is not available.

**Fund asset creation**: the unlisted fund entity is **not** persisted during Step 1. It is created by the backend as part of `createMutation` on final submit. The `buildCreateInput` for funds must include the full manual fund data (name, CNPJ) when `source === "manual"`. After the first transaction, the fund appears in the user's manually-created funds and surfaces in future searches.

### FR-3b: Asset Identity Block (Step 2)

- Always the first element in the drawer content, above all form fields.
- Displays: asset name, ticker (when applicable), asset type badge.
- When `config.step1Screen !== null` AND intent is `new-buy` or `new-sell`: shows an "Alterar" text link that navigates back to step 1. Clicking it clears `selectedAsset` and remounts step 1. Form data is discarded (the new asset may have an incompatible schema). No confirmation dialog — this is a low-stakes correction during a new transaction.
- When `config.step1Screen === null` (Fixed Income): no "Alterar" action. Issuer and asset type remain editable as form fields below.
- In `edit` intent: no "Alterar" action. If the user needs to transact against a different asset, they close the editor and open `HoldingManagementDrawer` for the correct holding. Asset changes on edits are not supported in the editor.

### FR-4: Section B — Values

- Direction toggle is a `ToggleButtonGroup` with class-appropriate labels (Aportar/Resgatar vs Comprar/Vender).
- For `row-sell` intent: quantity field maximum is set to `intent.maxQty`; validation error if user exceeds it.
- Gross value and net value co-validation: if both are entered, `netValue ≤ grossValue` is enforced at schema level.
- Settlement date: hidden by default behind the "Mais detalhes" toggle for classes where it's optional; always visible for Stocks/FIIs (D+2 default pre-filled).

### FR-5: Section C — Progressive Disclosure

- The "Mais detalhes" toggle expands to show Section C fields (costs) and secondary Section B fields (settlement date, notes).
- Default state:
  - `new-*` intents: collapsed.
  - `edit` intent: expanded if any Section C field has a non-zero value in the existing transaction.
- Classes with no Section C (Cash, Funds, Crypto): the toggle is not rendered.

### FR-6: Inline Hint for New Holdings

- When a user types a ticker/name in Section A that does not match any existing holding in the selected account, show an inline chip below the search field: "Primeiro investimento em {TICKER}".
- This replaces the "+ Investimento" button entirely. No separate flow.

### FR-7: Entry Point Contract

- All entry points pass an `intent` to `TransactionEditor`. No entry point encodes form logic.
- Entry points are responsible for: determining `assetClass`, providing `holdingId`/`transactionId` when applicable, opening/closing the drawer.
- Entry points are **not** responsible for: field defaults, validation, mutation wiring.

### FR-8: Unified Holding Detail + Transactions Drawer

`InvestmentHoldingDetailDrawer` is the single surface for all holding-level interactions. It uses `size="large"` and has two tabs.

**Tab 1 — "Resumo"**: existing visualization content (summary card, performance chart, event timeline). No behavior change.

**Tab 2 — "Movimentações"**: transaction management.

Layout within the Movimentações tab:
1. Table header row: tab title left, "+" (Nova transação) button right
2. Transaction history table — all transactions for this holding in the selected account, sorted by trade date descending

Transaction table columns: date | direction (Compra / Venda) | quantity | unit price | net value | edit icon | delete icon

**Edit**: clicking a transaction row (or edit icon) opens `TransactionEditor` with `edit` intent. The detail drawer stays open; the editor appears in front (z-index handled by MUI Modal stacking order). When the editor closes, focus returns to the Movimentações tab.

**Delete**: delete icon per row → inline `ConfirmationDialog` ("Excluir esta operação? Esta ação não pode ser desfeita."); on confirm fires `DELETE_INVESTMENT_TRANSACTION`; refetches `['InvestmentHoldings', 'InvestmentCashBalance', 'InvestmentPortfolioSummary']`.

**"+" button**: opens `TransactionEditor` with `row-buy` intent for the current holding (Step 1 skipped — asset already known).

**Drawer open target**: the drawer accepts an optional `initialTab?: 'summary' | 'transactions'` prop. Row click and "Visualizar" action open at "Resumo". Future shortcuts (e.g., a "Movimentações" deep-link) can open directly at the transactions tab.

### FR-9: Row Hover Entry Points for Existing Holdings

Holding rows in the portfolio table show "+" and "−" action buttons on hover (desktop) / tap (mobile). These are hidden at rest to keep rows visually clean.

- **"+"** → `TransactionEditor` with `row-buy` intent — asset pre-filled from holding, Step 1 skipped, opens at Step 2 directly
- **"−"** → `TransactionEditor` with `row-sell` intent — asset pre-filled, `maxQty` = current holding quantity, opens at Step 2 directly

Both mount `TransactionEditor` at the `InvestmentTable` level. The detail drawer is not opened or involved.

---

## Out of Scope

- **Backend changes**: no new mutations or schema changes required for the initial migration. The config's `createMutation`/`updateMutation` seam is designed to absorb per-class mutations when they arrive without touching the shell or hook.
- **Side-effect handler chain** (Phase 1 of `holding-transaction-operations.md`): independent work, should land separately.
- **Inline micro-form** for row +/−: the option of a 3-field inline strip (no drawer) was considered and deferred. The unified drawer with pre-filled intent is the chosen path (option b). The inline micro-form can be revisited if user research shows the drawer is too heavy for quick edits.
- **Crypto standalone drawer** (Phase 3 of `holding-transaction-operations.md`): the `TransactionEditor` for `assetClass=crypto` replaces the current `EquityDrawer` crypto variant. The `EquityRecordVariant = "mixed"` config value is removed.
- **Cash transactions**: `RecordCashDrawer` is not merged into `TransactionEditor` in this phase. Cash is structurally simpler and its entry path (cash card) is separate from the holdings grid. It may be unified later.

---

## Architecture Notes

### Design Patterns

Three patterns compose the architecture. Each solves a distinct problem.

#### 1. Strategy + Registry (`AssetClassTransactionConfig`)

**Problem**: If the drawer shell or hook contains `if (assetClass === 'funds') { ... } else if (...)`, adding a new class requires modifying the core. Sections become fat conditionals.

**Solution**: Each asset class is a self-contained config object. The shell and hook are generic — they consume whatever config they receive. Adding a new class = new config file, zero changes to the shell.

```typescript
interface AssetClassTransactionConfig<TFields extends FieldValues> {
  // Step 1 — null means no selection step (Fixed Income skips to form directly)
  step1Screen: React.ComponentType<Step1ScreenProps> | null

  // Step 2 — Asset Identity Block shown above form fields
  // When step1Screen !== null: includes "Alterar" nav action
  // When step1Screen === null: display-only (Fixed Income)
  AssetIdentityBlock: React.ComponentType<AssetIdentityBlockProps>

  // Step 2 — Form fields
  FormFields: React.ComponentType        // uses useFormContext<TFields> internally
  CostFields: React.ComponentType | null // null → "Mais detalhes" toggle not rendered (Funds, Crypto)

  // Form logic
  schema: (locale: Locale) => ZodSchema<TFields>
  defaultValues: (intent: TransactionEditorIntent, selectedAsset: ResolvedAsset | null) => DefaultValues<TFields>

  // Mutation wiring — isolated per config.
  // When new per-class mutations arrive, update the config entry; hook is unchanged.
  createMutation: DocumentNode
  updateMutation: DocumentNode
  buildCreateInput: (data: TFields, intent: NewBuyIntent | NewSellIntent | RowBuyIntent | RowSellIntent) => unknown
  buildUpdateInput: (data: TFields, intent: EditIntent) => unknown

  // Labels
  getLabels: (t: TFunction, intent: TransactionEditorIntent) => TransactionEditorLabels
}

// The registry — the only place that knows which classes exist.
const ASSET_CLASS_CONFIGS: Record<InvestmentAssetClass, AssetClassTransactionConfig<FieldValues>> = {
  'fixed-income': fixedIncomeConfig,
  'funds':        fundsConfig,
  'stocks':       stocksConfig,
  'reits':        reitsConfig,
  'crypto':       cryptoConfig,
}
```

Each config file lives at `transaction-editor/configs/{assetClass}.config.ts` and is the single place to change when a class's fields, schema, step 1 behavior, or mutations change.

#### 2. Discriminated Union Intent

**Problem**: "new vs. edit" and "buy vs. sell" are orthogonal but interacting concerns. Representing them as separate boolean props leads to impossible states (`isEdit=true` + `isBuy=false` + no `transactionId`).

**Solution**: A TypeScript discriminated union where each variant carries exactly the fields it needs — no more.

```typescript
type NewBuyIntent   = { kind: "new-buy";   assetClass: InvestmentAssetClass }
type NewSellIntent  = { kind: "new-sell";  assetClass: InvestmentAssetClass }
type RowBuyIntent   = { kind: "row-buy";   assetClass: InvestmentAssetClass; holdingId: string }
type RowSellIntent  = { kind: "row-sell";  assetClass: InvestmentAssetClass; holdingId: string; maxQty: number }
type EditIntent     = { kind: "edit";      assetClass: InvestmentAssetClass; holdingId: string; transactionId: string }

type TransactionEditorIntent = NewBuyIntent | NewSellIntent | RowBuyIntent | RowSellIntent | EditIntent
```

The shell narrows `intent.kind` once at the top and passes typed sub-intents down. `buildCreateInput` only receives non-edit intents; `buildUpdateInput` only receives `EditIntent` — enforced at the type level.

#### 3. Generic Hook + FormProvider (Compound Component)

**Problem**: The hook needs to be reusable across all five configs without knowing what `TFields` looks like. Section field components need form access without prop drilling.

**Solution**: The hook is generic over `TFields`. It owns mutation state, `refetchQueries`, `toast.success`, and `ErrorAlert`. It returns a `UseFormReturn<TFields>` that the shell passes via `FormProvider`. Section components call `useFormContext<TFields>()` — they receive form state from context, not props.

```typescript
function useTransactionEditor<TFields extends FieldValues>(
  config: AssetClassTransactionConfig<TFields>,
  intent: TransactionEditorIntent,
  options?: { onSuccess?: () => void }
): {
  form: UseFormReturn<TFields>
  onSubmit: (e: React.FormEvent) => void
  isLoading: boolean
  mutationError: ApolloError | undefined
}
```

This is a direct extension of the `useRecordHoldingForm<TFields>` pattern in `holding-transaction-operations.md`. The only new concern is intent-routing to the right mutation document.

A separate `TransactionEditorContext` (not react-hook-form) carries non-form state that field components may need:

```typescript
// Shell provides; field components consume via useTransactionEditorContext()
interface TransactionEditorContextValue {
  intent: TransactionEditorIntent
  labels: TransactionEditorLabels
  selectedAsset: ResolvedAsset | null   // null only during step 1; always set by step 2
  onChangeAsset: () => void             // navigates back to step 1; no-op when step1Screen=null
}
```

The hook is mounted once and persists across step transitions. When the user changes asset (step 1 → step 2 again), the hook resets the form via `form.reset(config.defaultValues(intent, newAsset))`.

---

### Component Tree

The shell manages `currentStep: 'select' | 'form'` and `selectedAsset: ResolvedAsset | null`. It is otherwise intent-agnostic — it renders whatever the config provides.

```
TransactionEditorShell                            ← reads ASSET_CLASS_CONFIGS[intent.assetClass]
  TransactionEditorContext.Provider               ← { intent, labels, selectedAsset, onChangeAsset }
  useTransactionEditor<TFields>(config, intent)   ← mounted once, persists across steps

  ── when currentStep = 'select' ──
  config.step1Screen                              ← onSelect(asset) → setSelectedAsset + setStep('form')

  ── when currentStep = 'form' ──
  FormProvider (react-hook-form)
    ├── config.AssetIdentityBlock                 ← display-only; "Alterar" calls onChangeAsset()
    ├── config.FormFields                         ← uses useFormContext<TFields>()
    ├── "Mais detalhes" DisclosureToggle          ← rendered only when config.CostFields != null
    │   └── config.CostFields
    └── Drawer.ActionButtons

ASSET_CLASS_CONFIGS registry
  'fixed-income' → { step1Screen: null,              AssetIdentityBlock: FixedIncomeIdentityBlock, FormFields: FixedIncomeFormFields, CostFields: FixedIncomeCostFields }
  'funds'        → { step1Screen: FundStep1,          AssetIdentityBlock: FundIdentityBlock,        FormFields: FundFormFields,        CostFields: null                  }
  'stocks'       → { step1Screen: AssetStep1(stocks), AssetIdentityBlock: EquityIdentityBlock,      FormFields: EquityFormFields,      CostFields: EquityCostFields      }
  'reits'        → { step1Screen: AssetStep1(reits),  AssetIdentityBlock: EquityIdentityBlock,      FormFields: EquityFormFields,      CostFields: EquityCostFields      }
  'crypto'       → { step1Screen: CryptoStep1,        AssetIdentityBlock: CryptoIdentityBlock,      FormFields: CryptoFormFields,      CostFields: null                  }
```

`stocks` and `reits` share `EquityIdentityBlock`, `EquityFormFields`, and `EquityCostFields` — the only difference is `AssetStep1` receives an `assetTypeFilter` prop from the config. No conditionals inside shared components.

#### Step 1 component contract

`step1Screen` components receive one prop:

```typescript
interface Step1ScreenProps {
  onSelect: (asset: ResolvedAsset) => void
}
```

They fire the appropriate GraphQL query internally. They never inspect `intent` — the shell handles all intent logic before step 1 is mounted. Calling `onSelect` is the only output.

---

### Mutation Seam

The current existing mutations (`RecordInvestmentTransaction`, `UpdateInvestmentTransaction`) are used as-is for the initial migration. When per-class mutations arrive, the change is isolated to each class's config:

```
// Before (shared mutation):
fixedIncomeConfig.createMutation = RECORD_INVESTMENT_TRANSACTION

// After (class-specific mutation, zero changes to shell or hook):
fixedIncomeConfig.createMutation = RECORD_FIXED_INCOME_TRANSACTION
fixedIncomeConfig.buildCreateInput = buildFixedIncomeInput  // narrower type
```

The hook calls `useMutation(config.createMutation)` or `useMutation(config.updateMutation)` based on `intent.kind === 'edit'`. No switch statement anywhere.

---

### Files to Create / Remove

**Create**:
- `transaction-editor/TransactionEditor.tsx` — shell; manages `currentStep` and `selectedAsset` state
- `transaction-editor/TransactionEditorContext.tsx` — non-form context (`intent`, `labels`, `selectedAsset`, `onChangeAsset`)
- `transaction-editor/hooks/useTransactionEditor.ts` — generic hook
- `transaction-editor/configs/fixedIncome.config.ts`
- `transaction-editor/configs/funds.config.ts`
- `transaction-editor/configs/stocks.config.ts`
- `transaction-editor/configs/reits.config.ts`
- `transaction-editor/configs/crypto.config.ts`
- `transaction-editor/configs/index.ts` — exports `ASSET_CLASS_CONFIGS` registry
- `transaction-editor/step1/FundStep1.tsx` — fund search + manual entry toggle
- `transaction-editor/step1/AssetStep1.tsx` — B3 asset search shared by stocks + reits
- `transaction-editor/step1/CryptoStep1.tsx` — free-text form (name, ticker, network)
- `transaction-editor/identity/FixedIncomeIdentityBlock.tsx`
- `transaction-editor/identity/FundIdentityBlock.tsx`
- `transaction-editor/identity/EquityIdentityBlock.tsx` — shared by stocks + reits
- `transaction-editor/identity/CryptoIdentityBlock.tsx`
- `transaction-editor/form/FixedIncomeFormFields.tsx`
- `transaction-editor/form/FundFormFields.tsx`
- `transaction-editor/form/EquityFormFields.tsx` — shared by stocks + reits
- `transaction-editor/form/CryptoFormFields.tsx`
- `transaction-editor/form/FixedIncomeCostFields.tsx`
- `transaction-editor/form/EquityCostFields.tsx` — shared by stocks + reits

**Modify** (significant changes):
- `InvestmentHoldingDetailDrawer.tsx` — add tabs (Resumo / Movimentações); add Movimentações tab with transaction table + add/edit/delete wiring; change to `size="large"`; accept `onOpenTransactionEditor` callback prop
- `InvestmentTable.tsx` — mount `TransactionEditor` for `row-buy`/`row-sell`/`edit` intents from detail drawer and row hover; remove `HoldingManagementDrawer` references
- `table/HoldingRowActionsColumn.tsx` (or equivalent) — add hover "+"/"-" buttons
- `table/InvestmentHoldingActionsMenu.tsx` — remove "Gerenciar movimentações" menu item
- `grid/InvestmentGridDrawers.tsx` — wire `TransactionEditor` with `new-buy` intent per class

**Remove** (after migration):
- `RecordFixedIncomeDrawer.tsx`
- `RecordEquityHoldingDrawer.tsx`
- `RecordFundHoldingDrawer.tsx`
- `InvestmentEventSelectorDrawer.tsx`
- `HoldingManagementDrawer.tsx` (if created as a standalone before this decision — replace with Movimentações tab)
- `EquityRecordVariant` type from `investmentGridConfig.ts`

---

## Implementation Plan

### Phase 1 — Foundation (no visible change)

1. Define `AssetClassTransactionConfig<TFields>` interface and `TransactionEditorIntent` union type.
2. Create `useTransactionEditor.ts` generic hook; wire it to the existing shared mutations as defaults.
3. Create `TransactionEditorShell` that reads `ASSET_CLASS_CONFIGS` and renders config components.
4. Implement `funds.config.ts` as the first config; migrate `RecordFundHoldingDrawer` to use the shell. Delete old drawer. Validate visual parity.

Starting with Funds is deliberate: it has a unique `SectionA` (fund search + manual toggle), no `SectionC`, and its own `SectionBFields` — so it exercises the full config surface without sharing field components with other classes.

### Phase 2 — Remaining class configs

5. Implement `fixedIncome.config.ts` + sections. Remove `RecordFixedIncomeDrawer.tsx`.
6. Implement `stocks.config.ts` + `reits.config.ts` — share `AssetSearch`, `EquityBFields`, `EquityCFields`. Remove `RecordEquityHoldingDrawer.tsx`.
7. Implement `crypto.config.ts` — reuses `EquityBFields`, has `CryptoManualEntry` for Section A, no Section C.

### Phase 3 — Entry point migration

8. Migrate row `+`/`−` to `TransactionEditor` with `row-buy`/`row-sell` intents.
9. Migrate timeline event click to `edit` intent.
10. Replace popover edit with "Editar" action → `edit` intent.
11. Remove "+ Investimento" button; add inline "Primeiro investimento" hint in `SectionA`.
12. Remove `InvestmentEventSelectorDrawer.tsx`.

**Done criteria**: all three original large drawers deleted, `InvestmentEventSelectorDrawer` deleted, `ASSET_CLASS_CONFIGS` is the only place that knows which classes exist, existing E2E smoke tests pass.

---

## Decisions

**DEC-1: No multistep wizard**
Rejected. Forces an extra step for the 80% case (user holds the ticker already). Single screen with adaptive focus is the chosen pattern. Progressive disclosure handles noise, not multistep.

**DEC-2: Inline micro-form deferred**
Row +/− will open the unified drawer pre-filled, not an inline strip. The strip would require a separate validation and submit path. If user research shows the drawer is too heavy, the micro-form can be added as an alternative entry point without changing the drawer.

**DEC-3: "+ Investimento" kept and wired to `TransactionEditor`**
Both the grid card button and the tab panel header button are kept. They are already wired through `openRecordDrawerForClass(key)` → `InvestmentGridDrawers` → `TransactionEditor` with `{ kind: "new-buy", assetClass: key }`. No changes needed to button wiring — the current implementation is correct. The button triggers Step 1 (asset selection) for applicable classes, which is the intended UX.

**DEC-4: Cash excluded from this refactor**
`RecordCashDrawer` is 72 lines, structurally different, and reached via a separate cash card path. Not worth merging now.

**DEC-5: `edit` intent allows direction change**
In edit mode the direction toggle is shown and editable. The mutation handles re-deriving cash ledger direction from the new transaction type. No special locking needed.

**DEC-6: Two-step flow applies to Funds, Stocks, FIIs, Crypto only — not Fixed Income**
Fixed Income asset selection (issuer + asset type) is deeply coupled to the form: the asset type determines which fields are required, which IR exemption applies, and whether coupon fields are shown. Separating it into a prior step would require the step 1 outcome to carry enough data to configure the form, which is equivalent to re-implementing the form logic outside the form. The selection stays inline as form fields. The Asset Identity Block for Fixed Income is display-only (no "Alterar" nav action).

**DEC-7: No "Alterar" in edit mode — close and reopen is the recovery path**
Allowing asset changes on an existing transaction inside the editor requires a confirmation dialog, a mid-flow intent reset, and complex schema reconciliation. The cost is high and the case is rare. The correct recovery path is: close the `TransactionEditor`, open `HoldingManagementDrawer` for the correct holding, and add or edit from there. This removes the confirmation dialog entirely and keeps the editor's intent contract clean.

**DEC-8: Form data discarded on asset change during new transaction (no confirmation needed)**
When a user is in step 2 of a `new-*` transaction and clicks "Alterar", form data is lost. No confirmation is shown — the user consciously navigated back to step 1. The new asset may have a different schema, so carrying forward partial data would be incorrect anyway. No draft persistence in V1.

**DEC-9: Detail and management merged into one tabbed drawer**
Originally specified as two separate drawers with swap navigation. The two-drawer model adds friction — users lose context on every navigation — without sufficient benefit from the content separation. The tabbed model provides the same cognitive split (Resumo = analytical, Movimentações = operational) with zero navigation cost. `HoldingManagementDrawer` as a standalone component is eliminated; its content becomes Tab 2 of `InvestmentHoldingDetailDrawer`.

**DEC-10: TransactionEditor layers on top of the detail drawer — no swap**
When the user triggers an edit or add-transaction action from within the Movimentações tab, `TransactionEditor` opens in front while the detail drawer remains visible. MUI Modal stacking handles z-index automatically when the editor is mounted later in the DOM than the detail drawer. When the editor closes (success or cancel), the user returns to the Movimentações tab without any navigation. This replaces the previously specified swap pattern.

**DEC-11: "Gerenciar movimentações" action removed from the actions menu**
The row actions menu no longer has a separate management entry. Row click (or "Visualizar") opens the unified detail drawer, which now contains the Movimentações tab. Row hover `+`/`−` buttons handle the high-frequency "add transaction" shortcut without opening the detail drawer first. This simplifies the actions menu to truly secondary, rare operations (transfer custody, withholding tax, etc.).

**DEC-12: Unified `InvestmentHoldingDetailDrawer` uses `size="large"`**
The Movimentações transaction table needs horizontal space to display date, direction, quantity, unit price, value, and action icons without wrapping or truncation. `size="large"` is inherited from the original `HoldingManagementDrawer` specification and now applies to the unified drawer.

**DEC-13: Row hover `+`/`−` buttons for existing holdings**
Fast-path entry for the most common operation: adding a transaction to a holding already in the portfolio. The buttons appear only on hover to keep rows visually clean. They open `TransactionEditor` with `row-buy`/`row-sell` intent, skipping Step 1 entirely (asset is known). `maxQty` on `row-sell` is set to the current holding quantity. These replace the old `InvestmentEventSelectorDrawer` quick-action path.

**DEC-14: All transaction edits go through TransactionEditor — no inline/cell editing**
Considered and rejected: inline row expansion and Excel-style cell editing for the Movimentações tab. The decision to use `TransactionEditor` for all edits (all classes) eliminates dual code paths, keeps one mental model for the user, and avoids the implementation complexity of inline validation, keyboard navigation, and conditional fields. The cost is one extra drawer open/close per edit; the benefit is zero ongoing maintenance of a parallel edit surface.
