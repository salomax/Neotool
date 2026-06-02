---
title: Fixed Income Coupon Payments
type: feature
module: investment-portfolio
feature_slug: fixed-income-coupon-payments
status: draft
version: 1.0.0
tags: [investment, fixed-income, coupon, juros, pagamento, renda-fixa, backlog]
ai_optimized: true
search_keywords: [coupon frequency, pagamento de juros, cupom renda fixa, interest payment schedule, fixed income coupon]
related:
  - 03-features/investment-portfolio/fixed-income-valuation.md
  - 03-features/investment-portfolio/fixed-income-valuation-backlog.md
  - 05-backend/patterns/domain-model-pattern.md
  - 07-frontend/patterns/form-pattern.md
last_updated: 2026-04-15
---

# Fixed Income Coupon Payments

> **One-line summary**: Allow users to record the coupon payment frequency of a fixed income holding and see a projected schedule of upcoming and past interest payments.

---

## Overview

### Problem Context

Some fixed income instruments pay periodic interest (cupom) to the holder during the holding period rather than accumulating it all until maturity. This is common for:

- **Debêntures** — semi-annual or annual coupon payments
- **CRI / CRA** — monthly, quarterly, or semi-annual payments
- **LF / LIG** — semi-annual payments
- **Tesouro IPCA+** — semi-annual coupon defined by the Treasury
- **CDB / LCI / LCA** — almost always bullet (no interim payments), but can optionally have coupons

The current data model treats all fixed income as bullet bonds — it has no concept of periodic coupon payment frequency. Users cannot record or track scheduled interest events on their holdings.

### Goals

1. Capture the coupon frequency (`couponFrequency`) on a holding — a single new field.
2. Generate a projected coupon schedule server-side from contractual data (no persisted rows).
3. Surface the projected schedule in the holding detail view: upcoming and past coupon dates with estimated amounts (clearly marked as projections).
4. Align the accrual period used in the valuation engine with the actual coupon cycle (accrue from last coupon date, not issue date).
5. Design for future import matching: when account statement import arrives, `INTEREST_PAYMENT` transactions can be matched against projected coupon dates using proximity logic.

### What This Is NOT

- **Not a coupon ledger**: scheduled coupons are not persisted as entity rows. They are derived on-the-fly from holding data.
- **Not a coupon amount calculator with high precision**: all amounts are marked as `~` projections. Post-fixed coupons (CDI, IPCA+) depend on future rate paths that are unknown.
- **Not a payment recording feature**: users record actual payments as `INTEREST_PAYMENT` investment transactions (already supported). This feature provides the schedule context around those transactions.

### Success Criteria

- A user can set `couponFrequency` on any fixed income holding via the existing record/edit drawer.
- The holding detail view shows a timeline of projected coupon dates from issue to maturity.
- Past coupon dates display whether an `INTEREST_PAYMENT` transaction exists for that period.
- The valuation engine accrues from the last matched `INTEREST_PAYMENT` date when one exists, improving accuracy for coupon-paying holdings.

---

## Domain Model

### New Enum: `CouponFrequency`

```
File: service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/investment/InvestmentEnums.kt
```

```kotlin
/**
 * How often a fixed income instrument pays interest (coupon / cupom) during its life.
 * AT_MATURITY is the default — all accrued interest is paid at redemption / maturity.
 */
enum class CouponFrequency {
    MONTHLY,      // mensal — some CRIs, some debentures
    QUARTERLY,    // trimestral
    SEMI_ANNUAL,  // semestral — most debentures, CRIs, CRAs, Tesouro IPCA+
    ANNUAL,       // anual
    AT_MATURITY,  // bullet — CDB, LCI, LCA typical; no interim payments
}
```

### New Field on `Holding` Domain Object

```
File: service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/investment/Holding.kt
```

Add below `isTaxExempt`:

```kotlin
// null means AT_MATURITY (default for legacy holdings, matches prior behaviour)
val couponFrequency: CouponFrequency? = null,
```

### New Field on `HoldingEntity`

