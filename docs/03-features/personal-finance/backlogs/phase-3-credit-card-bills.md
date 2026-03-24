---
title: Phase 3 — Credit Card Bills (Fatura)
type: feature-spec
category: personal-finance
status: backend-implemented
version: 1.0.0
tags: [feature, personal-finance, credit-card, bill, fatura, billing-cycle, payment]
ai_optimized: true
search_keywords: [credit card, bill, fatura, billing cycle, closingDay, dueDay, net worth, BillStatus, OPEN, CLOSED, PAID, OVERDUE, consolidated view, payBill]
related:
  - 03-features/personal-finance/phase-1-accounts-cards.md
  - 03-features/personal-finance/phase-2-monthly-summary-snapshots.md
  - 03-features/personal-finance/personal-finance-overview.md
last_updated: 2026-03-04
---

# Phase 3 — Credit Card Bills (Fatura)

> **Purpose**: Define how credit card billing cycles (faturas) are modeled, computed, and surfaced in the UI. Covers the backend domain (bill entity, cycle arithmetic, payment), the consolidated view strategy, and the frontend navigation mode for card accounts.

---

## Overview

Credit cards (CREDIT_CARD, CHARGE_CARD) operate on **billing cycles**, not calendar months. A bill (fatura) groups all purchases made between two closing dates, has an independent due date, and a discrete status lifecycle (OPEN → CLOSED → PAID | OVERDUE).

This document covers three concerns that arise from adding credit card support:

1. **Bill lifecycle** — how closing dates and due dates are computed from `closingDay`/`dueDay`, and how `totalAmount` and `status` stay consistent across transaction mutations.
2. **Consolidated view** — how to display bank and card accounts together without double-counting or balance discrepancy.
3. **Frontend navigation** — how the UI switches between calendar-month mode (bank accounts) and bill-cycle mode (card accounts).

---

## Problem Statement

### Why billing cycles are not calendar months

A credit card closing day of 7 means:
- Transactions on Jan 1–7 → **January bill** (closes Jan 7, due Feb 15)
- Transactions on Jan 8–Feb 7 → **February bill** (closes Feb 7, due Feb 15)

This does not align with calendar months. A user spending on Jan 31 and Feb 1 on the same card may be in **different bills**. The month-based `monthSummary` query cannot represent this correctly.

### Why "derived on-demand" doesn't work at scale

Computing bill totals on-demand requires scanning all transactions for a given `financialAccountId` and grouping by bill cycle — an O(n) scan over potentially years of history. More importantly, the bill entity must **exist** before a transaction can reference it (foreign key). Bills must be first-class entities.

### The balance discrepancy misconception

A common concern: "I spent R$ 100 in February but only pay in March — doesn't this create a discrepancy?"

No. The credit card account naturally carries a **negative balance** (a liability). When R$ 100 is purchased, the card's `closingBalance` drops to –R$ 100. The bank account is unaffected. Net worth = bank balance + card balance = R$ 5.000 + (–R$ 100) = R$ 4.900. When the payment is made in March (TRANSFER from bank to card), both balances shift equally and net worth is unchanged. The math is always consistent.

---

## Industry Research

### How major PFM apps handle billing cycles

| App | Bill modeling | Consolidated view | Payment flow |
|---|---|---|---|
| **Nubank** | Bill as first-class entity. Navigation by fatura (closingDate). Shows open/closed/paid bills. | Separate tabs: conta (bank) vs cartão (card). No cross-account consolidation. | Payment registered as a transaction; fatura marked paid automatically when total covered. |
| **Organizze** | Bill entity per cycle. Calendar month view for bank accounts; bill view for cards. | Optional: shows card outstanding in monthly summary as expense. | Explicit payment transaction links to bill; marks PAID. |
| **Mobills** | Bill entity. Separate card/bank views. | Consolidated "all accounts" uses calendar month, includes card expenses (not payment). | Payment transaction with bill linkage. |
| **YNAB** | No bill concept. Card payment is a reallocation between budget categories. | All accounts in one budget view; credit card spending tracked via dedicated card payment category. | Budget-based: card payment moves money from card payment category to card account. |
| **Monarch Money** | Bill entity. Cycle-based navigation. | Consolidated net worth: bank assets minus card liabilities. | Linked transaction marks bill paid. |
| **C6 Bank** | Native bank app: bill is the primary navigation unit for the card. Monthly calendar for bank accounts. | Separate tabs (savings vs card). | In-app payment with immediate PAID status. |

### Key takeaways from research

