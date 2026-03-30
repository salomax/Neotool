# Personal Finance — Code Review (March 2026)

Covers backend (`service/kotlin/banking/`) and frontend (`web/src/app/personal-finance/`).
Scope: transactions, monthly balance snapshots, credit card bills.
Excludes: investments / asset portfolio.

---

## Severity legend

| Label | Meaning |
|-------|---------|
| **BUG** | Incorrect behavior, data corruption, silent failure |
| **PERF** | Measurable performance problem |
| **REFACTOR** | Works correctly but hard to maintain/extend |
| **MINOR** | Low-risk smell, type safety gap, or trivial inconsistency |

---

## Backend (Kotlin banking service)

### BUG — High

#### B1: PAID bill status never reverts when payment is deleted
**File:** `repo/transaction/DefaultCreditCardBillRepository.kt` — `computeStatus()`

```kotlin
if (current == BillStatus.PAID) return BillStatus.PAID  // never reverts
```

If a user deletes the payment transaction that caused the bill to be marked PAID, the bill should revert to CLOSED or OVERDUE. Currently it stays PAID forever.
The `BillStatus.kt` comment even says "only reversed if a payment transaction is deleted" — but the code doesn't implement that.

**Fix:** remove the early-return guard; let `computeStatus()` re-evaluate based on current `totalAmount` and dates on every call.

---

#### B2: Transfer peer transaction has no referential integrity
**File:** `domain/transaction/Transaction.kt` — `transferPeerTransactionId: UUID?`

The peer transaction ID is a raw UUID with no FK constraint and no validation that the peer belongs to the same tenant. If the peer is deleted, the reference becomes orphaned silently.

**Fix:** add a DB-level FK constraint or, at minimum, validate the peer exists and belongs to the same `accountId` in `TransactionService`.

---

### BUG — Medium

#### B3: Ambiguous dirty-month boundary in markDirtyFromMonth
**File:** `repo/transaction/MonthlyBalanceSnapshotEntityRepository.kt:112`

```sql
AND (year > :year OR (year = :year AND month >= :month))
```

Uses `>=` for the same-year case. This is semantically inconsistent with `findLatestCleanBefore` which uses strict `<`. Intent is: mark the affected month **and** all later months as dirty. The `>=` is functionally correct but the asymmetry is confusing and a refactor trap.

**Fix:** document the intent explicitly in a comment, or make both queries use the same operator convention.

---

### PERF — Medium

#### P1: Missing composite index on transactions(financial_account_id, date)
**Files:** native queries in `DefaultTransactionRepository.kt` (month totals, snapshot recompute, month summary)

All high-traffic queries filter on `financial_account_id + date range`. Without a composite index on `(financial_account_id, deleted_at, date)`, every snapshot recompute is a full table scan filtered in-memory.

**Fix:** add a Flyway migration with:
```sql
CREATE INDEX idx_transactions_account_date
  ON banking.transactions(financial_account_id, date)
  WHERE deleted_at IS NULL;
```

#### P2: entityManager.clear() used as a blunt instrument
**Files:** `DefaultMonthlyBalanceSnapshotRepository.kt:58`, `DefaultCreditCardBillRepository.kt:75`

After native UPSERT/UPDATE, both files call `entityManager.clear()` which evicts the entire persistence context. This is safe but flushes all cached entities, not just the affected one — a performance hazard under bulk operations.

**Fix:** use `entityManager.detach(entity)` for the specific entity, or annotate the repository method with `@Modifying(clearAutomatically = true)` if using Spring Data style.

---

### REFACTOR

#### R1: computeStatus() lives in the wrong layer
**Files:** `DefaultCreditCardBillRepository.kt` (infra layer)

Business logic — deciding whether a bill is OPEN, CLOSED, OVERDUE, or PAID — is implemented inside a repository. Domain services should own this decision.

**Fix:** move `computeStatus()` to `CreditCardBillService` or a `CreditCardBill` domain method.

#### R2: resolveBelongsToForCreate / resolveBelongsToForUpdate are near-duplicates
**File:** `service/transaction/TransactionService.kt:904-937`

Both methods perform the same mutual-exclusion validation (`userId XOR name`).

**Fix:** extract a single `resolveBelongsTo(userId, name)` pure function called by both.

#### R3: Domain models use raw UUID foreign keys
**Files:** `Transaction.kt` (`billId`, `categoryId`, `merchantId`), `CreditCardBill.kt`