```
File: service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/investment/HoldingEntity.kt
```

Add below `isTaxExempt`:

```kotlin
@Enumerated(EnumType.STRING)
@Column(name = "coupon_frequency", length = 15)
open var couponFrequency: CouponFrequency? = null
```

### Projected Coupon Schedule (Not Persisted)

A `CouponScheduleEntry` value object is used only at query time:

```kotlin
data class CouponScheduleEntry(
    val scheduledDate: LocalDate,
    val periodStart: LocalDate,   // start of the coupon accrual period
    val periodEnd: LocalDate,     // = scheduledDate
    val isPast: Boolean,
    val matchedTransaction: InvestmentTransaction? = null, // INTEREST_PAYMENT near this date
)
```

---

## Business Rules

**BR-1: Default behaviour**
- If `couponFrequency` is null or `AT_MATURITY`, no schedule is generated and the holding behaves exactly as today.
- The valuation engine continues to accrue from `issueDate`.

**BR-2: Schedule generation**
- Generated backwards from `maturityDate` at the given frequency.
- First coupon date is `issueDate` + one frequency period.
- Last coupon date is the `maturityDate` itself (at maturity, the final coupon is folded into the redemption event — it appears in the schedule but is not a separate cash flow).
- If `maturityDate` is null, the schedule cannot be generated. Surface a warning in the UI.

**BR-3: Matching rule for past coupons**
- For each past `scheduledDate`, look for an `INTEREST_PAYMENT` transaction on the holding where `tradeDate` is within ±7 calendar days of `scheduledDate`.
- If a match is found, set `matchedTransaction`.
- If no match, the coupon appears as "expected, not recorded".

**BR-4: Valuation accrual adjustment**
- For holdings with `couponFrequency != AT_MATURITY`:
  - Determine the most recent past `scheduledDate` that has a `matchedTransaction`.
  - Use `matchedTransaction.tradeDate` as the accrual start instead of `issueDate`.
  - If no matched transaction exists yet, fall back to `issueDate` (same as today).
- `principal` for the accrual period is the holding's current `grossValue` as-of the matched transaction, or `averageCost × quantity` if no match.

**BR-5: Future import matching hook**
- When a future import pipeline creates an `INTEREST_PAYMENT` transaction, it should call `CouponScheduleService.findMatchingEntry(holdingId, tradeDate)` to check if the transaction falls within a projected coupon window.
- This service method is the single integration seam — no other coupling to the import pipeline.

**BR-6: All asset types**
- `couponFrequency` is available for **all** fixed income asset types, including CDB, LCI, LCA. The field is not restricted by asset type on the backend or frontend.

---

## New Service: `CouponScheduleService`

```
Location: service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/investment/CouponScheduleService.kt
```

Pure domain service — no I/O, no DB access. Injected with `InvestmentTransactionRepository` for transaction lookup at query time.

### Interface

```kotlin
interface CouponScheduleService {
    /**
     * Generate projected coupon entries for [holding].
     * Returns empty list when couponFrequency is null or AT_MATURITY.
     * Returns empty list when maturityDate is null.
     */
    fun generateSchedule(
        holding: Holding,
        transactions: List<InvestmentTransaction>,
        today: LocalDate = LocalDate.now(),
    ): List<CouponScheduleEntry>

    /**
     * Find the projected coupon entry that best matches [tradeDate] (within ±7 days).
     * Returns null if no schedule entry matches.
     * Used by the import pipeline (BR-5).
     */
    fun findMatchingEntry(
        holding: Holding,
        transactions: List<InvestmentTransaction>,
        tradeDate: LocalDate,
    ): CouponScheduleEntry?
}
```

---

## Valuation Engine Changes

```
File: service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/investment/FixedIncomeValuationService.kt
```

### Change: Accrual Start Date

When `holding.couponFrequency != null && holding.couponFrequency != AT_MATURITY`:

1. Fetch all `INTEREST_PAYMENT` transactions for the holding.
2. Find the most recent one with `tradeDate <= today`.
3. If found: `accrualStart = transaction.tradeDate`, `principal = transaction.grossValue` (the holding value at that payment date, i.e. the face value that continues accruing).
4. If not found: `accrualStart = holding.issueDate` (original behaviour).

This is the only change to the valuation engine — no new calculation formulas needed.

---

## GraphQL Schema Changes

```
File: contracts/graphql/subgraphs/banking/schema.graphqls
```

### New Enum

```graphql
"""
How often a fixed income instrument pays interest during its holding period.
"""
enum CouponFrequency {
  MONTHLY
  QUARTERLY
  SEMI_ANNUAL
  ANNUAL
  "No interim payments — all interest accumulates and is paid at maturity or redemption."
  AT_MATURITY
}
```

### New Fields on `InvestmentHolding`

```graphql
"Coupon payment frequency. Null and AT_MATURITY are equivalent — no scheduled payments."
couponFrequency: CouponFrequency

"Projected coupon schedule from issueDate to maturityDate. Empty when couponFrequency is null or AT_MATURITY, or when maturityDate is missing."
couponSchedule: [CouponScheduleEntry!]!
```

### New Type

```graphql
type CouponScheduleEntry {
  "Projected payment date."
  scheduledDate: Date!
  "Start of the accrual period for this coupon."
  periodStart: Date!
  "Whether this date is in the past."
  isPast: Boolean!
  "The INTEREST_PAYMENT transaction matched to this coupon, if any."
  matchedTransaction: InvestmentTransaction
}
```

### Mutation Input Changes

Add to `RecordFixedIncomeInput` and `UpdateFixedIncomeInput`:

```graphql
couponFrequency: CouponFrequency
```

---

## Frontend Changes

### 1. `RecordFixedIncomeDrawer` — Add `couponFrequency` field

```
File: web/src/app/personal-finance/investments/components/RecordFixedIncomeDrawer.tsx
```

- Add a `SelectField` for `couponFrequency` after the `maturityDate` field.
- Options: `AT_MATURITY` (default, shown first), `MONTHLY`, `QUARTERLY`, `SEMI_ANNUAL`, `ANNUAL`.
- No conditional visibility — show for all fixed income asset types (BR-6).
- Zod schema: `couponFrequency: z.nativeEnum(CouponFrequency).optional()`.
- Default value: `AT_MATURITY`.
- i18n keys in `pt.json` and `en.json`.

### 2. `FixedIncomeHoldingDetailContent` — Coupon Schedule Section

```
File: web/src/app/personal-finance/investments/components/fixed-income/FixedIncomeHoldingDetailContent.tsx
```

Add a **Pagamento de Juros (Cupom)** section below the performance chart, only when `couponSchedule.length > 0`.

#### Schedule Timeline Component

```
File: web/src/app/personal-finance/investments/components/fixed-income/CouponScheduleTimeline.tsx
```

Each row shows:

| Column | Content |
|---|---|
| Date | `scheduledDate` formatted as `dd/MM/yyyy` |
| Status | Paid (green check, linked to transaction) / Expected (grey clock) / Overdue (amber warning — past date with no matched transaction) |
| Amount | `~R$ X.XXX,XX` with `~` prefix to signal projection; `—` for AT_MATURITY or when not computable |

- Show at most 12 upcoming entries by default, with a "ver todos" expand.
- Past entries collapsed by default, with a "ver histórico" expand.

### 3. GraphQL Query Update

```
File: web/src/app/personal-finance/investments/components/fixed-income/api/queries.ts
```

Add `couponFrequency` to the holding fragment and add the `couponSchedule` sub-query to `FIXED_INCOME_HOLDING_DETAIL`.

---

## Database Migration

```
File: service/kotlin/banking/src/main/resources/db/migration/V0_21__investment_holding_coupon_frequency.sql
```

```sql
ALTER TABLE banking.investment_holdings
    ADD COLUMN coupon_frequency VARCHAR(15) NULL;

COMMENT ON COLUMN banking.investment_holdings.coupon_frequency
    IS 'Coupon payment frequency. NULL is equivalent to AT_MATURITY (bullet bond).';
```