1. **Bill as entity is the industry standard** for Brazilian PFM apps. Derived-on-demand is not used by any major player.
2. **Separate navigation per account type** (calendar for bank, cycle for card) is universal. Unified navigation is not attempted.
3. **Consolidated view** includes card outstanding as an expense in the current month (Organizze, Mobills) — **not** in the month the bill is paid. This is accrual-basis accounting.
4. **Net worth = bank balances – card outstanding** (Monarch Money, implicit in all apps). Payment does not change net worth.
5. **Payment as explicit transaction** is the standard. Auto-detection from bank transfers is not attempted in manual-entry apps.

### Chosen approach

- **Bill as entity** (`CreditCardBill`) with cycle arithmetic.
- **Calendar mode** for bank/cash/investment accounts; **Bill mode** for CREDIT_CARD/CHARGE_CARD.
- **Consolidated view**: calendar month, card expenses counted in the month of purchase (not payment month), TRANSFER type excluded from income/expenses to prevent double-counting.
- **Payment**: explicit `payBill` mutation; bill marked PAID when payment covers full `totalAmount`.

---

## Data Model

### `banking.credit_card_bills` (exists since V0_5)

One row per (financial account, billing cycle). The billing cycle is identified by its `closing_date`.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID (DB-generated) | Primary key |
| `financial_account_id` | UUID FK → banking.financial_accounts | Card account |
| `account_id` | UUID FK → security.accounts | Tenant isolation |
| `closing_date` | DATE NOT NULL | Last day of the billing cycle |
| `due_date` | DATE NOT NULL | Payment due date |
| `total_amount` | NUMERIC(18,4) | Recomputed: SUM(DEBIT) – SUM(CREDIT) from linked transactions |
| `minimum_payment` | NUMERIC(18,4)? | Optional; set manually or via Open Finance sync |
| `status` | TEXT NOT NULL | Enum: OPEN, CLOSED, PAID, OVERDUE |
| `provider_bill_id` | TEXT? | Reserved for future Open Finance sync (Pluggy bill ID) |
| `deleted_at` | TIMESTAMPTZ? | Soft-delete |
| `created_at` | TIMESTAMPTZ NOT NULL | |
| `updated_at` | TIMESTAMPTZ NOT NULL | |

**Unique constraint**: `uq_credit_card_bills_account_closing (financial_account_id, closing_date)` — one bill per cycle per account.

### `banking.transactions` — added field

| Column | Type | Notes |
|---|---|---|
| `bill_id` | UUID FK → banking.credit_card_bills? | Set for CREDIT_CARD/CHARGE_CARD transactions; null for all others |

---

## Business Rules

### 1. Which accounts use bills

Only `CREDIT_CARD` and `CHARGE_CARD` subtypes get bill assignment. `PREPAID_CARD` and `VOUCHER_CARD` have no billing cycle (`closingDay` is null) — the service ignores them naturally. `BALANCE_ADJUSTMENT` transactions are never assigned to a bill regardless of account type.

### 2. Billing cycle arithmetic

#### `computeClosingDate(transactionDate, closingDay)`

```
effectiveDay = min(closingDay, transactionDate.lengthOfMonth())  // month-end clamp

if transactionDate.dayOfMonth <= effectiveDay:
    → closingDate = transactionDate.withDayOfMonth(effectiveDay)   // this month's bill

else:
    nextMonth = transactionDate.plusMonths(1)
    → closingDate = nextMonth.withDayOfMonth(min(closingDay, nextMonth.lengthOfMonth()))
```

Examples with `closingDay = 7`:

| Transaction date | Closing date | Reason |
|---|---|---|
| Feb 7 | Feb 7 | On closing day — belongs to this cycle |
| Feb 8 | Mar 7 | After closing day — next cycle |
| Jan 31 (`closingDay=31`) | Jan 31 | Clamped to month end (Jan has 31 days) |
| Feb 28 (`closingDay=31`) | Feb 28 | Clamped to Feb 28 (or 29 in leap year) |

#### `computeDueDate(closingDate, dueDay)`

```
candidate = closingDate.withDayOfMonth(min(dueDay, closingDate.lengthOfMonth()))

if candidate > closingDate:
    → dueDate = candidate          // due this same month

else:
    nextMonth = closingDate.plusMonths(1)
    → dueDate = nextMonth.withDayOfMonth(min(dueDay, nextMonth.lengthOfMonth()))
```

Examples:

| `closingDay` | `dueDay` | Closing date | Due date | Reason |
|---|---|---|---|---|
| 7 | 15 | Feb 7 | Feb 15 | Feb 15 > Feb 7 → same month |
| 25 | 10 | Mar 25 | Apr 10 | Mar 10 ≤ Mar 25 → next month |
| 28 | 5 | Feb 28 | Mar 5 | Feb 5 ≤ Feb 28 → next month |

### 3. Bill status lifecycle

