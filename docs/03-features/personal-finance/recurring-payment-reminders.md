# Recurring Payment Reminders

**Feature:** Recurring Payment Reminders  
**Track:** Black (BK)  
**Status:** Implemented (v1) — pending refactor described in this doc  
**Scope:** Full-stack — backend (Kotlin/Micronaut) + frontend (Next.js/React/MUI)

---

## Context

Users need a way to track recurring bills and subscriptions (rent, streaming, utilities, gym, etc.), receive proactive reminders before they are due, and see upcoming payments in their dashboard. When a matching transaction is posted, the system **suggests** linking it to the template — the user confirms explicitly.

---

## Benchmark Summary

| Feature | Monarch | Copilot | YNAB | Mobills |
|---|---|---|---|---|
| Separate template entity | ✅ | ✅ | ✅ | ✅ |
| Auto-detection of patterns | ✅ | ✅ | ✗ | ✗ |
| Generates FUTURE transactions | ✅ | ✅ | ✅ | ✗ |
| Suggestion with user confirmation | ✅ | ✗ | ✗ | ✗ |
| Reminder N days before | ✅ | ✗ | ✗ | ✅ (24 h) |
| Dashboard "Upcoming" widget | ✅ | ✅ | ✗ | ✗ |

---

## Architecture Overview

```
RecurringPayment (template)
  │
  ├── RecurringReminderJob  (@Scheduled PT1H)
  │     └── for each ACTIVE template where nextDueDate <= today + remindDaysBefore
  │           and no non-expired PENDING action exists
  │           → creates AccountPendingAction(type=RECURRING_PAYMENT_DUE)
  │
  └── suggestRecurringForTransaction (GraphQL query — on demand)
        └── RecurringMatchingService.findBestMatch(transaction)
              → scores all ACTIVE templates by description + merchant + amount + date
              → returns top match if score >= SUGGESTION_THRESHOLD
              → user confirms via linkTransactionToRecurring mutation
```

**No automatic linking.** The system only suggests; the user always confirms.

**FUTURE transaction projection removed** — `RecurringFutureTransactionJob` is not implemented. `projectedBalance` has no frontend consumer. Can be revisited when a cash-flow projection screen is built.

---

## Key Design Decisions

### `financialAccountId` is optional on the template

A recurring bill (Comgás, rent, gym) represents an **obligation**, not a contract with a specific account. The user may pay from different accounts or cards each month. The template stores a preferred account as a hint (`financialAccountId nullable`), but matching searches across all accounts of the tenant (`account_id`).

### Suggestion instead of auto-link

Silent auto-linking by date + amount alone causes false matches (e.g., Netflix R$55,90 vs Spotify R$54,90, both due on the 10th of the month). Instead:
1. When a transaction is created/updated, a lightweight `suggestRecurringForTransaction` query is available.
2. The frontend calls it when the user opens the transaction action menu.
3. If a suggestion exists, an inline menu item is shown: `🔁 Vincular a "Comgás"?`
4. The user clicks → `linkTransactionToRecurring` mutation → `confirmPayment` → template advances.

### Overdue / late payment handling

If a payment is missed, the template stays at the overdue `nextDueDate`. Two rules apply:

1. **`existsOpenForEntity` must respect `expires_at`** — the idempotency check must include `AND (expires_at IS NULL OR expires_at > NOW())` so that a new reminder is created after the previous one expires, even if status was never explicitly dismissed.

2. **Date gate is waived for past-due templates** — when `template.nextDueDate < today`, the suggestion engine removes the date gate. Any transaction posted on or after `nextDueDate` is a candidate (description + amount score still apply). `confirmPayment` already has a while loop that advances through multiple missed cycles to the next future date.

---

## 1. Database Migration

### V0_20 — Create recurring_payments table

```sql
CREATE TABLE banking.recurring_payments (
    id                   UUID PRIMARY KEY DEFAULT uuidv7(),
    account_id           UUID NOT NULL,
    financial_account_id UUID REFERENCES banking.financial_accounts(id),  -- nullable
    name                 VARCHAR(255) NOT NULL,
    credit_debit_type    VARCHAR(10)  NOT NULL CHECK (credit_debit_type IN ('CREDIT', 'DEBIT')),
    type                 VARCHAR(50),
    expected_amount      NUMERIC(19,4),
    currency_code        CHAR(3)      NOT NULL DEFAULT 'BRL',
    category_id          UUID REFERENCES banking.transaction_categories(id),
    merchant_id          UUID REFERENCES banking.merchants(id) ON DELETE SET NULL,
    frequency            VARCHAR(20)  NOT NULL CHECK (frequency IN ('MONTHLY', 'WEEKLY', 'YEARLY', 'CUSTOM')),
    day_of_month         SMALLINT,
    interval_days        INT,
    remind_days_before   SMALLINT     NOT NULL DEFAULT 3,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED')),
    next_due_date        DATE         NOT NULL,
    last_paid_date       DATE,
    notes                TEXT,
    deleted_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_payments_account
    ON banking.recurring_payments(account_id);

CREATE INDEX idx_recurring_payments_financial_account
    ON banking.recurring_payments(financial_account_id)
    WHERE financial_account_id IS NOT NULL;

CREATE INDEX idx_recurring_payments_status_due
    ON banking.recurring_payments(status, next_due_date)
    WHERE deleted_at IS NULL;

ALTER TABLE banking.transactions
    ADD COLUMN recurring_payment_id UUID REFERENCES banking.recurring_payments(id);

CREATE INDEX idx_transactions_recurring_payment
    ON banking.transactions(recurring_payment_id)
    WHERE recurring_payment_id IS NOT NULL AND deleted_at IS NULL;
```

