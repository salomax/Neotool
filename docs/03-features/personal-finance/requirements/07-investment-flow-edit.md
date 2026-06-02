# Investment Flow Edit — Requirements Document

**Service:** Invistus Banking Service  
**Module:** Personal Finance — Investments  
**Document date:** 2026-04-18  
**Status:** Ready for implementation

---

## 1. Overview

Investment holdings display a timeline of inflow/outflow events (compras, vendas, resgates, dividendos, juros de cupom). Currently these events are **read-only** — there is no way to correct a manually entered transaction from the detail drawer.

This document specifies the complete UI/UX and implementation work to add **edit** and **delete** actions to investment flow transactions directly from the holding detail drawer, for all three holding kinds: fixed income, fund, and equity.

> **Scope boundary:** Only `source = MANUAL` transactions are editable. Transactions sourced from Open Finance (`OPEN_FINANCE`) or file imports (`FILE_IMPORT`) are read-only and display a tooltip explaining why.

---

## 2. Problem

- A user who records a purchase with the wrong price, quantity, or date cannot correct it.
- The backend already supports `updateInvestmentTransaction` and `deleteInvestmentTransaction` mutations for MANUAL transactions.
- The edit drawers (`RecordEquityHoldingDrawer`, `RecordFixedIncomeDrawer`, `RecordFundHoldingDrawer`) already have full edit-mode support (props `editHolding` + `editTransaction`).
- The `InvestmentEditDrawers` component already routes edit calls to the right drawer.
- The gap is **purely UI**: no action is surfaced on timeline event cards.

---

## 3. User Stories

**US-1 — Edit manual flow**  
As a user who manually recorded an investment transaction with wrong data, I want to click a menu on the timeline event card and edit the transaction so that my portfolio reflects the correct figures.

**US-2 — Delete manual flow**  
As a user who manually recorded a transaction that should not exist, I want to delete it so it is removed from the timeline and the holding is recalculated.

**US-3 — Read-only indicator for synced flows**  
As a user browsing synced transactions, I want to understand at a glance that I cannot edit them so I don't waste time trying.

---

## 4. Acceptance Criteria

| # | Criterion |
|---|---|
| AC-1 | A kebab menu (⋮) appears on every timeline event card in the holding detail drawer |
| AC-2 | For `source = MANUAL`: menu shows "Editar" and "Excluir" as active items |
| AC-3 | For `source ≠ MANUAL`: menu shows "Editar" and "Excluir" as disabled, with a tooltip "Sincronizado automaticamente — não pode ser editado" |
| AC-4 | Tapping "Editar" opens the correct edit drawer pre-filled with the transaction's current values |
| AC-5 | On save, the holding detail drawer refetches its data to reflect updated figures |
| AC-6 | Tapping "Excluir" opens a confirmation dialog showing the transaction summary |
| AC-7 | On confirm delete, the transaction is removed and the holding data refetches |
| AC-8 | The coupon schedule section (fixed income only) is NOT affected — it has its own edit/delete flow which must remain intact |
| AC-9 | On mobile the kebab button is always visible; on desktop it is visible on hover |

---

## 5. UI/UX Specification

### 5.1 Timeline Event Card — Kebab Menu

Add a `MoreVertIcon` `IconButton` to each `TimelineEventCard`. Visual placement: top-right corner of the card, outside the content flow.

```
┌─────────────────────────────────────────────┐
│ Compra · 14 mar 2025                    [⋮] │
│ PETR4 — Petróleo Brasileiro             │
│ Liq. 16 mar · IR R$ 0,00               │
│                              R$ 3.200,00 │
└─────────────────────────────────────────────┘
```

The `[⋮]` button:
- Desktop: `visibility: hidden` by default, `visible` on `Paper` hover (mirror `HoldingRowActionsColumn` pattern)
- Mobile: always visible

On click, opens a MUI `Menu` anchored to the button with two items:
- **Editar** (`EditIcon`) — disabled + tooltip when not MANUAL
- **Excluir** (`DeleteOutlineIcon`, `color: error.main`) — disabled + tooltip when not MANUAL

### 5.2 Edit Drawer

Opens the appropriate pre-existing record drawer in edit mode:
- Kind `fixedIncome` → `RecordFixedIncomeDrawer` with `editHolding` + `editTransaction`
- Kind `fund` → `RecordFundHoldingDrawer` with `editHolding` + `editTransaction`
- Kind `equity` → `RecordEquityHoldingDrawer` with `editHolding` + `editTransaction`