No backfill needed — `null` is treated as `AT_MATURITY` by the domain logic (BR-1).

---

## Phase 2 — Coupon Date Anchoring & Schedule Precision

> **Status**: Backlog — not yet implemented.  
> **Prerequisite**: Phase 1 (couponFrequency field + basic schedule generation) must be shipped first.

### Motivation

Phase 1 generates a schedule by simply adding frequency periods to `issueDate`. This is wrong for two important classes of instruments:

1. **Tesouro Direto** — the government publishes fixed payment windows tied to the maturity year. The schedule is entirely independent of when the user invested; it depends only on the bond series (NTN-B, NTN-F) and the parity of the maturity year.

2. **Private credit (CRI, CRA, Debêntures)** — the issuer defines a specific day-of-month for payment. The first coupon lands in the month *after* the accrual period, not one period after the issue date.

Without fixing this, projected coupon dates will be wrong for most real-world instruments.

---

### New Field: `couponDayOfMonth`

A new integer field on `Holding` and `HoldingEntity`:

```kotlin
// Day of month on which coupon cash settles (1–28).
// Required when couponFrequency != null && couponFrequency != AT_MATURITY
// AND the asset type is not a Tesouro Direto with a system-defined calendar.
// Null for AT_MATURITY and for Tesouro Direto (schedule is system-determined).
val couponDayOfMonth: Int? = null
```

DB migration (new file `V0_22__investment_holding_coupon_day_of_month.sql`):

```sql
ALTER TABLE banking.investment_holdings
    ADD COLUMN coupon_day_of_month SMALLINT NULL
        CONSTRAINT chk_coupon_day_of_month CHECK (coupon_day_of_month BETWEEN 1 AND 28);

COMMENT ON COLUMN banking.investment_holdings.coupon_day_of_month
    IS 'Day of month on which coupon cash settles (1–28). NULL for AT_MATURITY and Tesouro Direto (system calendar).';
```

Capped at 28 to avoid month-end edge cases — a coupon that pays "on the last day" should be modelled as day 28, not 31.

GraphQL schema:

```graphql
"Day of month (1–28) on which coupon cash settles. Null for AT_MATURITY and Tesouro Direto (system calendar)."
couponDayOfMonth: Int
```

---

### Schedule Generation Rules (Phase 2)

These rules replace BR-2 from Phase 1 for the affected instrument types. All other rules (BR-1, BR-3, BR-4, BR-5, BR-6) remain unchanged.

---

#### BR-P2-1: Tesouro IPCA+ Semiannual (NTN-B)

**Identification**: `assetType == TESOURO_IPCA_PLUS` and `couponFrequency == SEMI_ANNUAL`.

**Windows** (tied to the parity of the maturity year):

| Maturity year parity | Window 1 | Window 2 |
|---|---|---|
| Even (2026, 2028, …) | Feb 15 | Aug 15 |
| Odd (2025, 2027, …) | May 15 | Nov 15 |

**First payment**: the first window date that falls strictly after `issueDate`. If `issueDate` coincides with a window date, the next window is used (the user is receiving that coupon as the seller, not as the new buyer).

**Last payment**: the window date that falls on or immediately before `maturityDate`. The maturity redemption is a separate event and always includes the final coupon amount — it does not generate a separate `INTEREST_PAYMENT` schedule entry.

**`couponDayOfMonth`**: not user-configurable for NTN-B. The service hard-codes day 15 and the month derived from the maturity-year parity rule above.

**Example** — Tesouro IPCA+ 2026 (even year), issue date 2026-04-10:
- Windows: Feb 15 and Aug 15 each year
- 2026-02-15 is in the past → skip
- First payment: **2026-08-15** (interest accrued from 2026-04-10 to 2026-08-15)
- Subsequent: 2027-05-15, 2027-11-15, 2028-02-15, 2028-08-15 (= maturityDate, excluded from schedule)

---

#### BR-P2-2: Tesouro Prefixado Semiannual (NTN-F)

