# Feature: Data de Liquidação on Close Position

## Summary

When a user closes an investment holding (maturity, early redemption, or transfer), the date the position legally closes (`maturityDate` / `tradeDate`) is often **not** the same day the money lands in their financial account. This gap — typically D+1 for fixed income (Renda Fixa), D+2 for equities — is the **settlement date** ("Data de Liquidação").

Currently `InvestmentTransaction.settlementDate` exists as a nullable column in the DB and domain model but is **never populated** through the manual close-position flow. The user has no way to record it, and the backend never writes it.

This feature wires `settlementDate` end-to-end through the `confirmHoldingMaturity` flow so that:

1. The user can always enter a "Data de Liquidação" on the close-position drawer.
2. The investment transaction is stored with the correct `settlementDate`.
3. When the user also chooses to credit the proceeds to a bank account, the `creditDate` defaults to `settlementDate` (not `maturityDate`).

---

## Business Rules

| Rule | Detail |
|---|---|
| **settlementDate is optional** | Null is valid — not every asset has a predictable settlement lag. |
| **settlementDate ≥ maturityDate** | A settlement date before the trade date makes no sense. Validate on the frontend (Zod) and the backend. |
| **settlementDate is independent of cashMovement** | The field must be captured and stored even when `shouldCreditToAccount = false`. |
| **creditDate defaults to settlementDate** | When the user enables "Creditar na conta", `creditDate` should pre-fill with `settlementDate` (not `maturityDate` as it does today). |
| **Applies to all closeReasons** | `MATURED`, `REDEEMED_EARLY`, `TRANSFERRED` — all go through the same flow and all benefit from this field. |

---

## What Already Exists (Do Not Re-create)

The storage layer is already done. Do not touch these:

- `InvestmentTransaction.settlementDate: LocalDate?` — domain model field, already nullable optional.
- `InvestmentTransactionEntity.settlement_date` — JPA column, already mapped.
- `InvestmentTransaction.settlementDate: Date` — already in the GraphQL `InvestmentTransaction` type in `schema.graphqls`.
- `RecordInvestmentTransactionCommand.settlementDate: LocalDate? = null` — already a parameter on the low-level record command.

The gap is entirely in the **close-position input path**: GraphQL input type → command → service → `RecordInvestmentTransactionCommand`.

---

## Files to Change

### 1. GraphQL Schema

**File:** `invistus/service/kotlin/banking/src/main/resources/graphql/schema.graphqls`

Add `settlementDate` to `ConfirmHoldingMaturityInput`:

```graphql
# Before
input ConfirmHoldingMaturityInput {
  holdingId: ID!
  maturityDate: Date!
  grossValue: Float!
  netValue: Float!
  incomeTax: Float
  iof: Float
  closeReason: HoldingCloseReason!
  cashMovement: MaturityCashMovementInput
}

# After
input ConfirmHoldingMaturityInput {
  holdingId: ID!
  maturityDate: Date!
  settlementDate: Date          # NEW — nullable, must be >= maturityDate if provided
  grossValue: Float!
  netValue: Float!
  incomeTax: Float
  iof: Float
  closeReason: HoldingCloseReason!
  cashMovement: MaturityCashMovementInput
}
```

No other changes to the schema file.

---

### 2. Backend Command

**File:** `invistus/service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/investment/HoldingMaturityConfirmCommand.kt`

Add `settlementDate` to `HoldingMaturityConfirmCommand`:

```kotlin
// Before
data class HoldingMaturityConfirmCommand(
    val holdingId: UUID,
    val maturityDate: LocalDate,
    val grossValue: BigDecimal,
    val netValue: BigDecimal,
    val incomeTax: BigDecimal?,
    val iof: BigDecimal?,
    val closeReason: HoldingCloseReason,
    val cashMovement: MaturityCashMovementCommand?,
)

// After
data class HoldingMaturityConfirmCommand(
    val holdingId: UUID,
    val maturityDate: LocalDate,
    val settlementDate: LocalDate?,     // NEW
    val grossValue: BigDecimal,
    val netValue: BigDecimal,
    val incomeTax: BigDecimal?,
    val iof: BigDecimal?,
    val closeReason: HoldingCloseReason,
    val cashMovement: MaturityCashMovementCommand?,
)
```

`MaturityCashMovementCommand` is unchanged.

---

### 3. Backend Resolver

**File:** `invistus/service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/pendingaction/PendingActionResolver.kt`

In `confirmHoldingMaturity()`, map the new field when building `HoldingMaturityConfirmCommand`:

```kotlin
// Existing block that builds cmd — add settlementDate:
val cmd =
    HoldingMaturityConfirmCommand(
        holdingId = UUID.fromString(command["holdingId"] as String),
        maturityDate = command["maturityDate"].toLocalDate(),
        settlementDate = (command["settlementDate"] as? Any)?.toLocalDate(),  // NEW — nullable
        grossValue = BigDecimal((command["grossValue"] as Number).toString()),
        netValue = BigDecimal((command["netValue"] as Number).toString()),
        incomeTax = (command["incomeTax"] as? Number)?.let { BigDecimal(it.toString()) },
        iof = (command["iof"] as? Number)?.let { BigDecimal(it.toString()) },
        closeReason = HoldingCloseReason.valueOf(command["closeReason"] as String),
        cashMovement = cashMovement,
    )
```

