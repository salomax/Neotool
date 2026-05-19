---
title: Holding Transaction Editor — Implementation Backlog
type: task-backlog
category: investment-portfolio
status: ready
spec: 03-features/investment-portfolio/holding-transaction-editor-refactor.md
last_updated: 2026-05-12
---

# Holding Transaction Editor — Implementation Backlog

> Before starting any task: read the full spec at `invistus/docs/03-features/investment-portfolio/holding-transaction-editor-refactor.md` and the form pattern at `invistus/docs/07-frontend/patterns/form-pattern.md`.

All new files live under:
```
invistus/web/src/app/personal-finance/investments/components/transaction-editor/
```

Abbreviated below as `transaction-editor/`.

---

## Phase 1 — Foundation

### T-01: Define shared types

**Creates**: `transaction-editor/types.ts`

**No dependencies.**

Define and export:

```typescript
// The minimal resolved asset shape passed between step 1 and step 2.
// Entry points that already know the asset (row-*, edit) provide this directly.
export interface ResolvedAsset {
  id: string
  name: string
  ticker?: string
  assetType: AssetType          // from GraphQL enum
  source: 'cvm' | 'b3' | 'manual' | 'existing'
}

// Intent discriminated union
export type NewBuyIntent  = { kind: 'new-buy';  assetClass: InvestmentAssetClass }
export type NewSellIntent = { kind: 'new-sell'; assetClass: InvestmentAssetClass }
export type RowBuyIntent  = { kind: 'row-buy';  assetClass: InvestmentAssetClass; holdingId: string; asset: ResolvedAsset }
export type RowSellIntent = { kind: 'row-sell'; assetClass: InvestmentAssetClass; holdingId: string; asset: ResolvedAsset; maxQty: number }
export type EditIntent    = { kind: 'edit';     assetClass: InvestmentAssetClass; holdingId: string; transactionId: string; asset: ResolvedAsset }
export type TransactionEditorIntent = NewBuyIntent | NewSellIntent | RowBuyIntent | RowSellIntent | EditIntent

// Helper narrowers
export function isNewIntent(i: TransactionEditorIntent): i is NewBuyIntent | NewSellIntent {
  return i.kind === 'new-buy' || i.kind === 'new-sell'
}
export function isEditIntent(i: TransactionEditorIntent): i is EditIntent {
  return i.kind === 'edit'
}
export function intentAction(i: TransactionEditorIntent): 'buy' | 'sell' {
  return i.kind === 'new-sell' || i.kind === 'row-sell' ? 'sell' : 'buy'
}

// Labels derived by each class config
export interface TransactionEditorLabels {
  step1Title: string         // e.g. "Selecionar fundo"
  drawerTitle: string        // e.g. "Aportar em Trend Inflação"
  ctaLabel: string           // "Registrar" | "Salvar alterações"
  buyLabel: string           // "Comprar" | "Aportar"
  sellLabel: string          // "Vender" | "Resgatar"
}

// Step 1 component contract
export interface Step1ScreenProps {
  onSelect: (asset: ResolvedAsset) => void
}

// Asset identity block contract
export interface AssetIdentityBlockProps {
  asset: ResolvedAsset
  onChangeAsset?: () => void   // undefined = no "Alterar" affordance (Fixed Income)
}

// InvestmentAssetClass — the non-cash classes that use this editor
export type InvestmentAssetClass = 'fixed-income' | 'funds' | 'stocks' | 'reits' | 'crypto'
```

**Acceptance criteria**:
- [ ] All types exported from a single file, no runtime code
- [ ] `TransactionEditorIntent` is a discriminated union that TypeScript narrows correctly on `intent.kind`

---

### T-02: Define `AssetClassTransactionConfig` interface

**Reads**: `transaction-editor/types.ts` (T-01)
**Creates**: `transaction-editor/config.types.ts`
**Depends on**: T-01

```typescript
import type { FieldValues, DefaultValues } from 'react-hook-form'
import type { ZodSchema } from 'zod'
import type { DocumentNode } from '@apollo/client'
import type {
  ResolvedAsset, TransactionEditorIntent, TransactionEditorLabels,
  Step1ScreenProps, AssetIdentityBlockProps,
  NewBuyIntent, NewSellIntent, RowBuyIntent, RowSellIntent, EditIntent
} from './types'

export interface AssetClassTransactionConfig<TFields extends FieldValues> {
  // Step 1 — null for Fixed Income (no selection step)
  step1Screen: React.ComponentType<Step1ScreenProps> | null

  // Asset identity block shown at top of step 2
  AssetIdentityBlock: React.ComponentType<AssetIdentityBlockProps>

  // Form field components — use useFormContext<TFields>() internally
  FormFields: React.ComponentType
  CostFields: React.ComponentType | null  // null → no "Mais detalhes" toggle

  // Form logic
  schema: (locale: string) => ZodSchema<TFields>
  defaultValues: (
    intent: TransactionEditorIntent,
    selectedAsset: ResolvedAsset | null
  ) => DefaultValues<TFields>

  // Mutation wiring — update these entries when per-class mutations arrive
  createMutation: DocumentNode
  updateMutation: DocumentNode
  buildCreateInput: (
    data: TFields,
    intent: NewBuyIntent | NewSellIntent | RowBuyIntent | RowSellIntent,
    selectedAsset: ResolvedAsset
  ) => unknown
  buildUpdateInput: (
    data: TFields,
    intent: EditIntent
  ) => unknown

  // i18n
  getLabels: (
    t: TFunction,
    intent: TransactionEditorIntent,
    selectedAsset: ResolvedAsset | null
  ) => TransactionEditorLabels
}
```

**Acceptance criteria**:
- [ ] TypeScript compiles with no errors when a compliant config object is assigned to this type
- [ ] `buildCreateInput` does not accept `EditIntent` (compile-time error if passed)

---

### T-03: Create `TransactionEditorContext`

**Reads**: `transaction-editor/types.ts`
**Creates**: `transaction-editor/TransactionEditorContext.tsx`
**Depends on**: T-01