**Identification**: `assetType == TESOURO_PREFIXADO_JUROS_SEMESTRAIS` and `couponFrequency == SEMI_ANNUAL`.

**Windows**: always **Jan 1** and **Jul 1** of each year.

**First payment**: the first of these dates strictly after `issueDate`.

**`couponDayOfMonth`**: not user-configurable. Hard-coded as day 1.

---

#### BR-P2-3: Private Credit — Subsequent Month Rule (any frequency)

**Identification**: not a Tesouro Direto asset type (i.e., CRI, CRA, Debênture, LF, LIG, CDB, LCI, LCA with coupon).

**Anchor**: `couponDayOfMonth` (required; UI must enforce this).

**Payment timing**:
- The coupon cash settles on day `couponDayOfMonth` of the month *following* the end of the accrual period.
- The accrual period for coupon N is: `[startOfPeriodN, endOfPeriodN]` where periods are aligned to calendar months (for MONTHLY) or to multiples of months (QUARTERLY = 3, SEMI_ANNUAL = 6, ANNUAL = 12).

**First accrual period**:
- Starts on `issueDate`.
- Ends on the last day of the calendar month containing `issueDate` (for MONTHLY) or the last day of the Nth month after issue for longer frequencies.
- First payment date = day `couponDayOfMonth` of the month immediately following the first accrual period end.

**Partial first period**:
- If `issueDate` is not the 1st of the month, the first accrual period is shorter than a full period ("dirty price" entry — the user acquired the bond mid-cycle and receives partial interest).
- This partial payment is still shown in the schedule, flagged as `isPartialPeriod = true` in `CouponScheduleEntry`. The UI renders it with a note ("período parcial").

**Example** — Monthly CRA, `couponDayOfMonth = 15`, issue date 2026-01-25:
- First accrual period: 2026-01-25 → 2026-01-31 (partial, 6 days)
- First payment: **2026-02-15** (partial interest for 6 days)
- Second accrual: 2026-02-01 → 2026-02-28 (full month)
- Second payment: **2026-03-15**
- …and so on monthly.

**Example** — Semi-annual CRI, `couponDayOfMonth = 15`, issue date 2026-01-10:
- First accrual period: 2026-01-10 → 2026-06-30 (partial first period)
- First payment: **2026-07-15**
- Second accrual: 2026-07-01 → 2026-12-31
- Second payment: **2027-01-15**

---

#### BR-P2-4: Coupon Series Number

Each `CouponScheduleEntry` carries a `seriesNumber: Int` (1-based position in the schedule). This is purely derived — the Nth entry in the generated list is series N. It is useful for display ("Cupom 3 de 12") and for import matching.

Add to `CouponScheduleEntry`:

```kotlin
data class CouponScheduleEntry(
    val seriesNumber: Int,           // 1 = first coupon after issueDate
    val scheduledDate: LocalDate,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val isPartialPeriod: Boolean,    // true when periodStart != first day of a full cycle
    val isPast: Boolean,
    val matchedTransaction: InvestmentTransaction? = null,
)
```

Add to GraphQL:

```graphql
type CouponScheduleEntry {
  "1-based position in the full coupon series."
  seriesNumber: Int!
  scheduledDate: Date!
  periodStart: Date!
  "Whether the first period is shorter than a full cycle (dirty-price entry)."
  isPartialPeriod: Boolean!
  isPast: Boolean!
  matchedTransaction: InvestmentTransaction
}
```

---

#### BR-P2-5: `couponDayOfMonth` validation

- For Tesouro Direto (NTN-B, NTN-F): field must be null on save. Backend rejects non-null values with a 400.
- For all other non-AT_MATURITY instruments: field is required. Backend rejects null with a 400.
- Frontend must show a numeric input (1–28) whenever `couponFrequency != AT_MATURITY` and the asset type is not a Tesouro Direto type.

---

### Frontend Changes (Phase 2)

#### `RecordFixedIncomeDrawer` — `couponDayOfMonth` field

Add a `NumberField` for `couponDayOfMonth` immediately after the `couponFrequency` field:

- Visible only when `couponFrequency != AT_MATURITY && !isTesouroDireto(assetType)`.
- Label: `"Dia de pagamento do cupom"` / `"Coupon payment day"`.
- Zod: `z.number().int().min(1).max(28)`.
- Default: `15`.
- Helper text: `"Dia do mês em que o cupom cai na conta (máx. 28)"`.

#### `CouponScheduleTimeline` — Phase 2 display additions

- Show `"Cupom {seriesNumber}"` as the row title.
- Add a `"período parcial"` badge on rows where `isPartialPeriod == true`.
- For Tesouro Direto rows: show the window label (e.g., `"Janela Ago/15"`) as a subtitle.

---

### Testing Strategy (Phase 2)

#### Unit Tests

**`CouponScheduleService` — NTN-B cases (`BR-P2-1`)**:
- Even maturity year, issue date before first window → first payment on Feb 15
- Even maturity year, issue date between windows (after Aug 15) → first payment on Feb 15 of next year
- Odd maturity year, issue date after Nov 15 → first payment on May 15 of next year
- Issue date equals a window date → first payment is the *next* window, not the same day
- Full schedule from issue to maturity, last entry excluded (maturity date itself)

**`CouponScheduleService` — NTN-F cases (`BR-P2-2`)**:
- Issue date Jan 2 → first payment Jul 1 same year
- Issue date Jul 2 → first payment Jan 1 next year

**`CouponScheduleService` — Private credit monthly (`BR-P2-3`)**:
- Issue on 1st of month → first accrual is full period, `isPartialPeriod = false`
- Issue on 25th of month → first accrual is partial (6 days), `isPartialPeriod = true`
- `couponDayOfMonth = 28`, February → payment on Feb 28 (no overflow to Mar 1)
- Schedule terminates at `maturityDate`

**`CouponScheduleService` — Series numbers (`BR-P2-4`)**:
- First entry always has `seriesNumber = 1`
- Series numbers are contiguous and gapless

**`CouponScheduleService` — Validation (`BR-P2-5`)**:
- NTN-B holding with `couponDayOfMonth` set → throws `IllegalArgumentException`
- Non-Tesouro holding with `couponFrequency != AT_MATURITY` and `couponDayOfMonth = null` → throws

#### Integration Tests

**`RecordFixedIncomeIntegrationTest` (Phase 2)**:
- Persist `couponDayOfMonth` on a new holding; read it back via GraphQL and verify round-trip.
- Attempt to save a Tesouro IPCA+ holding with `couponDayOfMonth` set → verify 400 response.
- Attempt to save a CRI with `couponFrequency = MONTHLY` and `couponDayOfMonth = null` → verify 400 response.

**`CouponScheduleResolverIT`**:
- NTN-B holding with known `issueDate` and `maturityDate` → verify the schedule dates match the expected government windows.
- Private monthly CRI holding → verify `isPartialPeriod` flag on first entry and correct `scheduledDate` values.
- Holding with `couponFrequency = AT_MATURITY` → verify `couponSchedule` is empty.

#### E2E Tests

> E2E tests use the running frontend + seeded DB. Framework to be confirmed (Playwright assumed).

**Scenario 1 — Add a monthly CRI with coupon day**:
1. Navigate to Investments → Record Fixed Income.
2. Set `couponFrequency = MONTHLY`.
3. Verify `couponDayOfMonth` input appears.
4. Enter day `15`, complete remaining fields, submit.
5. Open holding detail → verify coupon schedule section visible with `seriesNumber` and correct dates.
6. Verify first entry has `"período parcial"` badge if issue date is not the 1st of the month.

**Scenario 2 — Add a Tesouro IPCA+ 2028 (even year)**:
1. Record a Tesouro IPCA+ holding, set `couponFrequency = SEMI_ANNUAL`, `maturityDate = 2028-08-15`.
2. Verify `couponDayOfMonth` field is hidden.
3. Issue date: `2026-01-20`. Submit.
4. Open holding detail → verify first scheduled date is `2026-02-15` and second is `2026-08-15`.

