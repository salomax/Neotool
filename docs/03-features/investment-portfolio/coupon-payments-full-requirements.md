---
title: Fixed Income Coupon Payments — Full Requirements
type: requirements
module: investment-portfolio
status: active
version: 1.0.0
tags: [investment, fixed-income, coupon, juros, pending-actions, reconciliation, performance, renda-fixa]
ai_optimized: true
search_keywords: [coupon payments requirements, cupom renda fixa, investment reconciliation, coupon performance display, pending action coupon]
related:
  - 03-features/investment-portfolio/fixed-income-coupon-payments.md
  - 03-features/investment-portfolio/fixed-income-coupon-payments-backlog.md
  - 03-features/investment-portfolio/fixed-income-valuation.md
  - 03-features/investment-portfolio/investment-performance.md
last_updated: 2026-04-16
---

# Fixed Income Coupon Payments — Full Requirements

> **Purpose**: Single source of truth for everything that needs to change or be built around fixed income coupon payments, informed by the original spec, the code review, and financial modelling research. Organised by phase. Each requirement explains *why* it exists, not just *what* to do.

---

## Background: Why Coupon Bonds Are Different

A bullet bond (CDB, LCI) accumulates all interest internally until maturity. The holding value grows continuously and the user gets one payout at the end. Everything the current system does maps to this model.

A coupon bond (NTN-B / Tesouro IPCA+, Debêntures, CRI, CRA) periodically pays interest to the holder during the holding period. This creates three problems the current system doesn't handle:

1. **Accrual resets after each coupon** — interest doesn't compound on previous coupons; the holding's accrual clock restarts from zero at each payment date. Valuation from `issueDate` forward is wrong.

2. **Gain calculation is incomplete without counting received coupons** — if a user has held a 2-year debenture and received 4 semi-annual coupons, the current "gain" shown (accrual since last coupon only) looks artificially small. The app makes the investment look like it's underperforming.

3. **The contracted rate is not the user's compound return** — "IPCA + 6% semestral" describes the coupon size. If the user spends the coupons, their actual compounding is zero beyond IPCA correction. If they reinvest, they compound — but on a net-of-IR basis (each coupon is taxed at source), making it less efficient than a same-rate bullet bond.

**Brazilian context:** NTN-B (Tesouro IPCA+ com juros semestrais) is one of the most commonly held instruments by Brazilian retail investors. Every 6 months the Treasury pays the coupon directly to the investor's account. IR is withheld at source on each payment following the regressive table (22.5% → 15%). The Valor Nominal Atualizado (VNA) — the IPCA-adjusted principal — continues to accrue, but the interest earned on that VNA is paid out, not reinvested automatically.

---

## Phase 1: Base Feature — IMPLEMENTED (fixes required)

> The coupon frequency field, schedule service, valuation accrual fix, GraphQL schema, frontend form and timeline are already implemented. See:
> - `docs/03-features/investment-portfolio/fixed-income-coupon-payments.md`
> - `docs/03-features/investment-portfolio/fixed-income-coupon-payments-backlog.md`

### Fixes Required in Current Implementation

These were identified during code review. None are breaking for users today, but two are correctness issues.

---

#### FIX-1 — Valuation principal after coupon payment (correctness risk)

**File:** `FixedIncomeValuationService.kt`

**Problem:** The valuation engine uses `lastInterestPayment.grossValue` as the principal for the post-coupon accrual period. The semantics of `grossValue` on an `INTEREST_PAYMENT` transaction are ambiguous — it likely represents the coupon amount paid (e.g. R$500), not the remaining holding balance (e.g. R$10.500). If so, the accrual after a coupon payment would be computed on R$500 instead of R$10.500 — a severe calculation error.

**Why this matters:** Incorrect post-coupon valuations would surface to the user immediately after recording an interest payment, making the holding appear to be worth far less than it is.

**Fix:** Do not use the transaction's `grossValue` as principal. The principal of a fixed income holding does not change when a coupon is paid — only the accrual clock resets. Use the holding's own `averageCost × quantity` as principal:

```kotlin
// WRONG — grossValue is the coupon amount, not the remaining balance
principal = lastInterestPayment.grossValue

// CORRECT — principal is unchanged by coupon payment
principal = holding.averageCost * holding.quantity
```

