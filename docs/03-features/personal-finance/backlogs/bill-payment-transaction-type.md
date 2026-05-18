# Feature: BILL_PAYMENT Transaction Type

## Context & motivation

When a user uploads a checking account statement, it may contain a credit card bill payment
row (e.g. "PAGAMENTO FATURA NUBANK"). If the user also uploads the credit card bill statement,
the individual purchases are already present in the system — so the bill payment row becomes a
duplicate expense entry in all income/expense reports.

Current workarounds are semantically wrong:
- **Delete**: destroys the cash-flow record (the checking account did lose that money).
- **Mark as Transfer ("Entre contas próprias")**: incorrect — paying a credit card bill is not a
  transfer between two owned accounts; it is a liability settlement.

The fix is a first-class `BILL_PAYMENT` transaction type that carries the right semantics:
it is a real cash outflow affecting the account balance, but is excluded from income/expense
totals (same exclusion rule as `TRANSFER`), because the underlying expenses are already captured
as individual credit card transactions.

No new `Category` entity is needed: the type itself carries the semantic meaning, and the UI
derives display labels from the type.

---

## Business rules

| Rule | Detail |
|---|---|
| `creditDebitType` | Always `DEBIT`. A bill payment is always an outflow from the checking account. |
| Balance calculation | **Included** in `totalCredit`/`totalDebit` → affects `closingBalance`. |
| Income/expense reporting | **Excluded** from `totalIncome` and `totalExpenses` (same as `TRANSFER`). |
| `totalCreditExclTransfer` / `totalDebitExclTransfer` | **Excluded** — treat like `TRANSFER` in these aggregates. |
| Category | Not required. Clear any existing category when the type is changed to `BILL_PAYMENT`. |
| `billId` | Optional link to a `CreditCardBill`. Agent may leave this nullable for now. |
| Credit card transactions | `BILL_PAYMENT` type is only valid on checking / savings account transactions, **never** on credit card account transactions. |
| Undo | Changing type back to any other value via `updateTransaction` must be allowed. |

---

## Scope

### Out of scope for this ticket
- Auto-detection heuristics during import.
- Linking `billId` to a specific bill (optional FK, nullable — no UI for it yet).
- Any changes to the PDF import pipeline.

---

## Backend — Kotlin

### B1. `domain/transaction/TransactionEnums.kt`

Add `BILL_PAYMENT` to `TransactionType`, in the "Payment types" section:

```kotlin
enum class TransactionType {
    // Payment types (OFB-aligned)
    PIX,
    TED,
    DOC,
    BOLETO,
    TRANSFER,
    BILL_PAYMENT,   // ← NEW: credit card bill payment from checking account
    ...
}
```

### B2. `repo/transaction/DefaultTransactionRepository.kt`

There are several native SQL queries that use `NOT IN ('TRANSFER','BALANCE_ADJUSTMENT')` to
compute `totalIncome`/`totalExpenses`. Add `'BILL_PAYMENT'` to every such list.

**Exact patterns to find and update (all occurrences):**

| Old | New |
|---|---|
| `NOT IN ('TRANSFER','BALANCE_ADJUSTMENT')` | `NOT IN ('TRANSFER','BALANCE_ADJUSTMENT','BILL_PAYMENT')` |
| `type <> 'TRANSFER'` (used for `totalCreditExclTransfer`/`totalDebitExclTransfer`) | `type NOT IN ('TRANSFER','BILL_PAYMENT')` |

> The pattern `t.type IN ('TRANSFER', 'INVESTMENT_RETURN', 'BALANCE_ADJUSTMENT')` at line ~763
> (investment custodian cash filter) should **not** be modified.

Also update the expense-filter condition at line ~1104:
```kotlin
// before
conditions += "t.type NOT IN ('TRANSFER', 'BALANCE_ADJUSTMENT')"
// after
conditions += "t.type NOT IN ('TRANSFER', 'BALANCE_ADJUSTMENT', 'BILL_PAYMENT')"
```

