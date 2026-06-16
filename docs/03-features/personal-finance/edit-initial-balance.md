# Edit Initial Balance — Financial Account Form

## Overview

The "EDITAR CONTA FINANCEIRA" drawer currently hides the `initialBalance` / `initialBalanceDate` fields that are shown in create mode. Users need to be able to:

- Set an initial balance on an account that was created without one
- Change the amount of an existing initial balance
- Change the date of an existing initial balance
- Remove an existing initial balance

This is a full-stack change. The backend `UpdateFinancialAccountInput` has no `initialBalance` or `initialBalanceDate` fields. The frontend form gates these fields behind `!isEditing`.

---

## Domain Background

The initial balance is stored as a **`BALANCE_ADJUSTMENT`** transaction created automatically when `CreateFinancialAccountInput.initialBalance` is provided. There is no separate "initial balance" record — the transaction IS the initial balance.

`validateBalanceAdjustmentUpdate` in `TransactionService` blocks date and description changes when a user tries to edit a `BALANCE_ADJUSTMENT` transaction directly via `updateTransaction`. That restriction exists to prevent accidental edits from the transaction list. The account form takes a different path: `FinancialAccountService.update()` owns the BALANCE_ADJUSTMENT transaction lifecycle directly, bypassing that restriction.

The initial balance transaction is identified as: the first (oldest by `created_at`) non-deleted `BALANCE_ADJUSTMENT` transaction on the account.

---

## Business Rules

| Scenario | Expected Behavior |
|---|---|
| Account has no initial balance, user sets one | Create a new `BALANCE_ADJUSTMENT` transaction |
| User changes only the amount | Update `amount` and `credit_debit_type`; keep date; recompute chain from that month |
| User changes only the date | Update `date`; keep amount; recompute chain from `min(oldDate, newDate)` |
| User changes both amount and date | Update both; recompute from `min(oldDate, newDate)` |
| User clears the balance field entirely | Soft-delete the `BALANCE_ADJUSTMENT` transaction; recompute chain from its month |
| Feature is BANK-only | Cards (`CARD` type) never have an initial balance; form hides fields for cards in both create and edit modes |
| Zero is not a valid balance | Same validation as create mode: `initialBalance != 0` |
| Date constraints | Same as create: cannot be before `FINANCE_MOVING_WINDOW_YEARS` and cannot be in the future |
| Date is required when amount is set | Same as create: providing a balance without a date is invalid |

---

## Files Changed

### Backend

| File | Change |
|---|---|
| `repo/transaction/TransactionEntityRepository.kt` | Add two new queries |
| `model/dto/account/FinancialAccountCommands.kt` | Add `initialBalance` and `initialBalanceDate` to `UpdateFinancialAccountCommand` |
| `service/account/FinancialAccountService.kt` | Handle initial balance lifecycle in `update()` |
| `graphql/account/FinancialAccountResolver.kt` | Parse new input fields; add `initialBalance`/`initialBalanceDate` to DTO; batch-load in `listByAccount` |
| `resources/graphql/schema.graphqls` | Add 2 fields to `FinancialAccount` type; add 2 fields to `UpdateFinancialAccountInput` |

### Frontend

| File | Change |
|---|---|
| `lib/graphql/operations/banking/queries.ts` | Add `initialBalance` and `initialBalanceDate` to `FINANCIAL_ACCOUNTS` query |
| `lib/graphql/operations/banking/mutations.ts` | Add `initialBalance` and `initialBalanceDate` to `UPDATE_FINANCIAL_ACCOUNT` mutation |
| `app/personal-finance/finance-transactions/account/CreateFinancialAccountDrawer.tsx` | Remove `!isEditing` gate; pre-populate fields; pass fields on update |
| i18n files | No new keys needed — labels already exist from create mode |

---

## Implementation Plan

### Step 1 — Backend: Repository

**File:** `repo/transaction/TransactionEntityRepository.kt`

Add two queries:

