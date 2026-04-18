---
title: Implementation Prompt — Holding Transaction Operations (Phase 1 + 2)
type: ai-prompt
category: investment-portfolio
status: ready
version: 1.0.0
tags: [ai-prompt, implementation, investment, holding, transaction, refactor]
---

# Implementation Prompt — Holding Transaction Operations

## Context

You are implementing a refactoring of the investment portfolio module in NeoTool, a Brazilian personal finance platform (Next.js 15 / React 18 / TypeScript / MUI + Kotlin / Micronaut / GraphQL / PostgreSQL).

**Before writing any code**, read these files in full — they are your primary sources of truth:

1. **Feature spec** (requirements + architecture decisions):
   `invistus/docs/03-features/investment-portfolio/holding-transaction-operations.md`

2. **Backend patterns**:
   - `invistus/docs/05-backend/patterns/service-pattern.md`
   - `invistus/docs/05-backend/kotlin/linting-standards.md` ← **CRITICAL: apply during generation, not after**

3. **Frontend patterns**:
   - `invistus/docs/07-frontend/patterns/form-pattern.md` ← **CRITICAL: every form must follow this**
   - `invistus/docs/07-frontend/patterns/graphql-mutation-pattern.md`

---

## What You Are Building

This is a **refactoring** — no new user-visible features. The external behavior of every operation must remain identical. You are restructuring internal code for maintainability and extensibility.

### Phase 1 — Backend: Side-Effect Handler Chain

**Goal**: Extract four private methods in `InvestmentTransactionService` into independent, testable handler components using the Chain of Responsibility pattern.

**File to read first** (the service being refactored):
`invistus/service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/investment/InvestmentTransactionService.kt`

**What to extract**:

| Current private method | New handler class |
|---|---|
| `syncInvestmentCashLedger(...)` | `CustodianCashLedgerHandler` |
| `syncMaturityPendingAction(...)` | `MaturityPendingActionHandler` |
| `syncCouponPendingAction(...)` + helpers | `CouponPendingActionHandler` |
| `syncPortfolioPerformance(...)` + `removePortfolioFlowSideEffects(...)` | `PortfolioFlowEventHandler` |

**Create these files** (new package `banking.service.investment.lifecycle`):

```
invistus/service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/investment/lifecycle/
├── InvestmentTransactionContext.kt
├── InvestmentTransactionSideEffectHandler.kt
├── InvestmentTransactionLifecycleOrchestrator.kt
├── CustodianCashLedgerHandler.kt
├── MaturityPendingActionHandler.kt
├── CouponPendingActionHandler.kt
└── PortfolioFlowEventHandler.kt
```

**Contracts** (implement exactly):

```kotlin
// InvestmentTransactionContext.kt
data class InvestmentTransactionContext(
    val transaction: InvestmentTransaction,
    val holding: Holding,
    val asset: Asset,
    val command: RecordInvestmentTransactionCommand?,
    val accountId: UUID,
    val userId: UUID,
)

// InvestmentTransactionSideEffectHandler.kt
interface InvestmentTransactionSideEffectHandler {
    fun appliesTo(context: InvestmentTransactionContext): Boolean = true
    fun onRecord(context: InvestmentTransactionContext)
    fun onUpdate(context: InvestmentTransactionContext, previous: InvestmentTransaction)
    fun onDelete(context: InvestmentTransactionContext)
}
```

**Atomicity rule — CRITICAL**: Every concrete `onRecord`/`onUpdate`/`onDelete` implementation must be annotated with `@Transactional(propagation = Propagation.MANDATORY)`. This ensures:
1. Handlers always run inside the caller's existing transaction (opened by `InvestmentTransactionService.record()`).
2. Any exception in a handler rolls back everything — including writes from previous handlers in the chain.
3. If a handler is accidentally called outside a transaction, Micronaut throws `IllegalTransactionStateException` immediately (fail-fast).

Never use `REQUIRES_NEW` on a handler — it would open a separate transaction that commits independently, breaking atomicity.

```kotlin
// Pattern for ALL handler implementations:
@Singleton
class CustodianCashLedgerHandler(...) : InvestmentTransactionSideEffectHandler {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onRecord(context: InvestmentTransactionContext) { ... }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onUpdate(context: InvestmentTransactionContext, previous: InvestmentTransaction) { ... }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun onDelete(context: InvestmentTransactionContext) { ... }
}
```