**Scenario 3 — Register a coupon payment from the pending action**:
1. With a CRI seeded with a past `scheduledDate`, open Pending Actions panel.
2. Click "Registrar cupom" — verify `CouponPaymentConfirmDrawer` opens.
3. Verify estimated amounts are pre-filled.
4. Submit — verify the pending action disappears and a new `INTEREST_PAYMENT` transaction exists.

---

### Open Questions (Phase 2)

- [ ] **NTN-B partial period**: when a user buys NTN-B after the Feb/Aug window, they pay the seller accrued interest (dirty price). The first coupon they *receive* covers the full Feb→Aug (or Aug→Feb) period, but they only earned a fraction. Should the schedule show `isPartialPeriod = true` for this first entry, or is the dirty price adjustment invisible (since the full coupon amount is received regardless)?  
  **Tentative**: mark as partial for informational purposes; the user receives the full coupon but paid accrued interest at purchase, so net economics are correct. Do not adjust projected amounts — always show the full coupon amount.

- [ ] **`couponDayOfMonth` for QUARTERLY / SEMI_ANNUAL / ANNUAL private credit**: confirm that the "subsequent month" rule applies to all frequencies (not just MONTHLY), i.e., payment always falls in the month after the accrual period ends, on `couponDayOfMonth`.

- [ ] Should the E2E suite live in `web/e2e/` (Playwright) or as a separate package? Confirm the test runner setup before implementing Scenario 3.

---

## Testing Strategy

### Unit Tests

- `CouponScheduleServiceTest`: schedule generation for each frequency, edge cases (maturityDate null, AT_MATURITY, single period, past-only schedule, future-only schedule).
- `CouponScheduleServiceTest.findMatchingEntry`: exact match, +6 days match, −6 days match, 8-day miss, no transactions.
- `FixedIncomeValuationServiceTest`: accrual from last coupon transaction date, fallback to issueDate when no transactions, AT_MATURITY unchanged.

### Integration Tests

- `RecordFixedIncomeIntegrationTest`: persist and retrieve `couponFrequency`, verify null default for legacy records.
- `CouponScheduleResolverTest`: GraphQL query returns correct schedule shape.

### Frontend Tests

- `CouponScheduleTimeline`: renders paid/expected/overdue statuses; collapses past entries; shows `~` prefix on amounts.
- `RecordFixedIncomeDrawer`: `couponFrequency` field present, defaults to `AT_MATURITY`, appears in mutation payload.

---

## Open Questions

- [ ] Should estimated coupon amounts be displayed at all when `couponFrequency != AT_MATURITY` and the indexer is post-fixed (CDI/IPCA)? Risk: displaying `~R$ 0,00` for the first period is confusing. **Current decision**: display `—` when the estimated amount is too uncertain (< 30 days into a period or indexer is post-fixed); display `~R$ X.XX` only for PRE_FIXADO where each coupon is computable.
- [ ] What is the matching window for import? ±7 calendar days chosen here — confirm with the team when the import feature is being designed.
- [ ] `couponSchedule` on the list query vs detail only: for performance, only compute schedule on the detail query (not the list). Confirm this is acceptable.

---

## References

- Feature spec: `03-features/investment-portfolio/fixed-income-valuation.md`
- Valuation backlog: `03-features/investment-portfolio/fixed-income-valuation-backlog.md`
- Domain patterns: `05-backend/patterns/domain-model-pattern.md`
- Form pattern: `07-frontend/patterns/form-pattern.md`
- GraphQL standards: `06-contracts/graphql-standards.md`
- Entity: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/investment/HoldingEntity.kt`
- Domain model: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/investment/Holding.kt`
- Enums: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/investment/InvestmentEnums.kt`

---

## Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.1.0 | 2026-04-16 | — | Phase 2 spec: Tesouro Direto fixed-calendar rules, private credit subsequent-month rule, `couponDayOfMonth` field, series number, partial periods, full test strategy |
| 1.0.0 | 2026-04-15 | — | Initial draft |
