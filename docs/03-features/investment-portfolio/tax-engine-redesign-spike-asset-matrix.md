---
title: Tax Calculation Engine — Spike: Asset & Tax Regime Matrix
type: spike
category: investment-portfolio
status: draft
version: 1.0.0
tags: [spike, tax-engine, investment, portfolio, design]
last_updated: 2026-07-29
---

# Tax Calculation Engine — Spike: Asset & Tax Regime Matrix

> Input for the design of the new tax calculation engine (GitHub Project
> https://github.com/orgs/invistus/projects/40). Builds on
> `tax-engine-redesign-handoff.md` (origin of the redesign decision). This document maps
> **every asset type the product models today** against **every tax regime that applies (or
> should apply) to it**, so the engine's abstractions are chosen against the full problem, not
> just the come-cotas bug that triggered the investigation.

---

## 1. Main goals (recap, unchanged from the handoff)

1. **Extensibility is the strategic goal.** Adding a new tax regime (new asset class, new rule)
   must be an *extension* (new `TaxRegime` implementation), never a modification of the core
   engine. We are **not** implementing regimes for asset classes with zero product support today
   (crypto, options, COE) — but the design must not make adding them expensive later.
2. **Strategy pattern** for tax regime: a common interface resolved per asset/holding by a
   registry/factory, replacing the `when` blocks scattered across
   `FundValuationService`, `ComeCotasSupport`, `FixedIncomeValuationService`.
3. **Pure core function**: `compute(lots, regime, referenceDate, navProvider) -> result`, no
   mutable state equivalent to today's `lot.withholdingPaid`. This is what makes the engine
   idempotent and replay-safe, structurally rather than by convention.
4. **Avoid over-engineering.** The composition (gain → IOF → taxable base → IR → net) is a
   simple pipeline. Strategy + pure orchestration is enough; no Chain of Responsibility or
   similar just for its own sake.

This spike exists to answer the question the handoff left open: **what must the `TaxRegime`
interface and the pure engine's inputs/outputs actually support**, given everything the product
already does (and doesn't do) today?

---

## 2. Asset type inventory (source of truth: `InvestmentEnums.kt`)

`AssetType` (banking domain, aligned to Open Finance Phase 4 / Pluggy / Belvo):

| Group | Asset types |
|---|---|
| Renda variável (equities/B3) | `STOCK`, `STOCK_RIGHTS`, `BDR`, `REAL_ESTATE_FUND` (FII), `AGRIBUSINESS_FUND` (FIAGRO), `ETF`, `ETF_FIXED_INCOME`, `ETF_INTERNATIONAL` |
| Renda fixa bancária | `CDB`, `RDB`, `LCI`, `LCA`, `LC`, `LF`, `LIG` |
| Renda fixa de crédito | `DEBENTURES`, `CRI`, `CRA` |
| Tesouro Direto | `TREASURY` |
| Fundos | `MUTUAL_FUND` |
| Previdência | `PGBL`, `VGBL` |
| Estruturado | `COE` |
| Outros | `CRYPTO`, `OPTION`, `DERIVATIVES`, `OTHER` |

`InvestmentHoldingAssetClass` groups these into `FIXED_INCOME`, `INVESTMENT_FUND`,
`VARIABLE_INCOME`, `CRYPTO`, `OPTIONS` for reporting/filtering — a coarser grouping than tax
regime actually requires (e.g. `MUTUAL_FUND` alone has 4 distinct tax profiles today).

---

## 3. Asset × tax-regime matrix

Status legend: 🟢 implemented and tested · 🟡 implemented but inconsistent/incomplete · 🔴 not implemented at all.

| Asset type(s) | Status | Regime today | Rate schedule | Day-count basis | Periodic withholding? | Exemption mechanism | Engine output expected |
|---|---|---|---|---|---|---|---|
| `CDB`, `RDB`, `LC`, `LF`, `LIG`, `DEBENTURES`, `CRI`, `CRA`, `TREASURY` | 🟢 | Brazilian regressive fixed-income table (`BrazilianFixedIncomeTaxRules`) | IR 22.5/20/17.5/15% by elapsed calendar days (≤180/181-360/361-720/>720); IOF 30-day regressive table (96%→0%), zero after day 30 | Calendar days, `accrualStart` (issue date or last coupon payment) → `min(maturity, asOfDate)` | No | Boolean `holding.isTaxExempt`, set at holding creation (meant for LCI/LCA/CRI/CRA and debênture incentivada) — **not derived from `AssetType` by the engine itself** | gross gain, IOF amount, taxable base (gain − IOF), IR amount, net value — all 🟢 today |
| `LCI`, `LCA` | 🟢 (same engine, exempt path) | Same as above, `isTaxExempt=true` short-circuits IR to 0 | n/a | n/a | No | Same boolean flag | Same output, IR forced to 0 |
| `MUTUAL_FUND` — long-term profile | 🟢 | `COME_COTAS_LONG`: full 4-bracket regressive table (reuses `BrazilianFixedIncomeTaxRules`) + periodic come-cotas withholding | 15% flat once fully aged (>720d bracket), or regressive if redeemed early | Calendar days | **Yes** — last business day of May/November, ±10 business-day reconciliation window to avoid double-counting provider-imported withholding | CVM raw field `Tributacao_Longo_Prazo` (`"S"`), fallback name heuristic ("CURTO PRAZO") | gross gain, prepaid come-cotas, residual IR, net value |
| `MUTUAL_FUND` — short-term profile | 🟡 | `COME_COTAS_SHORT`: **hardcoded 2-bracket rule (22.5% ≤180d, else 20%)**, does NOT reuse the shared 4-bracket table — a real inconsistency to reconcile in the redesign | 22.5%/20% flat, own logic in `FundValuationService.computeFinalIr` | Calendar days | Yes, same checkpoint mechanism, 20% flat rate | Same CVM field, `"N"` or name contains "CURTO PRAZO" | Same shape as above; known bug — for SHORT funds IR residual mathematically collapses to ~0 post-come-cotas (expected, not a bug) |
| `MUTUAL_FUND` — equity funds | 🟡 | `VARIABLE_INCOME_15`: flat 15% on taxable gain, no come-cotas | 15% flat | Calendar days (for IOF only; no bracket table applies) | No | n/a | gross gain, IR (flat 15%), net value |
| `MUTUAL_FUND` — exempt-by-name (incentivized infra funds) | 🟡 | Classifier matches name markers (`INCENTIVAD`, `LEI 12431`, `FI INFRA`, …) → `UNKNOWN`, excluded from come-cotas automation | n/a | n/a | No (intentionally excluded) | **Name heuristic only, not validated against an official field** — flagged gap, deferred by Marcos | No tax computed (correctly, but for the wrong structural reason — should be an explicit exempt regime, not "unknown") |
| `PGBL`, `VGBL` | 🔴 (real gap) | `FundTaxProfile.PENSION` — profile exists, but no tax computed anywhere | Brazil offers 2 regimes for pension: progressive (like income tax table) or regressive-by-term (10%→35%→...→10% down to 6 years+) — **neither implemented** | n/a | n/a (tax due only on withdrawal/portability, not periodic) | n/a | Currently emits `null` tax fields — needs its own regime once implemented |
| `STOCK`, `STOCK_RIGHTS`, `BDR` | 🔴 | None | Swing-trade 15% flat, day-trade 20% flat, monthly R$20,000 exemption threshold on aggregate stock sales (not BDR), loss carryforward across months | Monthly aggregation (not per-lot elapsed days) — a **fundamentally different day-count/aggregation model** than fixed income | No (no come-cotas equivalent; broker withholds 0.005%/1% "dedo-duro" IRRF at source, informational only) | Monthly R$20,000 sales-volume exemption (stocks only, not BDR/FII/ETF) | Not produced today; `incomeTax`/`iof` fields on `BrokerNote`/`InvestmentTransaction` are provider pass-through, not computed |
| `REAL_ESTATE_FUND` (FII), `AGRIBUSINESS_FUND` (FIAGRO) | 🔴 | None | Capital gain 20% flat, **no** monthly exemption (unlike direct stocks); monthly dividend distributions are exempt from IR for individuals — a separate exemption rule from capital gain | Monthly aggregation for capital gains | No | Distribution income (not capital gain) exempt by law — must be modeled as a distinct income type, not folded into capital gain | Not produced today |
| `ETF`, `ETF_FIXED_INCOME`, `ETF_INTERNATIONAL` | 🔴 | None | Equity ETFs: 15% flat capital gain, **no** R$20,000 exemption (differs from direct stock rule even though both are "ações"); fixed-income ETFs and international ETFs have yet other treatments | Monthly aggregation | No | None known | Not produced today |
| `CRYPTO` | 🔴 | None (classification/label only, "Criptomoeda") | Progressive 15%–22.5% by monthly gain bracket, R$35,000/month exemption threshold | Monthly aggregation | No | Monthly exemption threshold | Not produced today |
| `OPTION`, `DERIVATIVES` | 🔴 | None (grouped as "others") | Day-trade/swing-trade analogous to stocks, additional complexity (exercise, expiry) | Monthly aggregation | No | Likely none | Not produced today |
| `COE` | 🔴 | None | Hybrid — often taxed like fixed income at redemption (regressive table) but structured-note specifics vary by product | Depends on structure | Possibly, depends on issuer | Depends on product | Not produced today |
| Dividends (`InvestmentTransactionType.DIVIDEND`) | 🔴 (correctly untaxed, but not modeled) | Exempt from IR for individuals by law — currently just pass-through display, never computed either way | n/a (always 0 IR) | n/a | No | Statutory exemption | Should still flow through the engine as a zero-tax income type, for consistent reporting |
| JCP / Interest on Equity (`InvestmentTransactionType.INTEREST_ON_EQUITY`) | 🔴 | Provider-cached pass-through only, no withholding computed in-house | 15% flat withholding at source | n/a | Withheld at distribution, not periodic | None | Not produced today — real gap, company should compute 15% withholding independent of provider data |
| Stock lending (`STOCK_LENDING`) | 🔴 | Bookkeeping/cash-ledger only | Own tax treatment (aluguel de ações), rate depends on lender type | n/a | n/a | n/a | Not produced today |

---

## 4. Cross-cutting mechanisms already in the codebase (reuse candidates)

- **IOF 30-day rule** (`BrazilianFixedIncomeTaxRules.iofRate`/`computeIof`): pure, well-tested,
  shared today only by fixed-income direct holdings and fund lots. Should become a
  composable step in the pipeline (`gain → IOF → taxable base`), not baked into any one regime.
- **Come-cotas checkpoint mechanism** (last business day of May/November,
  `BrazilianBusinessCalendar.lastBusinessDay`, ±10 business-day reconciliation window against
  provider-imported withholding transactions): this is a *periodic withholding trigger*, logically
  separate from *rate calculation*. The engine should model "does this regime have a periodic
  withholding event, and when" independently from "what rate applies."
- **Day-count conventions**: today there are already two independent conventions in use —
  `calendarDaysBetween` (simple day count, drives IR bracket determination) and
  `businessDaysBetween` (approximated `days/365*252`, not a real B3 trading calendar — used only
  for indexer accrual, not tax). The equities/crypto family above introduces a **third**
  convention: monthly aggregation of realized gains rather than elapsed-time brackets per lot.
  The engine's `TaxRegime` abstraction must accommodate both per-lot elapsed-time regimes and
  monthly-aggregate regimes — this is the single biggest design fork the matrix surfaces.
- **Exemption today is two unrelated mechanisms**: a boolean flag set at data-entry time (fixed
  income) vs. a name-heuristic/CVM-field classifier (funds). Neither is a property of the
  `TaxRegime` itself. The new design should treat "exempt" as a regime (or a regime modifier),
  not an out-of-band flag consulted before the engine runs.

## 5. GraphQL output contract today (what the engine must be able to feed)

- `InvestmentHolding`: `isTaxExempt`, `grossValue`, `netValue`, `incomeTax`, `iof`,
  `comeCotasEnabled` (manual override).
- `InvestmentTransaction`: `grossValue`, `netValue`, `incomeTax`, `iof`.
- `BrokerNote`: `incomeTax`, `taxes` (fee breakdown for equity trades — currently provider
  pass-through).
- `HoldingPerformanceSummary`/`HoldingPerformancePoint`: `incomeTax`, `iof`, `grossGain`,
  `netGain`.
- `FixedIncomeTaxMethodology`/`FixedIncomeTaxBracket`: bracket-table introspection, used only by
  the rate-comparison simulator (`TaxPolicyService`), not live holdings — a precedent for
  exposing the *bracket table itself*, not just the final numbers.
- `ConfirmHoldingMaturityInput`/`ConfirmCouponPaymentInput`: today accept **client-supplied**
  `incomeTax`/`iof` at confirmation time rather than server-recomputed values — a known
  consistency gap (user/broker-supplied vs. engine-computed) worth flagging for the design, not
  solving in this spike.

**Implication:** the pure engine's output must be a superset shape covering: gross gain, IOF,
taxable base, tax rate(s) applied (possibly a bracket path, not just a final number), amount
already withheld/prepaid (for regimes with periodic withholding), residual tax due, net value —
so every one of the above GraphQL fields can be a thin projection of one `TaxComputationResult`,
never a separately-derived value.

## 6. What this means for the design (not yet decided, input to `/neotool-solution-design`)

- The `TaxRegime` Strategy interface needs at least two shapes of elapsed-time input: per-lot
  elapsed calendar days (fixed income, funds) **and** monthly-aggregate realized gain across
  lots (equities, FIIs, ETFs, crypto). Forcing both into a single "elapsed days" signature would
  distort the equities case — worth an explicit decision on whether this is one interface with a
  variant, or two families under a common umbrella (e.g. `PerLotTaxRegime` vs
  `MonthlyAggregateTaxRegime`), consistent with requirement #4 (avoid over-engineering) so this
  shouldn't multiply into more than these two shapes without new evidence.
- IOF should likely be its own composable step (a function of gain + elapsed days), not part of
  `TaxRegime` itself, since it applies identically across regimes that have it and not at all for
  others.
- Exemption should be modeled as a `TaxRegime` implementation (or a decorator over one), not a
  boolean consulted before the engine runs — this both fixes the current two-mechanism
  inconsistency and makes "is this a special case" queryable the same way for every asset type.
  Reprocessing whether a fund/bond's exemption classification changes over time also then falls
  out of the same idempotent-recompute property already required for come-cotas.
- Periodic withholding trigger scheduling (come-cotas checkpoints today) should be modeled as a
  regime capability (`periodicWithholdingRate(elapsedDays): BigDecimal?`, already proposed in the
  handoff) plus a separate, regime-agnostic "checkpoint calendar" concept, since other regimes
  (JCP distribution withholding, future pension withdrawal tax) also have an event-driven
  withholding moment that isn't the come-cotas May/November schedule.
- Scope for implementation remains unchanged from the handoff: **do not implement** regimes for
  crypto/options/COE/pension now — this matrix exists so the abstractions chosen are proven
  against these cases on paper, without writing code for them.

## 6a. Formal decision: monthly capital-gains apuração is a separate engine (2026-07-29)

Open question raised after the initial spike: sell events for `STOCK`, `BDR`, `ETF`,
`REAL_ESTATE_FUND`/`AGRIBUSINESS_FUND`, and `CRYPTO` do trigger real Brazilian tax obligations
(monthly capital-gains apuração), and this is confirmed as a **live gap**, not a hypothetical
one — `SALE` transactions are already generically recorded today for these asset types
(`HoldingPerformanceState`, `HoldingPositionEvolution`, `CustodianCashLedgerHandler`,
`PortfolioFlowEventHandler` all handle `InvestmentTransactionType.SALE` generically), so users
can already sell these holdings in the product with zero tax computed on the realized gain. A
repository method (`InvestmentTransactionRepository.findByTypeAndDateRange`) even has a doc
comment referencing "the IR engine" / "DARF calculation" that was never built.

**Decision: this is a separate engine from the one being designed under project #40, not a new
`TaxRegime` implementation.** The two problems have fundamentally different shapes:

| | Per-lot engine (project #40 — this redesign) | Monthly capital-gains apuração (deferred) |
|---|---|---|
| Unit of computation | One holding's lots, as of a reference date | All sell transactions across all holdings of a category, per investor, per calendar month |
| Trigger | Elapsed time since acquisition / checkpoint dates | Calendar month close |
| State | None — pure recompute from lots (the whole point of this redesign) | Cross-month loss carryforward (compensação de perdas) — a persistent running balance per account/category |
| Exemption | Regime-level flag/classification | Threshold on **aggregate monthly sales volume** (R$20k stocks, R$35k crypto), unrelated to gain or elapsed time |
| Advance withholding | Periodic checkpoint (come-cotas) or none | Per-trade "dedo-duro" IRRF (0.005%/1%), credited against the monthly DARF |

Forcing monthly apuração into the `TaxRegime` Strategy interface (`periodicWithholdingRate`,
`finalRate`, both keyed on elapsed days) would either distort that interface for every future
implementer, or produce a fake per-lot shape wrapped around what is actually portfolio-level
stateful accounting — the over-engineering/leaky-abstraction risk requirement #4 explicitly
warns against.

**Resulting scope:**
- Project #40's engine remains scoped to per-lot, elapsed-time regimes (fixed income + funds) —
  unchanged from section 6.
- A **new, separate GitHub Project**, "Capital Gains Apuração Engine (Future)"
  (https://github.com/orgs/invistus/projects/41), tracks this as a deferred initiative. Tracking
  issue: https://github.com/invistus/invistus/issues/533, cross-linked with this spike (#530).
- The two engines may share low-level pure primitives (e.g. bracket-rate lookup style) but never
  the `TaxRegime` hierarchy or the per-lot pure core.
- **Not scoped for design or implementation now** — recorded so the gap isn't silently dropped,
  and so a future spike has a documented starting point.

## 7. Explicit non-goals of this spike

- No decision is made here on the exact Kotlin interfaces/class names — that's the job of
  `/neotool-solution-design`.
- No new asset classes are being added to the product.
- The known come-cotas idempotency bug and the JCP/dividend/PGBL/VGBL computation gaps are not
  being fixed here — they are inputs to backlog issues once the design is formalized.
