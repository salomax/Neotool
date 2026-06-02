---
title: Fixed Income Coupon Payments — Implementation Backlog
type: backlog
category: investment-portfolio
status: draft
version: 1.0.0
tags: [investment, fixed-income, coupon, juros, pagamento, backlog]
ai_optimized: true
search_keywords: [coupon frequency backlog, pagamento cupom renda fixa backlog, interest payment schedule implementation]
related:
  - 03-features/investment-portfolio/fixed-income-coupon-payments.md
  - 03-features/investment-portfolio/fixed-income-valuation-backlog.md
  - 05-backend/patterns/domain-model-pattern.md
  - 07-frontend/patterns/form-pattern.md
last_updated: 2026-04-15
---

# Fixed Income Coupon Payments — Implementation Backlog

> **Purpose**: Implement coupon payment frequency tracking for fixed income holdings — a single new field on the holding, a derived schedule service, valuation accrual fix, and a schedule timeline in the UI.
>
> **Read first**: `docs/03-features/investment-portfolio/fixed-income-coupon-payments.md` — this backlog references the spec throughout.

---

## Context for the Implementing Agent

Before starting, read these files to understand the existing codebase:

| File | Why |
|---|---|
| `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/investment/InvestmentEnums.kt` | Where to add `CouponFrequency` enum |
| `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/investment/Holding.kt` | Domain model — add `couponFrequency` field |
| `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/investment/HoldingEntity.kt` | JPA entity — add `coupon_frequency` column |
| `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/investment/FixedIncomeValuationService.kt` | Valuation engine — modify accrual start date logic |
| `contracts/graphql/subgraphs/banking/schema.graphqls` | GraphQL schema — add enum, field, type |
| `web/src/app/personal-finance/investments/components/RecordFixedIncomeDrawer.tsx` | Frontend form — add `couponFrequency` field |
| `web/src/app/personal-finance/investments/components/fixed-income/FixedIncomeHoldingDetailContent.tsx` | Detail view — add coupon schedule section |
| `web/src/app/personal-finance/investments/components/fixed-income/api/queries.ts` | GraphQL queries — extend holding fragment |
| `service/kotlin/banking/src/main/resources/db/migration/` | Flyway migrations — find the highest `V0_XX` number for next migration |

---

## Epic Hierarchy

```
Feature: Fixed Income Coupon Payments
├── Epic 1: Domain Model & Persistence
│   ├── 1.1 CouponFrequency enum
│   ├── 1.2 Holding domain model field
│   ├── 1.3 HoldingEntity JPA field
│   └── 1.4 Database migration
├── Epic 2: Coupon Schedule Service
│   ├── 2.1 CouponScheduleEntry value object
│   ├── 2.2 CouponScheduleService implementation
│   └── 2.3 Unit tests
├── Epic 3: Valuation Engine Accrual Fix
│   ├── 3.1 Modify accrual start date logic
│   └── 3.2 Unit tests
├── Epic 4: GraphQL Contract
│   ├── 4.1 Schema changes (enum, fields, type)
│   ├── 4.2 DTO changes
│   ├── 4.3 Resolver integration
│   ├── 4.4 Mutation input changes
│   └── 4.5 Schema sync & codegen
└── Epic 5: Frontend
    ├── 5.1 RecordFixedIncomeDrawer — couponFrequency field
    ├── 5.2 CouponScheduleTimeline component
    ├── 5.3 FixedIncomeHoldingDetailContent — schedule section
    ├── 5.4 GraphQL query update
    └── 5.5 i18n keys
```

---

## Epic 1: Domain Model & Persistence

**Goal**: Persist `couponFrequency` on holdings. Null is backward-compatible with existing data (treated as `AT_MATURITY`).

---

### Story 1.1 — `CouponFrequency` Enum

**Tasks**:

- [ ] **T1.1.1** Add `CouponFrequency` enum to `InvestmentEnums.kt` (after the `Indexer` enum):
  ```kotlin
  enum class CouponFrequency {
      MONTHLY,
      QUARTERLY,
      SEMI_ANNUAL,
      ANNUAL,
      AT_MATURITY,  // default — bullet bond, no interim payments
  }
  ```

