# Investment Edit — Event Selector Drawer (2-Step Flow)

**Service:** Invistus Banking Service  
**Module:** Personal Finance — Investments  
**Document date:** 2026-04-20  
**Status:** Ready for implementation

---

## 1. Context & Problem

### 1.1 What already works

The feature described in `07-investment-flow-edit.md` is **already implemented**. The
`InvestmentHoldingDetailDrawer` (`components/InvestmentHoldingDetailDrawer.tsx`) already
supports edit and delete per event in the holding detail timeline:

- `editingTransactionId`, `deletingTransactionId`, `deleteConfirmOpen` states are live.
- `RecordFixedIncomeDrawer`, `RecordFundHoldingDrawer`, `RecordEquityHoldingDrawer` mount
  inside the detail drawer in edit mode.
- The delete confirmation `Dialog` is wired.
- `TimelineEvents` already exposes `onEditTransaction` / `onDeleteTransaction` callbacks with a
  kebab (⋮) menu per event card.

### 1.2 The remaining gap

The **"Editar"** item in the holding row's actions menu (`InvestmentHoldingActionsMenu`,
`table/InvestmentHoldingActionsMenu.tsx`) still uses a broken heuristic:

```ts
// InvestmentTable.tsx — handleOpenEdit (line ~253)
const transaction = await resolveLatestManualTransaction(holding.id);
// → picks the most-recently-created MANUAL transaction, silently
// → fails with a toast if zero MANUAL transactions exist
```

This approach was designed for a simple buy-and-hold model. It breaks in every real scenario:

| Scenario | Problem |
|---|---|
| Holding has 5 purchases | Picks only the most recent one — user cannot pick which to fix |
| Holding has purchases + dividends + splits | Randomly opens a dividend form when the user wanted to fix the original buy |
| All transactions are from Open Finance | Shows a toast "Não há lançamento manual" — dead end |
| Split or bonification event | No edit path exists at all from this menu |

### 1.3 Proposed solution

Replace the `onEdit` menu action with a **2-step drawer**:

1. **Step 1 — Event selector**: An intermediate drawer that fetches and lists all events
   for the selected holding in a timeline. Each event is a clickable row. Non-editable events
   (Open Finance / File Import) are shown but visually disabled.

2. **Step 2 — Edit form**: Clicking a manual event opens the appropriate existing record drawer
   pre-filled with that transaction's data.

---

## 2. User Stories

**US-1 — Choose which event to edit**  
As a user with multiple purchases for the same stock, I want to see a list of all recorded events
and choose which one to correct, so I do not accidentally edit the wrong transaction.

**US-2 — Awareness of non-editable events**  
As a user with Open Finance-synced data, I want to see all events in the list even if I cannot
edit them, so I understand the full history and am not confused by missing entries.

**US-3 — Rename the action**  
As a user, I want the holding row menu to say **"Ver eventos"** (not "Editar") so I understand
that I'm about to see a list, not a direct form.

---

## 3. Acceptance Criteria

| # | Criterion |
|---|---|
| AC-1 | The "Editar" item in `InvestmentHoldingActionsMenu` is renamed to "Ver eventos" and uses `HistoryIcon` (or `EventNoteIcon`) |
| AC-2 | Clicking "Ver eventos" opens a new `InvestmentEventSelectorDrawer` that lists all investment transactions for that holding |
| AC-3 | The drawer shows a loading skeleton while fetching; shows an error state with retry if the query fails |
| AC-4 | Each event row displays: direction color dot, direction label (Entrada/Saída), date, event type label, and amount |
| AC-5 | Events with `source = MANUAL` are clickable and show a right-arrow indicator |
| AC-6 | Events with `source ≠ MANUAL` are visually muted (opacity 0.5), not clickable, and show a `LockIcon` tooltip: "Sincronizado — não editável" |
| AC-7 | Clicking a manual event opens the correct record drawer in edit mode (same drawers used in `InvestmentHoldingDetailDrawer`) |
| AC-8 | After saving the edit, the event selector drawer closes, the underlying holdings table refreshes |
| AC-9 | If the holding has zero transactions (edge case), an empty state is shown with a message |
| AC-10 | The existing `onEdit` path in `InvestmentTable.tsx` (which calls `resolveLatestManualTransaction`) is **removed** and replaced with this new flow |
| AC-11 | The existing timeline edit/delete inside `InvestmentHoldingDetailDrawer` is **not affected** |

---

## 4. UI/UX Specification

### 4.1 Renamed menu item

