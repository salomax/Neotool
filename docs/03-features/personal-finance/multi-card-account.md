# Multi-Card Account (Umbrella Cards)

## Overview

A credit card account can represent multiple physical or virtual cards under the same account
(e.g. bank-issued virtual cards, partner cards, replaced cards). Today the model forces a
single `maskedNumber` (last-4 digits) per account, which is painful to set up and doesn't match
how users think about their cards.

This feature introduces an **`isMultiCard` toggle** on card accounts. When enabled:
- The last-4 field is hidden in the creation/edit drawer
- The account stores no `maskedNumber` / `card_last_four_digits`
- The account is skipped during bill-import auto-matching (user picks manually)
- The UI shows "Multiple cards" instead of "•••• XXXX"

---

## Business Rules

| Rule | Detail |
|---|---|
| `isMultiCard = true` clears last-4 | `maskedNumber` and `card_last_four_digits` are stored as NULL; any submitted value is ignored |
| Only card accounts | `isMultiCard` only applies when `type IN ('CREDIT', 'PAYMENT')`. BANK accounts never have it set |
| Auto-match exclusion | Because `card_last_four_digits` is NULL, `findCardAccountsByLastFourDigits` already excludes multi-card accounts — **no query change needed** |
| Display in selectors | `isMultiCard = true` and no `transaction.cardIdentifier` → show "Multiple cards" label instead of `•••• XXXX` |
| Transaction row | `transaction.cardIdentifier` (already stored on import) wins over account label. Fallback: `isMultiCard = true` → "Multiple cards"; single last-4 on account → `•••• XXXX` |
| Toggling back | User can turn off `isMultiCard` and provide a last-4 in an `updateFinancialAccount` call; the account behaves normally again |

---

## Implementation Plan

### 1. DB Migration — `V0_12__add_multi_card_to_card_data.sql`

**Path:** `service/kotlin/banking/src/main/resources/db/migration/V0_12__add_multi_card_to_card_data.sql`

```sql
ALTER TABLE banking.financial_account_card_data
    ADD COLUMN is_multi_card BOOLEAN NOT NULL DEFAULT FALSE;
```

No data backfill needed — all existing accounts default to `false` (single-card behaviour preserved).

---

### 2. Domain — `CardData` and `CardDataPatch`

**Path:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/account/CreateFinancialAccountCommand.kt`

In `CardData`, add:
```kotlin
data class CardData(
    val maskedNumber: String? = null,
    val bin: String? = null,
    val cardNetwork: CreditCardNetwork? = null,
    val cardLevel: CreditCardLevel? = null,
    val creditLimit: BigDecimal? = null,
    val availableCreditLimit: BigDecimal? = null,
    val minimumPaymentPercentage: BigDecimal? = null,
    val closingDay: Int? = null,
    val dueDay: Int? = null,
    val linkedBankAccountId: UUID? = null,
    val isMultiCard: Boolean = false,   // <-- add this
)
```

In `CardDataPatch`, add:
```kotlin
data class CardDataPatch(
    val maskedNumber: UpdateField<String> = UpdateField.Absent,
    val bin: UpdateField<String> = UpdateField.Absent,
    val cardNetwork: UpdateField<CreditCardNetwork> = UpdateField.Absent,
    val cardLevel: UpdateField<CreditCardLevel> = UpdateField.Absent,
    val creditLimit: UpdateField<BigDecimal> = UpdateField.Absent,
    val availableCreditLimit: UpdateField<BigDecimal> = UpdateField.Absent,
    val minimumPaymentPercentage: UpdateField<BigDecimal> = UpdateField.Absent,
    val closingDay: UpdateField<Int> = UpdateField.Absent,
    val dueDay: UpdateField<Int> = UpdateField.Absent,
    val linkedBankAccountId: UpdateField<UUID> = UpdateField.Absent,
    val isMultiCard: UpdateField<Boolean> = UpdateField.Absent,  // <-- add this
)
```

---

### 3. Entity — `FinancialAccountCardDataEntity`

**Path:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/account/FinancialAccountCardDataEntity.kt`

