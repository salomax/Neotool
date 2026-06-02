# Delete Financial Account

## Overview

Allow users to permanently remove a financial account they own. The removal uses a **soft-delete** pattern (sets `deleted_at`), consistent with how transactions and other entities are handled in the system.

The key safety guarantee: **an account with active (non-deleted) transactions cannot be deleted**. This prevents orphaned transaction data and preserves financial history integrity.

---

## User Flow

1. User clicks the **edit icon** next to an account inside `FinancialAccountSelector.tsx`.
2. The `CreateFinancialAccountDrawer` opens in edit mode.
3. A **"Remove account"** button (destructive style) appears at the bottom of the drawer.
4. Clicking it opens a **confirmation dialog** ("This action cannot be undone. Are you sure?").
5. On confirm, the `deleteFinancialAccount` mutation is called.
6. **Success**: drawer closes, account list refetches, `selectedAccountId` resets to `null`.
7. **Blocked (has transactions)**: a user-friendly inline error is shown — no drawer close.

---

## Business Rules

| Rule | Detail |
|---|---|
| **Block if active transactions exist** | `COUNT(*) > 0` on `banking.transactions WHERE financial_account_id = ? AND deleted_at IS NULL` |
| **Block if open credit card bills exist** | `COUNT(*) > 0` on `banking.credit_card_bills WHERE financial_account_id = ? AND deleted_at IS NULL AND status IN ('OPEN', 'CLOSED')` — a bill with no paid status means there are still obligations |
| **Soft-delete the account** | Set `deleted_at = NOW()` on `banking.financial_accounts` |
| **Clean up orphaned children** | In the same transaction: hard-delete rows from `banking.monthly_balance_snapshots` and soft-delete rows from `banking.credit_card_bills` WHERE `financial_account_id = ?` |
| **Ownership check** | Resolver must verify `account_id` (tenant) matches the authenticated principal before deleting |
| **No cascade to transactions** | Never delete or modify transaction records; block is the correct behaviour |

> **Why clean up snapshots?** `monthly_balance_snapshots` has `financial_account_id` as part of its primary key. If the account is deleted with no transactions, any remaining snapshots are zero-valued orphans. Hard-deleting them keeps the table lean and avoids confusion in future queries.

> **Why soft-delete bills?** `credit_card_bills` may be referenced by other audit paths. Soft-delete keeps the reference chain intact.

---

## Performance & Dirty-Data Analysis

### Accounts table

`idx_banking_financial_accounts_active` is already a **partial index with `WHERE deleted_at IS NULL`** (`V0_3__create_banking_accounts.sql`). All existing queries use `findByAccountIdAndDeletedAtIsNull(...)`, so soft-deleted accounts are completely invisible to the query planner — zero overhead.

### Transactions existence check

The check `SELECT COUNT(*) FROM banking.transactions WHERE financial_account_id = ? AND deleted_at IS NULL` will use `idx_transactions_account_date` — already a partial index on `(financial_account_id, date DESC, created_at DESC) WHERE deleted_at IS NULL`. The leading column is `financial_account_id`, so the index scan is O(matching rows). **No new index needed.**

### Orphaned monthly snapshots

If the precondition (no transactions) is met, any snapshots for this account will have zero totals. They are explicitly hard-deleted in the same write transaction, so they cannot accumulate over time.

### Orphaned credit card bills

With no transactions, bills should not exist (bills are created on first transaction assignment). The pre-flight check for open bills is a separate guard anyway. Soft-deleting remaining bills in the write transaction keeps `credit_card_bills` clean.

### Summary

No new DB indexes are required. No background jobs or async cleanup are needed. The entire delete path is a single synchronous DB transaction.

---

## Implementation Plan

### 1. New Error Codes (Backend)

**File:** `domain/error/BankingErrorCode.kt`

Add:
```kotlin
FINANCIAL_ACCOUNT_HAS_TRANSACTIONS,
FINANCIAL_ACCOUNT_HAS_OPEN_BILLS,
```

---

### 2. Repository Layer (Backend)

#### `FinancialAccountRepository.kt` (domain interface)

Add:
```kotlin
fun softDelete(id: UUID, accountId: UUID): Boolean
```

#### `DefaultFinancialAccountRepository.kt`

Add:
```kotlin
override fun softDelete(id: UUID, accountId: UUID): Boolean {
    val entity = jpaRepository.findByIdAndAccountIdAndDeletedAtIsNull(id, accountId) ?: return false
    entity.deletedAt = Instant.now()
    jpaRepository.save(entity)
    return true
}
```

#### `TransactionEntityRepository.kt` (JPA)

Add:
```kotlin
fun countByFinancialAccountIdAndDeletedAtIsNull(financialAccountId: UUID): Long
```