```typescript
interface TransactionEditorContextValue {
  intent: TransactionEditorIntent
  labels: TransactionEditorLabels
  selectedAsset: ResolvedAsset | null
  onChangeAsset: () => void   // navigates back to step 1; no-op when step1Screen=null
}

export const TransactionEditorContext = React.createContext<TransactionEditorContextValue | null>(null)

export function useTransactionEditorContext(): TransactionEditorContextValue {
  const ctx = React.useContext(TransactionEditorContext)
  if (!ctx) throw new Error('useTransactionEditorContext must be inside TransactionEditorContext.Provider')
  return ctx
}
```

**Acceptance criteria**:
- [ ] Throws a clear error if used outside the provider

---

### T-04: Create `useTransactionEditor` hook

**Reads**:
- `transaction-editor/types.ts`
- `transaction-editor/config.types.ts`
- `RecordFundHoldingDrawer.tsx` — reference for how mutation + cache invalidation + toast currently works
- `invistus/docs/07-frontend/patterns/form-pattern.md`

**Creates**: `transaction-editor/hooks/useTransactionEditor.ts`
**Depends on**: T-01, T-02

The hook is generic over `TFields`. It owns:
- `useMutation` — uses `config.createMutation` or `config.updateMutation` based on `isEditIntent(intent)`
- `refetchQueries`: always `['InvestmentHoldings', 'InvestmentCashBalance', 'InvestmentPortfolioSummary']`
- `useForm<TFields>({ resolver: zodResolver(config.schema(locale)), defaultValues: config.defaultValues(intent, selectedAsset) })`
- `toast.success` on `onCompleted`
- Returns `{ form, onSubmit, isLoading, mutationError }`

```typescript
export function useTransactionEditor<TFields extends FieldValues>(
  config: AssetClassTransactionConfig<TFields>,
  intent: TransactionEditorIntent,
  selectedAsset: ResolvedAsset | null,
  options?: { onSuccess?: () => void }
): {
  form: UseFormReturn<TFields>
  onSubmit: (e: React.FormEvent) => void
  isLoading: boolean
  mutationError: ApolloError | undefined
}
```

When `selectedAsset` changes (user goes back to step 1 and picks a different asset): call `form.reset(config.defaultValues(intent, newAsset))`.

**Acceptance criteria**:
- [ ] Uses `useMutation(config.createMutation)` for create, `useMutation(config.updateMutation)` for edit
- [ ] On `ApolloError`: surfaces via `mutationError` — does NOT toast
- [ ] On success: calls `toast.success`, then `options.onSuccess?.()`
- [ ] `refetchQueries` list is hardcoded in the hook (not per-config)

---

### T-05: Create `TransactionEditorShell`

**Reads**:
- `transaction-editor/types.ts`, `config.types.ts`, `TransactionEditorContext.tsx`, `hooks/useTransactionEditor.ts`
- `RecordFundHoldingDrawer.tsx` — reference for `Drawer.ActionButtons` and `ErrorAlert` usage
- `invistus/docs/07-frontend/patterns/form-pattern.md` — drawer form pattern

**Creates**: `transaction-editor/TransactionEditor.tsx`
**Depends on**: T-01, T-02, T-03, T-04

The shell manages:
- `currentStep: 'select' | 'form'`
- `selectedAsset: ResolvedAsset | null`

Initial state derived from intent:
```
isNewIntent(intent) && config.step1Screen !== null  → 'select', null
otherwise (row-*, edit)                             → 'form',   intent.asset
```

Render logic:
```tsx
<Drawer open={open} onClose={onClose}>
  <TransactionEditorContext.Provider value={{ intent, labels, selectedAsset, onChangeAsset }}>
    {currentStep === 'select' && config.step1Screen && (
      <config.step1Screen onSelect={(asset) => { setSelectedAsset(asset); setCurrentStep('form') }} />
    )}
    {currentStep === 'form' && selectedAsset && (
      <FormProvider {...form}>
        <Box component="form" onSubmit={onSubmit} noValidate>
          <config.AssetIdentityBlock
            asset={selectedAsset}
            onChangeAsset={config.step1Screen ? onChangeAsset : undefined}
          />
          <FormStack>
            <config.FormFields />
            {config.CostFields && (
              <DisclosureToggle label={t('investments.form.moreDetails')} defaultOpen={shouldExpandCosts}>
                <config.CostFields />
              </DisclosureToggle>
            )}
          </FormStack>
          <ErrorAlert error={mutationError} />
          <Drawer.ActionButtons
            onCancel={onClose}
            submitLabel={labels.ctaLabel}
            isLoading={isLoading}
          />
        </Box>
      </FormProvider>
    )}
  </TransactionEditorContext.Provider>
</Drawer>
```

`shouldExpandCosts`: true when `intent.kind === 'edit'` and any cost field in the pre-loaded transaction is non-zero.

`onChangeAsset`:
- For `new-*` and `row-*` intents: `setSelectedAsset(null); setCurrentStep('select')`; form is reset via hook.
- For `edit` intent: show a `ConfirmationDialog` ("Trocar o ativo vai descartar as alterações atuais e iniciar um novo registro. Continuar?"). On confirm: transition to step 1, clear transactionId, intent internally becomes `new-buy`.

**Acceptance criteria**:
- [ ] Opens at step 1 for `new-buy`/`new-sell` when `config.step1Screen !== null`
- [ ] Opens at step 2 directly for `row-*` and `edit`
- [ ] Drawer title shows step 1 title when in step 1, step 2 title when in step 2
- [ ] "Alterar" action in identity block is absent when `config.step1Screen === null`
- [ ] Edit + "Alterar" shows confirmation dialog before navigating to step 1

---

### T-06: Create `ASSET_CLASS_CONFIGS` registry (stub)

**Creates**: `transaction-editor/configs/index.ts`
**Depends on**: T-02

Create the registry with placeholder entries — each will be filled in by later tasks.

```typescript
import type { AssetClassTransactionConfig } from '../config.types'
import type { InvestmentAssetClass } from '../types'
import { FieldValues } from 'react-hook-form'

// Populated by T-10, T-14, T-18, T-22, T-26
export const ASSET_CLASS_CONFIGS: Record<InvestmentAssetClass, AssetClassTransactionConfig<FieldValues>> = {} as any
```