Add the column mapping:
```kotlin
@Column(name = "is_multi_card", nullable = false)
open var isMultiCard: Boolean = false,
```

---

### 4. Mapper — `FinancialAccountMapper`

**Path:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/account/FinancialAccountMapper.kt`

In `toDomain`, the `CardData` construction gains:
```kotlin
CardData(
    maskedNumber = c.maskedNumber,
    // ... existing fields ...
    isMultiCard = c.isMultiCard,
)
```

In `toCardDataEntity`:
```kotlin
FinancialAccountCardDataEntity(
    // ... existing fields ...
    isMultiCard = data.isMultiCard,
)
```

Update the card data entity in-place also (used on update path — wherever `cardData` entity fields are mutated): set `entity.isMultiCard = domain.isMultiCard`.

---

### 5. Service — `FinancialAccountService`

**Path:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/account/FinancialAccountService.kt`

#### `create` path

Replace the existing masked-number normalization block:

```kotlin
// BEFORE
val normalizedCreateCardMaskedNumber = normalizeCardMaskedNumber(command.cardData?.maskedNumber)

// AFTER
val isMultiCard = command.cardData?.isMultiCard ?: false
val normalizedCreateCardMaskedNumber =
    if (isMultiCard) null
    else normalizeCardMaskedNumber(command.cardData?.maskedNumber)
```

When building the `FinancialAccount` to save:
```kotlin
cardLastFourDigits = extractLastFourDigits(normalizedCreateCardMaskedNumber),
cardData = command.cardData?.copy(
    maskedNumber = normalizedCreateCardMaskedNumber,
    isMultiCard = isMultiCard,
),
```

#### `update` path

In `mergeCardData`, add resolution for the new field:
```kotlin
private fun mergeCardData(existing: CardData?, patch: CardDataPatch?): CardData? {
    if (patch == null) return existing
    val resolvedIsMultiCard = patch.isMultiCard.resolve(existing?.isMultiCard) ?: false
    return CardData(
        maskedNumber = patch.maskedNumber.resolve(existing?.maskedNumber),
        // ... existing fields ...
        isMultiCard = resolvedIsMultiCard,
    )
}
```

After `mergeCardData` and before `normalizeCardMaskedNumber`, add:
```kotlin
val resolvedIsMultiCard = resolvedCardData?.isMultiCard ?: false
val normalizedUpdateCardMaskedNumber =
    if (resolvedIsMultiCard) null
    else normalizeCardMaskedNumber(resolvedCardData?.maskedNumber)
val validatedResolvedCardData = resolvedCardData?.copy(
    maskedNumber = normalizedUpdateCardMaskedNumber,
    isMultiCard = resolvedIsMultiCard,
)
```

And in `existing.copy(...)`:
```kotlin
cardLastFourDigits = extractLastFourDigits(normalizedUpdateCardMaskedNumber),
```

---

### 6. GraphQL Schema

**Path:** `service/kotlin/banking/src/main/resources/graphql/schema.graphqls`

In the `CardData` type, add:
```graphql
type CardData {
  maskedNumber: String
  bin: String
  cardNetwork: CreditCardNetwork
  cardLevel: CreditCardLevel
  creditLimit: Float
  availableCreditLimit: Float
  minimumPaymentPercentage: Float
  closingDay: Int
  dueDay: Int
  linkedBankAccountId: ID
  """When true, this account represents multiple/virtual cards with no specific last-4."""
  isMultiCard: Boolean!
}
```

In the `CardDataInput` input type, add:
```graphql
input CardDataInput {
  maskedNumber: String
  bin: String
  cardNetwork: CreditCardNetwork
  cardLevel: CreditCardLevel
  creditLimit: Float
  closingDay: Int
  dueDay: Int
  """Set to true to mark this account as an umbrella for multiple/virtual cards."""
  isMultiCard: Boolean
}
```

---

### 7. GraphQL DTO — `CardDataDTO`