**Action:** Verify by reading `InvestmentTransactionService` to confirm how `grossValue` is populated on `INTEREST_PAYMENT` transactions. If it's the coupon amount, apply the fix above.

---

#### FIX-2 — Unsafe enum parsing in GraphQL resolver (runtime crash risk)

**File:** `InvestmentResolver.kt` (lines ~539 and ~590)

**Problem:**
```kotlin
(this["couponFrequency"] as? String)?.let(CouponFrequency::valueOf)
```
`CouponFrequency::valueOf` throws `IllegalArgumentException` on an invalid string. The `as?` safely returns null for non-String types, but if the value IS a String and is invalid (e.g. `"WEEKLY"`), `valueOf` throws at runtime — it is not caught.

**Fix:**
```kotlin
(this["couponFrequency"] as? String)?.let {
    runCatching { CouponFrequency.valueOf(it) }.getOrNull()
}
```

---

#### FIX-3 — Missing test: bond shorter than one coupon period

**File:** `CouponScheduleServiceTest.kt`

**Problem:** The spec requires that a bond shorter than one coupon period generates an empty schedule (e.g. 2-month bond with `SEMI_ANNUAL` frequency). This test case is missing. Without it, the edge case could silently generate an incorrect first entry before `issueDate`.

**Fix:** Add test:
```kotlin
@Test
fun `returns empty when bond life is shorter than one coupon period`() {
    val holding = holdingWith(
        issueDate = LocalDate.of(2024, 1, 1),
        maturityDate = LocalDate.of(2024, 3, 1),  // 2 months
        couponFrequency = CouponFrequency.SEMI_ANNUAL,
    )
    assertTrue(service.generateSchedule(holding, emptyList()).isEmpty())
}
```

---

#### FIX-4 — TypeScript enum casing check

**File:** `RecordFixedIncomeDrawer.tsx`

**Problem:** The Zod schema uses `.default(CouponFrequency.AtMaturity)`. TypeScript-generated enums from GraphQL codegen use the schema's casing — which may be `CouponFrequency.AtMaturity` (PascalCase) or `CouponFrequency.AT_MATURITY` (screaming snake). If the casing doesn't match what codegen produces, the default is silently wrong and the mutation sends `undefined`.

**Fix:** After running codegen, verify the generated enum shape in the `.generated.ts` file and confirm the default value in the Zod schema matches exactly.

---

### Utility Refactor: Extract `DateSequenceGenerator`

**Not urgent, but clean.** The date sequence generation in `CouponScheduleService` (loop backwards from `maturityDate` by N months) is a reusable utility with zero domain knowledge. Extracting it now avoids duplicating the logic when a second consumer (e.g. recurring payment alerts) needs it.

**New file:** `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/util/DateSequenceGenerator.kt`

```kotlin
object DateSequenceGenerator {
    fun generate(
        start: LocalDate,
        end: LocalDate? = null,   // null = open-ended
        stepMonths: Long,
        limit: Int = 600,         // safety cap
    ): List<LocalDate>
}
```

Add extension on `CouponFrequency` to keep domain knowledge out of the utility:

```kotlin
// InvestmentEnums.kt
fun CouponFrequency.toStepMonths(): Long = when (this) {
    CouponFrequency.MONTHLY -> 1L
    CouponFrequency.QUARTERLY -> 3L
    CouponFrequency.SEMI_ANNUAL -> 6L
    CouponFrequency.ANNUAL -> 12L
    CouponFrequency.AT_MATURITY -> error("AT_MATURITY has no step")
}
```

`CouponScheduleService` calls `DateSequenceGenerator.generate(issueDate, maturityDate, couponFrequency.toStepMonths())`. Zero change to external behaviour.

**Why not a shared module:** There is currently one consumer. Extract to a shared module only when a second service needs it. The import change at that point is trivial.

---

## Phase 2: Coupon Pending Actions

> **Status:** Not yet implemented. Depends on Phase 1 being stable.

### What and Why

The system already has a `UserPendingAction` mechanism used for holding maturity notifications. The maturity pattern:

```
PURCHASE recorded → create INVESTMENT_MATURED action, visible_from = maturityDate + 1d
```