The drawers already handle pre-population, validation, and the `updateInvestmentTransaction` mutation call internally. No changes required to the drawer components themselves.

### 5.3 Delete Confirmation Dialog

A MUI `Dialog` (not inline delete) with:
- **Title:** "Excluir transação?"
- **Body:** Shows direction label + date + gross value formatted as currency (e.g. "Compra · 14 mar 2025 · R$ 3.200,00"). Adds a warning: "Isso irá recalcular o custo médio e o histórico do ativo."
- **Actions:** `[Cancelar]` (secondary) and `[Excluir]` (error color, calls mutation)
- Loading state on the confirm button during deletion

---

## 6. Technical Implementation

### 6.1 Files to Modify

| File | Change |
|---|---|
| `TimelineEvents.tsx` | Extend `TimelineEventsItem` with `source` + `isEditable`; add action callbacks to `TimelineEventsProps`; render kebab menu on cards |
| `fixedIncomeHoldingDetailAdapter.ts` | Pass `source` + `id` (already present) through to `FixedIncomeTimelineItemViewModel` |
| `fundHoldingDetailAdapter.ts` | Pass `source` through to `FundTimelineItemViewModel` |
| `FixedIncomeHoldingDetailContent.tsx` | Accept + forward `onEditTransaction` / `onDeleteTransaction` callbacks |
| `FundHoldingDetailContent.tsx` | Accept + forward `onEditTransaction` / `onDeleteTransaction` callbacks |
| `InvestmentHoldingDetailDrawer.tsx` | Host edit/delete state; instantiate edit drawers + delete dialog |
| `fixed-income/api/queries.ts` | No change needed — `source` already fetched |
| `fund/api/queries.ts` | No change needed — `source` already fetched |

### 6.2 Step 1 — Extend `TimelineEventsItem` and `TimelineEventsProps`

**File:** `web/src/app/personal-finance/investments/components/TimelineEvents.tsx`

Add two optional fields to `TimelineEventsItem`:

```ts
export interface TimelineEventsItem {
  id: string;
  title: string;
  tradeDateLabel: string;
  settlementDateLabel?: string | null;
  amount: number;
  netAmount?: number | null;
  incomeTax?: number | null;
  iof?: number | null;
  direction: TimelineDirection;
  // NEW:
  source?: 'MANUAL' | 'OPEN_FINANCE' | 'FILE_IMPORT';
  isEditable?: boolean; // convenience flag: source === 'MANUAL'
}
```

Add action callbacks to `TimelineEventsProps`:

```ts
export interface TimelineEventsProps {
  // ... existing props ...
  // NEW optional action callbacks — when omitted, no kebab menu is rendered:
  onEditTransaction?: (transactionId: string) => void;
  onDeleteTransaction?: (transactionId: string) => void;
  /** Tooltip shown on disabled menu items for non-manual transactions */
  notEditableTooltip?: string;
}
```

Pass these down through `TimelineEventRow` → `TimelineEventCard`.

### 6.3 Step 2 — Kebab Menu in `TimelineEventCard`

**File:** `web/src/app/personal-finance/investments/components/TimelineEvents.tsx`

Inside `TimelineEventCard`, add:

```tsx
// State for menu anchor
const [menuAnchor, setMenuAnchor] = React.useState<null | HTMLElement>(null);

// Hover state (for desktop visibility)
const [hovered, setHovered] = React.useState(false);
```

Wrap the existing `Paper` content, adding:
1. `onMouseEnter` / `onMouseLeave` on the `Paper` to control `hovered`
2. An `IconButton` (MoreVertIcon) in the top-right corner, positioned absolutely or via flex layout
3. A MUI `Menu` with two `MenuItem` entries