**Path:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/account/FinancialAccountDTO.kt`

```kotlin
data class CardDataDTO(
    val maskedNumber: String?,
    val bin: String?,
    val cardNetwork: CreditCardNetwork?,
    val cardLevel: CreditCardLevel?,
    val creditLimit: Double?,
    val availableCreditLimit: Double?,
    val minimumPaymentPercentage: Double?,
    val closingDay: Int?,
    val dueDay: Int?,
    val linkedBankAccountId: UUID?,
    val isMultiCard: Boolean,   // <-- add this
)
```

---

### 8. GraphQL Resolver — `FinancialAccountResolver`

**Path:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/account/FinancialAccountResolver.kt`

**Query (DTO construction)** — when building `CardDataDTO` from `cardData`:
```kotlin
CardDataDTO(
    maskedNumber = it.maskedNumber,
    // ... existing fields ...
    isMultiCard = it.isMultiCard,
)
```

**Create mutation** — when reading `cardDataInput` from the GraphQL arguments:
```kotlin
CardData(
    maskedNumber = it["maskedNumber"] as? String,
    // ... existing fields ...
    isMultiCard = (it["isMultiCard"] as? Boolean) ?: false,
)
```

**Update mutation** — when building `CardDataPatch`:
```kotlin
CardDataPatch(
    maskedNumber = it.updateField("maskedNumber") { value -> value as? String },
    // ... existing fields ...
    isMultiCard = it.updateField("isMultiCard") { value -> value as? Boolean },
)
```

---

### 9. Frontend — GraphQL Query

**Path:** `web/src/lib/graphql/operations/banking/queries.ts`

In the `FINANCIAL_ACCOUNTS` query (and any other query that selects `cardData`), add `isMultiCard` to the `cardData` fragment:

```graphql
cardData {
  maskedNumber
  bin
  cardNetwork
  cardLevel
  creditLimit
  availableCreditLimit
  closingDay
  dueDay
  isMultiCard   # <-- add
}
```

Run `npm run codegen` (or equivalent) to regenerate `queries.generated.ts` and the GraphQL types.

---

### 10. Frontend — `billImportTypes.ts`

**Path:** `web/src/app/personal-finance/finance-transactions/bill/billImportTypes.ts`

Add `isMultiCard` to `CardAccountOption`:

```ts
export interface CardAccountOption {
  id: string;
  name: string;
  issuerId: string | null;
  issuerName: string | null;
  issuerKey: string | null;
  maskedNumber: string | null;
  isMultiCard: boolean;   // <-- add
}
```

Wherever `CardAccountOption` objects are constructed from `FinancialAccount` data (search for `maskedNumber:` in bill import files), also map `isMultiCard: account.cardData?.isMultiCard ?? false`.

---

### 11. Frontend — `CreateFinancialAccountDrawer`

**Path:** `web/src/app/personal-finance/finance-transactions/account/CreateFinancialAccountDrawer.tsx`

#### Form schema

Add `multiCard` to `CreateAccountFormData`:
```ts
type CreateAccountFormData = {
  // ... existing fields ...
  multiCard: boolean;
};
```

Add to the Zod schema:
```ts
multiCard: z.boolean().default(false),
```

Add to `defaultValues`:
```ts
multiCard: false,
```

When editing an existing account, hydrate from:
```ts
multiCard: accountToEdit.cardData?.isMultiCard ?? false,
```

#### Toggle in the form

Inside the `isBillingCycleCard` block (where `maskedNumber` currently renders), add a MUI `FormControlLabel` + `Switch` **above** the last-4 field:

```tsx
{isBillingCycleCard && (
  <>
    <FormControlLabel
      control={
        <Switch
          checked={watchedMultiCard}
          onChange={(e) => setValue('multiCard', e.target.checked, { shouldValidate: true })}
          data-testid="multi-card-toggle"
        />
      }
      label={t('financeTransactions.createAccount.step2.fields.multiCard')}
    />

    {!watchedMultiCard && (
      <Stack direction="row" spacing={2}>
        <TextField
          name="maskedNumber"
          label={t('financeTransactions.createAccount.step2.fields.maskedNumber')}
          placeholder="1234"
          InputProps={{ startAdornment: <InputAdornment position="start">••••</InputAdornment> }}
          inputProps={{ 'data-testid': 'card-last-four-input' }}
          sx={{ flex: 1 }}
          size="medium"
        />
        {/* ... creditLimit field stays ... */}
      </Stack>
    )}
    {/* closing/due day stack stays, always visible for billing-cycle cards */}
  </>
)}
```

Add the watcher:
```ts
const watchedMultiCard = useWatch({ control, name: 'multiCard' });
```

#### Submit handler

In `onSubmit`, when building `input.cardData`, include `isMultiCard`:
```ts
input.cardData = {
  cardNetwork: data.cardNetwork,
  cardLevel: data.cardLevel,
  maskedNumber: data.multiCard ? undefined : normalizedMaskedNumber,
  creditLimit: data.creditLimit,
  closingDay: data.closingDay,
  dueDay: data.dueDay,
  isMultiCard: data.multiCard,
};
```

---

### 12. Frontend — `TransactionAccountCell`

**Path:** `web/src/app/personal-finance/finance-transactions/transaction/TransactionAccountCell.tsx`

Update `getCardLastFourDigits` to return a sentinel when the account is multi-card and there is no transaction-level identifier:

```ts
function getCardDisplayLabel(
  account: FinancialAccount,
  transaction: Transaction,
  multipleCardsLabel: string,
): string | null {
  // Transaction-level identifier always wins
  const raw = transaction.cardIdentifier || '';
  const txDigits = raw.replace(/\D/g, '').slice(-4);
  if (txDigits.length === 4) return txDigits;

  // Fall back to account last-4
  if (account.cardData?.isMultiCard) return null; // caller shows multipleCardsLabel
  const accountRaw = account.cardData?.maskedNumber || '';
  const accountDigits = accountRaw.replace(/\D/g, '').slice(-4);
  return accountDigits.length === 4 ? accountDigits : null;
}
```

In the render:
```tsx
const isMultiCard = account.cardData?.isMultiCard ?? false;
const last4 = showCardDetails ? getCardDisplayLabel(account, transaction, '') : null;

{showCardDetails && (
  <Stack direction="row" spacing={0.5} alignItems="center">
    <CardNetworkIcon network={account.cardData?.cardNetwork} size={18} />
    {last4 ? (
      <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
        •••• {last4}
      </Typography>
    ) : isMultiCard ? (
      <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
        {t('financeTransactions.multipleCards')}
      </Typography>
    ) : null}
  </Stack>
)}
```

---

### 13. Frontend — `FinancialAccountSelector`

**Path:** `web/src/app/personal-finance/finance-transactions/account/FinancialAccountSelector.tsx`

Update `getCardLastFourDigits` to handle multi-card:
```ts
const getCardSubtitle = (account: FinancialAccount, multipleCardsLabel: string): string | null => {
  if (account.cardData?.isMultiCard) return multipleCardsLabel;
  const raw = account.cardData?.maskedNumber || '';
  const digits = raw.replace(/\D/g, '').slice(-4);
  return digits.length === 4 ? `•••• ${digits}` : null;
};
```

Use this helper in both the **selected account header** (line ~225) and the **popover list items** (line ~429), replacing the current `last4 && <Typography>•••• {last4}</Typography>` pattern.

Pass `t('financeTransactions.multipleCards')` as the label. No need to check `last4` for null — the helper returns the label directly.

---

### 14. i18n Keys

**Files:** `web/src/app/personal-finance/i18n/locales/pt.json` and `en.json`

Add:

| Key | PT | EN |
|---|---|---|
| `financeTransactions.createAccount.step2.fields.multiCard` | `Múltiplos cartões / virtuais` | `Multiple / virtual cards` |
| `financeTransactions.multipleCards` | `Múltiplos cartões` | `Multiple cards` |

---

## What Does NOT Change