```
OPEN    — closingDate >= today   (billing cycle still accumulating purchases)
CLOSED  — closingDate < today AND dueDate >= today   (cycle ended, not yet due)
OVERDUE — dueDate < today AND NOT PAID
PAID    — terminal state; once set, never automatically reverted
```

`PAID` is preserved across `recomputeTotal` calls. All other statuses are re-derived from dates on every recompute. The status is recomputed **synchronously** after every transaction mutation that touches the bill.

### 4. Bill total computation (SQL)

```sql
total_amount =
    COALESCE(
        SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT')
      - SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT'),
        0
    )
FROM banking.transactions
WHERE bill_id = :billId
  AND deleted_at IS NULL
```

CREDIT transactions reduce `total_amount` (e.g., a refund or a payment partial credit). `DEBIT` purchases increase it. Net result is the outstanding amount owed.

### 5. Bill auto-creation on transaction write

When a transaction is created or updated for a card account, the correct bill is resolved automatically:

```
1. computeClosingDate(transactionDate, account.cardData.closingDay)
2. computeDueDate(closingDate, account.cardData.dueDay)
3. INSERT INTO credit_card_bills ... ON CONFLICT (financial_account_id, closing_date) DO NOTHING
4. Re-read bill by (financialAccountId, closingDate) → get id
5. Set transaction.billId = bill.id
6. recomputeTotal(bill.id)
```

The `ON CONFLICT DO NOTHING` + re-read pattern is safe under concurrent writes (two simultaneous transactions for the same bill will not create duplicate rows).

### 6. Transaction date change across billing cycles

When `updateTransaction` changes the date to a different billing cycle:

```
1. Resolve new bill (may be different from old bill)
2. Save transaction with new billId
3. recomputeTotal(oldBillId)   ← old bill total decreases
4. recomputeTotal(newBillId)   ← new bill total increases
```

### 7. Bill payment

Payment is registered as an **explicit mutation** `payBill(billId, amount)`:

```
1. Create CREDIT TRANSFER transaction on the card account with billId set
2. recomputeTotal(billId) → totalAmount decreases
3. if paymentAmount >= bill.totalAmount → status = PAID (terminal)
   else → status stays CLOSED or OVERDUE
4. Optionally create matching DEBIT TRANSFER on the source bank account
```

**Why explicit, not automatic**: Automatic detection (mark PAID when `totalAmount <= 0`) fails for partial payments, refunds, and promotions that temporarily bring balance to zero. An explicit user action is unambiguous and matches the UX model of all major Brazilian PFM apps.

---

## Consolidated View: Double-Counting and Net Worth

### Why card purchases don't double-count

The `monthSummary` query excludes `TRANSFER` type from `totalIncome` and `totalExpenses`. The flow for a card purchase and its payment:

```
February: R$ 100 purchase on credit card
  → transaction: type=PURCHASE, creditDebitType=DEBIT, amount=100
  → card account totalExpenses += 100  ✓

March: R$ 100 payment from bank to card
  → bank transaction: type=TRANSFER, creditDebitType=DEBIT, amount=100
  → card transaction: type=TRANSFER, creditDebitType=CREDIT, amount=100
  → neither appears in totalIncome or totalExpenses (TRANSFER excluded)  ✓
```

Result: The R$ 100 expense appears exactly once in February's consolidated expenses. The March payment is invisible in the income/expense summary — it is a reallocation between own accounts, not a new cash flow.

### Net worth in the consolidated view

```
consolidated closingBalance =
    SUM(closingBalance of all BANK/CASH accounts)
  + SUM(closingBalance of all CREDIT_CARD/CHARGE_CARD accounts)
```

Credit card `closingBalance` is naturally negative because purchases are DEBIT transactions that reduce the balance. This makes net worth mathematically correct:

```
Example:
  Bank: closingBalance = +5.000
  Card: closingBalance = -1.200  (R$ 1.200 outstanding bill)
  Net worth = +3.800

After paying R$ 1.200 (bank → card TRANSFER):
  Bank: closingBalance = +3.800
  Card: closingBalance = 0
  Net worth = +3.800  ← unchanged ✓
```

---

## Frontend Navigation Strategy

The frontend uses two display modes depending on the selected account type.

### Calendar mode (bank, cash, investment)

```
Selected account: BANK / CASH / null (all accounts)
Navigation unit: calendar month (MonthYearCarousel)
Summary query: monthSummary(financialAccountId, year, month)
Transactions query: transactions(financialAccountId, year, month)
```

### Bill mode (credit card)

```
Selected account: CREDIT_CARD / CHARGE_CARD
Navigation unit: billing cycle (BillCarousel)
Summary query: creditCardBill(financialAccountId, closingDate?)
Transactions query: transactions(financialAccountId, billId)
Bill list query: creditCardBills(financialAccountId) → navigation + open bill detection
```