```tsx
{(onEditTransaction || onDeleteTransaction) && (
  <>
    <IconButton
      size="small"
      onClick={(e) => { e.stopPropagation(); setMenuAnchor(e.currentTarget); }}
      aria-label="Ações da transação"
      sx={{
        position: 'absolute',
        top: 4,
        right: 4,
        opacity: hovered || Boolean(menuAnchor) ? 1 : { xs: 1, md: 0 },
        transition: 'opacity 0.15s',
      }}
    >
      <MoreVertIcon fontSize="small" />
    </IconButton>

    <Menu
      anchorEl={menuAnchor}
      open={Boolean(menuAnchor)}
      onClose={() => setMenuAnchor(null)}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      transformOrigin={{ vertical: 'top', horizontal: 'right' }}
    >
      <Tooltip
        title={!item.isEditable ? (notEditableTooltip ?? '') : ''}
        placement="left"
        disableHoverListener={item.isEditable}
      >
        <span> {/* span wrapper needed for Tooltip on disabled MenuItem */}
          <MenuItem
            disabled={!item.isEditable}
            onClick={() => { setMenuAnchor(null); onEditTransaction?.(item.id); }}
          >
            <ListItemIcon><EditIcon fontSize="small" /></ListItemIcon>
            <ListItemText>Editar</ListItemText>
          </MenuItem>
        </span>
      </Tooltip>

      <Tooltip
        title={!item.isEditable ? (notEditableTooltip ?? '') : ''}
        placement="left"
        disableHoverListener={item.isEditable}
      >
        <span>
          <MenuItem
            disabled={!item.isEditable}
            onClick={() => { setMenuAnchor(null); onDeleteTransaction?.(item.id); }}
            sx={{ color: 'error.main' }}
          >
            <ListItemIcon sx={{ color: 'inherit' }}>
              <DeleteOutlineIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>Excluir</ListItemText>
          </MenuItem>
        </span>
      </Tooltip>
    </Menu>
  </>
)}
```

The `Paper` needs `position: 'relative'` added to its sx for the absolute positioning of the icon button to work.

**Imports to add:**
- `MoreVertIcon` from `@mui/icons-material/MoreVert`
- `EditIcon` from `@mui/icons-material/Edit`
- `DeleteOutlineIcon` from `@mui/icons-material/DeleteOutline`
- `Menu`, `MenuItem`, `ListItemIcon`, `ListItemText`, `Tooltip` from `@mui/material`

### 6.4 Step 3 — Pass `source` through the Adapters

**File:** `fixedIncomeHoldingDetailAdapter.ts`

`FixedIncomeTimelineItemViewModel` already has `id`. Add `source`:

```ts
export interface FixedIncomeTimelineItemViewModel {
  // ... existing fields ...
  source: 'MANUAL' | 'OPEN_FINANCE' | 'FILE_IMPORT' | null;
}
```

In the adapter function that maps transactions to timeline items, populate this field:

```ts
source: tx.source ?? null,
```

The raw transaction already has `source` from the GraphQL query — it's typed as `InvestmentSource` enum.

**File:** `fundHoldingDetailAdapter.ts`

Same change — add `source` to `FundTimelineItemViewModel` and populate from `tx.source`.

### 6.5 Step 4 — Map `source` to `TimelineEventsItem` in Content Components

**File:** `FixedIncomeHoldingDetailContent.tsx`

When building the `items` array passed to `<TimelineEvents>`, map `viewModel.timelineItems` and add:

```ts
items={viewModel.timelineItems.map(item => ({
  ...item,
  source: item.source ?? undefined,
  isEditable: item.source === 'MANUAL',
}))}
```

Also accept and forward callbacks:

```tsx
interface FixedIncomeHoldingDetailContentProps {
  // ... existing props ...
  onEditTransaction?: (transactionId: string) => void;
  onDeleteTransaction?: (transactionId: string) => void;
}
```

Pass them to `<TimelineEvents>`:

```tsx
<TimelineEvents
  // ... existing props ...
  onEditTransaction={onEditTransaction}
  onDeleteTransaction={onDeleteTransaction}
  notEditableTooltip={t('investments.detailDrawer.timeline.notEditableTooltip')}
/>
```

**File:** `FundHoldingDetailContent.tsx`

Identical changes — add the two optional callback props and forward to `<TimelineEvents>`.

### 6.6 Step 5 — Wire Edit + Delete State in `InvestmentHoldingDetailDrawer`

**File:** `InvestmentHoldingDetailDrawer.tsx`

#### 6.6.1 New state variables

```ts
// Edit state
const [editingTransactionId, setEditingTransactionId] = React.useState<string | null>(null);

// Delete state
const [deletingTransactionId, setDeletingTransactionId] = React.useState<string | null>(null);
const [deleteConfirmOpen, setDeleteConfirmOpen] = React.useState(false);
const [deleteInProgress, setDeleteInProgress] = React.useState(false);
```

#### 6.6.2 Handlers