**File:** `components/table/InvestmentHoldingActionsMenu.tsx`

Change the "Editar" `MenuItem`:
- Label: `"Ver eventos"` (i18n key: `investments.table.rowActions.viewEvents`)
- Icon: `HistoryIcon` (from `@mui/icons-material/History`) or `EventNoteIcon`
- Prop signature remains `onEdit: (holding: HoldingRow) => Promise<void>` — the caller
  signature does not change, only the implementation in `InvestmentTable.tsx` changes

### 4.2 Event Selector Drawer — visual layout

```
┌─────────────────────────────────────────────────────────────┐
│  RENDA VARIÁVEL                                       [✕]   │
├─────────────────────────────────────────────────────────────┤
│  PETR4 — Petrobras PN          Selecionar evento para editar│
├─────────────────────────────────────────────────────────────┤
│  ● Entrada · Compra · 14 mar 2025                     →     │
│    R$ 3.200,00                                              │
│  ─────────────────────────────────────────────────────────  │
│  ● Entrada · Compra · 02 jan 2025                     →     │
│    R$ 2.850,00                                              │
│  ─────────────────────────────────────────────────────────  │
│  ● Saída · Dividendo · 15 fev 2025             🔒 (muted)   │
│    R$ 120,00  (Open Finance — não editável)                 │
│  ─────────────────────────────────────────────────────────  │
│  ● Saída · Venda · 10 abr 2025                        →     │
│    R$ 1.600,00                                              │
└─────────────────────────────────────────────────────────────┘
```

- Drawer anchors to the **right**, size `md` (480 px wide on desktop, full-width on mobile).
- Header shows asset name and subtitle "Selecionar evento para editar".
- Rows are sorted by `tradeDate` descending (most recent first).
- Direction color dot: green for INFLOW, red for OUTFLOW (same palette as `TimelineEvents`).
- No footer buttons — the action is triggered by row click.

### 4.3 Row states

| State | Visual |
|---|---|
| Manual (editable) | Full opacity, cursor pointer, right chevron (`ChevronRightIcon`) on hover |
| Non-manual (read-only) | opacity 0.45, cursor default, `LockOutlinedIcon` at right, tooltip on the icon |

### 4.4 Edit form (Step 2)

When a manual row is clicked, the event selector drawer **does not close** — instead, the
appropriate record drawer opens on top (stacked on the right), exactly as in the existing
`InvestmentHoldingDetailDrawer` flow. After the user saves or cancels in the edit drawer:

- On **save**: edit drawer closes → event selector drawer closes → holdings table refetches.
- On **cancel**: edit drawer closes → event selector drawer remains open (user can pick a
  different event or close manually).

---

## 5. Technical Implementation

### 5.1 New file to create

**`web/src/app/personal-finance/investments/components/InvestmentEventSelectorDrawer.tsx`**

This is the only new file. All edit form drawers already exist and are reused as-is.

#### 5.1.1 Props interface

```ts
import type { HoldingRow } from './table/investmentTableHelpers';
import type { HoldingKind } from './table/investmentTableHelpers';
import type { IssuerSelectionAccount } from './IssuerSelection';

export interface InvestmentEventSelectorDrawerProps {
  open: boolean;
  holding: HoldingRow | null;
  kind: HoldingKind | null;
  financialAccountId: string | null;
  investmentAccounts: IssuerSelectionAccount[];
  onClose: () => void;
  onRecorded: () => Promise<void>;
}
```

#### 5.1.2 Data fetching

Use the existing `INVESTMENT_TRANSACTIONS` query (already used in `InvestmentTable.tsx`
via `resolveManualTransactions`). This time fetch **all** transactions (not filtered by source):

```ts
const { data, loading, error, refetch } = useQuery<
  { investmentTransactions: InvestmentTransaction[] },
  { holdingId: string }
>(INVESTMENT_TRANSACTIONS, {
  variables: { holdingId: holding?.id ?? '' },
  skip: !open || !holding?.id,
  fetchPolicy: 'cache-and-network',
  errorPolicy: 'all',
});

const transactions = React.useMemo(
  () => [...(data?.investmentTransactions ?? [])].sort(
    (a, b) => new Date(String(b.tradeDate)).getTime() - new Date(String(a.tradeDate)).getTime()
  ),
  [data?.investmentTransactions],
);
```