### Mode detection

```typescript
const isBillMode = selectedAccount != null &&
  ['CREDIT_CARD', 'CHARGE_CARD'].includes(selectedAccount.subtype)
```

### Open bill identification

```typescript
// creditCardBills returns bills ordered by closingDate DESC
// The open bill is the first one with status === 'OPEN',
// or the most recent if none is open.
const openBill = bills.find(b => b.status === 'OPEN') ?? bills[0]
```

---

## GraphQL Schema

```graphql
enum BillStatus { OPEN CLOSED PAID OVERDUE }

type CreditCardBill {
  id: ID!
  financialAccountId: ID!
  closingDate: Date!
  dueDate: Date!
  totalAmount: Float!
  minimumPayment: Float
  status: BillStatus!
  createdAt: DateTime!
  updatedAt: DateTime!
}

extend type Query {
  # Returns all bills for a card account, ordered by closingDate DESC
  creditCardBills(financialAccountId: ID!): [CreditCardBill!]!

  # Returns a specific bill by closingDate, or the current open/most-recent bill if null
  creditCardBill(financialAccountId: ID!, closingDate: Date): CreditCardBill
}

extend type Mutation {
  # Register a payment against a bill. Creates a CREDIT TRANSFER on the card account.
  # Marks bill as PAID if paymentAmount >= totalAmount.
  payBill(billId: ID!, amount: Float!, sourceAccountId: ID): CreditCardBill!
}
```

The `transactions` query will also accept a `billId` filter for card account transaction lists:

```graphql
extend type Query {
  # Existing query — adding billId as an alternative filter for card accounts
  transactions(
    financialAccountId: ID
    year: Int
    month: Int
    billId: ID          # ← new: filter by bill (used in Bill mode)
  ): [Transaction!]!
}
```

---

## Write Flow

### create/update/delete transaction (card account)

```
TransactionService.create(command):
  1. account = financialAccountRepository.find(command.financialAccountId)
  2. billId  = creditCardBillService.resolveBillForTransaction(account, command.date, command.type)
  3. transaction = Transaction(... billId = billId)
  4. transactionRepository.save(transaction)
  5. creditCardBillService.recomputeBill(billId, accountId)
  6. snapshotComputationService.recomputeChain(accountId, command.date)

TransactionService.update(id, command):
  1. existing = transactionRepository.findById(id)
  2. oldBillId = existing.billId
  3. account   = financialAccountRepository.find(existing.financialAccountId)
  4. newBillId = creditCardBillService.resolveBillForTransaction(account, command.date, command.type)
  5. UPDATE transaction SET ... billId = newBillId
  6. if oldBillId != newBillId: creditCardBillService.recomputeBill(oldBillId, accountId)
  7. creditCardBillService.recomputeBill(newBillId, accountId)
  8. snapshotComputationService.recomputeChain(accountId, min(old.date, new.date))

TransactionService.delete(id):
  1. existing = transactionRepository.findById(id)
  2. SOFT DELETE (set deleted_at)
  3. creditCardBillService.recomputeBill(existing.billId, accountId)
  4. snapshotComputationService.recomputeChain(accountId, existing.date)
```

---

## Implementation Status

### Backend (Kotlin/Micronaut) — ✅ Implemented

| File | Status |
|---|---|
| `domain/transaction/BillStatus.kt` | ✅ Done |
| `domain/transaction/CreditCardBill.kt` | ✅ Done |
| `domain/transaction/CreditCardBillRepository.kt` | ✅ Done |
| `domain/transaction/CreditCardBillService.kt` | ✅ Done |
| `model/transaction/CreditCardBillEntity.kt` | ✅ Done |
| `model/transaction/CreditCardBillMapper.kt` | ✅ Done |
| `repo/CreditCardBillEntityRepository.kt` | ✅ Done |
| `repo/DefaultCreditCardBillRepository.kt` | ✅ Done |
| `graphql/dto/CreditCardBillDTO.kt` | ✅ Done |
| `graphql/schema.graphqls` — BillStatus, CreditCardBill, queries | ✅ Done |
| `graphql/resolver/TransactionResolver.kt` — creditCardBills/creditCardBill | ✅ Done |
| `graphql/BankingWiringFactory.kt` — 2 new data fetchers | ✅ Done |
| `domain/transaction/TransactionService.kt` — bill resolution + recompute | ✅ Done |
| `model/transaction/TransactionMapper.kt` — billId in updateEntity | ✅ Done |

### Frontend — ⏳ Planned (see Backlog)

---

## Backlog