- `findCardAccountsByLastFourDigits` query — multi-card accounts have `card_last_four_digits = NULL` so they are already excluded from auto-matching
- `useCardMatchingState.findExactAccountMatches` — `option.maskedNumber?.endsWith(...)` returns falsy when `maskedNumber` is null; no change needed
- `BillImportService.resolveCardMatchProjection` — no change; the DB query handles exclusion
- Transaction entity — `cardIdentifier` already exists and already takes display priority
- Bill cycle logic (`CreditCardBillService`) — unaffected; operates on `financial_account_id`, not last-4

---

## Files Changed Summary

### Backend

| File | Change |
|---|---|
| `V0_12__add_multi_card_to_card_data.sql` | New migration — add `is_multi_card` column |
| `domain/account/CreateFinancialAccountCommand.kt` | Add `isMultiCard` to `CardData` and `CardDataPatch` |
| `model/account/FinancialAccountCardDataEntity.kt` | Add `isMultiCard` column mapping |
| `model/account/FinancialAccountMapper.kt` | Map `isMultiCard` in `toDomain`, `toCardDataEntity` |
| `service/account/FinancialAccountService.kt` | Skip masked-number validation when `isMultiCard = true`; null out last-4 |
| `src/main/resources/graphql/schema.graphqls` | Add `isMultiCard: Boolean!` to `CardData`; `isMultiCard: Boolean` to `CardDataInput` |
| `graphql/account/FinancialAccountDTO.kt` | Add `isMultiCard` to `CardDataDTO` |
| `graphql/account/FinancialAccountResolver.kt` | Pass `isMultiCard` through create, update, and query paths |

### Frontend

| File | Change |
|---|---|
| `web/src/lib/graphql/operations/banking/queries.ts` | Add `isMultiCard` to `cardData` selection |
| `web/src/lib/graphql/types/__generated__/graphql.ts` | Regenerate (codegen) |
| `web/src/lib/graphql/operations/banking/queries.generated.ts` | Regenerate (codegen) |
| `web/src/app/personal-finance/finance-transactions/bill/billImportTypes.ts` | Add `isMultiCard` to `CardAccountOption` |
| `web/src/app/personal-finance/finance-transactions/account/CreateFinancialAccountDrawer.tsx` | Add `multiCard` field, toggle, conditional last-4 field, include in submit |
| `web/src/app/personal-finance/finance-transactions/transaction/TransactionAccountCell.tsx` | Update display logic — show "Multiple cards" when `isMultiCard = true` and no `cardIdentifier` |
| `web/src/app/personal-finance/finance-transactions/account/FinancialAccountSelector.tsx` | Update card subtitle helper to handle multi-card label |
| `web/src/app/personal-finance/i18n/locales/pt.json` | Add 2 i18n keys |
| `web/src/app/personal-finance/i18n/locales/en.json` | Add 2 i18n keys |

---

## Testing Requirements

### Unit Tests (Backend)

File: `FinancialAccountServiceTest.kt`

| Scenario | Expected |
|---|---|
| Create card with `isMultiCard = true` | `cardLastFourDigits = null`, `cardData.maskedNumber = null`, `cardData.isMultiCard = true` |
| Create card with `isMultiCard = true` and `maskedNumber` provided | `maskedNumber` ignored, stored as null |
| Create card with `isMultiCard = false` and valid `maskedNumber` | Behaves as before; `cardLastFourDigits` extracted normally |
| Update card to set `isMultiCard = true` | Clears `maskedNumber` and `cardLastFourDigits` |
| Update card to set `isMultiCard = false` with new `maskedNumber` | Validates and stores the new last-4 |

### Integration Tests (Backend)

File: `FinancialAccountIT.kt` (or equivalent)

- Create multi-card account → verify `isMultiCard = true` is returned in `financialAccounts` query
- Create multi-card account → run `findCardAccountsByLastFourDigits` with any last-4 → verify account is NOT returned
- Toggle `isMultiCard` off with a last-4 → verify account IS returned by matching query