```kotlin
/** Returns the first BALANCE_ADJUSTMENT transaction for a financial account (the "initial balance" tx). */
@Query(
    """
    SELECT * FROM banking.transactions
     WHERE financial_account_id = CAST(:financialAccountId AS uuid)
       AND type = 'BALANCE_ADJUSTMENT'
       AND deleted_at IS NULL
     ORDER BY created_at ASC
     LIMIT 1
    """,
    nativeQuery = true,
)
fun findInitialBalanceTransaction(financialAccountId: String): Optional<TransactionEntity>

/** Batch version: returns first BALANCE_ADJUSTMENT per account for a list of account IDs. */
@Query(
    """
    SELECT DISTINCT ON (financial_account_id) *
      FROM banking.transactions
     WHERE financial_account_id = ANY(CAST(:financialAccountIds AS uuid[]))
       AND type = 'BALANCE_ADJUSTMENT'
       AND deleted_at IS NULL
     ORDER BY financial_account_id, created_at ASC
    """,
    nativeQuery = true,
)
fun findInitialBalanceTransactions(financialAccountIds: List<UUID>): List<TransactionEntity>
```

---

### Step 2 — Backend: Command

**File:** `model/dto/account/FinancialAccountCommands.kt`

Add to `UpdateFinancialAccountCommand`:

```kotlin
data class UpdateFinancialAccountCommand(
    // ... existing fields ...
    val initialBalance: UpdateField<BigDecimal> = UpdateField.Absent,
    val initialBalanceDate: UpdateField<java.time.LocalDate> = UpdateField.Absent,
)
```

`UpdateField.Absent` means "do not touch the initial balance". `UpdateField.Present(null)` means "clear it". `UpdateField.Present(value)` means "set or update to this value".

The `initialBalanceDate` field follows the same pattern — `Absent` means don't touch, `Present(null)` should not be sent by the frontend when a balance is set (same validation as create: if balance is present, date is required).

---

### Step 3 — Backend: Service

**File:** `service/account/FinancialAccountService.kt`

Inject `SnapshotComputationService` and add its import. The constructor already has `transactionEntityRepository`.

In `update()`, after the existing account save block, add:

```kotlin
// Handle initial balance lifecycle
when (val ibField = command.initialBalance) {
    UpdateField.Absent -> { /* no change */ }
    is UpdateField.Present -> {
        val newBalance = ibField.value
        val existingTx = transactionEntityRepository
            .findInitialBalanceTransaction(financialAccountId.toString())
            .orElse(null)

        if (newBalance == null) {
            // Clear: soft-delete the existing BALANCE_ADJUSTMENT if present
            if (existingTx != null) {
                val oldDate = existingTx.date
                existingTx.deletedAt = java.time.Instant.now()
                transactionEntityRepository.save(existingTx)
                snapshotComputationService.recomputeChain(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    fromYear = oldDate.year,
                    fromMonth = oldDate.monthValue,
                )
            }
        } else {
            // Validate
            require(newBalance.compareTo(java.math.BigDecimal.ZERO) != 0) {
                "initialBalance must be non-zero when provided"
            }
            val newDateField = command.initialBalanceDate
            val newDate: java.time.LocalDate = when {
                newDateField is UpdateField.Present && newDateField.value != null -> newDateField.value
                newDateField is UpdateField.Absent && existingTx != null -> existingTx.date
                else -> throw ValidationException(
                    errorCode = BankingErrorCode.INITIAL_BALANCE_DATE_REQUIRED,
                    field = "initialBalanceDate",
                )
            }
            val minAllowedDate = DateWindowPolicy.minAllowedDate()
            if (newDate.isBefore(minAllowedDate)) {
                throw ValidationException(
                    errorCode = BankingErrorCode.TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW,
                    field = "initialBalanceDate",
                    parameters = mapOf(
                        "minAllowedDate" to minAllowedDate.toString(),
                        "windowYears" to DateWindowPolicy.MAX_PAST_YEARS,
                    ),
                )
            }
            if (newDate.isAfter(java.time.LocalDate.now())) {
                throw ValidationException(
                    errorCode = BankingErrorCode.TRANSACTION_DATE_IN_FUTURE,
                    field = "initialBalanceDate",
                )
            }

            val newCreditDebitType = if (newBalance > java.math.BigDecimal.ZERO)
                CreditDebitType.CREDIT else CreditDebitType.DEBIT
            val newAmount = newBalance.abs()

            if (existingTx != null) {
                val oldDate = existingTx.date
                existingTx.amount = newAmount
                existingTx.creditDebitType = newCreditDebitType
                existingTx.date = newDate
                transactionEntityRepository.save(existingTx)
                val recomputeFrom = if (oldDate.isBefore(newDate)) oldDate else newDate
                snapshotComputationService.recomputeChain(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    fromYear = recomputeFrom.year,
                    fromMonth = recomputeFrom.monthValue,
                )
            } else {
                // Create new BALANCE_ADJUSTMENT
                transactionService.create(
                    CreateTransactionCommand(
                        financialAccountId = financialAccountId,
                        creditDebitType = newCreditDebitType,
                        type = TransactionType.BALANCE_ADJUSTMENT,
                        amount = newAmount,
                        date = newDate,
                        description = "Saldo inicial",
                        status = TransactionStatus.POSTED,
                        currencyCode = updated.currencyCode,
                    ),
                    accountId,
                    userId,
                )
                // recomputeChain is called inside transactionService.create
            }
        }
    }
}
```