Clean architecture convention is that domain entities reference other domain objects, not raw IDs. Raw IDs leak persistence concerns into the domain layer and make relationship validation implicit.

**Fix:** acceptable as-is for now given Micronaut JPA constraints, but document the decision and avoid adding more raw-ID relationships without justification.

---

### MINOR

#### M1: No max-date validation in validateDateWithinMovingWindow
**File:** `TransactionService.kt:968-981`

Only a min-date is validated. A user can create a transaction dated year 2099 with no error.

#### M2: Installment transactions not restricted to card accounts
**File:** `TransactionService.kt:893-901`

`installmentNumber`/`totalInstallments` are accepted on bank account transactions, but installments are only meaningful for credit/charge card accounts (bill assignment). A bank account installment transaction is created without a `billId`, silently.

#### M3: Magic string for sweeper schedule
**File:** `job/transaction/MonthlySnapshotSweeperJob.kt:36`

`PT30M` is documented in the annotation default but not as a named constant.

#### M4: No validation that payment amount is positive
**File:** `TransactionService.kt` — `payBill()`

A payment of 0.01 BRL on a 1000 BRL bill succeeds silently. Add `require(amount > BigDecimal.ZERO)`.

---

## Frontend (Next.js / React)

### BUG — Critical

#### F1: Bill mode is dead code — credit card features are non-functional
**File:** `finance-transactions/account/useAccountDisplayMode.ts`

The hook's implementation always returns `kind: 'calendar'`. Every branch in `page.tsx` that checks `displayMode.kind === 'bill'` is **unreachable**. This means:
- `BillCarousel` renders but is never shown
- `BillSummaryHeader` is never shown
- "Pay Bill" button is never reachable
- Transaction filtering by bill is never applied

This is the single most impactful issue in the frontend: **the entire credit card bill view is broken**.

**Fix:** implement the bill-mode logic in `useAccountDisplayMode` — detect when the selected account is a credit/charge card and return `kind: 'bill'` with the appropriate bill state.

#### F2: "Pay Bill" button handler is empty
**File:** `finance-transactions/page.tsx:465`

```typescript
onPayClick={() => {
  // TODO: Implement pay bill
}}
```

Users clicking "Pay Bill" get no feedback, no mutation, no error. Since bill mode is also broken (F1), this has never been reachable — but must be implemented together.

#### F3: Test mocks bill mode as working — passes on false assumptions
**File:** `finance-transactions/__tests__/FinanceTransactionsPage.test.tsx:88-96`

The test returns `kind: 'bill'` from the mocked hook, but the real hook never returns bill mode. The test is testing behavior that can never occur in production, giving a false sense of coverage.

**Fix:** once F1 is fixed, verify this test actually exercises the real hook path.

---

### PERF

#### F4: TransactionTable re-renders all rows on any state change
**File:** `finance-transactions/transaction/TransactionTable.tsx` (1344 lines)

No `React.memo` on individual row components. A single transaction update (e.g. category change) triggers a full re-render of all grouped rows, including installment progress recalculation and currency formatting.

**Fix:** extract a memoized `TransactionRow` component.

#### F5: Currency formatter cache grows unbounded
**File:** `TransactionTable.tsx:156`

`currencyFormattersRef` accumulates a `Intl.NumberFormat` instance per unique currency code, with no eviction. In a multi-currency account this leaks memory over time.

**Fix:** cap at 10 entries with LRU eviction, or use a single formatter with runtime currency override.

#### F6: Category picker rebuilds full tree on every keystroke
**File:** `finance-transactions/category/CategoryPicker.tsx:179-196`

`filteredRoots` / `filteredChildren` are recomputed synchronously on every search character. For 100+ categories this causes input lag.

**Fix:** `useMemo` on the filter result, or debounce the search input.

---

### REFACTOR

#### F7: TransactionTable.tsx is 1344 lines handling 10+ concerns
**File:** `finance-transactions/transaction/TransactionTable.tsx`

One file handles: data fetching, pagination, grouping by date, inline edit/delete, category mutations, attachment preview, similar-transaction propagation, installment display, currency formatting, and action menu state.

**Suggested split:**
- `TransactionTableContainer` — data/state/mutations
- `TransactionTableView` — layout and rendering
- `TransactionRow` — memoized row (see F4)
- `useTransactionMutations` — all mutation hooks