**Important:** `INVESTMENT_TRANSACTIONS` is defined in
`lib/graphql/operations/banking/queries.ts`. Verify it selects at minimum:
`id`, `type`, `direction`, `grossValue`, `tradeDate`, `source`, `source` enum value.
No new fields need to be added to this query — it already fetches `source`.

#### 5.1.3 Internal state

```ts
// Which transaction is being edited (null = step 1, non-null = step 2)
const [editingTransactionId, setEditingTransactionId] = React.useState<string | null>(null);
```

Reset on `open` change:
```ts
React.useEffect(() => {
  if (!open) setEditingTransactionId(null);
}, [open]);
```

#### 5.1.4 Resolve the full InvestmentTransaction for the edit drawer

The `INVESTMENT_TRANSACTIONS` query returns `InvestmentTransaction[]` objects. Cast the
selected transaction for use as `editTransaction`:

```ts
const editingTransaction = React.useMemo<InvestmentTransaction | null>(() => {
  if (!editingTransactionId) return null;
  return (transactions.find((tx) => tx.id === editingTransactionId) as InvestmentTransaction | undefined) ?? null;
}, [editingTransactionId, transactions]);
```

#### 5.1.5 Build editHolding shapes

Reuse the same shapes as `InvestmentEditDrawers` (see `components/InvestmentEditDrawers.tsx`).
The `holding: HoldingRow` prop passed to this component contains all necessary fields.

**Fixed income:**
```ts
const fixedIncomeEditHolding = React.useMemo(() => {
  if (!holding) return null;
  return {
    id: holding.id,
    asset: {
      id: holding.asset.id,
      name: holding.asset.name,
      assetType: holding.asset.assetType,
      issuerName: holding.asset.issuerName ?? null,
    },
    indexer: holding.indexer ?? null,
    indexerPercentage: holding.indexerPercentage ?? null,
    fixedAnnualRate: holding.fixedAnnualRate ?? null,
    maturityDate: holding.maturityDate ?? null,
    isTaxExempt: holding.isTaxExempt ?? false,
    couponFrequency: holding.couponFrequency ?? null,
    couponDayOfMonth: holding.couponDayOfMonth ?? null,
  };
}, [holding]);
```

**Fund:**
```ts
const fundEditHolding = React.useMemo(() => {
  if (!holding) return null;
  return {
    id: holding.id,
    pricingMode: holding.pricingMode ?? null,
    valuationReferenceDate: holding.valuationReferenceDate ?? null,
    grossValue: holding.grossValue ?? null,
    netValue: holding.netValue ?? null,
    asset: {
      id: holding.asset.id,
      name: holding.asset.name,
      assetType: holding.asset.assetType as AssetType,
    },
  };
}, [holding]);
```

**Equity:**
```ts
const equityEditHolding = React.useMemo(() => {
  if (!holding) return null;
  return {
    id: holding.id,
    asset: {
      id: holding.asset.id,
      ticker: holding.asset.ticker ?? null,
      name: holding.asset.name,
      assetType: holding.asset.assetType as AssetType,
    },
  };
}, [holding]);
```

#### 5.1.6 Event type label helper

Map `InvestmentTransaction.type` to a human-readable PT-BR label:

```ts
function getEventTypeLabel(type: string, t: TFunction): string {
  // Reuse the i18n key pattern already used elsewhere in the module
  // Expected key pattern: investments.transactionTypes.<TYPE>
  return t(`investments.transactionTypes.${type}`, { defaultValue: type });
}
```

If the `investments.transactionTypes.*` keys don't exist, add them (see section 5.4).

#### 5.1.7 Skeleton loading state

Show 4 skeleton rows while loading:

```tsx
function EventRowSkeleton() {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, px: 2, py: 1.5 }}>
      <Skeleton variant="circular" width={8} height={8} />
      <Box sx={{ flex: 1 }}>
        <Skeleton variant="text" width="40%" />
        <Skeleton variant="text" width="25%" />
      </Box>
      <Skeleton variant="text" width={80} />
    </Box>
  );
}
```

#### 5.1.8 Full component structure