Coupon payments are the same concept: a known future event on a holding that needs user attention. The difference is that coupons recur. Instead of creating all future coupon actions upfront (too many — a 10-year monthly bond = 120 records), we chain: only one pending action exists at a time per holding, pointing at the next coupon.

### New Action Type

```kotlin
// Add to PendingActionType enum:
COUPON_PAYMENT_DUE
```

### Lifecycle

```
PURCHASE recorded (couponFrequency != AT_MATURITY)
  → upsertNextCouponPendingActionIfNeeded(holding)
  → creates COUPON_PAYMENT_DUE, visible_from = nextCouponDate - 7 days

INTEREST_PAYMENT transaction recorded on holding
  → find open COUPON_PAYMENT_DUE for this holding → mark COMPLETED
  → upsertNextCouponPendingActionIfNeeded(holding)  ← creates the following coupon's action

maturityDate changed on holding
  → update visible_from on existing PENDING action (same as maturity pattern)

couponFrequency changed to AT_MATURITY
  → cancel any open COUPON_PAYMENT_DUE for this holding

Holding deleted / closed
  → pending action stays (audit trail) — same as maturity pattern
```

### Rule: User ignores the action

Nothing happens. The action stays open until the user either records an `INTEREST_PAYMENT` transaction (which completes it and chains the next) or dismisses it manually. No cascade, no auto-close. Same behaviour as `INVESTMENT_MATURED`.

### Where to implement

`InvestmentTransactionService` — same file as `upsertMaturityPendingActionIfNeeded()`. Add:

```kotlin
fun upsertNextCouponPendingActionIfNeeded(holding: Holding, today: LocalDate = LocalDate.now())
fun completeCurrentCouponPendingAction(holding: Holding)
```

Call `completeCurrentCouponPendingAction` + `upsertNextCouponPendingActionIfNeeded` whenever an `INTEREST_PAYMENT` is saved for a holding with `couponFrequency != AT_MATURITY`.

### Notification

`PendingActionNotifierJob` (daily 08:00) already handles delivery — no changes needed there. The comms-service owns template selection. Banking is agnostic to the channel.

### Frontend action config

Add `COUPON_PAYMENT_DUE` to the `ACTION_CONFIG` registry that maps `PendingActionType → CTA + drawer`.

```
CTA label:   "Registrar cupom"
Drawer:      Pre-filled INTEREST_PAYMENT recording drawer (see Phase 3)
```

---

## Phase 3: Investment Cash Event Reconciliation

> **Status:** Not yet implemented. Depends on Phase 2 (pending actions) for the best UX, but can be built independently.

### The Problem

When a coupon is paid, two things happen in the real world:
1. The holding accrues and pays interest (investment domain)
2. Money arrives in the user's bank account (cash domain)

Currently these are entirely disconnected. The user has to manually record both. The investment domain has `INTEREST_PAYMENT` transactions. The cash domain has bank transactions. Nothing links them.

This same disconnection exists for all investment cash events:

| Real-world event | Investment transaction | Cash transaction |
|---|---|---|
| Coupon payment | INTEREST_PAYMENT | INFLOW |
| Stock dividend | DIVIDEND | INFLOW |
| JCP | INTEREST_ON_EQUITY | INFLOW |
| FII dividend | DIVIDEND | INFLOW |
| Amortization | AMORTIZATION | INFLOW |

### Two Entry Points (both create the same data state)

**Path A — Pending action first (proactive):**
1. User sees `COUPON_PAYMENT_DUE` action
2. Taps "Registrar cupom" → opens pre-filled drawer with projected date and estimated gross/net amounts
3. User adjusts date and actual net value received
4. On confirm: system atomically creates `INTEREST_PAYMENT` investment transaction + `INFLOW` cash transaction linked together
5. Pending action marked COMPLETED → next action created

**Path B — Cash transaction first (reactive, most common):**
1. User sees money arrive in bank account (creates or imports cash transaction)
2. Opens the transaction → selects "Vincular a investimento"
3. Selects the holding from their portfolio
4. System surfaces pending actions for that holding near the transaction date as suggested matches:
   ```
   💡 Possível correspondência:
      Cupom semestral — Debênture XYZ
      Previsto para 14/04/2026 · Estimado ~R$500
      [Confirmar vínculo]   [Selecionar outro evento]
   ```