```ts
const handleEditTransaction = React.useCallback((transactionId: string) => {
  setEditingTransactionId(transactionId);
}, []);

const handleRequestDeleteTransaction = React.useCallback((transactionId: string) => {
  setDeletingTransactionId(transactionId);
  setDeleteConfirmOpen(true);
}, []);

const handleConfirmDeleteTransaction = React.useCallback(async () => {
  if (!deletingTransactionId) return;
  setDeleteInProgress(true);
  try {
    await deleteInvestmentTransaction({ variables: { id: deletingTransactionId } });
    toast.success(t('investments.detailDrawer.timeline.deleteSuccess'));
    // Refetch based on kind
    if (kind === 'fixedIncome') void fixedIncomeQuery.refetch();
    else void fundQuery.refetch();
  } finally {
    setDeleteInProgress(false);
    setDeleteConfirmOpen(false);
    setDeletingTransactionId(null);
  }
}, [deletingTransactionId, deleteInvestmentTransaction, fixedIncomeQuery, fundQuery, kind, t, toast]);
```

> Note: `deleteInvestmentTransaction` mutation is already imported and used in this component for coupon deletions (line ~509). Reuse the existing `useMutation` hook instance — do not declare a second one.

#### 6.6.3 Resolve the transaction object for the edit drawer

The edit drawers need an `InvestmentTransaction` object. Build it from the already-fetched transaction list. Add a helper:

```ts
const editingTransaction = React.useMemo<InvestmentTransaction | null>(() => {
  if (!editingTransactionId) return null;
  const txs =
    kind === 'fixedIncome'
      ? (fixedIncomeData?.investmentTransactions ?? [])
      : (fundData?.investmentTransactions ?? []);
  return (txs.find((tx) => tx.id === editingTransactionId) as InvestmentTransaction | undefined) ?? null;
}, [editingTransactionId, kind, fixedIncomeData?.investmentTransactions, fundData?.investmentTransactions]);
```

Note: The transaction objects returned by the detail queries are partial (they only include fields selected in the GraphQL query). Cross-check that each edit drawer only reads fields that are already selected. See the full field list needed per drawer in section 6.7 below.

#### 6.6.4 Build `editHolding` shapes for each kind

**Fixed income:**
```ts
const fixedIncomeEditHolding = React.useMemo(() => {
  const h = fixedIncomeData?.investmentHolding;
  if (!h) return null;
  return {
    id: h.id,
    asset: {
      id: h.asset.id,
      name: h.asset.name,
      assetType: h.asset.assetType,
      issuerName: h.asset.issuerName ?? null,
    },
    indexer: h.indexer ?? null,
    indexerPercentage: h.indexerPercentage ?? null,
    fixedAnnualRate: h.fixedAnnualRate ?? null,
    maturityDate: h.maturityDate ?? null,
    isTaxExempt: h.isTaxExempt,
    couponFrequency: h.couponFrequency ?? null,
    couponDayOfMonth: h.couponDayOfMonth ?? null,
  };
}, [fixedIncomeData?.investmentHolding]);
```

**Fund:**
```ts
const fundEditHolding = React.useMemo(() => {
  const h = fundData?.investmentHolding;
  if (!h) return null;
  return {
    id: h.id,
    asset: {
      id: h.asset.id,
      name: h.asset.name,
      assetType: h.asset.assetType as AssetType,
    },
    pricingMode: null,  // not available in detail query — acceptable default
    valuationReferenceDate: h.valuationReferenceDate ?? null,
    grossValue: h.grossValue ?? null,
    netValue: h.netValue ?? null,
  };
}, [fundData?.investmentHolding]);
```

**Equity** — equity holdings use the same `FundHoldingDetail` query (kind `equity` maps to `fundQuery`). The `RecordEquityHoldingDrawer` needs:
```ts
const equityEditHolding = React.useMemo(() => {
  const h = fundData?.investmentHolding;
  if (!h) return null;
  return {
    id: h.id,
    asset: {
      id: h.asset.id,
      ticker: null, // ticker not in detail query — RecordEquityHoldingDrawer handles null ticker
      name: h.asset.name,
      assetType: h.asset.assetType as AssetType,
    },
  };
}, [fundData?.investmentHolding]);
```

#### 6.6.5 Mount the edit drawers

Add inside the JSX of `InvestmentHoldingDetailDrawer`, alongside the existing `MaturityConfirmDrawer` and `CouponPaymentConfirmDrawer`:

```tsx
{/* Edit drawers — only when an editingTransaction is resolved */}
<RecordFixedIncomeDrawer
  open={kind === 'fixedIncome' && Boolean(editingTransactionId) && Boolean(editingTransaction)}
  onClose={() => setEditingTransactionId(null)}
  financialAccountId={fixedIncomeData?.investmentHolding?.financialAccount?.id ?? null}
  financialAccounts={[]}
  editHolding={fixedIncomeEditHolding}
  editTransaction={editingTransaction}
  onRecorded={async () => {
    setEditingTransactionId(null);
    await fixedIncomeQuery.refetch();
  }}
/>

<RecordFundHoldingDrawer
  open={(kind === 'fund') && Boolean(editingTransactionId) && Boolean(editingTransaction)}
  onClose={() => setEditingTransactionId(null)}
  financialAccountId={fundData?.investmentHolding?.financialAccount?.id ?? null}
  financialAccounts={[]}
  editHolding={fundEditHolding}
  editTransaction={editingTransaction}
  onRecorded={async () => {
    setEditingTransactionId(null);
    await fundQuery.refetch();
  }}
/>

<RecordEquityHoldingDrawer
  open={(kind === 'equity') && Boolean(editingTransactionId) && Boolean(editingTransaction)}
  onClose={() => setEditingTransactionId(null)}
  financialAccountId={fundData?.investmentHolding?.financialAccount?.id ?? null}
  financialAccounts={[]}
  editHolding={equityEditHolding}
  editTransaction={editingTransaction}
  onRecorded={async () => {
    setEditingTransactionId(null);
    await fundQuery.refetch();
  }}
/>
```

#### 6.6.6 Delete Confirmation Dialog

Add a new component inline or extract as `DeleteTransactionDialog`. Use MUI `Dialog`:

```tsx
{/* Delete confirmation dialog */}
<Dialog open={deleteConfirmOpen} onClose={() => !deleteInProgress && setDeleteConfirmOpen(false)}>
  <DialogTitle>Excluir transação?</DialogTitle>
  <DialogContent>
    <Typography variant="body2">
      {deletingTransactionSummary /* see below */}
    </Typography>
    <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
      Isso irá recalcular o custo médio e o histórico do ativo.
    </Typography>
  </DialogContent>
  <DialogActions>
    <Button
      onClick={() => setDeleteConfirmOpen(false)}
      disabled={deleteInProgress}
    >
      Cancelar
    </Button>
    <Button
      color="error"
      variant="contained"
      onClick={handleConfirmDeleteTransaction}
      disabled={deleteInProgress}
      startIcon={deleteInProgress ? <CircularProgress size={16} /> : undefined}
    >
      Excluir
    </Button>
  </DialogActions>
</Dialog>
```

For `deletingTransactionSummary`, resolve the summary from the transaction list at the time `deletingTransactionId` is set. Store it in a ref or derived state:

```ts
const deletingTransactionSummary = React.useMemo<string>(() => {
  if (!deletingTransactionId) return '';
  const txs =
    kind === 'fixedIncome'
      ? (fixedIncomeData?.investmentTransactions ?? [])
      : (fundData?.investmentTransactions ?? []);
  const tx = txs.find((t) => t.id === deletingTransactionId);
  if (!tx) return '';
  const dirLabel = tx.direction === 'INFLOW' ? 'Entrada' : 'Saída';
  const dateLabel = tx.tradeDate ? String(tx.tradeDate) : '—';
  const valueLabel = new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: 'BRL',
  }).format(tx.grossValue);
  return `${dirLabel} · ${dateLabel} · ${valueLabel}`;
}, [deletingTransactionId, kind, fixedIncomeData?.investmentTransactions, fundData?.investmentTransactions, locale]);
```

#### 6.6.7 Forward callbacks to content components

In the `strategy` object inside `InvestmentHoldingDetailDrawer`, pass the callbacks into the body components:

**Fixed income:**
```tsx
body: (
  <FixedIncomeHoldingDetailContent
    // ... existing props ...
    onEditTransaction={handleEditTransaction}
    onDeleteTransaction={handleRequestDeleteTransaction}
  />
),
```

**Fund & Equity:**
```tsx
body: (
  <FundHoldingDetailContent
    // ... existing props ...
    onEditTransaction={handleEditTransaction}
    onDeleteTransaction={handleRequestDeleteTransaction}
  />
),
```

### 6.7 GraphQL Query Field Audit

Verify that the transaction fields consumed by each edit drawer are present in the detail queries.

**`RecordFixedIncomeDrawer` reads from `editTransaction`:**
- `id` ✅ selected
- `type` ✅ selected
- `direction` ✅ selected
- `quantity` ✅ selected
- `unitPrice` ✅ selected
- `grossValue` ✅ selected
- `netValue` ✅ selected
- `incomeTax` ✅ selected
- `iof` ✅ selected
- `tradeDate` ✅ selected
- `settlementDate` ✅ selected
- `indexerPercentage` ✅ selected
- `fixedAnnualRate` ✅ selected
- `source` ✅ selected