> **Note:** The `uq_transactions_future_recurring` partial index from the original v1 is **not needed** — FUTURE transaction projection was removed.

---

## 2. Backend — Domain Layer

### 2.1 Enums

`RecurringFrequency.kt`: `MONTHLY | WEEKLY | YEARLY | CUSTOM`  
`RecurringPaymentStatus.kt`: `ACTIVE | PAUSED | CANCELLED`

### 2.2 Domain Model

**`RecurringPayment.kt`** — `financialAccountId: UUID?` (nullable)

```kotlin
data class RecurringPayment(
    val id: UUID? = null,
    val accountId: UUID,
    val financialAccountId: UUID?,          // nullable — template is account-agnostic
    val name: String,
    val creditDebitType: CreditDebitType,
    val type: TransactionType? = null,
    val expectedAmount: BigDecimal? = null,  // null = variable amount
    val currencyCode: String = "BRL",
    val categoryId: UUID? = null,
    val merchantId: UUID? = null,
    val frequency: RecurringFrequency,
    val dayOfMonth: Int? = null,
    val intervalDays: Int? = null,
    val remindDaysBefore: Int = 3,
    val status: RecurringPaymentStatus = RecurringPaymentStatus.ACTIVE,
    val nextDueDate: LocalDate,
    val lastPaidDate: LocalDate? = null,
    val notes: String? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

### 2.3 Repository Interface

**`RecurringPaymentRepository.kt`**

```kotlin
interface RecurringPaymentRepository {
    fun findById(id: UUID, accountId: UUID): RecurringPayment?
    fun findAllByAccountId(accountId: UUID): List<RecurringPayment>
    fun findAllByFinancialAccountId(financialAccountId: UUID, accountId: UUID): List<RecurringPayment>
    fun findActiveWithDueBefore(cutoffDate: LocalDate): List<RecurringPayment>
    fun findAllActive(): List<RecurringPayment>
    fun save(template: RecurringPayment): RecurringPayment
    fun update(template: RecurringPayment): RecurringPayment
    fun softDelete(id: UUID, accountId: UUID)
}
```

### 2.4 RecurringPaymentService

Responsibilities: `computeNextDueDate`, `confirmPayment` (while loop for overdue catch-up), `upcomingDates`.

**`computeNextDueDate`** — MONTHLY caps day to last day of target month.  
**`confirmPayment`** — while loop advances past paymentDate (handles multiple missed cycles):

```kotlin
fun confirmPayment(template: RecurringPayment, paymentDate: LocalDate): RecurringPayment {
    var next = template.nextDueDate
    while (!next.isAfter(paymentDate)) {
        next = computeNextDueDate(template.copy(nextDueDate = next))
    }
    return recurringPaymentRepository.update(
        template.copy(lastPaidDate = paymentDate, nextDueDate = next)
    )
}
```

### 2.5 RecurringMatchingService (new)

**`RecurringMatchingService.kt`** — replaces `tryAutoLink`.

```kotlin
@Singleton
class RecurringMatchingService(
    private val recurringPaymentRepository: RecurringPaymentRepository,
    @Value("\${banking.recurring.suggestion-threshold:0.80}")
    private val threshold: Float,
    @Value("\${banking.recurring.date-gate-days:10}")
    private val dateGateDays: Long,
) {
    fun findBestMatch(transaction: Transaction): RecurringPayment? {
        if (transaction.status != TransactionStatus.POSTED) return null
        if (transaction.type == TransactionType.BALANCE_ADJUSTMENT) return null
        if (transaction.recurringPaymentId != null) return null

        val candidates = recurringPaymentRepository.findAllByAccountId(transaction.accountId)
            .filter { it.status == RecurringPaymentStatus.ACTIVE && it.deletedAt == null }

        return candidates
            .asSequence()
            .map { it to score(transaction, it) }
            .filter { (_, s) -> s >= threshold }
            .minByOrNull { (template, _) ->
                // among suggestions above threshold, prefer closest due date
                Math.abs(transaction.date.toEpochDay() - template.nextDueDate.toEpochDay())
            }
            ?.first
    }

    internal fun score(transaction: Transaction, template: RecurringPayment): Float {
        val today = LocalDate.now()
        val isPastDue = template.nextDueDate.isBefore(today)
        val dateDelta = Math.abs(transaction.date.toEpochDay() - template.nextDueDate.toEpochDay())

        // Date gate: only waived for past-due templates (to handle late payments)
        if (!isPastDue && dateDelta > dateGateDays) return 0f
        // For past-due templates: transaction must be on or after nextDueDate
        if (isPastDue && transaction.date.isBefore(template.nextDueDate)) return 0f

        var s = 0f

        // Description match — highest weight (most reliable signal)
        s += descriptionScore(template.name, transaction.description) * 0.55f

        // Merchant exact match
        if (template.merchantId != null && template.merchantId == transaction.merchantId) s += 0.25f

        // Amount match (±15%, wider than before since account may differ)
        val expected = template.expectedAmount
        if (expected == null) {
            s += 0.10f  // variable amount: partial credit
        } else if (expected.compareTo(BigDecimal.ZERO) != 0) {
            val pct = transaction.amount.subtract(expected).abs()
                .divide(expected.abs(), 6, RoundingMode.HALF_UP)
            if (pct <= BigDecimal("0.15")) s += 0.20f * (1f - pct.toFloat())
        }

        return s.coerceIn(0f, 1f)
    }

    internal fun descriptionScore(templateName: String, txDescription: String): Float {
        val normTemplate = normalize(templateName)
        val normTx = normalize(txDescription)

        // 1. Direct substring: "comgas" in "transferencia comgas"
        if (normTx.contains(normTemplate)) return 1.0f

        // 2. Token intersection (tokens >= 3 chars)
        val tTokens = normTemplate.split(" ").filter { it.length >= 3 }.toSet()
        val dTokens = normTx.split(" ").filter { it.length >= 3 }.toSet()
        val hits = tTokens.intersect(dTokens)
        if (hits.isNotEmpty()) return hits.size.toFloat() / tTokens.size.toFloat()

        return 0f
    }

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
```

**Score breakdown:**

| Signal | Weight | Notes |
|---|---|---|
| Description match | 0.55 | Substring or token intersection after normalization |
| Merchant exact match | 0.25 | `merchantId` equality |
| Amount within ±15% | up to 0.20 | Proportional to how close; 0 if variable |
| Variable amount | 0.10 | Flat credit when `expectedAmount` is null |

**Threshold:** configurable via `banking.recurring.suggestion-threshold` (default `0.80`). Boolean — either suggests or doesn't.  
**Tie-break:** when multiple candidates exceed threshold, pick the one with `nextDueDate` closest to `transaction.date`.

**Examples:**

| Template | Transaction | Desc score | Merchant | Amount | Total | Suggest? |
|---|---|---|---|---|---|---|
| "Comgas" R$100 | "Transferência comgás" R$100 | 1.0 × 0.55 = 0.55 | — | 1.0 × 0.20 = 0.20 | **0.75** | ❌ below 0.80 |
| "Comgas" R$100 | "Transferência comgás" R$100 + merchant match | 0.55 | 0.25 | 0.20 | **1.00** | ✅ |
| "Comgas" (variable) | "Transferência comgás" R$100 | 0.55 | — | 0.10 | **0.65** | ❌ |
| "Netflix" | "NETFLIX.COM *BR" + merchant | 0.55 | 0.25 | 0.20 | **1.00** | ✅ |
| "Conta de luz" | "ENEL SP PAGAMENTO" | 0.0 | — | — | **0.00** | ❌ |

> **Observation about "Comgas" (variable):** with description only it scores 0.65. If the merchant is linked on the template (R$0.25 bonus) it reaches 0.75 — still below 0.80. This is intentional: variable-amount bills without merchant context should not be auto-suggested; the user should link manually via `linkTransactionToRecurring`.

---

## 3. TransactionService — Remove auto-link

Remove the private `tryAutoLinkRecurring(saved)` call from `create` and `update`. The service no longer touches recurring payments at write time. Matching is on-demand via GraphQL query.

---

## 4. Reminder Job — Fix overdue idempotency

**`RecurringReminderJob.kt`** — job logic unchanged except:

`existsOpenForEntity` SQL must include expiry check:

```sql
-- AccountPendingActionEntityRepository
SELECT COUNT(*) > 0
  FROM banking.account_pending_actions p
 WHERE p.entity_type = :entityType
   AND p.entity_id   = :entityId
   AND p.type        = :type
   AND p.status      = 'PENDING'
   AND (p.expires_at IS NULL OR p.expires_at > NOW())  -- ← add this
```

This ensures that after a PendingAction expires (e.g., the 7-day window after due date), the job creates a fresh reminder on the next hourly tick. The overdue template keeps generating reminders until the user pays or manually skips the cycle.

**Pause/Cancel must delete FUTURE rows (bug fix):**  
`pauseRecurringPayment` and `cancelRecurringPayment` in the resolver must call `transactionRepository.deleteFutureByRecurringPaymentId(id)` (relevant only if FUTURE projection is later re-enabled, but the call should be there for correctness).

---

## 5. GraphQL Schema Changes

### Modified types

```graphql
type RecurringPayment {
  id: ID!
  financialAccountId: ID          # nullable — was ID!
  name: String!
  creditDebitType: CreditDebitType!
  type: TransactionType
  expectedAmount: Float           # null = variable
  currencyCode: String!
  frequency: RecurringFrequency!
  dayOfMonth: Int
  intervalDays: Int
  remindDaysBefore: Int!
  status: RecurringPaymentStatus!
  nextDueDate: Date!
  lastPaidDate: Date
  notes: String
  category: TransactionCategory
  merchant: Merchant
}

input CreateRecurringPaymentInput {
  financialAccountId: ID          # nullable
  name: String!
  # ... rest unchanged
}
```

### New query

```graphql
# Returns the best-scoring template above threshold, or null
suggestRecurringForTransaction(transactionId: ID!): RecurringPayment
```

Resolver: loads transaction by id + accountId, calls `RecurringMatchingService.findBestMatch(transaction)`.

---

## 6. Frontend Changes

### 6.1 RecurringPaymentForm — financialAccountId optional

Make the account selector optional (not required). Label: "Conta de pagamento (opcional)".

### 6.2 TransactionActionMenu — suggestion inline

When the menu opens for a non-BALANCE_ADJUSTMENT, non-TRANSFER transaction that is not already linked:

1. Lazy-query `suggestRecurringForTransaction(transactionId)`
2. If result is non-null, render a highlighted menu item:

```tsx
<MenuItem onClick={() => handleLinkSuggestion(suggestion.id)}>
  <ListItemIcon><RepeatIcon fontSize="small" color="primary" /></ListItemIcon>
  <ListItemText
    primary={t('recurringPayments.suggestion.link', { name: suggestion.name })}
    secondary={t('recurringPayments.suggestion.nextDue', { date: suggestion.nextDueDate })}
  />
</MenuItem>
```

`handleLinkSuggestion` → calls `linkTransactionToRecurring` mutation → `confirmPayment` on backend.

If result is null, the existing "Criar pagamento recorrente" item remains (manual creation).

### 6.3 PendingActionsDrawer — use generated enum

Replace string comparison with generated type:

```tsx
// Before (fragile):
if (rawType === "RECURRING_PAYMENT_DUE") ...

// After:
import { PendingActionType } from "@/lib/graphql/types/__generated__/graphql";
if (rowType === PendingActionType.RecurringPaymentDue) ...
```

Ensure codegen is run after backend deploys new enum value.

---

## 7. Unit Tests Required

### RecurringPaymentServiceTest

- `computeNextDueDate` MONTHLY: day 31 in Feb → capped to 28/29
- `computeNextDueDate` MONTHLY: day 31 in months with 30 days → capped to 30
- `confirmPayment`: single cycle, payment on exact date
- `confirmPayment`: payment 1 month late → advances two cycles

### RecurringMatchingServiceTest

- Description substring match: "Comgas" in "Transferência comgás" → score = 1.0
- Description token match: "Conta Luz" in "CPFL LUZ PAGAMENTO" → partial
- No description match + amount only → below threshold → null
- Past-due template: date gate waived, transaction on day+31 still scores
- Past-due template: transaction before `nextDueDate` → score = 0
- Tie-break: two candidates above threshold → returns closest date

---

## 8. Key Business Rules

1. `financialAccountId` is nullable — template is account-agnostic; matching searches entire tenant.
2. `expected_amount = NULL` — variable amount; amount signal gives flat 0.10 bonus in scoring.
3. **No auto-link** — matching is always on-demand and user-confirmed.
4. Threshold default = `0.80`, configurable via application property. Boolean result.
5. Tie-break: among candidates above threshold, closest `nextDueDate` to `transaction.date` wins.
6. Past-due handling: date gate waived for templates where `nextDueDate < today`; `confirmPayment` while loop advances through all missed cycles.
7. `existsOpenForEntity` must respect `expires_at` — expired actions do not block new reminders.
8. Pause/Cancel must call `deleteFutureByRecurringPaymentId` (no-op now; safety for future).
9. `BALANCE_ADJUSTMENT` transactions are never eligible for matching.
10. `MONTHLY` `computeNextDueDate` caps day to last valid day of target month.