5. User confirms (or selects a different event type)
6. System creates `INTEREST_PAYMENT` investment transaction linked to the cash transaction
7. Pending action marked COMPLETED → next action created

Both paths resolve to the same data state. Neither path is mandatory — the system works without them (transactions can still be recorded manually as today).

### The Link

```kotlin
// On InvestmentTransaction — investment event owns the reference
val relatedCashTransactionId: UUID? = null
```

**Why investment owns the reference:** The investment event is the domain fact (the coupon happened contractually). The cash transaction is the settlement evidence. Not all investment events produce a cash transaction (e.g. some maturities roll over). The investment side is more stable.

### Accounting: What Happens to Balances

When a coupon is paid and both transactions are recorded:

```
Before:
  Holding gross value:  R$10.500  (R$10.000 principal + R$500 accrued)
  Cash account:         R$0
  Net worth:            R$10.500

After INTEREST_PAYMENT + cash INFLOW:
  Holding gross value:  R$10.000  (accrual resets — valuation engine handles this automatically)
  Cash account:         R$425     (R$500 gross − R$75 IR at 15%)
  Net worth:            R$10.425  (decrease = IR paid, correct)
```

The holding's gross value decreases naturally because the valuation engine now accrues from the payment date (Phase 1 fix). No explicit debit of the holding is required — the valuation math handles it.

The cash transaction is always an **INFLOW** — the user receives money. Direction never varies for coupon/dividend events.

**Which cash account receives the credit:**
- For brokerage instruments (Debêntures, CRI, CRA): the cash balance of the same brokerage account as the holding. Default to same `financialAccountId` as the holding.
- For bank instruments (CDB, LCI): the coupon may go to a separate checking account. The user should be able to select the destination account, with the holding's account as the default suggestion.

### GraphQL / Schema additions

```graphql
# On InvestmentTransaction:
relatedCashTransactionId: ID

# New mutation for Path A (confirm from pending action):
confirmCouponPayment(input: ConfirmCouponPaymentInput!): ConfirmCouponPaymentResult!

input ConfirmCouponPaymentInput {
  holdingId: ID!
  tradeDate: Date!
  grossValue: BigDecimal!
  netValue: BigDecimal!        # after IR/IOF
  incomeTax: BigDecimal!
  destinationAccountId: ID!   # where cash goes
  pendingActionId: ID          # optional — links and closes the action
}

# Path B is handled by extending the existing cash transaction create/update mutation
# to accept an optional investmentEventInput: InvestmentEventLinkInput
```

---

## Phase 4: Performance Display for Coupon Bonds

> **Status:** Not yet implemented. Can be built after Phase 1 fixes are stable. Independent of Phases 2 and 3.

### The Problem

The current holding detail shows:

```
Valor bruto atual:   R$10.247
Ganho:               +R$247  (+2,47%)
```

For a coupon bond where the user has already received 4 semi-annual coupons totalling R$1.800 (net), this display is wrong in a user-visible way: it shows only the current accrual (since the last coupon), ignoring all the cash that has already left the holding and landed in their account. The investment looks like it's barely earning anything, when it has actually paid out significant income.

### Correct Gain Calculation for Coupon Holdings

```
totalGain (R$) = (currentGrossValue − initialInvestment) + sumOfNetCouponsReceived
totalGain (%)  = totalGain / initialInvestment
```

Where `sumOfNetCouponsReceived` = sum of `netValue` on all `INTEREST_PAYMENT` transactions for this holding. These transactions already exist in the database — this is a query change, not a new data model.

**XIRR already handles this correctly** if the coupon transactions are included in the cash flow series. The existing XIRR computation should be verified to include `INTEREST_PAYMENT` outflows. If it does, the XIRR number is already the honest metric. If not, this is the fix.

### Display Changes

**Detail view — summary section:**

| Field | Current | Required |
|---|---|---|
| Valor atual | currentGrossValue | currentGrossValue (unchanged) |
| Cupons recebidos | *(not shown)* | Sum of INTEREST_PAYMENT netValue |
| Ganho total | currentGrossValue − invested | (currentGrossValue − invested) + coupons |
| XIRR | Shown (verify includes coupons) | Shown, verified to include coupons |