**`RecordFundHoldingDrawer` reads from `editTransaction`:**
- Same core fields — all ✅ selected in `FundHoldingDetail` query

**`RecordEquityHoldingDrawer` reads from `editTransaction`:**
- `brokerNote` — **NOT selected** in either detail query
- All other core fields ✅ selected

**Action required for equity editing:** Add `brokerNote` fields to the `FundHoldingDetail` query (used for equity kind). Add to `investmentTransactions` selection:

```graphql
brokerNote {
  brokerNoteNumber
  brokerageFee
  settlementFee
  otherFees
}
```

This change requires regenerating the GraphQL types (`codegen`).

If adding `brokerNote` to the fund detail query is undesirable in phase 1, equity edit support can be deferred: simply do not pass `onEditTransaction` to `FundHoldingDetailContent` when `kind === 'equity'`, or pass it but omit broker note fields (the drawer handles null broker note gracefully).

### 6.8 i18n Keys to Add

Add to the `personalFinanceTranslations` (PT-BR):

```json
"investments.detailDrawer.timeline.notEditableTooltip": "Sincronizado automaticamente — não pode ser editado",
"investments.detailDrawer.timeline.deleteSuccess": "Transação excluída com sucesso",
"investments.detailDrawer.timeline.deleteConfirmTitle": "Excluir transação?",
"investments.detailDrawer.timeline.deleteConfirmWarning": "Isso irá recalcular o custo médio e o histórico do ativo."
```

---

## 7. Scope Boundaries — What Is Out of Scope

The following are explicitly **not** part of this requirement:

- Editing coupon payments (`INTEREST_PAYMENT` type) — these already have their own flow via `CouponPaymentConfirmDrawer` in the coupon schedule section. Do not modify this.
- Editing holding contract terms (indexer, maturity date, etc.) separately from the first purchase — this goes through the existing edit drawer flow when editing the initial PURCHASE transaction.
- Bulk edit of multiple transactions.
- Adding new transactions from within the detail drawer (already possible from the main holdings table row actions).
- Editing `OPEN_FINANCE` or `FILE_IMPORT` transactions — these are read-only by design.
- Any backend changes — the API already supports all operations.

---

## 8. File Change Summary

| File (relative to `web/src/app/personal-finance/investments/`) | Type of change |
|---|---|
| `components/TimelineEvents.tsx` | Extend interfaces + add kebab menu + new imports |
| `components/fixed-income/fixedIncomeHoldingDetailAdapter.ts` | Add `source` to ViewModel + populate |
| `components/fund/fundHoldingDetailAdapter.ts` | Add `source` to ViewModel + populate |
| `components/fixed-income/FixedIncomeHoldingDetailContent.tsx` | New optional callback props + pass to TimelineEvents |
| `components/fund/FundHoldingDetailContent.tsx` | New optional callback props + pass to TimelineEvents |
| `components/InvestmentHoldingDetailDrawer.tsx` | New state + handlers + edit drawers + delete dialog |
| `components/fund/api/queries.ts` | Add `brokerNote` fields (equity edit, optional phase 2) |

---

## 9. Implementation Order

1. **`TimelineEvents.tsx`** — Extend types and add kebab menu. Can be done and tested in isolation by passing stub callbacks.
2. **Adapters** — Add `source` field. Pure data transformation, no UI risk.
3. **Content components** — Accept and forward callbacks. Minimal change.
4. **`InvestmentHoldingDetailDrawer.tsx`** — Wire all the state, drawers, and dialog. This is the largest change.
5. **i18n keys** — Add translation strings.
6. **GraphQL query update + codegen** — Only needed for equity broker note support (can be deferred).

---

## 10. Testing Notes

- After implementation, test the following scenarios manually:
  - **Fixed income:** Edit a PURCHASE transaction (change grossValue) → verify holding value updates in the summary cards
  - **Fund:** Edit an application (change quantity/amount) → verify the performance chart updates after refetch
  - **Equity:** Edit a buy transaction → verify average cost recalculates
  - **Delete:** Delete a PURCHASE transaction → verify holding quantity decreases or position is removed if it was the only transaction
  - **Synced transaction:** Verify ⋮ menu appears but items are disabled with tooltip
  - **Coupon schedule (fixed income):** Verify existing coupon edit/delete flow is completely unaffected