> Stories ordered by dependency. Each story is independently deliverable within its sprint group.
> All stories assume the backend listed above is complete and deployed.

---

### Dependency Graph

```
Sprint 1: Backend completion
  BILL-01 (billId filter on transactions query)
  BILL-02 (payBill mutation)

Sprint 2: GraphQL codegen + foundation
  BILL-03 (GraphQL operations codegen)  ← depends on BILL-01, BILL-02

Sprint 3: Frontend — Bill mode components (parallel)
  BILL-04 (BillSummaryHeader)    ← depends on BILL-03
  BILL-05 (BillCarousel)         ← depends on BILL-03
  BILL-06 (mode detection hook)  ← depends on BILL-03

Sprint 4: Frontend — Integration
  BILL-07 (Transaction page — Bill mode wiring)  ← depends on BILL-04, BILL-05, BILL-06
  BILL-08 (Consolidated view net worth)          ← depends on BILL-06

Sprint 5: Tests
  BILL-09 (Backend unit tests — CreditCardBillService)
  BILL-10 (Backend integration tests — full lifecycle)
  BILL-11 (Frontend component tests)

Sprint 6: Polish
  BILL-12 (Bruno API collection)
  BILL-13 (Performance review + indexes)
```

---

### Sprint 1 — Backend Completion

---

#### BILL-01 — `billId` filter on `transactions` query

**Depends on**: _(none — backend already done)_

**Description**: Add `billId: UUID?` as an optional filter parameter to the `transactions` GraphQL query and its backing service/repository. When `billId` is provided, ignore `year`/`month` and filter by `t.bill_id = :billId` instead. Used by the frontend in Bill mode to list the transactions belonging to a specific fatura.

**Files**:
- `graphql/schema.graphqls` — add `billId: ID` param to `transactions` query
- `domain/transaction/TransactionQueryService.kt` (or service layer) — handle `billId` branch
- `repo/TransactionEntityRepository.kt` — add `findByBillId(billId, accountId)` method
- `graphql/resolver/TransactionResolver.kt` — pass `billId` to service

**Acceptance criteria**:
- `transactions(billId: "...")` returns only transactions with that `bill_id`
- `transactions(year: 2026, month: 3)` continues to work unchanged
- Permission check applies to both filter modes

**Design note**: `billId` and `year/month` are mutually exclusive filters. If both are provided, `billId` takes precedence. Enforce this in the service layer, not GraphQL.

---

#### BILL-02 — `payBill` mutation

**Depends on**: _(none — backend already done)_

**Description**: Implement `payBill(billId: ID!, amount: Float!, sourceAccountId: ID): CreditCardBill!` mutation. Registers payment, creates a CREDIT TRANSFER on the card account, optionally a matching DEBIT TRANSFER on the source bank account, recomputes the bill total, and sets status to PAID if payment covers the full amount.

**Files**:
- `graphql/schema.graphqls` — add `payBill` mutation
- `domain/transaction/CreditCardBillService.kt` — add `payBill(billId, amount, sourceAccountId?, accountId)` method
- `domain/transaction/CreditCardBillRepository.kt` — add `setStatus(billId, status)` method
- `repo/DefaultCreditCardBillRepository.kt` — implement `setStatus`
- `repo/CreditCardBillEntityRepository.kt` — add `updateStatus(billId, status, updatedAt)` native query
- `graphql/resolver/TransactionResolver.kt` — add `payBill` handler
- `graphql/BankingWiringFactory.kt` — wire `payBill`

**Business logic**:
```
payBill(billId, amount, sourceAccountId, accountId):
  1. bill = billRepository.findById(billId, accountId)
  2. Assert bill.status != PAID (idempotency guard)
  3. Create CREDIT TRANSFER on bill.financialAccountId with billId = billId, amount = amount
  4. if sourceAccountId != null: create DEBIT TRANSFER on sourceAccountId, amount = amount
  5. recomputeTotal(billId, accountId)           → updates totalAmount
  6. if amount >= bill.totalAmount: setStatus(PAID)
  7. Return updated bill
```

**Acceptance criteria**:
- Partial payment: totalAmount decreases, status stays CLOSED/OVERDUE
- Full payment: totalAmount = 0, status = PAID
- Calling payBill on PAID bill returns error (or is idempotent, TBD)
- Payment transaction appears in transactions list with `type=TRANSFER`

**Pattern note**: Follow the same double-TRANSFER pattern as `createTransfer` in `TransactionService` — both legs in one service call, each triggering snapshot recompute on its respective account.

---

### Sprint 2 — GraphQL Codegen

---

#### BILL-03 — GraphQL operations codegen

**Depends on**: BILL-01, BILL-02