**Acceptance criteria**:
- [ ] TypeScript accepts the type annotation (even with stub `as any`)
- [ ] Registry is the single import point — callers never import individual configs directly

---

## Phase 2 — Funds Class (Pilot)

> Funds is the pilot class. It exercises the full config surface: step 1 with two search sources + manual escalation, no cost fields, MANUAL pricing default for unlisted funds.

### T-07: Create `FundStep1`

**Reads**:
- `RecordFundHoldingDrawer.tsx` — find the existing fund autocomplete query and component
- `transaction-editor/types.ts` (Step1ScreenProps)
- Any existing `FundAutocomplete` or `searchInvestmentFundsAutocomplete` usage in the codebase

**Creates**: `transaction-editor/step1/FundStep1.tsx`
**Depends on**: T-01

Implements the fund selection screen per FR-3c:

**Structure**:
1. Search input using `searchInvestmentFundsAutocomplete` (debounced, min 2 chars).
2. Results list combining:
   - CVM-indexed funds (from query)
   - User's manually-created funds (from a separate query or same query with a `source` flag — check existing API; if not available, add a `TODO: add manual funds to search results` comment)
   - Section labels to distinguish the two sources visually
3. At the bottom of the list (always): a secondary list item "Registrar fundo manualmente" with an `AddCircleOutline` icon.
4. When search returns zero results: replace list with a centered empty state + primary button "Registrar fundo manualmente".
5. Tapping "Registrar manualmente" expands an **inline sub-form** below the search (no screen transition):
   - Fund name field (required, `size="medium"`)
   - CNPJ field (optional, Brazilian CNPJ mask)
   - "Confirmar" button — calls `onSelect({ id: crypto.randomUUID(), name, cnpj, source: 'manual', assetType: AssetType.InvestmentFund })`
6. Tapping a CVM/existing result calls `onSelect({ id, name, ticker, source: 'cvm' | 'existing', assetType })` immediately.

**Acceptance criteria**:
- [ ] Renders a search field with debounced query on mount
- [ ] Results combine CVM and manual funds (or TODO comment if API unavailable)
- [ ] "Registrar manualmente" always visible at bottom of results
- [ ] Zero-results state shows "Registrar manualmente" as primary action
- [ ] Manual sub-form is inline (no navigation); CNPJ field is optional
- [ ] Calling `onSelect` always produces a valid `ResolvedAsset`

---

### T-08: Create `FundIdentityBlock`

**Creates**: `transaction-editor/identity/FundIdentityBlock.tsx`
**Depends on**: T-01

Renders: fund name (primary text), "Fundo de Investimento" badge, CNPJ if available, and optional "Alterar" link.

```tsx
export function FundIdentityBlock({ asset, onChangeAsset }: AssetIdentityBlockProps) { ... }
```

Visual: a compact card or banner at the top of the drawer content — not inside a `FormStack`. Use MUI `Paper` with `variant="outlined"` or a styled `Box`.

**Acceptance criteria**:
- [ ] Shows asset name prominently
- [ ] "Alterar" link visible when `onChangeAsset` is defined; absent when undefined
- [ ] Clicking "Alterar" calls `onChangeAsset()`

---

### T-09: Extract `FundFormFields`

**Reads**: `RecordFundHoldingDrawer.tsx` — read the full file; identify all form fields
**Creates**: `transaction-editor/form/FundFormFields.tsx`
**Depends on**: T-01

Extract the fund-specific form fields from `RecordFundHoldingDrawer.tsx`. These fields use `useFormContext<FundTransactionFields>()` internally — no props except what context provides.

Fields to include (from existing drawer):
- Direction toggle (Aportar / Resgatar) — hidden when intent action is locked (read from `useTransactionEditorContext()`)
- Trade date (`DatePickerField`)
- Pricing mode toggle (MARKET / MANUAL)
- Gross value / quantity + unit price (conditional on pricing mode)
- Valuation date (when MANUAL pricing mode)
- Net value
- Income tax withheld
- Notes (textarea)
- Settlement date (inside the "Mais detalhes" disclosure — handled by shell, not this component)

**Note on pricing mode for unlisted funds**: when `useTransactionEditorContext().selectedAsset?.source === 'manual'`, force `pricingMode` to `'MANUAL'` and render it as read-only text, not a toggle.

**Acceptance criteria**:
- [ ] All fields from `RecordFundHoldingDrawer` are present
- [ ] No `useMutation` call inside this component
- [ ] Pricing mode is read-only when `selectedAsset.source === 'manual'`
- [ ] Direction toggle hidden when action is locked by intent

---

### T-10: Create `funds.config.ts` and register it

**Reads**:
- `RecordFundHoldingDrawer.tsx` — find the Zod schema, `buildInput` logic, mutation imports
- `transaction-editor/types.ts`, `config.types.ts`

**Creates**: `transaction-editor/configs/funds.config.ts`
**Modifies**: `transaction-editor/configs/index.ts`
**Depends on**: T-07, T-08, T-09

```typescript
export const fundsConfig: AssetClassTransactionConfig<FundTransactionFields> = {
  step1Screen: FundStep1,
  AssetIdentityBlock: FundIdentityBlock,
  FormFields: FundFormFields,
  CostFields: null,   // funds have no transaction costs

  schema: (locale) => fundTransactionSchema(locale),
  defaultValues: (intent, selectedAsset) => ({
    pricingMode: selectedAsset?.source === 'manual' ? 'MANUAL' : 'MARKET',
    direction: intentAction(intent) === 'buy' ? 'INFLOW' : 'OUTFLOW',
    tradeDate: new Date(),
    // ... rest from existing drawer defaults
  }),

  createMutation: RECORD_INVESTMENT_TRANSACTION,
  updateMutation: UPDATE_INVESTMENT_TRANSACTION,
  buildCreateInput: (data, intent, selectedAsset) => ({ /* port from RecordFundHoldingDrawer */ }),
  buildUpdateInput: (data, intent) => ({ /* port from RecordFundHoldingDrawer */ }),

  getLabels: (t, intent, selectedAsset) => ({
    step1Title: t('investments.form.funds.selectFund'),
    drawerTitle: selectedAsset
      ? intentAction(intent) === 'buy'
        ? t('investments.form.funds.applyIn', { name: selectedAsset.name })
        : t('investments.form.funds.redeemFrom', { name: selectedAsset.name })
      : t('investments.form.funds.apply'),
    ctaLabel: isEditIntent(intent) ? t('common.saveChanges') : t('common.record'),
    buyLabel: t('investments.transactions.apply'),
    sellLabel: t('investments.transactions.redeem'),
  }),
}
```