#### `CreditCardBillEntityRepository.kt` (JPA)

Add:
```kotlin
@Query("SELECT COUNT(b) FROM CreditCardBillEntity b WHERE b.financialAccountId = :financialAccountId AND b.deletedAt IS NULL AND b.status IN ('OPEN', 'CLOSED')")
fun countOpenByFinancialAccountId(financialAccountId: UUID): Long

@Query("UPDATE CreditCardBillEntity b SET b.deletedAt = :now WHERE b.financialAccountId = :financialAccountId AND b.deletedAt IS NULL")
@Modifying
fun softDeleteByFinancialAccountId(financialAccountId: UUID, now: Instant)
```

#### `MonthlyBalanceSnapshotEntityRepository.kt` (JPA)

Add:
```kotlin
fun deleteByFinancialAccountId(financialAccountId: UUID)
```

---

### 3. Service Layer (Backend)

**File:** `service/account/FinancialAccountService.kt`

Inject: `TransactionEntityRepository`, `CreditCardBillEntityRepository`, `MonthlyBalanceSnapshotEntityRepository`

Add:

```kotlin
@Transactional
open fun delete(
    financialAccountId: UUID,
    accountId: UUID,
): Boolean {
    // 1. Ownership check
    financialAccountRepository.findById(financialAccountId, accountId)
        ?: throw NotFoundException(
            errorCode = CommonErrorCode.NOT_FOUND,
            field = "id",
            parameters = mapOf("financialAccountId" to financialAccountId.toString()),
        )

    // 2. Guard: active transactions
    val txCount = transactionEntityRepository.countByFinancialAccountIdAndDeletedAtIsNull(financialAccountId)
    if (txCount > 0) {
        throw ValidationException(
            errorCode = BankingErrorCode.FINANCIAL_ACCOUNT_HAS_TRANSACTIONS,
            field = "id",
            parameters = mapOf("count" to txCount),
        )
    }

    // 3. Guard: open/closed credit card bills
    val openBillCount = creditCardBillEntityRepository.countOpenByFinancialAccountId(financialAccountId)
    if (openBillCount > 0) {
        throw ValidationException(
            errorCode = BankingErrorCode.FINANCIAL_ACCOUNT_HAS_OPEN_BILLS,
            field = "id",
            parameters = mapOf("count" to openBillCount),
        )
    }

    // 4. Clean up orphaned children (same transaction)
    monthlyBalanceSnapshotEntityRepository.deleteByFinancialAccountId(financialAccountId)
    creditCardBillEntityRepository.softDeleteByFinancialAccountId(financialAccountId, Instant.now())

    // 5. Soft-delete the account
    return financialAccountRepository.softDelete(financialAccountId, accountId)
}
```

---

### 4. GraphQL Schema (Backend)

**File:** `src/main/resources/graphql/schema.graphqls`

In the `Mutation` type, add after `updateFinancialAccount`:

```graphql
deleteFinancialAccount(id: ID!): Boolean!
```

---

### 5. GraphQL Resolver (Backend)

**File:** `graphql/account/FinancialAccountResolver.kt`

Add:

```kotlin
fun delete(
    id: String,
    principal: RequestPrincipal,
): Boolean {
    val accountId = principal.resolveAccountId()
    return service.delete(UUID.fromString(id), accountId)
}
```

---

### 6. BankingWiringFactory (Backend)

**File:** `graphql/config/BankingWiringFactory.kt`

Wire the new mutation following the same pattern as `deleteTransaction`:

```kotlin
"deleteFinancialAccount" -> DataFetcher { env ->
    val principal = env.resolvePrincipal()
    val id = env.getArgument<String>("id")
    financialAccountResolver.delete(id, principal)
}
```

Use permission: `BankingPermissions.BANKING_ACCOUNT_CREATE` (same as create/update — no separate delete permission exists yet).

---

### 7. Frontend — GraphQL Mutation

**File:** `web/src/lib/graphql/operations/banking/mutations.ts`

Add:

```ts
export const DELETE_FINANCIAL_ACCOUNT = gql`
  mutation DeleteFinancialAccount($id: ID!) {
    deleteFinancialAccount(id: $id)
  }
`;
```

---

### 8. Frontend — Delete Button in Drawer

**File:** `web/src/app/personal-finance/finance-transactions/account/CreateFinancialAccountDrawer.tsx`

The drawer already has an `accountToEdit` prop that indicates edit mode. Changes:

1. Import `DELETE_FINANCIAL_ACCOUNT` mutation and `useMutation`.
2. Add local state: `deleteConfirmOpen: boolean`, `deleteError: string | null`.
3. Add a **"Remove account"** `Button` at the bottom of the form, visible only when `accountToEdit != null`. Style: `color="error"`, `variant="outlined"`.
4. Clicking it sets `deleteConfirmOpen = true`.
5. Add a MUI `Dialog` for confirmation:
   - Title: `t('financeTransactions.actions.deleteAccount')`
   - Body: `t('financeTransactions.actions.deleteAccountConfirm')`
   - Actions: **Cancel** and **Remove** (`variant="contained"`, `color="error"`)