**Description**: Add GraphQL operation files for all bill-related queries and mutations, then run codegen to generate typed hooks.

**New operation files** (in the frontend GraphQL operations folder):
- `GetCreditCardBills.graphql` — `creditCardBills(financialAccountId)`
- `GetCreditCardBill.graphql` — `creditCardBill(financialAccountId, closingDate?)`
- `GetTransactionsByBill.graphql` — `transactions(billId)`
- `PayBill.graphql` — `payBill(billId, amount, sourceAccountId?)`

**Acceptance criteria**: `useGetCreditCardBillsQuery`, `useGetCreditCardBillQuery`, `usePayBillMutation` hooks generated with full type safety.

---

### Sprint 3 — Frontend Components

---

#### BILL-04 — `BillSummaryHeader` component

**Depends on**: BILL-03

**Description**: Card-mode equivalent of `MonthSummaryHeader`. Shows bill-specific metrics instead of income/expenses/balance.

**Layout**:
```
┌────────────────────────────────────────────────────────┐
│  Fatura         Vencimento      Limite disponível       │
│  R$ 1.200,00    15 mar 2026     R$ 3.800,00             │
│                                                         │
│  Status: ●ABERTA     [Pagar fatura]                     │
└────────────────────────────────────────────────────────┘
```

**Fields**:
- `totalAmount` — current outstanding amount
- `dueDate` — formatted due date
- `availableCreditLimit` (from `FinancialAccount.cardData.creditLimit - totalAmount`)
- `status` — color-coded chip (OPEN=green, CLOSED=yellow, PAID=blue, OVERDUE=red)
- "Pagar fatura" CTA — opens payment modal (calls `payBill` mutation)

**Data source**: `creditCardBill(financialAccountId, closingDate)`

**Acceptance criteria**:
- Skeleton shown during query loading
- Status chip color changes correctly per BillStatus value
- "Pagar fatura" button hidden when status = PAID
- OVERDUE state shows visual warning

---

#### BILL-05 — `BillCarousel` component

**Depends on**: BILL-03

**Description**: Navigation component for credit card accounts. Replaces `MonthYearCarousel` when in Bill mode.

**Behavior**:
- Fetches `creditCardBills(financialAccountId)` — list of all bills ordered by `closingDate DESC`
- Displays selected bill's label: "Fatura de jan/2026 · fecha 7 jan"
- Left/right arrows to navigate to adjacent bills
- Highlights the OPEN bill as the default selection
- Shows a status indicator dot per bill in the navigation dots (OPEN, CLOSED, PAID, OVERDUE colors)

**Acceptance criteria**:
- Initial load selects the OPEN bill (or most recent if none open)
- Navigating to a past bill updates the `closingDate` state used by `BillSummaryHeader` and transactions list
- Shows "← Fatura anterior" / "Próxima fatura →" navigation labels
- Disabled state when at the oldest/newest bill

---

#### BILL-06 — `useAccountDisplayMode` hook

**Depends on**: BILL-03

**Description**: Encapsulates the logic that determines whether the current view is in Calendar mode or Bill mode. Returns the active mode and all mode-specific parameters (year/month vs billId/closingDate).

```typescript
type DisplayMode =
  | { kind: 'calendar'; year: number; month: number }
  | { kind: 'bill'; bills: CreditCardBill[]; selectedBill: CreditCardBill | null }

function useAccountDisplayMode(selectedAccount: FinancialAccount | null): DisplayMode
```

**Design pattern**: Strategy pattern — consumers use `mode.kind` to branch their rendering without scattered `if (isCreditCard)` checks.

**Acceptance criteria**:
- `kind = 'calendar'` when `selectedAccount` is null or non-card subtype
- `kind = 'bill'` when `selectedAccount.subtype` is CREDIT_CARD or CHARGE_CARD
- `selectedBill` defaults to the OPEN bill on first load
- Changing `selectedAccount` resets the selected bill

---

### Sprint 4 — Frontend Integration

---

#### BILL-07 — Transaction page — Bill mode wiring

**Depends on**: BILL-04, BILL-05, BILL-06

**Description**: Update the main transaction page to use `useAccountDisplayMode` and conditionally render `BillCarousel + BillSummaryHeader` vs `MonthYearCarousel + MonthSummaryHeader`.

**Changes**:
- Replace inline `isCreditCard` checks with `useAccountDisplayMode`
- When `mode.kind === 'bill'`: render `BillCarousel`, `BillSummaryHeader`, fetch `transactions(billId=mode.selectedBill.id)`
- When `mode.kind === 'calendar'`: existing behavior unchanged
- `AccountSelector` drives `selectedAccount` for both modes