Register: `ASSET_CLASS_CONFIGS['funds'] = fundsConfig`

**Schema**: extract the existing Zod schema from `RecordFundHoldingDrawer.tsx` into a `fundTransactionSchema.ts` file alongside the config. Do not duplicate it.

**Acceptance criteria**:
- [ ] TypeScript accepts `fundsConfig` as `AssetClassTransactionConfig<FundTransactionFields>`
- [ ] `CostFields` is `null`
- [ ] `defaultValues` sets `pricingMode: 'MANUAL'` when `selectedAsset.source === 'manual'`

---

### T-11: Wire `TransactionEditor` for funds — replace `RecordFundHoldingDrawer`

**Reads**: `InvestmentGridDrawers.tsx`, `InvestmentHoldingDetailDrawer.tsx`
**Modifies**: `InvestmentGridDrawers.tsx`
**Depends on**: T-05, T-10

In `InvestmentGridDrawers.tsx`, replace:
```tsx
<RecordFundHoldingDrawer open={recordDrawer === 'fund'} ... />
```
with:
```tsx
<TransactionEditor
  open={recordDrawer === 'fund'}
  intent={{ kind: 'new-buy', assetClass: 'funds' }}
  onClose={onCloseRecordDrawer}
  onSuccess={onRecorded}
/>
```

**Do not yet remove** `RecordFundHoldingDrawer.tsx` — keep it until all entry points are migrated (T-28).

**Smoke test**: open the grid "Aportar" button for Funds. Verify:
- Step 1 shows fund search
- Selecting a CVM fund transitions to step 2 with identity block
- Form submits successfully (check network tab for correct mutation)
- Toast appears on success
- Cache is refreshed (holding row updates)

**Acceptance criteria**:
- [ ] Grid entry for Funds opens `TransactionEditor` at step 1
- [ ] Submit creates a transaction (mutation fires, no console errors)
- [ ] Visual parity with `RecordFundHoldingDrawer` in step 2

---

## Phase 3 — Fixed Income Class

### T-12: Create `FixedIncomeIdentityBlock`

**Creates**: `transaction-editor/identity/FixedIncomeIdentityBlock.tsx`
**Depends on**: T-01

Renders: issuer name (primary text), instrument type badge (CDB / LCI / etc.), no "Alterar" action (`onChangeAsset` is always `undefined` for this class).

The asset identity for Fixed Income is populated from the form fields (issuer + type), not from step 1. The block reads `selectedAsset` from `TransactionEditorContext` — for Fixed Income, `selectedAsset` will be pre-populated by the shell from the intent's `asset` field (for `edit`) or built from `defaultValues` (for `new-*`). See T-16 for how this is handled.

**Acceptance criteria**:
- [ ] No "Alterar" affordance rendered
- [ ] Shows issuer name and instrument type

---

### T-13: Extract `FixedIncomeFormFields` and `FixedIncomeCostFields`

**Reads**: `RecordFixedIncomeDrawer.tsx` — read the full file
**Creates**:
- `transaction-editor/form/FixedIncomeFormFields.tsx`
- `transaction-editor/form/FixedIncomeCostFields.tsx`

**Depends on**: T-01

Extract all form fields. Key groups:

**`FixedIncomeFormFields`** (Section B):
- Issuer picker (institution search) — this IS a form field for Fixed Income (not step 1)
- Asset type selector (CDB, LCI, LCA, LC, LF, LIG, LCI, Debêntures, CRI, CRA, Tesouro Direto)
- Indexer + rate fields (conditional on indexer type)
- Emission date, maturity date (required), grace period (optional)
- Coupon frequency + day of month (conditional on frequency)
- IR exemption flag (auto-derived from asset type; user can override)
- Trade date
- Gross value
- Direction toggle (Aportar / Resgatar) — hidden when locked by intent

**`FixedIncomeCostFields`** (Section C, inside "Mais detalhes"):
- IR withheld
- IOF

**Acceptance criteria**:
- [ ] No `useMutation` in either component
- [ ] Asset type selector change triggers correct field visibility (e.g., LCI shows IR exemption = true by default)
- [ ] Coupon fields only visible when coupon frequency ≠ AT_MATURITY

---

### T-14: Create `fixedIncome.config.ts` and register it

**Reads**:
- `RecordFixedIncomeDrawer.tsx` — Zod schema, `buildInput` logic, mutation imports

**Creates**: `transaction-editor/configs/fixedIncome.config.ts`
**Modifies**: `transaction-editor/configs/index.ts`
**Depends on**: T-12, T-13

Key differences from other configs:
- `step1Screen: null` — no step 1
- `CostFields: FixedIncomeCostFields`

**For `new-*` intents**: `selectedAsset` starts as `null` (there is no step 1 to resolve it). The identity block receives `null` and renders a placeholder ("Preencha emissor e tipo acima"). As the user fills in issuer + asset type in `FixedIncomeFormFields`, the shell does **not** update `selectedAsset` — the identity block for Fixed Income reads from form context directly via `useFormContext()`, not from `selectedAsset`. Document this as the Fixed Income exception.

**Acceptance criteria**:
- [ ] `step1Screen: null`
- [ ] `CostFields: FixedIncomeCostFields`
- [ ] TypeScript accepts as `AssetClassTransactionConfig<FixedIncomeTransactionFields>`

---

### T-15: Wire `TransactionEditor` for fixed-income

**Modifies**: `InvestmentGridDrawers.tsx`
**Depends on**: T-05, T-14

Replace `<RecordFixedIncomeDrawer ... />` with `<TransactionEditor intent={{ kind: 'new-buy', assetClass: 'fixed-income' }} ... />`.