> **Note:** `INITIAL_BALANCE_DATE_REQUIRED` and `TRANSACTION_DATE_IN_FUTURE` error codes may already exist. Check `BankingErrorCode.kt` before adding. If they don't exist, add them.

> **Note about `updated`:** The `update()` method has a `val updated = financialAccountRepository.save(...)` (or similar) call before this block. Use `updated.currencyCode` when creating the new tx.

---

### Step 4 — Backend: GraphQL Schema

**File:** `resources/graphql/schema.graphqls`

In `type FinancialAccount`, add after `dataSince`:

```graphql
"""
Signed initial balance amount (positive = credit, negative = debit/overdraft).
Null if no initial balance was set on this account.
"""
initialBalance: Float

"""Reference date of the initial balance. Null when initialBalance is null."""
initialBalanceDate: Date
```

In `input UpdateFinancialAccountInput`, add after `cardData`:

```graphql
"""
Signed initial balance. Positive = credit (opening balance), negative = debit (overdraft).
Non-zero when provided. Pass null to remove the existing initial balance.
Only applicable to BANK accounts.
"""
initialBalance: Float

"""
Reference date for the initial balance. Required when initialBalance is provided.
"""
initialBalanceDate: Date
```

---

### Step 5 — Backend: Resolver

**File:** `graphql/account/FinancialAccountResolver.kt`

#### 5a. Batch-load initial balances in `listByAccount`

The resolver already batch-loads issuers and `dataSinceByAccountId`. Add the initial balance batch:

```kotlin
fun listByAccount(principal: RequestPrincipal): List<FinancialAccountDTO> {
    val accountId = principal.resolveAccountId()
    val accounts = service.listByAccount(accountId)

    val issuerIds = accounts.mapNotNull { it.issuerId }.toSet()
    val issuersById = issuerRepository.findByIds(issuerIds).associateBy { it.id!! }
    val financialAccountIds = accounts.mapNotNull { it.id }
    val dataSinceByAccountId = dataSinceService.findDataSinceByFinancialAccountIds(financialAccountIds)
    // NEW: batch-load initial balance transactions
    val initialBalanceTxByAccountId = transactionEntityRepository
        .findInitialBalanceTransactions(financialAccountIds)
        .associateBy { it.financialAccountId }

    return accounts.map { account ->
        val financialAccountId = requireNotNull(account.id)
        val initialBalanceTx = initialBalanceTxByAccountId[financialAccountId]
        account.toDTO(
            issuer = issuersById[account.issuerId],
            dataSince = dataSinceByAccountId[financialAccountId]?.let {
                YearMonthDTO(year = it.year, month = it.monthValue)
            },
            initialBalance = initialBalanceTx?.let {
                val signed = it.amount.toDouble()
                if (it.creditDebitType == CreditDebitType.CREDIT) signed else -signed
            },
            initialBalanceDate = initialBalanceTx?.date,
        )
    }
}
```

Inject `transactionEntityRepository: TransactionEntityRepository` into the resolver constructor.

#### 5b. Update `toDTO` signature

```kotlin
private fun FinancialAccount.toDTO(
    issuer: Issuer?,
    dataSince: YearMonthDTO?,
    initialBalance: Double? = null,
    initialBalanceDate: LocalDate? = null,
): FinancialAccountDTO =
    FinancialAccountDTO(
        // ... existing fields ...
        initialBalance = initialBalance,
        initialBalanceDate = initialBalanceDate,
        // ...
    )
```

Also update the call in `create()` to pass `initialBalance = null, initialBalanceDate = null` (no initial balance info immediately after create, or load it separately).