**Acceptance criteria**:
- Switching from a bank account to a card account swaps the navigation and summary header
- Switching back to "All accounts" returns to Calendar mode
- Transaction list correctly shows bill-filtered transactions in Bill mode

---

#### BILL-08 — Consolidated view net worth adaptation

**Depends on**: BILL-06

**Description**: Ensure the "All accounts" consolidated view (Calendar mode) correctly computes net worth by treating credit card balances as negative.

**Backend**: The `monthSummary` query with `financialAccountId = null` must compute:
```
closingBalance = SUM(closingBalance of BANK/CASH accounts)
              + SUM(closingBalance of CREDIT_CARD/CHARGE_CARD accounts)
```
The card accounts already have negative `closingBalance` (since purchases are DEBIT). The SUM is correct as-is — no special sign inversion needed.

**Frontend**: Verify the "Saldo total" figure in the consolidated `MonthSummaryHeader` reflects this net worth. Add a tooltip or subtitle: "Inclui saldo de cartões como passivo."

**Acceptance criteria**:
- Consolidated `closingBalance` = bank balances + (negative) card balances
- After registering a bill payment: consolidated closingBalance unchanged
- Tooltip/subtitle visible in consolidated view

---

### Sprint 5 — Tests

---

#### BILL-09 — Backend unit tests: `CreditCardBillService`

**Depends on**: _(existing backend code)_

**Test cases**:

| Test | What to verify |
|---|---|
| `computeClosingDate` — on closing day | Returns same month |
| `computeClosingDate` — after closing day | Returns next month |
| `computeClosingDate` — `closingDay=31` in Feb | Clamped to Feb 28/29 |
| `computeClosingDate` — `closingDay=31` in Jan | Returns Jan 31 |
| `computeDueDate` — due day after closing | Same month |
| `computeDueDate` — due day before closing | Next month |
| `computeDueDate` — due day end-of-month clamp | Handles short months |
| `resolveBillForTransaction` — non-card account | Returns null |
| `resolveBillForTransaction` — BALANCE_ADJUSTMENT | Returns null |
| `resolveBillForTransaction` — no cardData | Returns null |
| `resolveBillForTransaction` — valid card | Calls findOrCreate, returns billId |
| `recomputeBill` — null billId | No-op, no exception |

**Test framework**: JUnit 5 + Mockito-Kotlin. Mock `CreditCardBillRepository`. No DB required.

---

#### BILL-10 — Backend integration tests: full bill lifecycle

**Depends on**: _(existing backend code)_

**Extends**: `BankingPostgresIntegrationTest`

**Test scenarios**:

| Scenario | What to verify |
|---|---|
| Create transaction on card → bill auto-created | Bill exists with correct closingDate/dueDate |
| Two transactions same cycle → same bill | Only one bill row, totalAmount = sum of both |
| Transaction after closing day → new bill | Two separate bills |
| `closingDay=7` transactions: Feb 7 vs Feb 8 | Different bills |
| Update transaction date crossing cycle boundary | Old bill decreases, new bill increases |
| Delete transaction → bill totalAmount decreases | Soft-deleted transaction not counted |
| `computeClosingDate` edge cases via real transactions | Feb 28 with closingDay=31 → Feb 28 |
| `payBill(fullAmount)` → status = PAID | Bill.status = PAID; CREDIT TRANSFER created |
| `payBill(partialAmount)` → status stays | Bill.status = CLOSED; totalAmount decreased |
| Concurrent transaction creation same cycle | No duplicate bills (ON CONFLICT DO NOTHING) |
| `creditCardBills` query → ordered DESC | Most recent bill first |
| `creditCardBill(closingDate=null)` → OPEN bill | Returns the bill with closingDate >= today |
| BALANCE_ADJUSTMENT transaction → no billId | Transaction.billId is null |

---

#### BILL-11 — Frontend component tests

**Depends on**: BILL-04, BILL-05, BILL-06, BILL-07

**Test cases**:

| Component | Test |
|---|---|
| `BillSummaryHeader` | Renders totalAmount, dueDate, status chip correctly |
| `BillSummaryHeader` | Hides "Pagar fatura" when status = PAID |
| `BillSummaryHeader` | Shows OVERDUE visual warning |
| `BillCarousel` | Selects OPEN bill by default |
| `BillCarousel` | Left/right navigation changes selectedBill |
| `BillCarousel` | Disables left arrow at oldest bill |
| `useAccountDisplayMode` | Returns 'calendar' for null account |
| `useAccountDisplayMode` | Returns 'calendar' for BANK account |
| `useAccountDisplayMode` | Returns 'bill' for CREDIT_CARD account |
| `useAccountDisplayMode` | Resets selectedBill on account change |
| Transaction page | Renders BillCarousel + BillSummaryHeader for card accounts |
| Transaction page | Renders MonthYearCarousel + MonthSummaryHeader for bank accounts |
| `payBill` modal | Submits mutation with correct billId and amount |
| `payBill` modal | Shows success state after mutation |