```kotlin

// InvestmentTransactionLifecycleOrchestrator.kt
@Singleton
class InvestmentTransactionLifecycleOrchestrator(
    private val handlers: List<InvestmentTransactionSideEffectHandler>,
) {
    fun onRecord(ctx: InvestmentTransactionContext) =
        handlers.filter { it.appliesTo(ctx) }.forEach { it.onRecord(ctx) }

    fun onUpdate(ctx: InvestmentTransactionContext, previous: InvestmentTransaction) =
        handlers.filter { it.appliesTo(ctx) }.forEach { it.onUpdate(ctx, previous) }

    fun onDelete(ctx: InvestmentTransactionContext) =
        handlers.filter { it.appliesTo(ctx) }.forEach { it.onDelete(ctx) }
}
```

**How to implement each handler**:
- Move the body of the corresponding private method(s) verbatim into the handler.
- The handler receives its dependencies via constructor injection (`@Singleton`).
- `CustodianCashLedgerHandler.onDelete` absorbs the logic currently in `removeCustodianLedgerLegsLinkedToInvestmentTransactions`.
- `PortfolioFlowEventHandler.onDelete` absorbs `removePortfolioFlowSideEffects`.
- `CouponPendingActionHandler` also moves `completeCurrentCouponPendingAction` and `upsertNextCouponPendingActionIfNeeded` — keep them as internal helpers within the handler (or as `internal fun` if needed by tests).

**After extracting**, update `InvestmentTransactionService`:
- Inject `InvestmentTransactionLifecycleOrchestrator` via constructor.
- Replace all 4+ explicit `sync*` / `remove*` calls in `record()`, `update()`, `delete()`, `deleteHolding()`, `recomputeHoldingState()` with `orchestrator.on{Record,Update,Delete}(context)`.
- Build the `InvestmentTransactionContext` inline before calling the orchestrator.
- Keep `removeCustodianLedgerLegsLinkedToInvestmentTransactions` as a private method in `InvestmentTransactionService` only if it's still called from somewhere besides the handler — otherwise delete it from the service.

**Micronaut DI note**: Micronaut auto-collects `List<InvestmentTransactionSideEffectHandler>` by scanning `@Singleton` implementations. No explicit binding needed.

**Do not change**:
- Any public API of `InvestmentTransactionService` (method signatures, return types).
- Any GraphQL resolver code.
- Any domain models (`Holding`, `InvestmentTransaction`, `Asset`).
- Any database migrations or repositories.

---

### Phase 2 — Frontend: Shared Hook + Base Component

**Goal**: Extract duplicated code from three large drawers (~33KB each) into a shared hook and a shared component. No visual or behavioral change for the user.

**Files to read first** (the drawers being refactored):
- `invistus/web/src/app/personal-finance/investments/components/RecordFixedIncomeDrawer.tsx`
- `invistus/web/src/app/personal-finance/investments/components/RecordEquityHoldingDrawer.tsx`
- `invistus/web/src/app/personal-finance/investments/components/RecordFundHoldingDrawer.tsx`
- `invistus/web/src/app/personal-finance/investments/components/InvestmentRecordDrawer.tsx` ← **do not modify**

**Create these files**:

```
invistus/web/src/app/personal-finance/investments/
├── hooks/
│   └── useRecordHoldingForm.ts      ← new
└── components/
    ├── BaseTransactionFields.tsx    ← new
    ├── FixedIncomeSpecificFields.tsx ← extracted from RecordFixedIncomeDrawer
    ├── EquitySpecificFields.tsx      ← extracted from RecordEquityHoldingDrawer
    └── FundSpecificFields.tsx        ← extracted from RecordFundHoldingDrawer
```

**`useRecordHoldingForm` contract**:

```typescript
import type { ZodSchema } from "zod"
import type { ApolloError } from "@apollo/client"
import type { UseFormReturn } from "react-hook-form"
import type { RecordInvestmentTransactionInput, InvestmentTransaction } from "@/lib/graphql/types/__generated__/graphql"

interface UseRecordHoldingFormConfig<TFields extends Record<string, unknown>> {
  schema: ZodSchema<TFields>
  financialAccountId: string | null
  editTransaction?: InvestmentTransaction | null
  editHoldingId?: string | null
  onBuildInput: (data: TFields) => RecordInvestmentTransactionInput
  onBuildUpdateInput?: (data: TFields) => UpdateInvestmentTransactionInput
  onSuccess?: () => void
}

interface UseRecordHoldingFormReturn<TFields> {
  form: UseFormReturn<TFields>
  onSubmit: (e: React.FormEvent) => void
  isLoading: boolean
  mutationError: ApolloError | undefined
}

export function useRecordHoldingForm<TFields extends Record<string, unknown>>(
  config: UseRecordHoldingFormConfig<TFields>,
): UseRecordHoldingFormReturn<TFields>
```