> After `create()`, the initial balance was just created — either reload it or pass it from the command. The simplest: pass `initialBalance = command.initialBalance?.toDouble()` and `initialBalanceDate = command.initialBalanceDate` directly.

After `update()`, reload similarly from the command or pass through — follow the same pattern.

#### 5c. Add to `FinancialAccountDTO`

The `FinancialAccountDTO` data class (likely in a separate file in `graphql/account/`) needs:

```kotlin
val initialBalance: Double? = null,
val initialBalanceDate: LocalDate? = null,
```

#### 5d. Parse new fields in `toUpdateCommand()`

```kotlin
private fun Map<String, Any?>.toUpdateCommand(): UpdateFinancialAccountCommand {
    // ... existing fields ...
    return UpdateFinancialAccountCommand(
        // ... existing fields ...
        initialBalance = updateField("initialBalance") { (it as? Number)?.let { n -> BigDecimal(n.toString()) } },
        initialBalanceDate = updateField("initialBalanceDate") { value ->
            when (value) {
                null -> null
                is String -> LocalDate.parse(value)
                is LocalDate -> value
                else -> runCatching { LocalDate.parse(value.toString()) }.getOrNull()
            }
        },
    )
}
```

---

### Step 6 — Frontend: GraphQL Operations

**File:** `lib/graphql/operations/banking/queries.ts`

In `FINANCIAL_ACCOUNTS`, add to the return fields:

```graphql
initialBalance
initialBalanceDate
```

**File:** `lib/graphql/operations/banking/mutations.ts`