Smoke test: open grid "Aportar" for Renda Fixa — verify form renders with issuer picker + asset type selector, no step 1, "Mais detalhes" toggle shows IR/IOF fields.

**Acceptance criteria**:
- [ ] Grid entry opens at step 2 directly (no step 1)
- [ ] "Mais detalhes" disclosure renders `FixedIncomeCostFields`
- [ ] Submit creates transaction

---

## Phase 4 — Stocks Class

### T-16: Create `AssetStep1` (shared by Stocks + FIIs)

**Reads**:
- `RecordEquityHoldingDrawer.tsx` — find the B3 asset search/autocomplete component and query
- `transaction-editor/types.ts`

**Creates**: `transaction-editor/step1/AssetStep1.tsx`
**Depends on**: T-01

```typescript
interface AssetStep1Props extends Step1ScreenProps {
  assetTypeFilter: AssetType[]   // passed from config, not from intent
}
```

Behavior:
1. Search field using `searchInvestmentAssetsAutocomplete` with `assetTypeFilter` applied.
2. Results list — selecting an item immediately calls `onSelect(asset)`.
3. No manual entry fallback (B3 assets are standardized).

**Acceptance criteria**:
- [ ] `assetTypeFilter` correctly limits results (verify with stocks filter vs reits filter)
- [ ] Selecting a result transitions to step 2 immediately
- [ ] No "Registrar manualmente" option (unlike FundStep1)

---

### T-17: Create `EquityIdentityBlock`, `EquityFormFields`, `EquityCostFields`

**Reads**: `RecordEquityHoldingDrawer.tsx` — read the full file
**Creates**:
- `transaction-editor/identity/EquityIdentityBlock.tsx`
- `transaction-editor/form/EquityFormFields.tsx`
- `transaction-editor/form/EquityCostFields.tsx`

**Depends on**: T-01

**`EquityIdentityBlock`**: ticker (large, bold), company name, asset type badge. "Alterar" action present.

**`EquityFormFields`** (Section B):
- Direction toggle (Comprar / Vender) — hidden when locked by intent
- Trade date
- Quantity (required) — when `row-sell` intent, max = `intent.maxQty` (read via `useTransactionEditorContext()`)
- Unit price (required)
- Price factor (optional, default 1)
- Settlement date (default D+2; also appears in "Mais detalhes" if behind disclosure — check existing drawer)

**`EquityCostFields`** (Section C):
- Brokerage fee (corretagem)
- Settlement fee (emolumentos)
- IRRF withheld

**Acceptance criteria**:
- [ ] Quantity field has `max` validation when `intent.kind === 'row-sell'`
- [ ] Price factor field present
- [ ] No `useMutation` in any component

---

### T-18: Create `stocks.config.ts` and register

**Creates**: `transaction-editor/configs/stocks.config.ts`
**Modifies**: `transaction-editor/configs/index.ts`
**Depends on**: T-16, T-17

```typescript
import { AssetType } from '...'
import { AssetStep1 } from '../step1/AssetStep1'

const StocksStep1 = (props: Step1ScreenProps) =>
  <AssetStep1 {...props} assetTypeFilter={[AssetType.Stock, AssetType.StockRights, AssetType.Bdr, AssetType.Etf, AssetType.EtfInternational]} />

export const stocksConfig: AssetClassTransactionConfig<EquityTransactionFields> = {
  step1Screen: StocksStep1,
  AssetIdentityBlock: EquityIdentityBlock,
  FormFields: EquityFormFields,
  CostFields: EquityCostFields,
  // ...
  getLabels: (t, intent, asset) => ({
    step1Title: t('investments.form.stocks.selectAsset'),
    drawerTitle: asset ? `${intentAction(intent) === 'buy' ? 'Comprar' : 'Vender'} ${asset.ticker}` : 'Comprar ação',
    buyLabel: t('investments.transactions.buy'),
    sellLabel: t('investments.transactions.sell'),
    ctaLabel: isEditIntent(intent) ? t('common.saveChanges') : t('common.record'),
  }),
}
```

**Acceptance criteria**:
- [ ] `assetTypeFilter` excludes FII types
- [ ] `CostFields: EquityCostFields`

---

### T-19: Wire `TransactionEditor` for stocks

**Modifies**: `InvestmentGridDrawers.tsx`
**Depends on**: T-05, T-18