The hook must encapsulate:
1. `useMutation(RECORD_INVESTMENT_TRANSACTION)` with `refetchQueries: [INVESTMENT_HOLDINGS, INVESTMENT_CASH_BALANCE, INVESTMENT_PORTFOLIO_SUMMARY]`.
2. `useMutation(UPDATE_INVESTMENT_TRANSACTION)` for edit mode.
3. `useForm<TFields>({ resolver: zodResolver(schema) })`.
4. `form.handleSubmit` that calls `onBuildInput(data)` → record mutation, or `onBuildUpdateInput(data)` → update mutation.
5. `toast.success(t(...))` in `onCompleted` — use `useToast` from `@/shared/providers`.
6. Branch create vs. edit via `editTransaction != null`.

**`BaseTransactionFields` contract**:

```typescript
interface BaseTransactionFieldsProps {
  form: UseFormReturn<BaseTransactionFormData>
  direction: InvestmentTransactionDirection
  onDirectionChange: (d: InvestmentTransactionDirection) => void
  directionOptions: { inflow: string; outflow: string }
  minDate?: Date
  showNetValue?: boolean   // default true
  showTaxFields?: boolean  // default true
}
```

Must render (using `FormStack` + `FormRowStack` as per form-pattern.md):
- Direction toggle (inflow / outflow segmented button or select)
- `tradeDate` — `DatePickerField` with `size="medium"`
- `grossValue` — `MoneyInput` with `size="medium"`
- `netValue` — `MoneyInput` with `size="medium"` (when `showNetValue`)
- `incomeTax` — `MoneyInput` with `size="medium"` (when `showTaxFields`)
- `iof` — `MoneyInput` with `size="medium"` (when `showTaxFields`)

**Specific fields extraction**: Move all asset-class-specific JSX from each drawer into the corresponding `*SpecificFields` component. Pass `form` (from `FormProvider` context via `useFormContext`) and any additional props needed. Keep the full `FormProvider` wrapper and `Drawer.ActionButtons` in the drawer — do not move them into specific fields.

**Refactored drawer structure**:
```tsx
// RecordFixedIncomeDrawer.tsx (after refactor)
const { form, onSubmit, isLoading, mutationError } = useRecordHoldingForm({ ... })
return (
  <InvestmentRecordDrawer ...>
    {({ resolvedFinancialAccountId }) => (
      <FormProvider {...form}>
        <Box component="form" onSubmit={onSubmit} noValidate>
          <FormStack>
            <BaseTransactionFields form={form} ... />
            <FixedIncomeSpecificFields />
          </FormStack>
          <ErrorAlert error={mutationError} />
        </Box>
      </FormProvider>
    )}
    footer={<Drawer.ActionButtons ... />}
  </InvestmentRecordDrawer>
)
```

**Do not modify**:
- `InvestmentRecordDrawer.tsx` (account picker wrapper — keep as-is).
- GraphQL operation documents (`mutations.ts`, `queries.ts`).
- Any `investmentGridConfig.ts` or grid files.

**i18n**: Any new user-visible strings must be added to the existing `personalFinanceTranslations` namespace.

---

## Execution Order

1. Read all referenced files before writing any code.
2. Implement Phase 1 completely before starting Phase 2.
3. After Phase 1: verify `./gradlew ktlintCheck` passes (zero errors) before proceeding.
4. After Phase 2: verify `pnpm run typecheck` and `pnpm run lint` pass (zero errors).

## Success Criteria

- [ ] All 4 handler classes created in `banking.service.investment.lifecycle`.
- [ ] `InvestmentTransactionService` calls `orchestrator.on{Record,Update,Delete}` — no remaining `syncXxx` or `removeXxx` calls for these concerns.
- [ ] `useRecordHoldingForm` hook created and used in all three drawers.
- [ ] `BaseTransactionFields` component created and used in all three drawers.
- [ ] Each refactored drawer is under 400 lines.
- [ ] Zero ktlint errors. Zero TypeScript errors. Zero ESLint errors.
- [ ] No change to any public GraphQL API.
- [ ] No visual change to any drawer UI.

## Important Constraints

- **Do not invent new abstractions** beyond what is specified above.
- **Do not modify any database migration files**.
- **Do not add new GraphQL fields or mutations**.
- **Do not add docstrings or comments** to code you didn't change.
- **Refactoring only** — if you find a bug while reading the code, note it in a comment `// TODO: [COST_BASIS_NET_VALUE_FIX]` but do not fix it.
- Follow `invistus/docs/05-backend/kotlin/linting-standards.md` for all Kotlin formatting.
- Follow `invistus/docs/07-frontend/patterns/form-pattern.md` for all React form code.