In `UPDATE_FINANCIAL_ACCOUNT`, the `$input: UpdateFinancialAccountInput!` already covers the new fields automatically (they're part of the input type). No change to the mutation document needed — the frontend already sends `input` as a whole object from the form.

---

### Step 7 — Frontend: Form Changes

**File:** `app/personal-finance/finance-transactions/account/CreateFinancialAccountDrawer.tsx`

#### 7a. Remove `!isEditing` gate (line 723)

Before:
```tsx
{effectiveSelectedType !== 'CARD' && !isEditing && (
```

After:
```tsx
{effectiveSelectedType !== 'CARD' && (
```

#### 7b. Pre-populate fields in the `reset()` call for edit mode

In the `useEffect` that runs when `accountToEdit?.id` is truthy (around line 402), the `reset()` call sets `initialBalance: undefined` and `initialBalanceDate: null`. Replace with:

```ts
initialBalance: accountToEdit.initialBalance !== undefined && accountToEdit.initialBalance !== null
  ? accountToEdit.initialBalance
  : undefined,
initialBalanceDate: accountToEdit.initialBalanceDate
  ? new Date(accountToEdit.initialBalanceDate)
  : null,
```

#### 7c. Update `onSubmit` to send fields on update

In `onSubmit` (around line 567), the existing block:
```ts
if (data.initialBalance !== undefined) {
  input.initialBalance = data.initialBalance;
  input.initialBalanceDate = ...;
}
```
...already runs regardless of `isEditing`, so it will be included in the update mutation automatically.

However, when the user **clears** the initial balance field (empties it), `data.initialBalance` becomes `undefined` and the block is skipped — meaning the backend sees the field as `Absent` and makes no change. This is actually incorrect for the "remove" case.

To support removal: if the user had an initial balance (i.e., `accountToEdit.initialBalance` was set) and now the field is cleared, we need to explicitly send `initialBalance: null`.

Add this logic in `onSubmit`, inside the `if (isEditing)` branch before building `input`:

```ts
if (isEditing && accountToEdit?.initialBalance !== undefined && accountToEdit.initialBalance !== null) {
  // Account had an initial balance. If user cleared it, send null explicitly.
  if (data.initialBalance === undefined || data.initialBalance === null) {
    input.initialBalance = null;
    input.initialBalanceDate = null;
  }
}
```

#### 7d. Zod schema — validation in edit mode

The existing `superRefine` runs for both create and edit modes and already handles:
- `initialBalance === 0` → error
- `initialBalance` provided without `initialBalanceDate` → error
- `initialBalanceDate` out of range → error

This is correct and needs no change.

---

### Step 8 — Run codegen

After the backend schema changes, run the GraphQL codegen to regenerate `web/src/lib/graphql/types/__generated__/graphql.ts`:

```bash
# from web/
npm run generate
```

This adds `initialBalance` and `initialBalanceDate` to the generated `FinancialAccount` TypeScript type, making `accountToEdit.initialBalance` type-safe.

---

## Testing Requirements

### Backend Unit Tests

**File:** `FinancialAccountServiceTest.kt` (or equivalent unit test)

| Scenario | Expected |
|---|---|
| Update with `initialBalance = Absent` | No BALANCE_ADJUSTMENT touched |
| Update with `initialBalance = Present(5000), no existing tx` | Creates new BALANCE_ADJUSTMENT CREDIT 5000; recompute called |
| Update with `initialBalance = Present(-2000), no existing tx` | Creates new BALANCE_ADJUSTMENT DEBIT 2000; recompute called |
| Update with `initialBalance = Present(6000)`, existing tx at same date | Updates amount only; recompute from that month |
| Update with `initialBalance = Present(6000)`, existing tx, new date is earlier | Updates date and amount; recompute from new (earlier) date |
| Update with `initialBalance = Present(6000)`, existing tx, new date is later | Updates date and amount; recompute from old (earlier) date |
| Update with `initialBalance = Present(null)`, existing tx | Soft-deletes existing tx; recompute from its month |
| Update with `initialBalance = Present(null)`, no existing tx | No-op (nothing to delete) |
| Update with `initialBalance = Present(0)` | Throws `ValidationException` (non-zero required) |
| Update with `initialBalance = Present(5000)`, `initialBalanceDate = Absent`, no existing tx | Throws `ValidationException` (date required) |
| Update with `initialBalance = Present(5000)`, `initialBalanceDate = Present(date in future)` | Throws `ValidationException` |
| Update with `initialBalance = Present(5000)`, `initialBalanceDate = Present(date too old)` | Throws `ValidationException(TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW)` |

### Backend Integration Tests

**File:** `FinancialAccountIntegrationTest.kt` (or new `EditInitialBalanceIntegrationTest.kt`)

- Create account without initial balance → update with balance → verify BALANCE_ADJUSTMENT created and snapshot recomputed
- Create account with initial balance → update with new amount → verify old tx updated; month summary reflects new amount
- Create account with initial balance on date D1 → update to date D2 (earlier) → verify snapshot recomputed from D2's month
- Create account with initial balance → update with `initialBalance: null` → verify tx soft-deleted; month summary zeroed

### Frontend Tests

No new test file required. Update `CreateFinancialAccountDrawer.test.tsx`:

- Add test: "shows initial balance fields in edit mode for BANK account"
- Add test: "hides initial balance fields in edit mode for CARD account"
- Add test: "pre-populates initial balance and date from accountToEdit"
- Add test: "submits initialBalance: null when user clears an existing initial balance"

---

## Execution Order

1. `TransactionEntityRepository.kt` — add two queries (compiles independently)
2. `FinancialAccountCommands.kt` — add fields to `UpdateFinancialAccountCommand`
3. `BankingErrorCode.kt` — add `INITIAL_BALANCE_DATE_REQUIRED` if not present (check first)
4. `FinancialAccountService.kt` — implement update logic (depends on 1–3)
5. `schema.graphqls` — add fields to `FinancialAccount` and `UpdateFinancialAccountInput`
6. `FinancialAccountDTO` (data class) — add `initialBalance` and `initialBalanceDate` fields
7. `FinancialAccountResolver.kt` — batch-load, parse, map (depends on 1–6)
8. Run `./gradlew :banking:compileKotlin` to verify backend compiles
9. Run `./gradlew :banking:testUnit` to run unit tests
10. Frontend: run `npm run generate` to update generated types
11. `queries.ts` — add fields to `FINANCIAL_ACCOUNTS`
12. `CreateFinancialAccountDrawer.tsx` — remove gate, pre-populate, clear logic
13. Run frontend unit tests: `npm test`

---

## What NOT to Do

- Do not call `transactionService.update()` for the BALANCE_ADJUSTMENT — it will be rejected by `validateBalanceAdjustmentUpdate` for date changes
- Do not add a `clearInitialBalance: Boolean` field; use `initialBalance: null` for the clear semantic
- Do not add the initial balance fields to CARD accounts — the feature is BANK-only
- Do not show the fields for `CREDIT`, `PAYMENT` account types in the frontend (those map to `CARD` type in the form)
- Do not remove the `!isEditing` guard for card accounts — keep `effectiveSelectedType !== 'CARD'` in place
- Do not bypass the existing date window validation; same rules apply in edit as in create