Replace stocks entry in `InvestmentGridDrawers`. Remove the `equityRecordVariant` prop from the grid (it's now encoded in the config).

**Acceptance criteria**:
- [ ] Grid Stocks entry opens step 1 with B3 search
- [ ] Submit creates transaction

---

## Phase 5 — FIIs Class

### T-20: Create `reits.config.ts` and register

**Creates**: `transaction-editor/configs/reits.config.ts`
**Modifies**: `transaction-editor/configs/index.ts`
**Depends on**: T-16, T-17

Identical structure to `stocks.config.ts` except:
- `assetTypeFilter: [AssetType.RealEstateFund, AssetType.AgribusinessFund]`
- Labels use "Comprar / Vender FII"

```typescript
const ReitsStep1 = (props: Step1ScreenProps) =>
  <AssetStep1 {...props} assetTypeFilter={[AssetType.RealEstateFund, AssetType.AgribusinessFund]} />
```

**Acceptance criteria**:
- [ ] `assetTypeFilter` returns only FII/CRI types
- [ ] Shares `EquityIdentityBlock`, `EquityFormFields`, `EquityCostFields` — no duplication

---

### T-21: Wire `TransactionEditor` for reits

**Modifies**: `InvestmentGridDrawers.tsx`
**Depends on**: T-05, T-20

**Acceptance criteria**:
- [ ] Grid FIIs entry opens step 1 with FII-filtered B3 search
- [ ] Submit creates transaction

---

## Phase 6 — Crypto Class

### T-22: Create `CryptoStep1`

**Reads**: `RecordEquityHoldingDrawer.tsx` — find Crypto-specific fields (the `variant="mixed"` or `variant="crypto"` path)
**Creates**: `transaction-editor/step1/CryptoStep1.tsx`
**Depends on**: T-01

No search query — crypto assets are created manually. Renders a simple form:
- Asset name (required)
- Ticker / symbol (required)
- Network / blockchain (optional)
- "Confirmar" button → calls `onSelect({ id: crypto.randomUUID(), name, ticker, source: 'manual', assetType: AssetType.Crypto })`

**Acceptance criteria**:
- [ ] No GraphQL query
- [ ] "Confirmar" button is disabled until name + ticker filled
- [ ] Calls `onSelect` with `source: 'manual'`

---

### T-23: Create `CryptoIdentityBlock` and `CryptoFormFields`

**Reads**: `RecordEquityHoldingDrawer.tsx` — find the crypto-specific form path
**Creates**:
- `transaction-editor/identity/CryptoIdentityBlock.tsx`
- `transaction-editor/form/CryptoFormFields.tsx`

**Depends on**: T-01

**`CryptoIdentityBlock`**: ticker (large), asset name, network badge if provided. "Alterar" action present.

**`CryptoFormFields`** (Section B):
- Direction toggle (Comprar / Vender) — hidden when locked
- Trade date
- Quantity (required)
- Unit price (required)
- No settlement date, no price factor, no cost fields

**Acceptance criteria**:
- [ ] No corretagem/cost fields
- [ ] No settlement date

---

### T-24: Create `crypto.config.ts` and register

**Creates**: `transaction-editor/configs/crypto.config.ts`
**Modifies**: `transaction-editor/configs/index.ts`
**Depends on**: T-22, T-23

`CostFields: null` (no transaction costs for crypto).

**Acceptance criteria**:
- [ ] `CostFields: null` — no "Mais detalhes" toggle rendered

---

### T-25: Wire `TransactionEditor` for crypto

**Modifies**: `InvestmentGridDrawers.tsx`
**Depends on**: T-05, T-24

Crypto currently uses `recordDrawer === 'equity'` with `equityRecordVariant === 'mixed'` or similar. Replace with a dedicated `crypto` entry.

**Acceptance criteria**:
- [ ] Grid Crypto entry opens step 1 (CryptoStep1 free-text form)
- [ ] Submit creates transaction

---

## Phase 7 — Entry Point Migration

> At this point, `TransactionEditor` handles all five classes from the grid. Now refactor `InvestmentHoldingDetailDrawer` into a unified tabbed drawer (Resumo + Movimentações), wire all holding-level entry points, and remove legacy components. **There is no `HoldingManagementDrawer` — the Movimentações tab replaces it entirely.**

### T-26: Refactor `InvestmentHoldingDetailDrawer` — add Movimentações tab

**Reads**:
- `InvestmentHoldingDetailDrawer.tsx` — **read the full file first**; understand all existing tabs/sections and the GraphQL query that fetches holding transactions (reuse it — do not create a new query)
- `transaction-editor/intent.ts` — `EditIntent`, `RowBuyIntent`, `mapHoldingToResolvedAsset`
- `investmentTableHelpers.ts` — `HoldingRow` type
- `invistus/docs/07-frontend/patterns/form-pattern.md`
- `InvestmentTable.tsx` — `TransactionEditor` is mounted here; editor intent state must propagate from detail drawer up to table level

**Modifies**:
- `components/InvestmentHoldingDetailDrawer.tsx` — primary change
- `components/table/InvestmentTable.tsx` — mount `TransactionEditor`; add editor intent state; wire handlers down to detail drawer

**Does NOT create** a standalone `HoldingManagementDrawer.tsx`. The Movimentações tab replaces it.

**Depends on**: T-05, T-10, T-14, T-18, T-20, T-24

---

#### Step 1 — Find and verify the transactions query

Before any UI work:
```bash
grep -n "useQuery\|useLazyQuery\|INVESTMENT_TRANSACTIONS\|investmentTransactions" \
  web/src/app/personal-finance/investments/components/InvestmentHoldingDetailDrawer.tsx
```
Reuse the query. Verify it returns per-transaction fields: `id`, `direction`, `tradeDate`, `quantity`, `unitPrice`, `netValue`. If any fields are missing, add them to the selection — do not create a new query.

---

#### Step 2 — Verify `DELETE_INVESTMENT_TRANSACTION` mutation

```bash
grep -rn "DELETE_INVESTMENT_TRANSACTION\|deleteInvestmentTransaction" \
  web/src/lib/graphql --include="*.ts" --include="*.tsx"
```
- If found: import and use it.
- If not found: define it in the investment mutations file:
```typescript
export const DELETE_INVESTMENT_TRANSACTION = gql`
  mutation DeleteInvestmentTransaction($id: String!) {
    deleteInvestmentTransaction(id: $id)
  }
`
// TODO: confirm exact mutation name and return type against GraphQL schema
```

---

#### Step 3 — Add tabs to `InvestmentHoldingDetailDrawer`

Change the drawer to `size="large"` (DEC-12). Add tab state and wrap existing content in "Resumo" tab. Add "Movimentações" tab.

Accept new prop: `initialTab?: 'summary' | 'transactions'` (defaults to `'summary'`).

Accept new prop: `onOpenTransactionEditor: (intent: RowBuyIntent | EditIntent) => void` — the table-level handler that sets the `TransactionEditor` intent state.

```tsx
<Drawer open={open} onClose={onClose} size="large">
  <Drawer.Header title={holding?.asset?.name ?? ''} onClose={onClose} />
  <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)}>
    <Tab label="Resumo" value="summary" />
    <Tab label="Movimentações" value="transactions" />
  </Tabs>
  {activeTab === 'summary' && <ExistingSummaryContent />}
  {activeTab === 'transactions' && (
    <MovimentacoesTab
      holdingId={holdingId}
      holding={holding}
      transactions={transactions}
      onAddTransaction={() =>
        onOpenTransactionEditor({ kind: 'row-buy', assetClass, holdingId, asset })
      }
      onEditTransaction={(txId) =>
        onOpenTransactionEditor({ kind: 'edit', assetClass, holdingId, transactionId: txId, asset })
      }
    />
  )}
</Drawer>
```

---

#### Step 4 — Implement `MovimentacoesTab`

Create as a local sub-component: `InvestmentHoldingDetailDrawer/MovimentacoesTab.tsx`.

**Layout**:
1. Header row: label left, "Nova transação" `+` button right
2. Transaction table

**Transaction table columns**: date | direction (Compra / Venda / Aporte / Resgate) | quantity | unit price | net value | edit icon | delete icon

**Edit icon**: calls `onEditTransaction(tx.id)` which propagates to `onOpenTransactionEditor` above.

**Delete icon**: opens local `ConfirmationDialog` state:
```tsx
const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null)

<ConfirmationDialog
  open={Boolean(confirmDeleteId)}
  title={t('investments.deleteTransaction.title')}
  message={t('investments.deleteTransaction.message')}
  onClose={() => setConfirmDeleteId(null)}
  onConfirm={async () => {
    await deleteTransaction({ variables: { id: confirmDeleteId! } })
    setConfirmDeleteId(null)
  }}
  confirmColor="error"
/>
```
Mutation: `DELETE_INVESTMENT_TRANSACTION`. `refetchQueries`: `['InvestmentHoldings', 'InvestmentCashBalance', 'InvestmentPortfolioSummary']`.

---

#### Step 5 — Wire `TransactionEditor` at `InvestmentTable` level

`InvestmentTable` already mounts `TransactionEditor` in the current (shim) version. Extend this to handle intents from the detail drawer:

```tsx
// In InvestmentTable.tsx — add to useInvestmentTableDrawers or local state
const [transactionEditorIntent, setTransactionEditorIntent] =
  useState<RowBuyIntent | RowSellIntent | EditIntent | null>(null)

// Pass handler to detail drawer
<InvestmentHoldingDetailDrawer
  // ... existing props ...
  onOpenTransactionEditor={setTransactionEditorIntent}
/>

// Mount TransactionEditor — after InvestmentHoldingDetailDrawer in the DOM
// so MUI Modal z-index stacks it on top automatically
<TransactionEditor
  open={Boolean(transactionEditorIntent)}
  intent={transactionEditorIntent}
  financialAccountId={selectedAccountId}
  financialAccounts={investmentAccounts}
  onClose={() => setTransactionEditorIntent(null)}
  onRecorded={refreshInvestmentData}
/>
```

The detail drawer stays open when `TransactionEditor` opens — MUI Modal stack handles z-index.

---

**Acceptance criteria**:
- [ ] Detail drawer has two tabs: "Resumo" and "Movimentações"
- [ ] "Resumo" tab content is unchanged
- [ ] Drawer uses `size="large"`
- [ ] `DELETE_INVESTMENT_TRANSACTION` mutation verified to exist before delete is wired
- [ ] Transactions query reused — no new GraphQL operation
- [ ] "Movimentações" tab shows all transactions sorted by trade date descending
- [ ] "+" button opens `TransactionEditor` with `row-buy` intent (Step 2 directly)
- [ ] Edit icon opens `TransactionEditor` with `edit` intent
- [ ] Delete icon shows `ConfirmationDialog`; on confirm deletes and refreshes
- [ ] `TransactionEditor` layers on top of the open detail drawer (both visible simultaneously)
- [ ] `initialTab` prop works — callers can open directly at "Movimentações"

---

### T-27: Wire row hover "+"/"-" buttons and clean up legacy row actions

**Reads**: `table/HoldingsGroupsSection.tsx` or `table/HoldingRowActionsColumn.tsx` (wherever row action buttons are rendered), `InvestmentTable.tsx`, `table/InvestmentHoldingActionsMenu.tsx`
**Modifies**: row action column component, `InvestmentTable.tsx`, `InvestmentHoldingActionsMenu.tsx`
**Depends on**: T-26

#### Holding row changes

Add hover-visible "+" and "−" buttons to each holding row (FR-9):

```tsx
// Inside the row actions column — visibility controlled by hover
<Box sx={{ display: 'flex', gap: 0.5, visibility: 'hidden', 'tr:hover &, &:focus-within': { visibility: 'visible' } }}>
  <IconButton
    size="small"
    aria-label={t('investments.row.addTransaction')}
    onClick={() => onRowBuy(holding)}
  >
    <AddIcon fontSize="small" />
  </IconButton>
  <IconButton
    size="small"
    aria-label={t('investments.row.sellTransaction')}
    onClick={() => onRowSell(holding)}
  >
    <RemoveIcon fontSize="small" />
  </IconButton>
</Box>
```

Wire at `InvestmentTable` level:
```tsx
// onRowBuy / onRowSell call setTransactionEditorIntent (already added in T-26)
onRowBuy={(holding) => setTransactionEditorIntent({
  kind: 'row-buy',
  assetClass: mapAssetTypeToTransactionEditorClass(holding.asset?.assetType),
  holdingId: holding.id,
  asset: mapHoldingToResolvedAsset(holding)!,
})}
onRowSell={(holding) => setTransactionEditorIntent({
  kind: 'row-sell',
  assetClass: mapAssetTypeToTransactionEditorClass(holding.asset?.assetType),
  holdingId: holding.id,
  asset: mapHoldingToResolvedAsset(holding)!,
  maxQty: holding.quantity,
})}
```

#### Actions menu changes

Remove "Gerenciar movimentações" menu item from `InvestmentHoldingActionsMenu` (DEC-11). The detail drawer's Movimentações tab (T-26) replaces it.

#### Verify no old paths remain

```bash
grep -rn "InvestmentEventSelectorDrawer\|openEventSelector\|Gerenciar movimentações" \
  web/src/app/personal-finance/investments --include="*.tsx" --include="*.ts"
```
Must return zero results.

**Acceptance criteria**:
- [ ] "+" and "−" buttons appear on row hover only; invisible at rest
- [ ] "+" opens `TransactionEditor` at Step 2 with `row-buy` intent, asset pre-filled
- [ ] "−" opens `TransactionEditor` at Step 2 with `row-sell` intent, `maxQty` = current holding quantity
- [ ] "Gerenciar movimentações" removed from actions menu
- [ ] Row click / "Visualizar" still opens detail drawer at "Resumo" tab
- [ ] `InvestmentEventSelectorDrawer` references: zero

---

### T-28: Remove legacy drawers and clean up

**Reads**: all files that import the legacy drawers
**Deletes**:
- `RecordFixedIncomeDrawer.tsx`
- `RecordEquityHoldingDrawer.tsx`
- `RecordFundHoldingDrawer.tsx`
- `InvestmentEventSelectorDrawer.tsx`

**Modifies**:
- `InvestmentGridDrawers.tsx` — remove all four legacy drawer imports and usages
- `investmentGridConfig.ts` — remove `GridRecordDrawer` type, `EquityRecordVariant` type, `equityRecordVariantForGridClass()` function; replace with `InvestmentAssetClass` mapping
- `InvestmentEditDrawers.tsx` — audit for any remaining references to legacy drawers
- Holding row action component — confirm `+`/`−` buttons are gone (done in T-27; verify no stale references)

**Before deleting each file**: run:
```bash
grep -r "RecordFixedIncomeDrawer\|RecordEquityHoldingDrawer\|RecordFundHoldingDrawer\|InvestmentEventSelectorDrawer" \
  --include="*.tsx" --include="*.ts" .
```
Verify zero results outside the files being deleted.

**Acceptance criteria**:
- [ ] Zero TypeScript compilation errors after deletion
- [ ] `grep` for legacy drawer names returns no results
- [ ] `GridRecordDrawer` type is gone from `investmentGridConfig.ts`
- [ ] No row `+`/`−` button references remain

---

### T-29: Verify "+ Investimento" wiring and clean up dead prop

**Reads**: `InvestmentGridTabsPanel.tsx`, `InvestmentGridCardMatrix.tsx`, `InvestmentGridDrawers.tsx`, `InvestmentTable.tsx`
**Modifies**: `InvestmentTable.tsx`
**Depends on**: T-28

Both "+" buttons are already wired correctly through `openRecordDrawerForClass(key)` → `InvestmentGridDrawers` → `TransactionEditor` with `{ kind: "new-buy", assetClass: key }`. No button changes are needed. The only cleanup required:

1. Remove the dead `onRecordHolding?: () => void` prop from `InvestmentTable`'s interface — it is never passed by any caller and never used inside the component.

**Acceptance criteria**:
- [ ] `onRecordHolding` prop removed from `InvestmentTable` interface
- [ ] Grid card "+ Investimento" button opens `TransactionEditor` Step 1 for the appropriate class
- [ ] Tab panel "+ Investimento" button opens the asset-class popover then `TransactionEditor` Step 1
- [ ] `RecordCashDrawer` still opens for the "cash" class (unchanged)

---

## Phase 8 — Validation

### T-30: End-to-end smoke test pass

**No code changes** — verification only.

Run through all scenarios documented in `invistus/docs/03-features/investment-portfolio/tasks/holding-transaction-scenarios.md`.

**Quick checklist**:
- [ ] All scenarios in the scenario matrix pass without console errors
- [ ] Cache refreshes after every create / update / delete
- [ ] Row hover "+"/"-" buttons visible on hover and functional for all classes
- [ ] Detail drawer "Movimentações" tab shows correct transaction list for all classes
- [ ] `TransactionEditor` layers on top of detail drawer (both visible simultaneously) without z-index issues
- [ ] No `RecordFixedIncomeDrawer`, `RecordEquityHoldingDrawer`, `RecordFundHoldingDrawer`, or `InvestmentEventSelectorDrawer` remain
- [ ] No standalone `HoldingManagementDrawer` component in the codebase
- [ ] `tsc --noEmit` passes with zero errors

---

## Appendix: Key File Locations

```
# Source (read these before implementing)
web/src/app/personal-finance/investments/components/RecordFixedIncomeDrawer.tsx
web/src/app/personal-finance/investments/components/RecordEquityHoldingDrawer.tsx
web/src/app/personal-finance/investments/components/RecordFundHoldingDrawer.tsx
web/src/app/personal-finance/investments/components/InvestmentEventSelectorDrawer.tsx
web/src/app/personal-finance/investments/components/InvestmentHoldingDetailDrawer.tsx
web/src/app/personal-finance/investments/components/grid/InvestmentGridDrawers.tsx
web/src/app/personal-finance/investments/components/grid/investmentGridConfig.ts
web/src/app/personal-finance/investments/components/table/useInvestmentTableDrawers.ts
web/src/app/personal-finance/investments/components/table/investmentTableHelpers.ts

# Patterns (read before writing any form or hook)
invistus/docs/07-frontend/patterns/form-pattern.md
invistus/docs/07-frontend/patterns/graphql-mutation-pattern.md

# Spec
invistus/docs/03-features/investment-portfolio/holding-transaction-editor-refactor.md

# New files — transaction editor
web/src/app/personal-finance/investments/components/transaction-editor/
  types.ts
  config.types.ts
  TransactionEditor.tsx
  TransactionEditorContext.tsx
  hooks/useTransactionEditor.ts
  configs/index.ts
  configs/funds.config.ts
  configs/fixedIncome.config.ts
  configs/stocks.config.ts
  configs/reits.config.ts
  configs/crypto.config.ts
  step1/FundStep1.tsx
  step1/AssetStep1.tsx
  step1/CryptoStep1.tsx
  identity/FundIdentityBlock.tsx
  identity/FixedIncomeIdentityBlock.tsx
  identity/EquityIdentityBlock.tsx
  identity/CryptoIdentityBlock.tsx
  form/FundFormFields.tsx
  form/FixedIncomeFormFields.tsx
  form/FixedIncomeCostFields.tsx
  form/EquityFormFields.tsx
  form/EquityCostFields.tsx
  form/CryptoFormFields.tsx
  utils.ts                              ← holdingToResolvedAsset helper

# New files — holding management
web/src/app/personal-finance/investments/components/
  HoldingManagementDrawer.tsx           ← new (T-26)
  HoldingManagementDrawer/
    TransactionHistoryTable.tsx         ← sub-component (T-26)
    PositionSummaryStrip.tsx            ← sub-component (T-26)

# Kept (visualization — read-only, modified only to add nav CTAs)
web/src/app/personal-finance/investments/components/InvestmentHoldingDetailDrawer.tsx
```