6. On confirm:
   - Call `deleteFinancialAccount({ variables: { id: accountToEdit.id } })`
   - On success: call `onDeleted()` (new prop) and close drawer
   - On `FINANCIAL_ACCOUNT_HAS_TRANSACTIONS` or `FINANCIAL_ACCOUNT_HAS_OPEN_BILLS` error: close dialog, set `deleteError` with localized message, show as MUI `Alert` inside the drawer
   - On other errors: show generic error alert

**New prop on drawer:**
```ts
onDeleted?: () => void;
```

---

### 9. Frontend — Parent Page Wiring

**File:** `web/src/app/personal-finance/finance-transactions/page.tsx`

1. Pass `onDeleted={handleAccountDeleted}` to `CreateFinancialAccountDrawer`.
2. Implement `handleAccountDeleted`:
   - Close the drawer
   - Set `selectedAccountId` to `null`
   - Refetch the accounts query (Apollo `refetch` or `cache.evict({ id: 'FinancialAccount:' + id })` followed by `cache.gc()`)

---

### 10. i18n Keys

Add to both `pt-BR` and `en` translation files:

```ts
'financeTransactions.actions.deleteAccount': 'Remover conta',
'financeTransactions.actions.deleteAccountConfirm': 'Tem certeza? Esta ação não pode ser desfeita.',
'financeTransactions.errors.accountHasTransactions': 'Esta conta possui transações. Remova as transações antes de excluir a conta.',
'financeTransactions.errors.accountHasOpenBills': 'Esta conta possui faturas abertas. Quite as faturas antes de excluir a conta.',
```

---

## Testing Requirements

### Unit Tests (Backend)

File: `FinancialAccountServiceTest.kt`

| Scenario | Expected |
|---|---|
| Delete account with no transactions and no bills | Returns `true`, soft-deletes account |
| Delete account with active transactions | Throws `ValidationException(FINANCIAL_ACCOUNT_HAS_TRANSACTIONS)` |
| Delete account with open credit card bills | Throws `ValidationException(FINANCIAL_ACCOUNT_HAS_OPEN_BILLS)` |
| Delete account belonging to different tenant | Throws `NotFoundException` |
| Delete already-deleted account | Throws `NotFoundException` |
| Successful delete cleans up snapshots | `deleteByFinancialAccountId` is called |
| Successful delete soft-deletes bills | `softDeleteByFinancialAccountId` is called |

### Integration Tests (Backend)

File: `FinancialAccountDeleteIntegrationTest.kt`

- Create account → delete → verify absent from `listByAccount`
- Create account → create transaction → try delete → verify blocked with `FINANCIAL_ACCOUNT_HAS_TRANSACTIONS`
- Create account with `initialBalance` → try delete → verify blocked (initial balance creates a `BALANCE_ADJUSTMENT` transaction)

---

## Files Changed Summary

### Backend (no DB migration needed — `deleted_at` already exists on all affected tables)

| File | Change |
|---|---|
| `domain/error/BankingErrorCode.kt` | Add 2 error codes |
| `domain/account/FinancialAccountRepository.kt` | Add `softDelete()` |
| `repo/account/DefaultFinancialAccountRepository.kt` | Implement `softDelete()` |
| `repo/transaction/TransactionEntityRepository.kt` | Add count query |
| `repo/transaction/CreditCardBillEntityRepository.kt` | Add count + soft-delete queries |
| `repo/transaction/MonthlyBalanceSnapshotEntityRepository.kt` | Add `deleteByFinancialAccountId()` |
| `service/account/FinancialAccountService.kt` | Add `delete()` method |
| `src/main/resources/graphql/schema.graphqls` | Add `deleteFinancialAccount` mutation |
| `graphql/account/FinancialAccountResolver.kt` | Add `delete()` resolver |
| `graphql/config/BankingWiringFactory.kt` | Wire `deleteFinancialAccount` |

### Frontend

| File | Change |
|---|---|
| `web/src/lib/graphql/operations/banking/mutations.ts` | Add `DELETE_FINANCIAL_ACCOUNT` |
| `web/src/app/personal-finance/finance-transactions/account/CreateFinancialAccountDrawer.tsx` | Add delete button, confirmation dialog, error handling, `onDeleted` prop |
| `web/src/app/personal-finance/finance-transactions/page.tsx` | Pass `onDeleted`, handle post-delete state reset |
| i18n files | Add 4 translation keys |