```tsx
export const InvestmentEventSelectorDrawer = React.memo(
  function InvestmentEventSelectorDrawer({
    open,
    holding,
    kind,
    financialAccountId,
    investmentAccounts,
    onClose,
    onRecorded,
  }: InvestmentEventSelectorDrawerProps) {
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('md'));
    const locale = /* resolve from i18n */;
    const { t } = useTranslation(personalFinanceTranslations);

    const [editingTransactionId, setEditingTransactionId] = React.useState<string | null>(null);

    React.useEffect(() => {
      if (!open) setEditingTransactionId(null);
    }, [open]);

    const { data, loading, error, refetch } = useQuery(...); // see 5.1.2
    const transactions = ...; // sorted list, see 5.1.2

    const editingTransaction = ...; // see 5.1.4
    const fixedIncomeEditHolding = ...; // see 5.1.5
    const fundEditHolding = ...; // see 5.1.5
    const equityEditHolding = ...; // see 5.1.5

    const handleRowClick = React.useCallback((transactionId: string) => {
      setEditingTransactionId(transactionId);
    }, []);

    const handleEditClose = React.useCallback(() => {
      setEditingTransactionId(null);
    }, []);

    const handleEditRecorded = React.useCallback(async () => {
      setEditingTransactionId(null);
      onClose();           // close selector drawer
      await onRecorded();  // refresh parent holdings table
    }, [onClose, onRecorded]);

    return (
      <>
        {/* Step 1 — Selector drawer */}
        <Drawer
          open={open}
          onClose={onClose}
          anchor="right"
          size={isMobile ? undefined : 'md'}
          width={isMobile ? '100%' : undefined}
        >
          <Drawer.Header>
            {/* Asset name + subtitle */}
          </Drawer.Header>

          <Drawer.Body>
            {loading && <EventRowSkeleton x4 />}
            {!loading && error && <QueryErrorState onRetry={() => void refetch()} />}
            {!loading && !error && transactions.length === 0 && (
              <EmptyState
                title={t('investments.eventSelector.empty.title')}
                description={t('investments.eventSelector.empty.description')}
              />
            )}
            {!loading && !error && transactions.map((tx, index) => (
              <EventSelectorRow
                key={tx.id}
                transaction={tx}
                locale={locale}
                isFirst={index === 0}
                onClick={
                  tx.source === 'MANUAL'
                    ? () => handleRowClick(tx.id)
                    : undefined
                }
                t={t}
              />
            ))}
          </Drawer.Body>
        </Drawer>

        {/* Step 2 — Edit drawers (stacked on top, same as InvestmentEditDrawers) */}
        <RecordFixedIncomeDrawer
          open={kind === 'fixedIncome' && Boolean(editingTransactionId) && Boolean(editingTransaction)}
          onClose={handleEditClose}
          financialAccountId={financialAccountId}
          financialAccounts={investmentAccounts}
          editHolding={fixedIncomeEditHolding}
          editTransaction={editingTransaction}
          onRecorded={handleEditRecorded}
        />

        <RecordFundHoldingDrawer
          open={kind === 'fund' && Boolean(editingTransactionId) && Boolean(editingTransaction)}
          onClose={handleEditClose}
          financialAccountId={financialAccountId}
          financialAccounts={investmentAccounts}
          editHolding={fundEditHolding}
          editTransaction={editingTransaction}
          onRecorded={handleEditRecorded}
        />

        <RecordEquityHoldingDrawer
          open={kind === 'equity' && Boolean(editingTransactionId) && Boolean(editingTransaction)}
          onClose={handleEditClose}
          financialAccountId={financialAccountId}
          financialAccounts={investmentAccounts}
          editHolding={equityEditHolding}
          editTransaction={editingTransaction}
          onRecorded={handleEditRecorded}
        />
      </>
    );
  }
);
```

#### 5.1.9 EventSelectorRow sub-component

Extract as a `React.memo` component in the same file:

```ts
interface EventSelectorRowProps {
  transaction: InvestmentTransaction;
  locale: string;
  isFirst: boolean;
  onClick?: () => void; // undefined = non-editable
  t: TFunction;
}
```

Visual rules:
- `onClick` provided → full opacity, `cursor: pointer`, `ChevronRightIcon` visible on hover.
- `onClick` undefined (read-only) → `opacity: 0.45`, `cursor: default`, `LockOutlinedIcon`
  at right, wrapped in a `Tooltip` with "Sincronizado — não editável".

Row layout:

```
[dot] [direction · type · date]           [amount] [→ or 🔒]
```

The direction dot and amount color follow the same convention as `TimelineEvents`:
`success.main` for INFLOW, `error.main` for OUTFLOW.

Format the amount using `CurrencyAmountParts` (already available) with the holding's
`currencyCode`.

### 5.2 Modify `InvestmentTable.tsx`

**File:** `components/table/InvestmentTable.tsx`

#### 5.2.1 Add new state