---

### Story 1.2 — `Holding` Domain Model Field

**Tasks**:

- [ ] **T1.2.1** Add field to `Holding.kt` data class, after `isTaxExempt`:
  ```kotlin
  val couponFrequency: CouponFrequency? = null,
  ```

---

### Story 1.3 — `HoldingEntity` JPA Field

**Tasks**:

- [ ] **T1.3.1** Add field to `HoldingEntity.kt`, after `isTaxExempt`:
  ```kotlin
  @Enumerated(EnumType.STRING)
  @Column(name = "coupon_frequency", length = 15)
  open var couponFrequency: CouponFrequency? = null
  ```

- [ ] **T1.3.2** Update the entity-to-domain mapper (find the mapper for `HoldingEntity → Holding`) to map `couponFrequency`.

- [ ] **T1.3.3** Update the domain-to-entity mapper (`Holding → HoldingEntity`) to map `couponFrequency`.

- [ ] **T1.3.4** Update `RecordFixedIncomeInput` command / holding creation command to accept `couponFrequency: CouponFrequency? = null`.

- [ ] **T1.3.5** Update `UpdateFixedIncomeInput` command / holding update command to accept `couponFrequency: CouponFrequency? = null`.

---

### Story 1.4 — Database Migration

**Tasks**:

- [ ] **T1.4.1** Identify the current highest migration number in `service/kotlin/banking/src/main/resources/db/migration/`. Create `V0_21__investment_holding_coupon_frequency.sql` (adjust version number if a higher one already exists):
  ```sql
  ALTER TABLE banking.investment_holdings
      ADD COLUMN coupon_frequency VARCHAR(15) NULL;

  COMMENT ON COLUMN banking.investment_holdings.coupon_frequency
      IS 'Coupon payment frequency. NULL is equivalent to AT_MATURITY (bullet bond).';
  ```
  No backfill needed — `null` maps to `AT_MATURITY` in domain logic.

---

## Epic 2: Coupon Schedule Service

**Goal**: Pure domain service that generates projected coupon entries from a holding's contractual terms. No I/O — receives pre-fetched transactions as parameter.

---

### Story 2.1 — `CouponScheduleEntry` Value Object

**Tasks**:

- [ ] **T2.1.1** Create `CouponScheduleEntry.kt` in `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/investment/`:
  ```kotlin
  data class CouponScheduleEntry(
      val scheduledDate: LocalDate,
      val periodStart: LocalDate,
      val periodEnd: LocalDate,   // = scheduledDate
      val isPast: Boolean,
      val matchedTransaction: InvestmentTransaction? = null,
  )
  ```

---

### Story 2.2 — `CouponScheduleService` Implementation

**Tasks**:

- [ ] **T2.2.1** Create `CouponScheduleService.kt` in `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/investment/`.

- [ ] **T2.2.2** Implement `generateSchedule(holding, transactions, today)`:
  - Return empty list if `holding.couponFrequency == null || holding.couponFrequency == AT_MATURITY`.
  - Return empty list if `holding.maturityDate == null` or `holding.issueDate == null`.
  - Generate dates backwards from `maturityDate` using `LocalDate.minusMonths(n)` where `n` = frequency in months:
    - `MONTHLY` → 1 month
    - `QUARTERLY` → 3 months
    - `SEMI_ANNUAL` → 6 months
    - `ANNUAL` → 12 months
  - Stop adding entries when the date would be ≤ `issueDate`.
  - Reverse the list to ascending date order.
  - For each entry, determine `periodStart` = previous entry's `scheduledDate` (or `issueDate` for the first entry).
  - For each entry, compute `isPast = scheduledDate < today`.
  - For each past entry, find a matching `INTEREST_PAYMENT` transaction within ±7 calendar days of `scheduledDate` (matching window defined in spec BR-3). Set `matchedTransaction` if found.
  - Matching logic: `transactions.filter { it.type == INTEREST_PAYMENT }.firstOrNull { abs(ChronoUnit.DAYS.between(it.tradeDate, entry.scheduledDate)) <= 7 }`