### B3. `service/transaction/CreditCardBillService.kt`

In `recomputeBill()` (and any method that assigns `billId` to a transaction), ensure
`BILL_PAYMENT` type transactions are **never** assigned to a bill:

```kotlin
// Guard: do not assign billId to BILL_PAYMENT transactions
if (transaction.type == TransactionType.BILL_PAYMENT) return
```

### B4. `service/transaction/TransactionService.kt`

In the `updateTransaction` service method, when the incoming type is `BILL_PAYMENT`:
- Enforce `creditDebitType = DEBIT` regardless of what was sent. Throw `BankingError` if the
  client sends `CREDIT` with `BILL_PAYMENT`.
- Clear `categoryId` (set to `null`) if not explicitly provided.

### B5. `src/main/resources/graphql/schema.graphqls`

Add `BILL_PAYMENT` to the `TransactionType` enum:

```graphql
enum TransactionType {
  ...
  TRANSFER
  BILL_PAYMENT
  ...
}
```

### B6. `UpdateTransactionInput` (schema.graphqls)

No change needed — `type: TransactionType` is already an optional field on `UpdateTransactionInput`.

### B7. Verify & test

```bash
cd service/kotlin
./gradlew :banking:testUnit
./gradlew :banking:compileKotlin
```

---

## Frontend — TypeScript / React

### F1. Run codegen after backend schema is updated

```bash
cd web
npx graphql-codegen
```

Confirm `TransactionType.BillPayment` is now generated in
`web/src/lib/graphql/types/__generated__/graphql.ts`.

Also update `contracts/graphql/supergraph/supergraph.local.graphql` — add `BILL_PAYMENT` to
the `TransactionType` enum block under `@join__type(graph: BANKING)`.

### F2. `TransactionActionMenu.tsx`

File: `web/src/app/personal-finance/finance-transactions/transaction/TransactionActionMenu.tsx`

**Props changes** — add two new props alongside `onMarkAsTransfer`:

```typescript
interface TransactionActionMenuProps {
  ...
  markingBillPaymentTransactionId: string | null;   // ← NEW
  onMarkAsBillPayment: () => void;                  // ← NEW
  ...
}
```

**New menu item** — insert below the existing "Entre contas próprias" `MenuItem` and before
the `<Divider />`:

```tsx
import CreditCardOffIcon from '@mui/icons-material/CreditCardOff';

const isBillPayment = transaction?.type === TransactionType.BillPayment;
const isMarkingThisBillPayment = markingBillPaymentTransactionId === transaction?.id;

{!isBalanceAdjustment && !isBillPayment && !isTransfer && (
  <MenuItem onClick={onMarkAsBillPayment} disabled={isMarkingThisBillPayment}>
    <ListItemIcon>
      {isMarkingThisBillPayment
        ? <CircularProgress size={18} />
        : <CreditCardOffIcon color="warning" fontSize="small" />}
    </ListItemIcon>
    <ListItemText>
      {t('financeTransactions.transactionTable.rowActions.markAsBillPayment')}
    </ListItemText>
  </MenuItem>
)}
```

Hide the "Entre contas próprias" item when type is already `BILL_PAYMENT` (same guard as
`isTransfer` — add `!isBillPayment` to the condition on that `MenuItem`).

### F3. `useActionMenu.ts`

File: `web/src/app/personal-finance/finance-transactions/transaction/hooks/useActionMenu.ts`

Add state and handler:

```typescript
const [markingBillPaymentTransactionId, setMarkingBillPaymentTransactionId] = useState<string | null>(null);

const handleMarkAsBillPayment = useCallback(async () => {
  const tx = menuTransaction;
  if (!tx) return;
  if (tx.type === TransactionType.BillPayment) return;

  setMarkingBillPaymentTransactionId(tx.id);
  try {
    beginBusy();
    await updateTransaction({
      variables: {
        id: tx.id,
        input: {
          type: TransactionType.BillPayment,
          categoryId: null,
        },
      },
    });
    toast.success(t('financeTransactions.transactionTable.rowActions.markAsBillPaymentSuccess'));
    onSuccess?.();
    close();
  } catch (err) {
    toast.error(
      extractErrorMessage(err, t('financeTransactions.transactionTable.rowActions.markAsBillPaymentError')),
    );
  } finally {
    setMarkingBillPaymentTransactionId(null);
    endBusy();
  }
}, [menuTransaction, updateTransaction, beginBusy, endBusy, close, onSuccess, t, toast]);
```

Return both from the hook:
```typescript
return {
  ...
  markingBillPaymentTransactionId,
  handleMarkAsBillPayment,
};
```

Wire through `TransactionDialogs.tsx` (or whichever parent passes props to
`TransactionActionMenu`) the same way `markingTransferTransactionId`/`onMarkAsTransfer` are
currently wired.

### F4. i18n — `web/src/app/personal-finance/i18n/locales/pt.json`

Add under `financeTransactions.transactionTable.rowActions`:

```json
"markAsBillPayment": "Pagamento de fatura",
"markAsBillPaymentSuccess": "Transação marcada como pagamento de fatura.",
"markAsBillPaymentError": "Erro ao marcar como pagamento de fatura."
```

### F5. TypeScript check

```bash
cd web
npx tsc --noEmit
```

Must return 0 errors.

---

## Implementation order

1. **Backend first**
   - [ ] B1 — Add `BILL_PAYMENT` to `TransactionEnums.kt`
   - [ ] B2 — Update SQL `NOT IN` exclusion lists in `DefaultTransactionRepository.kt`
   - [ ] B3 — Guard in `CreditCardBillService.recomputeBill()`
   - [ ] B4 — Enforce DEBIT + clear category in `TransactionService.updateTransaction()`
   - [ ] B5 — Add `BILL_PAYMENT` to `schema.graphqls`
   - [ ] B7 — `./gradlew :banking:testUnit` passes

2. **Frontend**
   - [ ] F1 — Update `supergraph.local.graphql`, run codegen, confirm generated type
   - [ ] F2 — Add menu item to `TransactionActionMenu.tsx`
   - [ ] F3 — Add handler to `useActionMenu.ts`, wire props
   - [ ] F4 — Add i18n keys to `pt.json`
   - [ ] F5 — `npx tsc --noEmit` passes

---

## Files affected (summary)

| File | Change |
|---|---|
| `banking/.../domain/transaction/TransactionEnums.kt` | + `BILL_PAYMENT` to enum |
| `banking/.../repo/transaction/DefaultTransactionRepository.kt` | Update SQL `NOT IN` exclusion lists |
| `banking/.../service/transaction/CreditCardBillService.kt` | Guard: skip `BILL_PAYMENT` in `recomputeBill` |
| `banking/.../service/transaction/TransactionService.kt` | Enforce DEBIT, clear category on `BILL_PAYMENT` |
| `banking/.../resources/graphql/schema.graphqls` | + `BILL_PAYMENT` to `TransactionType` enum |
| `contracts/graphql/supergraph/supergraph.local.graphql` | + `BILL_PAYMENT` to `TransactionType` enum |
| `web/src/lib/graphql/types/__generated__/graphql.ts` | Re-generated by codegen |
| `web/src/app/personal-finance/finance-transactions/transaction/TransactionActionMenu.tsx` | + new menu item |
| `web/src/app/personal-finance/finance-transactions/transaction/hooks/useActionMenu.ts` | + handler + state |
| `web/src/app/personal-finance/finance-transactions/transaction/TransactionDialogs.tsx` | Wire new props |
| `web/src/app/personal-finance/i18n/locales/pt.json` | + 3 i18n keys |