#### F8: Duplicated fileToBase64 in two drawers
**Files:** `transaction/ImportTransactionsDrawer.tsx:91-111`, `bill/BillImportDrawer.tsx:174-184`

Identical implementation. Extract to `utils/fileUtils.ts`.

#### F9: Three parallel filter states should be one hook
**File:** `finance-transactions/page.tsx:117-183`

`categoryFilter`, `tagFilter`, and `creditDebitTypeFilter` each have their own scope-key and state management. Adding a new filter requires touching 3+ places.

**Fix:** consolidate into `useTransactionFilters()` returning a single filter object.

#### F10: BillCarousel variable names are inverted
**File:** `finance-transactions/bill/BillCarousel.tsx:40-57`

Bills are stored in DESC order (newest first). The variable `isFirst` actually means "is newest" and `handlePrev` navigates to older bills. This is correct but confusing.

**Fix:** rename to `isNewest`/`isOldest`; add a comment explaining DESC ordering assumption.

---

### MINOR (Frontend)

#### FM1: Hardcoded locale and currency
**File:** `finance-transactions/summary/MonthSummary.tsx:16-18`

```typescript
const MONTH_SUMMARY_LOCALE = 'pt-BR';
const MONTH_SUMMARY_CURRENCY = 'BRL';
```

Blocks i18n. Should be driven by user preferences once wired.

#### FM2: Unsafe `as any` casts in two files
- `bill/BillSummaryHeader.tsx:54` — `(color as any).main`
- `account/FinancialAccountSelector.tsx:91` — `(theme as any).custom?.radius`

#### FM3: `AnyTransactionEditPreview` type defined twice
**Files:** `TransactionTable.tsx:77-81` and `InlineTransactionForm.tsx:56-60`

Extract to a shared types file.

---

### UX gaps

#### U1: No loading state during "apply to similar" propagation
**File:** `TransactionTable.tsx:410-436`

The button is not disabled and no spinner is shown while propagating a category edit to many transactions. Appears frozen on large datasets.

#### U2: BillCarousel returns null silently on empty
**File:** `bill/BillCarousel.tsx:26-28`

User cannot distinguish "no bills" from "still loading".

**Fix:** show an empty state card.

#### U3: Date validation calculated but never blocks submission
**File:** `transaction/InlineTransactionForm.tsx:127-128`

`isDateOutsideWindow` is computed but the submit button is never disabled based on it. User submits, gets a backend error, no inline feedback.

**Fix:** disable submit and show inline warning when `isDateOutsideWindow` is true.

#### U4: BillCarousel defaults to first OPEN bill, not most recent
**File:** `bill/BillCarousel.tsx:30`

```typescript
const openBill = bills.find((b) => b.status === 'OPEN') ?? bills[0];
```

If the current bill is being paid but an older bill is still open, the carousel jumps to the older one unexpectedly.

**Fix:** default to `bills[0]` (most recent) and let the user navigate.

---

## Prioritised action list

| # | Area | Item | Severity |
|---|------|------|----------|
| 1 | Frontend | Implement bill mode in `useAccountDisplayMode` (F1) | BUG — Critical |
| 2 | Frontend | Implement Pay Bill mutation (F2) | BUG — Critical |
| 3 | Backend | Fix PAID bill status not reverting on payment deletion (B1) | BUG — High |
| 4 | Backend | Add FK / validation for transferPeerTransactionId (B2) | BUG — Medium |
| 5 | Backend | Add DB index on transactions(financial_account_id, date) (P1) | PERF — Medium |
| 6 | Frontend | Fix test that mocks unreachable bill mode (F3) | BUG — Test |
| 7 | Frontend | Memoize TransactionRow (F4) | PERF |
| 8 | Frontend | Add loading state for "apply to similar" (U1) | UX |
| 9 | Frontend | Block form submission when date is out of window (U3) | UX |
| 10 | Backend | Move computeStatus() to domain layer (R1) | REFACTOR |
| 11 | Frontend | Split TransactionTable.tsx (F7) | REFACTOR |
| 12 | Frontend | Consolidate filter state into useTransactionFilters (F9) | REFACTOR |
| 13 | Backend | Restrict installments to card accounts (M2) | MINOR |
| 14 | Backend | Add positive-amount validation to payBill (M4) | MINOR |
| 15 | Frontend | Fix BillCarousel default to most-recent bill (U4) | UX |