---

### Sprint 6 — Polish

---

#### BILL-12 — Bruno API collection

**Depends on**: BILL-01, BILL-02

**Description**: Add bill-related requests to the Bruno API collection used for manual testing.

**Requests to add**:
- `GET creditCardBills(financialAccountId)`
- `GET creditCardBill(financialAccountId, closingDate)`
- `GET transactions(billId)`
- `POST payBill(billId, amount)`

---

#### BILL-13 — Performance review and index audit

**Depends on**: BILL-10

**Description**: Review query plans for all new SQL queries introduced in this phase and add indexes if needed.

**Queries to audit**:

```sql
-- Bill lookup by closing date (used in findOrCreate — called on every card transaction write)
SELECT * FROM banking.credit_card_bills
WHERE financial_account_id = $1 AND closing_date = $2 AND deleted_at IS NULL;
-- Index: uq_credit_card_bills_account_closing already covers this ✓

-- Bill list for navigation
SELECT * FROM banking.credit_card_bills
WHERE financial_account_id = $1 AND account_id = $2 AND deleted_at IS NULL
ORDER BY closing_date DESC;
-- Index: consider (financial_account_id, account_id, closing_date DESC) if table grows large

-- Total recompute (called on every card transaction write)
SELECT COALESCE(SUM(amount) FILTER ... FROM banking.transactions WHERE bill_id = $1 AND deleted_at IS NULL;
-- Index: (bill_id) on banking.transactions — verify this exists, add if missing

-- Transactions by bill (frontend list)
SELECT * FROM banking.transactions WHERE bill_id = $1 AND account_id = $2 AND deleted_at IS NULL;
-- Same index as above
```

**Acceptance criteria**: All queries use index scans (no sequential scans) as confirmed by `EXPLAIN ANALYZE`. Add Flyway migration for any missing indexes.

---

## Resolved Design Decisions

| Decision | Resolution | Rationale |
|---|---|---|
| Bill as entity vs derived on-demand? | **Entity** (`banking.credit_card_bills`) | Bills must exist before transactions reference them (FK). On-demand computation is O(n) over history and cannot serve the bill navigation list efficiently. |
| Which subtypes use bills? | **CREDIT_CARD and CHARGE_CARD only** | PREPAID_CARD and VOUCHER_CARD have no credit cycle (`closingDay` is null). The service ignores them naturally without a subtype check. |
| Closing day on the closing day itself? | **Inclusive** — transaction ON closing day belongs to that bill | Matches Brazilian banking convention (Nubank, C6, Itaú). "Fecha dia 7" means the 7th is the last day included in the current bill. |
| Concurrent bill creation? | **INSERT ON CONFLICT DO NOTHING + re-read** | Same pattern as monthly snapshots. Safe under concurrent requests. No application-level locking needed. |
| Payment: explicit mutation vs auto-detect? | **Explicit `payBill` mutation** | Auto-detection fails for partial payments, refunds, and edge cases. Explicit mutation is unambiguous and matches Brazilian PFM UX conventions. |
| PAID status: auto-revert if refund posted? | **No** — PAID is terminal | Once a bill is paid, retroactive refunds appear in the next bill's balance, not the paid bill. Matches real bank behavior. |
| Consolidated view: card expenses in purchase month or payment month? | **Purchase month (accrual)** | TRANSFER excluded from income/expenses means payment is invisible; card DEBIT purchase counted in its calendar month. Consistent with accrual-basis accounting and all major Brazilian PFM apps. |
| `totalAmount` computed in DB or application? | **Native SQL correlated subquery** | Single round-trip; consistent with snapshot recompute pattern. Avoids loading all transaction rows into application memory. |
| Frontend mode switching: scattered if-checks vs abstraction? | **`useAccountDisplayMode` hook (Strategy pattern)** | Centralizes mode logic; consumers branch on `mode.kind` without knowing card subtype details. Easier to extend (e.g., future DEBIT_CARD mode). |

---

## Open Questions

- Should `payBill` automatically create the corresponding DEBIT TRANSFER on the source bank account, or leave it as optional (user may not want to track bank side)? Current proposal: `sourceAccountId` is optional.
- Should `minimumPayment` be editable via a mutation, or only set via Open Finance sync (Phase 5)?
- When Open Finance is integrated (Phase 5), how do provider-fetched bills reconcile with manually-created ones? Key: `provider_bill_id` field is reserved for this.
- Should OVERDUE bills trigger a push notification? (Out of scope for this phase.)