- [ ] **T2.2.3** Implement `findMatchingEntry(holding, transactions, tradeDate)`:
  - Generate the full schedule.
  - Return the first entry where `abs(ChronoUnit.DAYS.between(tradeDate, entry.scheduledDate)) <= 7`.
  - Return null if no match.

---

### Story 2.3 — Unit Tests for `CouponScheduleService`

**Tasks**:

- [ ] **T2.3.1** Test `generateSchedule` returns empty list for `AT_MATURITY`.
- [ ] **T2.3.2** Test `generateSchedule` returns empty list when `maturityDate` is null.
- [ ] **T2.3.3** Test `MONTHLY` frequency produces correct dates (12 entries for a 1-year bond).
- [ ] **T2.3.4** Test `SEMI_ANNUAL` frequency produces correct dates (2 entries for a 1-year bond, 4 for 2 years).
- [ ] **T2.3.5** Test `periodStart` of the first entry equals `issueDate`.
- [ ] **T2.3.6** Test `isPast` classification: past entries have `isPast = true`, future entries `false`.
- [ ] **T2.3.7** Test transaction matching: entry at day 0 matches transaction at day +6, day -6; does not match at day +8.
- [ ] **T2.3.8** Test `findMatchingEntry`: exact hit, near hit (+6 days), miss (+8 days), no transactions.
- [ ] **T2.3.9** Test that when `issueDate` and `maturityDate` are the same day (degenerate holding), no entries are generated.
- [ ] **T2.3.10** Test that a bond shorter than one coupon period (e.g. 2-month bond with SEMI_ANNUAL frequency) generates no entries (first generated date would be ≤ issueDate).

---

## Epic 3: Valuation Engine Accrual Fix

**Goal**: For coupon-paying holdings, accrue from the last actual coupon payment date rather than the issue date.

---

### Story 3.1 — Modify Accrual Start Date in `FixedIncomeValuationService`

**Tasks**:

- [ ] **T3.1.1** Read `FixedIncomeValuationService.kt` in full before modifying.

- [ ] **T3.1.2** In the valuation service, add a parameter `transactions: List<InvestmentTransaction> = emptyList()` to the estimate function (or the relevant internal method that computes accrual start).

- [ ] **T3.1.3** Add helper `resolveAccrualStart(holding: Holding, transactions: List<InvestmentTransaction>): LocalDate`:
  ```
  if holding.couponFrequency == null || AT_MATURITY → return holding.issueDate
  else → find most recent INTEREST_PAYMENT transaction with tradeDate <= today
         if found → return transaction.tradeDate
         else → return holding.issueDate
  ```

- [ ] **T3.1.4** Replace the hardcoded `issueDate` reference in the accrual formula with `resolveAccrualStart(holding, transactions)`.

- [ ] **T3.1.5** In the resolver that calls the valuation service (`InvestmentResolver` or equivalent), pass the holding's transactions to the valuation call. Transactions should already be fetched for the schedule; batch-fetch once per request to avoid N+1.

---

### Story 3.2 — Unit Tests for Accrual Fix

**Tasks**:

- [ ] **T3.2.1** Test: holding with `AT_MATURITY` → accrual start unchanged from `issueDate` (regression test).
- [ ] **T3.2.2** Test: holding with `SEMI_ANNUAL` and no `INTEREST_PAYMENT` transactions → accrual start = `issueDate`.
- [ ] **T3.2.3** Test: holding with `SEMI_ANNUAL` and one `INTEREST_PAYMENT` transaction 6 months ago → accrual start = transaction's `tradeDate`, gross value reflects only 6 months of accrual.
- [ ] **T3.2.4** Test: holding with future-dated only transactions (shouldn't happen but guard against it) → accrual start = `issueDate`.

---

## Epic 4: GraphQL Contract

**Goal**: Expose `couponFrequency` on the holding and `couponSchedule` as a derived field on the detail query. Update mutation inputs.

---

### Story 4.1 — Schema Changes

**Tasks**:

- [ ] **T4.1.1** Add `CouponFrequency` enum to `contracts/graphql/subgraphs/banking/schema.graphqls`:
  ```graphql
  enum CouponFrequency {
    MONTHLY
    QUARTERLY
    SEMI_ANNUAL
    ANNUAL
    AT_MATURITY
  }
  ```

- [ ] **T4.1.2** Add `CouponScheduleEntry` type:
  ```graphql
  type CouponScheduleEntry {
    scheduledDate: Date!
    periodStart: Date!
    isPast: Boolean!
    matchedTransaction: InvestmentTransaction
  }
  ```

- [ ] **T4.1.3** Add fields to `InvestmentHolding`:
  ```graphql
  couponFrequency: CouponFrequency
  couponSchedule: [CouponScheduleEntry!]!
  ```

- [ ] **T4.1.4** Add `couponFrequency: CouponFrequency` to `RecordFixedIncomeInput` (or the relevant mutation input type for creating a fixed income holding).

- [ ] **T4.1.5** Add `couponFrequency: CouponFrequency` to `UpdateFixedIncomeInput` (or the relevant mutation input type for editing a fixed income holding).

---

### Story 4.2 — DTO Changes

**Tasks**:

- [ ] **T4.2.1** Find the DTO class that maps to `InvestmentHolding` (likely `InvestmentHoldingDTO` in `InvestmentDTO.kt`). Add `couponFrequency: CouponFrequency?` and `couponSchedule: List<CouponScheduleEntryDTO>`.

- [ ] **T4.2.2** Create `CouponScheduleEntryDTO` (likely in the same DTO file):
  ```kotlin
  data class CouponScheduleEntryDTO(
      val scheduledDate: LocalDate,
      val periodStart: LocalDate,
      val isPast: Boolean,
      val matchedTransaction: InvestmentTransactionDTO?,
  )
  ```

---

### Story 4.3 — Resolver Integration

**Tasks**:

- [ ] **T4.3.1** Find the resolver method for `InvestmentHolding` queries (likely in `InvestmentResolver.kt`).

- [ ] **T4.3.2** Inject `CouponScheduleService` into the resolver.

- [ ] **T4.3.3** For the `couponSchedule` field: in the holding resolution, call `couponScheduleService.generateSchedule(holding, transactions, today)` and map the result to `List<CouponScheduleEntryDTO>`.
  - **Important**: only compute `couponSchedule` in the detail query context (where `transactions` are already fetched). In the list query, return an empty list for `couponSchedule` to avoid N+1 queries.
  - One way to achieve this: add a resolver hint or data fetching environment check; alternatively, set `couponSchedule` to empty list in the list mapper and only populate it in the detail resolver.

- [ ] **T4.3.4** Map `couponFrequency` from `Holding → HoldingDTO` in the mapper.

---

### Story 4.4 — Mutation Handler Changes

**Tasks**:

- [ ] **T4.4.1** In the mutation handler for recording a fixed income holding (find via the mutation resolver), extract `couponFrequency` from the input and pass it to the holding creation command.

- [ ] **T4.4.2** In the mutation handler for updating a fixed income holding, extract `couponFrequency` from the update input and update the holding entity.

---

### Story 4.5 — Schema Sync & Codegen

**Tasks**:

- [ ] **T4.5.1** Run `./neotool graphql sync` to regenerate the supergraph schema after adding the new types and fields.
- [ ] **T4.5.2** Run `./neotool graphql generate --all` to regenerate typed clients.
- [ ] **T4.5.3** Run `cd web && pnpm run codegen` to regenerate frontend TypeScript types.
- [ ] **T4.5.4** Fix any TypeScript type errors that arise from the new generated types.

---

## Epic 5: Frontend

**Goal**: Add `couponFrequency` to the record/edit form and show the projected schedule in the holding detail view.

> **Before writing any frontend code, read**:
> - `docs/07-frontend/patterns/form-pattern.md` — form conventions (react-hook-form, zod, FormProvider, FormStack, SelectField, size="medium")
> - `docs/07-frontend/patterns/graphql-query-pattern.md` — Apollo query conventions

---

### Story 5.1 — `RecordFixedIncomeDrawer`: Add `couponFrequency` Field

```
File: web/src/app/personal-finance/investments/components/RecordFixedIncomeDrawer.tsx
```

**Tasks**:

- [ ] **T5.1.1** Read `RecordFixedIncomeDrawer.tsx` in full before editing.

- [ ] **T5.1.2** Add `couponFrequency` to the Zod schema:
  ```typescript
  couponFrequency: z.nativeEnum(CouponFrequency).default('AT_MATURITY'),
  ```

- [ ] **T5.1.3** Add a `<SelectField>` for `couponFrequency` after the `maturityDate` field, using `size="medium"` per form pattern:
  ```tsx
  <SelectField
    name="couponFrequency"
    label={t("investments.form.couponFrequency")}
    options={couponFrequencyOptions}
    size="medium"
  />
  ```
  Options order: `AT_MATURITY` (first/default), `MONTHLY`, `QUARTERLY`, `SEMI_ANNUAL`, `ANNUAL`.

- [ ] **T5.1.4** Define `couponFrequencyOptions` — either inline or in a shared constants file alongside other investment option arrays.

- [ ] **T5.1.5** Ensure `couponFrequency` is included in the mutation payload passed to the create/update GraphQL mutation.

- [ ] **T5.1.6** Ensure `couponFrequency` is pre-filled when the drawer opens in edit mode (map existing holding data into the form default values).

---

### Story 5.2 — `CouponScheduleTimeline` Component

```
File: web/src/app/personal-finance/investments/components/fixed-income/CouponScheduleTimeline.tsx
```

**Tasks**:

- [ ] **T5.2.1** Create `CouponScheduleTimeline.tsx`. Props:
  ```typescript
  interface CouponScheduleTimelineProps {
    entries: CouponScheduleEntry[]
  }
  ```

- [ ] **T5.2.2** Implement the collapsible layout:
  - Past entries (where `isPast = true`) are collapsed by default under a "ver histórico (N)" toggle.
  - Upcoming entries: show up to 12, with a "ver todos" toggle for the rest.

- [ ] **T5.2.3** For each entry render:
  - **Date**: `scheduledDate` formatted as `dd/MM/yyyy`.
  - **Status**:
    - `matchedTransaction != null` → green check icon + "Pago" label.
    - `isPast && matchedTransaction == null` → amber warning icon + "Não registrado" label.
    - `!isPast` → grey clock icon + "Previsto" label.
  - **Amount**: `—` (per spec open question: show amount as `—` for all entries in V1 to avoid confusion with projections).

- [ ] **T5.2.4** When `matchedTransaction` exists, make the status row clickable to navigate to the transaction detail (or highlight it in the transaction list if no separate detail exists).

---

### Story 5.3 — `FixedIncomeHoldingDetailContent`: Add Schedule Section

```
File: web/src/app/personal-finance/investments/components/fixed-income/FixedIncomeHoldingDetailContent.tsx
```

**Tasks**:

- [ ] **T5.3.1** Read the file before editing.

- [ ] **T5.3.2** Below the performance chart section, add a conditional section that renders `<CouponScheduleTimeline entries={holding.couponSchedule} />` only when `holding.couponSchedule.length > 0`.

- [ ] **T5.3.3** Add a section title: "Pagamento de Juros (Cupom)".

---

### Story 5.4 — GraphQL Query Update

```
File: web/src/app/personal-finance/investments/components/fixed-income/api/queries.ts
```

**Tasks**:

- [ ] **T5.4.1** Add `couponFrequency` to the holding fragment used in list and detail queries.

- [ ] **T5.4.2** Add `couponSchedule` sub-query to `FIXED_INCOME_HOLDING_DETAIL` only (not the list query):
  ```graphql
  couponSchedule {
    scheduledDate
    periodStart
    isPast
    matchedTransaction {
      id
      tradeDate
      grossValue
      netValue
      incomeTax
    }
  }
  ```

---

### Story 5.5 — i18n Keys

**Tasks**:

- [ ] **T5.5.1** Add Portuguese keys to `web/src/shared/i18n/domains/investments/locales/pt.json` (locate the correct i18n file by searching for existing investment-related keys):
  ```json
  "couponFrequency": "Pagamento de Juros",
  "couponFrequencyOptions": {
    "AT_MATURITY": "No vencimento (Bullet)",
    "MONTHLY": "Mensal",
    "QUARTERLY": "Trimestral",
    "SEMI_ANNUAL": "Semestral",
    "ANNUAL": "Anual"
  },
  "couponSchedule": {
    "sectionTitle": "Pagamento de Juros (Cupom)",
    "statusPaid": "Pago",
    "statusExpected": "Previsto",
    "statusOverdue": "Não registrado",
    "showHistory": "Ver histórico ({{count}})",
    "showAll": "Ver todos"
  }
  ```

- [ ] **T5.5.2** Add English keys to the equivalent `en.json` file.

---

## Dependency Order

```
T1.1.1 (enum)
  └─► T1.2.1 (Holding field) + T1.3.1 (Entity field)
        └─► T1.3.2/T1.3.3 (mappers)
              └─► T1.3.4/T1.3.5 (commands)
                    └─► T1.4.1 (migration)

T2.1.1 (CouponScheduleEntry)
  └─► T2.2.1–T2.2.3 (CouponScheduleService)
        └─► T2.3.x (unit tests)

T3.1.1–T3.1.5 (valuation fix) ← depends on T1.2.1 and T2.2.1
  └─► T3.2.x (unit tests)

T4.1.x (schema) ← depends on T1.1.1
  └─► T4.2.x (DTOs)
        └─► T4.3.x (resolver) ← depends on T2.2.1
              └─► T4.4.x (mutation handlers)
                    └─► T4.5.x (sync + codegen)

T5.4.1/T5.4.2 (query update) ← depends on T4.5.3 (codegen)
  └─► T5.1.x (drawer form) ← depends on T4.5.3
  └─► T5.2.x (timeline component) ← depends on T4.5.3
        └─► T5.3.x (detail content)
              └─► T5.5.x (i18n)
```

Parallel tracks:
- Epic 2 (schedule service) and Epic 3 (valuation fix) can be developed in parallel after Epic 1 is done.
- Epic 4 can start in parallel with Epic 2/3 once Epic 1 is done.
- Epic 5 can start once Epic 4 codegen is complete.

---

## Validation Commands

```bash
# Backend
cd invistus/service/kotlin && ./gradlew :banking:test

# GraphQL schema sync
./neotool graphql sync
./neotool graphql generate --all

# Frontend codegen + typecheck + lint
cd invistus/web && pnpm run codegen
cd invistus/web && pnpm run typecheck
cd invistus/web && pnpm run lint
cd invistus/web && pnpm run test:unit
```

---

## Definition of Done

- [ ] `CouponFrequency` enum exists in `InvestmentEnums.kt`.
- [ ] `couponFrequency` field on `Holding` domain object, `HoldingEntity`, and migration.
- [ ] Null `couponFrequency` (legacy data) behaves identically to `AT_MATURITY` — no regression.
- [ ] `CouponScheduleService.generateSchedule` produces correct entries for all frequencies.
- [ ] `CouponScheduleService.findMatchingEntry` matches within ±7 days.
- [ ] Valuation accrual uses last `INTEREST_PAYMENT` date when `couponFrequency != AT_MATURITY`.
- [ ] Valuation accrual falls back to `issueDate` when no transactions exist (no regression).
- [ ] `couponFrequency` and `couponSchedule` exposed on `InvestmentHolding` GraphQL type.
- [ ] `couponFrequency` accepted on create/update mutation inputs.
- [ ] `RecordFixedIncomeDrawer` shows `couponFrequency` field defaulting to `AT_MATURITY`.
- [ ] Detail view shows coupon schedule section when entries exist, hidden when empty.
- [ ] Timeline shows paid/expected/overdue status per entry.
- [ ] Past entries collapsed by default; upcoming capped at 12 with expand.
- [ ] All backend unit tests pass.
- [ ] TypeScript compiles with no errors after codegen.
- [ ] i18n keys present for `pt` and `en`.

---

## Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-04-15 | — | Initial backlog |