```ts
const [eventSelectorDrawer, setEventSelectorDrawer] = React.useState<{
  holding: HoldingRow | null;
  kind: HoldingKind;
  financialAccountId: string | null;
}>({ holding: null, kind: 'equity', financialAccountId: null });

const closeEventSelectorDrawer = React.useCallback(() => {
  setEventSelectorDrawer({ holding: null, kind: 'equity', financialAccountId: null });
}, []);
```

#### 5.2.2 Replace `handleOpenEdit`

**Remove** the current implementation (lines ~253–274):
```ts
// REMOVE:
const handleOpenEdit = React.useCallback(
  async (holding: HoldingRow) => {
    try {
      const transaction = await resolveLatestManualTransaction(holding.id);
      if (!transaction) {
        toast.info('Esta posição não possui lançamento manual editável.');
        return;
      }
      setEditDrawer({ ... });
    } catch { ... }
  },
  [...],
);
```

**Replace with:**
```ts
const handleOpenEdit = React.useCallback(
  async (holding: HoldingRow) => {
    setEventSelectorDrawer({
      holding,
      kind: getHoldingKind(holding.asset?.assetType),
      financialAccountId: resolveFinancialAccountId(holding),
    });
  },
  [resolveFinancialAccountId],
);
```

Note: `handleOpenEdit` is typed `async` for compatibility with `InvestmentHoldingActionsMenu`
`onEdit: (holding: HoldingRow) => Promise<void>`. The new implementation is synchronous but
wrapping in an async function is acceptable.

#### 5.2.3 Remove orphaned helpers (if no longer used)

After removing the old `handleOpenEdit`, check if `resolveLatestManualTransaction` and
`resolveManualTransactions` are still referenced elsewhere in the file.

- `resolveManualTransactions` is only used by `resolveLatestManualTransaction`.
- If both are now unused, **remove both** to avoid dead code.

Also check if the `editDrawer` state and `InvestmentEditDrawers` component are still needed.
They are only used in the old `handleOpenEdit` path. If no other code references them, remove:
- `editDrawer` state and `closeEditDrawer` callback
- The `<InvestmentEditDrawers ... />` JSX

#### 5.2.4 Mount the new drawer

Add `InvestmentEventSelectorDrawer` in the JSX, alongside the other drawers:

```tsx
<InvestmentEventSelectorDrawer
  open={Boolean(eventSelectorDrawer.holding)}
  holding={eventSelectorDrawer.holding}
  kind={eventSelectorDrawer.kind}
  financialAccountId={eventSelectorDrawer.financialAccountId}
  investmentAccounts={investmentAccounts}
  onClose={closeEventSelectorDrawer}
  onRecorded={refreshInvestmentData}
/>
```

### 5.3 Modify `InvestmentHoldingActionsMenu.tsx`

**File:** `components/table/InvestmentHoldingActionsMenu.tsx`

Change the "Editar" menu item:

```tsx
// BEFORE:
<MenuItem onClick={async () => { ... await onEdit(holding); }}>
  <ListItemIcon><EditIcon fontSize="small" /></ListItemIcon>
  <ListItemText>{t('investments.table.rowActions.edit')}</ListItemText>
</MenuItem>

// AFTER:
<MenuItem
  disabled={deleting || !holding}
  onClick={async () => {
    if (!holding) return;
    onClose();
    await onEdit(holding);
  }}
>
  <ListItemIcon>
    <HistoryIcon fontSize="small" />
  </ListItemIcon>
  <ListItemText>{t('investments.table.rowActions.viewEvents')}</ListItemText>
</MenuItem>
```

Add import:
```ts
import HistoryIcon from '@mui/icons-material/History';
```

The `onEdit` prop keeps its name and signature to minimize the change surface. Only the label,
icon, and underlying behavior change (the behavior change is in `InvestmentTable.tsx`).

### 5.4 i18n Keys

Add to the `personalFinanceTranslations` (PT-BR):