**Show "Cupons recebidos (líq.)" as a separate line** — don't fold it silently into the gain. Users should understand where their return is coming from: accrued value vs. paid income.

### Performance Chart

The current chart likely shows a continuous upward curve (compounding projection). For a coupon bond this is misleading — it shows value that the user doesn't actually hold in the position (it has been paid out).

**Two honest approaches:**

Option A — **Sawtooth + income overlay** (more informative):
- Line 1: Holding value over time — rises between coupons, drops on each payment date. Shows the sawtooth pattern.
- Line 2 (stacked or separate): Cumulative net coupons received — a step function going up at each payment.
- Combined: Total wealth = position + coupons. This is the true picture.

Option B — **Total wealth only** (more readable):
- Single line: `holdingValue + cumulativeCoupons` at each point in time.
- Smoother curve. Less granular but immediately understandable.

**Recommendation: Option B for the chart, with an information note** explaining that for coupon instruments, the curve includes both accrual and paid coupons. The detail view's separate "Cupons recebidos" field gives the breakdown.

### The Contracted Rate vs. Effective Return

The rate displayed on the holding card in the list view (e.g. "IPCA + 6% a.a.") is the **contracted rate** — not the user's effective return. This is correct and should stay. But it needs a clear label: "Taxa contratada" not "Rentabilidade".

The actual effective return metric is XIRR, shown in the detail view.

**Why the difference matters for coupon bonds:**
- Bullet bond at 14% a.a.: effective return ≈ 14% a.a. (IR applies once at maturity)
- Coupon bond at 14% a.a. with semi-annual payments: effective return < 14% a.a. because each coupon is taxed at source (early IR payment has a time-value cost) and reinvestment is not guaranteed
- The contracted rate overstates the effective return for coupon bonds
- Showing XIRR makes this honest without requiring the user to understand tax mechanics

---

## Summary: What Needs to Happen and In What Order

```
Phase 1 (done, fixes needed)
├── FIX-1: Valuation principal after coupon payment ← verify and fix if wrong
├── FIX-2: Safe enum parsing in resolver
├── FIX-3: Missing edge case test
├── FIX-4: TypeScript enum casing
└── REFACTOR: Extract DateSequenceGenerator utility

Phase 2 (next)
├── New PendingActionType: COUPON_PAYMENT_DUE
├── upsertNextCouponPendingActionIfNeeded() in InvestmentTransactionService
├── completeCurrentCouponPendingAction() in InvestmentTransactionService
├── Wire on PURCHASE and INTEREST_PAYMENT
└── Frontend ACTION_CONFIG entry

Phase 3 (after Phase 2)
├── InvestmentTransaction.relatedCashTransactionId field + migration
├── confirmCouponPayment mutation (Path A — from pending action)
├── Cash transaction "wire to investment" UI (Path B — from cash side)
├── Holding account suggestion logic for destination account
└── Surface pending action suggestions when wiring cash → investment

Phase 4 (independent, can run in parallel with Phase 2/3)
├── Gain calculation: include sumOfNetCouponsReceived
├── "Cupons recebidos (líq.)" line in detail view
├── Verify XIRR includes INTEREST_PAYMENT cash flows
├── Performance chart: total wealth curve (position + coupons)
└── "Taxa contratada" label clarification in holding list card
```

---

## Open Questions

- [ ] **FIX-1 verification**: Does `INTEREST_PAYMENT.grossValue` store the coupon amount or the holding balance? Answer determines whether FIX-1 is needed.
- [ ] **Phase 3 atomicity**: Is creating an `InvestmentTransaction` + `CashTransaction` in a single DB transaction feasible in the current service architecture? Or do they live in separate transaction boundaries?
- [ ] **Phase 3 destination account**: When the coupon goes to a separate checking account (bank instruments), how does the user select it? Should the pending action pre-fill based on the user's linked accounts?
- [ ] **Phase 4 chart**: Option A (sawtooth) vs Option B (total wealth)? UX decision.
- [ ] **Phase 4 scope**: Does the gain fix apply to the list view card or only the detail view?

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-04-16 | Initial consolidated requirements from spec, code review, and financial modelling research |