The `toLocalDate()` helper already handles both `LocalDate` and ISO string — reuse it as-is.

---

### 4. Backend Service

**File:** `invistus/service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/investment/HoldingMaturityConfirmService.kt`

Two changes:

**a) Validate settlementDate ≥ maturityDate** (add after the existing maturity date validation):

```kotlin
command.settlementDate?.let { sd ->
    if (sd.isBefore(command.maturityDate)) {
        throw ValidationException(
            BankingErrorCode.HOLDING_MATURITY_INVALID_STATE,
            field = "settlementDate",
            parameters = mapOf("reason" to "Settlement date cannot be before maturity date"),
        )
    }
}
```

**b) Pass `settlementDate` to `RecordInvestmentTransactionCommand`:**

```kotlin
// Existing block — add settlementDate:
val recordCommand =
    RecordInvestmentTransactionCommand(
        financialAccountId = holding.financialAccountId,
        assetId = holding.assetId,
        direction = InvestmentTransactionDirection.OUTFLOW,
        type = InvestmentTransactionType.MATURITY,
        quantity = null,
        grossValue = command.grossValue,
        netValue = command.netValue,
        incomeTax = command.incomeTax,
        iof = command.iof,
        tradeDate = command.maturityDate,
        settlementDate = command.settlementDate,    // NEW
        source = InvestmentSource.MANUAL,
        providerInvestmentId = holding.providerInvestmentId,
    )
```

No other changes to the service.

---

### 5. Frontend — Form

**File:** `invistus/web/src/app/personal-finance/investments/MaturityConfirmDrawer.tsx`

#### 5a. Add `settlementDate` to the form data type

```ts
// Before
type MaturityConfirmFormData = {
  grossValue: number | undefined;
  netValue: number | undefined;
  incomeTax: number | undefined;
  iof: number | undefined;
  maturityDate: Date | null;
  closeReason: HoldingCloseReason;
  shouldCreditToAccount: boolean;
  bankAccountId: string;
  creditAmount: number | undefined;
  creditDate: Date | null;
};

// After — add settlementDate
type MaturityConfirmFormData = {
  grossValue: number | undefined;
  netValue: number | undefined;
  incomeTax: number | undefined;
  iof: number | undefined;
  maturityDate: Date | null;
  settlementDate: Date | null;   // NEW
  closeReason: HoldingCloseReason;
  shouldCreditToAccount: boolean;
  bankAccountId: string;
  creditAmount: number | undefined;
  creditDate: Date | null;
};
```

#### 5b. Add `settlementDate` to the Zod schema

The existing `step1Schema` validates the first block of fields. Add `settlementDate` and its cross-field constraint:

```ts
const step1Schema = z
  .object({
    grossValue: z.number().positive("Gross must be positive"),
    netValue: z.number().min(0),
    incomeTax: z.number().min(0).optional(),
    iof: z.number().min(0).optional(),
    maturityDate: z.date(),
    settlementDate: z.date().nullable().optional(),   // NEW
    closeReason: z.nativeEnum(HoldingCloseReason),
  })
  .refine((d) => d.netValue <= d.grossValue, {
    message: "Net must be ≤ gross",
    path: ["netValue"],
  })
  .refine(                                             // NEW cross-field validation
    (d) => !d.settlementDate || !d.maturityDate || d.settlementDate >= d.maturityDate,
    {
      message: "Data de Liquidação não pode ser anterior à data de vencimento",
      path: ["settlementDate"],
    },
  );
```

#### 5c. Update `defaultValues` in `useForm`

```ts
defaultValues: {
  // ... existing fields ...
  settlementDate: null,   // NEW
  creditDate: null,
},
```

#### 5d. Update the `useEffect` reset to populate `settlementDate`

`settlementDate` should default to `null` (user can optionally fill it). The `creditDate` should default to `settlementDate` when it exists, otherwise fall back to `maturityDate`:

```ts
React.useEffect(() => {
  if (!open || !holding) return;
  setStep1Error(null);
  const g = holding.grossValue ?? 0;
  const n = holding.netValue ?? g;
  const defaultMaturityDate = holding.maturityDate
    ? dayjs(holding.maturityDate).toDate()
    : dayjs().toDate();

  methods.reset({
    grossValue: g,
    netValue: n,
    incomeTax: holding.incomeTax ?? undefined,
    iof: holding.iof ?? undefined,
    maturityDate: defaultMaturityDate,
    settlementDate: null,                  // NEW — always start empty; user fills if known
    closeReason: HoldingCloseReason.Matured,
    shouldCreditToAccount: false,
    bankAccountId: selectableBanks[0]?.id ?? "",
    creditAmount: n,
    creditDate: defaultMaturityDate,       // keeps falling back to maturityDate on reset
  });
}, [holding, methods, open, selectableBanks]);
```

#### 5e. Add a `useWatch` for `settlementDate`