```json
"investments.table.rowActions.viewEvents": "Ver eventos",
"investments.eventSelector.title": "Selecionar evento",
"investments.eventSelector.subtitle": "Toque no evento que deseja editar",
"investments.eventSelector.empty.title": "Sem eventos registrados",
"investments.eventSelector.empty.description": "Nenhum evento encontrado para esta posição.",
"investments.eventSelector.notEditable": "Sincronizado — não editável",
"investments.transactionTypes.PURCHASE": "Compra",
"investments.transactionTypes.SALE": "Venda",
"investments.transactionTypes.APPLICATION": "Aplicação",
"investments.transactionTypes.REDEMPTION": "Resgate",
"investments.transactionTypes.INTEREST_PAYMENT": "Juros / Cupom",
"investments.transactionTypes.DIVIDEND": "Dividendo",
"investments.transactionTypes.JCP": "JCP",
"investments.transactionTypes.INCOME": "Rendimento",
"investments.transactionTypes.SPLIT": "Desdobramento",
"investments.transactionTypes.INPLIT": "Grupamento",
"investments.transactionTypes.BONUS": "Bonificação",
"investments.transactionTypes.SUBSCRIPTION": "Subscrição",
"investments.transactionTypes.SUBSCRIPTION_RIGHT": "Direito de subscrição",
"investments.transactionTypes.BALANCE_ADJUSTMENT": "Ajuste de saldo",
"investments.transactionTypes.TRANSFER": "Transferência"
```

> Check if any `investments.transactionTypes.*` keys already exist before adding.

---

## 6. Files to Create / Modify

| File (relative to `web/src/`) | Change type |
|---|---|
| `app/personal-finance/investments/components/InvestmentEventSelectorDrawer.tsx` | **CREATE** (new component) |
| `app/personal-finance/investments/components/table/InvestmentTable.tsx` | **MODIFY** — replace `handleOpenEdit`, add new drawer state + JSX |
| `app/personal-finance/investments/components/table/InvestmentHoldingActionsMenu.tsx` | **MODIFY** — rename label + icon |
| i18n PT-BR translations file | **MODIFY** — add keys listed in section 5.4 |

### Files explicitly NOT to modify

| File | Reason |
|---|---|
| `components/InvestmentHoldingDetailDrawer.tsx` | Already has its own edit/delete flow; do not touch |
| `components/TimelineEvents.tsx` | Already implemented per req. 07; no changes needed |
| `components/InvestmentEditDrawers.tsx` | Will be removed from InvestmentTable (it becomes dead code there) but the component itself is still used in InvestmentHoldingDetailDrawer — do NOT delete the file |
| Any `Record*Drawer` component | Reused as-is; no changes |
| Backend / GraphQL mutations | All mutations already exist |

---

## 7. Verification Checklist

Manual test scenarios after implementation:

- [ ] "Editar" label no longer appears in the holding row menu; "Ver eventos" appears instead
- [ ] Clicking "Ver eventos" on a holding with 3 manual purchases shows 3 clickable rows
- [ ] Clicking a row opens the correct edit drawer pre-filled with that transaction's values
- [ ] Editing and saving updates the holding list (grossValue, quantity, average cost)
- [ ] Clicking "Ver eventos" on a holding where all transactions are Open Finance shows the
  list with all rows muted and non-clickable
- [ ] The `🔒` tooltip text displays on hover over a muted row
- [ ] Empty state shows correctly for a holding with 0 transactions
- [ ] Cancel in the edit drawer returns to the event selector (selector stays open)
- [ ] The existing edit/delete flow inside `InvestmentHoldingDetailDrawer` (opened via
  "Ver detalhes") is completely unaffected
- [ ] No console errors or type errors (run `tsc --noEmit`)

---

## 8. Open Questions

| # | Question | Default assumption |
|---|---|---|
| OQ-1 | Should corporate events (SPLIT, BONUS, SUBSCRIPTION) be editable via this flow? | Yes — they appear in the list; if `source = MANUAL` they are clickable |
| OQ-2 | Should the event selector show ALL event types including INTEREST_PAYMENT coupons? | Yes — show all, but coupon payments (`INTEREST_PAYMENT`) should display a note "Use a seção de cupons para editar" instead of opening the generic edit drawer. Alternatively, disable them even if manual and add a different tooltip. Confirm with product. |
| OQ-3 | Should the drawer show a position summary (current quantity, average cost) in the header? | Not in V1 — keep it simple. The asset name + type is enough context. |

---

## 9. Implementation Order

1. **i18n keys** — Add all translation strings. Zero risk, independent.
2. **`InvestmentEventSelectorDrawer.tsx`** — Create the new component with stub handlers
   (no-ops for `onRecorded`). Can be tested in isolation by temporarily adding a trigger
   button.
3. **`InvestmentHoldingActionsMenu.tsx`** — Rename label + icon. One-line change.
4. **`InvestmentTable.tsx`** — Replace `handleOpenEdit`, remove dead code, mount the new
   drawer. Verify `resolveManualTransactions` / `InvestmentEditDrawers` can be safely removed.
5. **End-to-end test** — Go through all verification checklist items.