```ts
const watchedSettlementDate = useWatch({ control: methods.control, name: "settlementDate" });
```

#### 5f. When the user sets `settlementDate`, auto-update `creditDate`

Add a `useEffect` that syncs `creditDate` to `settlementDate` whenever `settlementDate` changes **and** `shouldCreditToAccount` is true:

```ts
React.useEffect(() => {
  if (!watchedShouldCreditToAccount) return;
  if (watchedSettlementDate) {
    methods.setValue("creditDate", watchedSettlementDate, { shouldDirty: false });
  }
}, [watchedSettlementDate, watchedShouldCreditToAccount, methods]);
```

#### 5g. Render the `settlementDate` field in the JSX

Place it immediately **after** the `maturityDate` / `closeReason` row, always visible (not conditional):

```tsx
{/* Existing row: maturityDate + closeReason */}
<FormRowStack alignItems="flex-start">
  <Box sx={{ flex: 1, minWidth: 0 }}>
    <DatePickerField
      name="maturityDate"
      label={t("investments.pendingActions.maturityDrawer.maturityDate")}
      size="medium"
    />
  </Box>
  <Box sx={{ flex: 1, minWidth: 0 }}>
    <SelectField
      name="closeReason"
      label={t("investments.pendingActions.maturityDrawer.closeReason")}
      options={closeReasonOptions}
      size="medium"
    />
  </Box>
</FormRowStack>

{/* NEW row: settlementDate */}
<DatePickerField
  name="settlementDate"
  label={t(
    "investments.pendingActions.maturityDrawer.settlementDate",
    { defaultValue: "Data de Liquidação" },
  )}
  size="medium"
/>
```

#### 5h. Update `submit` to include `settlementDate` in mutation variables

```ts
const submit = async () => {
  if (!holding) return;
  const values = methods.getValues();
  const parsed = step1Schema.safeParse({
    grossValue: values.grossValue,
    netValue: values.netValue,
    incomeTax: values.incomeTax,
    iof: values.iof,
    maturityDate: values.maturityDate,
    settlementDate: values.settlementDate,   // NEW — include in Zod parse
    closeReason: values.closeReason,
  });

  if (!parsed.success) {
    setStep1Error(parsed.error.issues[0]?.message || "Invalid");
    return;
  }

  const { grossValue, netValue, incomeTax, iof, closeReason, maturityDate, settlementDate } = parsed.data;
  const selectedMaturityDateStr = dayjs(maturityDate).format("YYYY-MM-DD");
  const settlementDateStr = settlementDate
    ? dayjs(settlementDate).format("YYYY-MM-DD")
    : undefined;
  const cashMovementDate = values.creditDate
    ? dayjs(values.creditDate).format("YYYY-MM-DD")
    : (settlementDateStr ?? selectedMaturityDateStr);   // prefer settlementDate fallback

  // ... cashMovement construction unchanged ...

  await confirmMaturity({
    variables: {
      command: {
        holdingId: holding.id,
        maturityDate: selectedMaturityDateStr,
        settlementDate: settlementDateStr,    // NEW
        grossValue,
        netValue,
        incomeTax,
        iof,
        closeReason,
        cashMovement,
      },
    },
  });
  // ...
};
```

---

### 6. Frontend — GraphQL Mutation

**File:** `invistus/web/src/lib/graphql/operations/banking/pendingActionOperations.ts`

Find the `CONFIRM_HOLDING_MATURITY` mutation string and add `settlementDate`:

```ts
// The mutation input variables block — add settlementDate
const CONFIRM_HOLDING_MATURITY = gql`
  mutation ConfirmHoldingMaturity($command: ConfirmHoldingMaturityInput!) {
    confirmHoldingMaturity(command: $command) {
      maturityTransaction {
        id
        type
        grossValue
        tradeDate
        settlementDate     # NEW — return it so the frontend can confirm what was stored
      }
      bankingTransaction {
        id
        amount
        date
        type
      }
      pendingAction {
        id
        status
      }
    }
  }
`;
```

The generated TypeScript types (`pendingActionOperations.generated.ts`) must be regenerated via the usual codegen command after the schema and operation are updated.

---

## No Database Migration Needed

The `settlement_date` column already exists on the `investment_transactions` table (mapped by `InvestmentTransactionEntity`). No Flyway migration is required.

---

## Testing Notes

- **Backend unit test** (`HoldingMaturityConfirmServiceTest` or a new file): add a case where `settlementDate` is provided and verify it is stored on the resulting `InvestmentTransaction`. Add a case where `settlementDate < maturityDate` and verify `ValidationException` is thrown.
- **Frontend**: existing tests should continue to pass. Add a test that renders `MaturityConfirmDrawer`, fills in a `settlementDate`, enables credit, and asserts that `creditDate` is auto-populated with the same date.

---

## Out of Scope

- No change to `MaturityCashMovementInput` or `MaturityCashMovementCommand` — the credit date is a banking transaction concern and is already separate from `settlementDate`.
- No change to the `HoldingEntity` — settlement date belongs on the transaction, not the holding.
- No change to how pending actions are created or displayed — this only affects the confirmation step.
