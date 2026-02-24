---
title: Fixed Income Comparison Implementation Backlog (Epics, Stories, Tasks, Subtasks)
type: backlog
category: financial-data
status: draft
version: 2.1.0
tags: [financial-data, fixed-income, backlog, epics, stories, tasks, cdb, lci, lca, lc]
ai_optimized: true
search_keywords: [fixed income implementation backlog hierarchy, cdb vs lci story task subtask, cdi ipca defaults api]
related:
  - 03-features/financial_data/fixed_income_comparison/fixed-income-comparison.md
  - 08-workflows/feature-development.md
  - 06-contracts/graphql-standards.md
last_updated: 2026-02-22
---

# Fixed Income Comparison Implementation Backlog

> **Purpose**: Provide an implementation-ready hierarchy using `Feature -> Epic -> Story -> Task -> Subtask`, including mandatory CDI/IPCA bootstrap from the existing economic indicators API.

## 1) Hierarchy Model (Recommended)

For this feature, do **not** put everything into one single Story.  
Use this hierarchy:

1. **Feature**: Fixed Income Comparison
2. **Epic**: Major deliverable chunk (backend core, market-data defaults, UI, quality)
3. **Story**: User or system outcome that can be validated
4. **Task**: Technical unit of work
5. **Subtask**: Precise implementation step

## 2) Scope and Constraints

### In Scope (V1)

- Single scenario simulation.
- Product types: `CDB`, `LC`, `LCI`, `LCA`.
- Rate models: `%CDI`, `FIXED`, `IPCA_PLUS`.
- Net equivalent comparison, table, performance-series chart, methodology panel.
- CDI/IPCA default values fetched from existing API query.

### Out of Scope (V1)

- Credit risk/default modeling.
- Product fees/cost modeling.
- Historical replay with daily real series.
- Portfolio simulation with multiple assets.

### Core Rules

- Taxable: `CDB`, `LC`.
- Tax-exempt: `LCI`, `LCA`.
- Return accrual: `252 business days`.
- IR bracket selection: calendar days between start and maturity.
- CDI/IPCA projections: auto-filled from API, user can override.

## 3) Mandatory Market Data Source (CDI/IPCA)

The simulator must bootstrap defaults from the existing query:

```graphql
query EconomicIndicatorsLatest {
  economicIndicators {
    label
    variations {
      label
      series(limit: 1) {
        unit
        values {
          referenceDate
          value
        }
      }
    }
  }
}
```

### Interpretation Contract

- Fetch `CDI` default from indicator `label = CDI` using:
  - preferred variation: `EFFECTIVE_RATE`;
  - fallback: first variation with non-empty `series.values`.
- Fetch `IPCA` default from indicator `label = IPCA` using:
  - preferred variation: `MONTHLY_CHANGE`;
  - fallback: first variation with non-empty `series.values`.
- If API has no usable value:
  - keep form editable,
  - show warning state,
  - require manual entry before simulation submit.

## 4) Epic Roadmap

1. `FIC-E01` Domain and Backend Contract
2. `FIC-E02` CDI/IPCA Defaults from Existing API
3. `FIC-E03` Frontend Experience (Form + Dashboard)
4. `FIC-E04` Quality, Observability, and Handoff

---

## Epic `FIC-E01` - Domain and Backend Contract

### Story `FIC-S011` - Lock Formula Contract and Golden Scenarios

**Outcome**: Engineering and QA share the same deterministic formulas.

#### Tasks

- [ ] `FIC-S011-T01` Finalize formula definitions in feature spec.
  - **Subtasks**:
    - [ ] Document `%CDI`, `FIXED`, `IPCA_PLUS` formulas.
    - [ ] Document IR brackets and boundary day examples.
    - [ ] Document equivalent-yield solve strategy.
  - **Files**:
    - `docs/03-features/financial_data/fixed_income_comparison/fixed-income-comparison.md`

- [ ] `FIC-S011-T02` Create golden test vectors.
  - **Subtasks**:
    - [ ] Add at least 6 scenarios.
    - [ ] Include all rate models and both tax classes.
    - [ ] Include boundary scenarios (`180/181`, `360/361`, `720/721`).
  - **Files**:
    - `docs/03-features/financial_data/fixed_income_comparison/calculation-examples.md` (new)

- [ ] `FIC-S011-T03` Define precision policy.
  - **Subtasks**:
    - [ ] Define internal precision.
    - [ ] Define display rounding.
    - [ ] Define automated test tolerance.
  - **Files**:
    - `docs/03-features/financial_data/fixed_income_comparison/calculation-examples.md`

#### Acceptance Criteria

- [ ] Formula contract is explicit and unambiguous.
- [ ] Golden examples cover core and boundary cases.
- [ ] Precision and tolerance are documented.
- [ ] Golden examples reviewed and signed off by at least one engineer and one QA before `FIC-S012` starts.

### Story `FIC-S012` - Implement Calculation Engine

**Outcome**: Backend computes deterministic results for any valid input.

#### Tasks

- [ ] `FIC-S012-T01` Create domain models and enums.
  - **Subtasks**:
    - [ ] Add input/output models for simulation.
    - [ ] Add enums for product and rate model.
  - **Files**:
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/domain/fixedincome/` (new package)

- [ ] `FIC-S012-T02` Implement tax and day-count policies.
  - **Subtasks**:
    - [ ] Tax class resolver (`taxable` vs `exempt`).
    - [ ] IR bracket selector by calendar day delta.
    - [ ] 252-day accrual conversion helper.
  - **Files**:
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/service/fixedincome/TaxPolicyService.kt` (new)
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/service/fixedincome/DayCountService.kt` (new)

- [ ] `FIC-S012-T03` Implement rate calculators.
  - **Subtasks**:
    - [ ] `%CDI` calculator.
    - [ ] `FIXED` calculator.
    - [ ] `IPCA_PLUS` calculator.
    - [ ] Harmonize to effective annual basis.
  - **Files**:
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/service/fixedincome/RateModelCalculator.kt` (new)

- [ ] `FIC-S012-T04` Implement equivalent-rate solver.
  - **Subtasks**:
    - [ ] Implement closed-form equivalent-rate solver (no iterative root finder).
    - [ ] Invert net-to-gross and gross-to-effective-rate formulas.
    - [ ] Convert effective rate back to `%CDI`, `FIXED`, and `IPCA_PLUS` contracted rates.
  - **Files**:
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/service/fixedincome/EquivalentYieldSolver.kt` (new)

- [ ] `FIC-S012-T05` Add engine unit tests.
  - **Subtasks**:
    - [ ] Core model tests.
    - [ ] IR boundary tests.
    - [ ] Closed-form solver correctness tests by rate model.
    - [ ] Golden vector assertions.
  - **Files**:
    - `service/kotlin/financialdata/src/test/kotlin/io/github/salomax/neotool/financialdata/test/unit/service/fixedincome/` (new package)

- [ ] `FIC-S012-T06` Implement orchestrator service.
  - **Subtasks**:
    - [ ] Inject `TaxPolicyService` and `EquivalentYieldSolver` via constructor DI.
    - [ ] Orchestrate full pipeline: parse input → solve equivalents → build `rows` + `performanceSeries` output model.
    - [ ] Keep service stateless and free of I/O (pure domain orchestration).
  - **Files**:
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/service/fixedincome/FixedIncomeComparisonService.kt` (new)

#### Acceptance Criteria

- [ ] Engine is deterministic and stateless.
- [ ] Outputs match golden vectors within tolerance.
- [ ] Boundary and solver behavior is covered by tests.
- [ ] Orchestrator service is the single entry point consumed by the resolver.

### Story `FIC-S013` - Expose GraphQL Query Contract

**Outcome**: Frontend consumes one query for the simulator response.

#### Tasks

- [ ] `FIC-S013-T01` Add schema types and query field.
  - **Subtasks**:
    - [ ] Add input types and enums.
    - [ ] Add output types (`summary`, `rows`, `performanceSeries`, `methodology`).
    - [ ] Ensure `rows` includes numeric `equivalentRate`.
    - [ ] Add query `fixedIncomeComparison(input: FixedIncomeComparisonInput!): FixedIncomeComparisonResult!`.
  - **Files**:
    - `service/kotlin/financialdata/src/main/resources/graphql/schema.graphqls`
    - `contracts/graphql/subgraphs/financialdata/schema.graphqls`

- [ ] `FIC-S013-T02` Implement resolver, mapper, and wiring.
  - **Subtasks**:
    - [ ] Create resolver class injecting `FixedIncomeComparisonService`.
    - [ ] Create mapper converting domain output model → DTOs.
    - [ ] Add `FixedIncomeComparisonResolver` to `FinancialDataWiringFactory` constructor injection.
    - [ ] Register `.dataFetcher("fixedIncomeComparison", createValidatedDataFetcher { ... })` in `registerQueryResolvers()`.
    - [ ] Validate payload and return typed errors.
  - **Files**:
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/graphql/resolver/FixedIncomeComparisonResolver.kt` (new)
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/graphql/mapper/FixedIncomeComparisonMapper.kt` (new)
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/graphql/FinancialDataWiringFactory.kt`
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/graphql/dto/FixedIncomeComparisonDTOs.kt` (new)

- [ ] `FIC-S013-T03` Add integration tests and sync contracts.
  - **Subtasks**:
    - [ ] Happy path query test.
    - [ ] Invalid input query test.
    - [ ] Sync/generate GraphQL contracts.
  - **Files**:
    - `service/kotlin/financialdata/src/test/kotlin/io/github/salomax/neotool/financialdata/test/integration/FixedIncomeComparisonIntegrationTest.kt` (new)
  - **Commands**:
    - `./neotool graphql sync` — regenerates `contracts/graphql/supergraph/supergraph.local.graphql` after schema change
    - `./neotool graphql generate --all` — generates typed clients for all subgraphs

#### Acceptance Criteria

- [ ] Query is available and returns full response shape.
- [ ] Resolver validates payload and rejects invalid inputs.
- [ ] Integration tests pass.

---

## Epic `FIC-E02` - CDI/IPCA Defaults from Existing API

### Story `FIC-S021` - Implement Market Defaults Loader

**Outcome**: UI auto-fills CDI/IPCA from current API before first simulation.

#### Tasks

- [ ] `FIC-S021-T01` Add query operation for `EconomicIndicatorsLatest`.
  - **Note**: Before creating a new file, verify whether `features/financial-data/national-indicators/hooks/useIndicatorsLatest.ts` already issues an equivalent query with `series(limit: 1)`. If so, reuse the existing generated types and skip creating a new operation file — only `FIC-S021-T02` and `FIC-S021-T03` remain necessary.
  - **Subtasks**:
    - [ ] Check `national-indicators` hooks for reusable query/types.
    - [ ] If no reusable query exists, create feature-level operation file named `economicIndicatorsLatest.ts` (explicit name to avoid conflict with simulation `index.ts`).
    - [ ] Keep query exactly aligned with existing API contract.
    - [ ] Generate typed document with codegen.
  - **Files** (only if new operation file is needed):
    - `web/src/features/financial-data/fixed-income-comparison/api/economicIndicatorsLatest.ts` (new)
    - `web/src/features/financial-data/fixed-income-comparison/api/economicIndicatorsLatest.generated.ts` (generated)

- [ ] `FIC-S021-T02` Create selector utilities for CDI and IPCA values.
  - **Subtasks**:
    - [ ] Implement CDI extraction policy.
    - [ ] Implement IPCA extraction policy.
    - [ ] Return `value + referenceDate + variationLabel`.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/model/marketDefaults.ts` (new)

- [ ] `FIC-S021-T03` Create hook to fetch and expose defaults.
  - **Subtasks**:
    - [ ] Build `useMarketIndicatorDefaults`.
    - [ ] Expose `loading`, `error`, `defaults`, `staleness`.
    - [ ] Add fallback state for missing data.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/hooks/useMarketIndicatorDefaults.ts` (new)

- [ ] `FIC-S021-T04` Add tests for selector and hook logic.
  - **Subtasks**:
    - [ ] Unit tests for CDI/IPCA extraction.
    - [ ] Missing-series fallback tests.
    - [ ] Error-state handling tests.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/model/__tests__/marketDefaults.test.ts` (new)
    - `web/src/features/financial-data/fixed-income-comparison/hooks/__tests__/useMarketIndicatorDefaults.test.ts` (new)

#### Acceptance Criteria

- [ ] CDI/IPCA defaults are fetched from `economicIndicators` endpoint.
- [ ] Extraction policy is deterministic and tested.
- [ ] Missing data produces explicit fallback/warning state.

### Story `FIC-S022` - Integrate Defaults into Simulation Form

**Outcome**: Form starts with API values and still allows manual override.

> **Dependency**: `SimulationForm.tsx` is created by `FIC-S032-T01`. This story adds market-defaults behavior on top of an already existing component. Execute `FIC-S031-T01` and `FIC-S032-T01` before starting `FIC-S022-T01`.

#### Tasks

- [ ] `FIC-S022-T01` Bind market defaults into form initial state.
  - **Subtasks**:
    - [ ] Use fetched CDI/IPCA as initial projection values.
    - [ ] Show source date in UI.
    - [ ] Track if user overrides defaults.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/components/SimulationForm.tsx` (extends existing — created in `FIC-S032-T01`)

- [ ] `FIC-S022-T02` Implement fallback behavior when API fails.
  - **Subtasks**:
    - [ ] Keep fields editable.
    - [ ] Show inline warning about missing defaults.
    - [ ] Block submit if required projection remains empty.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/components/SimulationForm.tsx`

- [ ] `FIC-S022-T03` Add UX test cases for default-loading flow.
  - **Subtasks**:
    - [ ] Defaults loaded and visible.
    - [ ] Manual override preserved across re-render.
    - [ ] API failure fallback path.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/components/__tests__/SimulationForm.market-defaults.test.tsx` (new)

#### Acceptance Criteria

- [ ] First render uses API defaults when available.
- [ ] User can override CDI/IPCA manually.
- [ ] API failure path is safe and explicit.

---

## Epic `FIC-E03` - Frontend Experience (Form + Dashboard)

### Story `FIC-S031` - Add Page, Route, and Navigation Entry

**Outcome**: Feature is discoverable under Financial Data.

#### Tasks

- [ ] `FIC-S031-T01` Create route and client container.
  - **Subtasks**:
    - [ ] Add `page.tsx` and `FixedIncomeComparisonClient.tsx`.
    - [ ] Register breadcrumb title.
    - [ ] Add SEO metadata.
  - **Files**:
    - `web/src/app/financial-data/fixed-income-comparison/page.tsx` (new)
    - `web/src/app/financial-data/fixed-income-comparison/FixedIncomeComparisonClient.tsx` (new)

- [ ] `FIC-S031-T02` Add module entry in financial-data home and sitemap.
  - **Subtasks**:
    - [ ] Add module card in `/financial-data`.
    - [ ] Add new route to sitemap.
  - **Files**:
    - `web/src/app/financial-data/page.tsx`
    - `web/src/app/sitemap.ts`

#### Acceptance Criteria

- [ ] Route is reachable and linked from financial-data home.
- [ ] Sitemap includes the new page.

### Story `FIC-S032` - Implement Simulation Form

**Outcome**: User can submit a valid scenario.

#### Tasks

- [ ] `FIC-S032-T01` Implement form schema and validation.
  - **Subtasks**:
    - [ ] Add `zod` schema for all fields.
    - [ ] Validate date order and positive amount.
    - [ ] Validate projection requirements by rate model.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/model/formSchema.ts` (new)

- [ ] `FIC-S032-T02` Implement API submit flow.
  - **Subtasks**:
    - [ ] Add fixed-income query operation and generated types.
    - [ ] Build hook for submit/load/error state.
    - [ ] Keep previous valid results until next valid run.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/api/index.ts` (new)
    - `web/src/features/financial-data/fixed-income-comparison/api/index.generated.ts` (generated)
    - `web/src/features/financial-data/fixed-income-comparison/hooks/useFixedIncomeComparison.ts` (new)

#### Acceptance Criteria

- [ ] Invalid forms cannot submit.
- [ ] Valid submit triggers one request with valid payload.
- [ ] User sees loading and error states clearly.

### Story `FIC-S033` - Implement Result Dashboard Components

**Outcome**: User can compare outcomes and performance-series evolution visually.

#### Tasks

- [ ] `FIC-S033-T01` Build equivalent summary card.
  - **Subtasks**:
    - [ ] Render headline with equivalent yield.
    - [ ] Highlight compared products/rates.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/components/EquivalentYieldCard.tsx` (new)

- [ ] `FIC-S033-T02` Build comparison table.
  - **Subtasks**:
    - [ ] Render rows (`base + equivalents`).
    - [ ] Render columns (`rate`, `gross`, `IR`, `net`, `real gain`).
    - [ ] Show `EXEMPT` marker for tax-free rows.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/components/ComparisonTable.tsx` (new)

- [ ] `FIC-S033-T03` Build profitability performance-series chart.
  - **Subtasks**:
    - [ ] Map `performanceSeries` points to chart.
    - [ ] Render two lines by tax regime (`TAXABLE_IR`, `EXEMPT_IR`).
    - [ ] Ensure final point equals table maturity net value.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/components/ProfitabilityEvolutionChart.tsx` (new)

- [ ] `FIC-S033-T04` Build methodology panel.
  - **Subtasks**:
    - [ ] Display 252-day convention.
    - [ ] Display IR table and active bracket.
    - [ ] Display calculation notes.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/components/MethodologyPanel.tsx` (new)

#### Acceptance Criteria

- [ ] Summary, table, chart, and methodology render from one response.
- [ ] Formatting is consistent (`pt-BR` currency/percentage).
- [ ] Layout works on desktop and mobile.

---

## Epic `FIC-E04` - Quality, Observability, and Handoff

### Story `FIC-S041` - Add Observability

**Outcome**: Runtime behavior is measurable and debuggable.

#### Tasks

- [ ] `FIC-S041-T01` Add backend logs and metrics.
  - **Subtasks**:
    - [ ] Add latency metric for fixed-income query.
    - [ ] Add validation error counters by type.
    - [ ] Add structured logs with correlation id.
  - **Files**:
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/graphql/resolver/FixedIncomeComparisonResolver.kt`
    - `service/kotlin/financialdata/src/main/kotlin/io/github/salomax/neotool/financialdata/service/fixedincome/FixedIncomeComparisonService.kt`

- [ ] `FIC-S041-T02` Add frontend error tracking.
  - **Subtasks**:
    - [ ] Capture simulation API errors in Sentry with simulation input as context (strip PII if needed).
    - [ ] Log market-defaults fallback event when CDI/IPCA cannot be auto-filled.
    - [ ] Ensure Sentry breadcrumbs include form state at time of error.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/hooks/useFixedIncomeComparison.ts`
    - `web/src/features/financial-data/fixed-income-comparison/hooks/useMarketIndicatorDefaults.ts`

#### Acceptance Criteria

- [ ] Query latency and failures are observable.
- [ ] Logs are sufficient for production triage.
- [ ] Frontend simulation errors and market-defaults fallbacks are captured in Sentry.

### Story `FIC-S042` - Complete Test Matrix

**Outcome**: Feature has backend, frontend, and E2E confidence.

#### Tasks

- [ ] `FIC-S042-T01` Backend integration tests.
  - **Subtasks**:
    - [ ] End-to-end GraphQL response shape and values.
    - [ ] Invalid payload behavior.
  - **Files**:
    - `service/kotlin/financialdata/src/test/kotlin/io/github/salomax/neotool/financialdata/test/integration/FixedIncomeComparisonIntegrationTest.kt`

- [ ] `FIC-S042-T02` Frontend unit/integration tests.
  - **Subtasks**:
    - [ ] Form validation and defaults loading.
    - [ ] Dashboard rendering from mock payload.
  - **Files**:
    - `web/src/features/financial-data/fixed-income-comparison/components/__tests__/` (new)

- [ ] `FIC-S042-T03` E2E smoke test.
  - **Subtasks**:
    - [ ] Open page.
    - [ ] Confirm defaults loaded.
    - [ ] Run one simulation.
    - [ ] Assert key widgets rendered.
  - **Files**:
    - `web/tests/e2e/fixed-income-comparison.spec.ts` (new)

#### Acceptance Criteria

- [ ] Backend and frontend test suites include feature coverage.
- [ ] E2E smoke validates primary path.

### Story `FIC-S043` - Final Documentation and Release Checklist

**Outcome**: Teams can safely ship and support the feature.

#### Tasks

- [ ] `FIC-S043-T01` Update feature docs and i18n keys.
  - **Subtasks**:
    - [ ] Sync final behavior in feature spec.
    - [ ] Add `pt` and `en` translation keys.
  - **Files**:
    - `docs/03-features/financial_data/fixed_income_comparison/fixed-income-comparison.md`
    - `web/src/shared/i18n/domains/financial-data/locales/pt.json`
    - `web/src/shared/i18n/domains/financial-data/locales/en.json`

- [ ] `FIC-S043-T02` Record release verification steps.
  - **Subtasks**:
    - [ ] Add command checklist.
    - [ ] Add rollback and fallback notes.
    - [ ] Add known limitations.
  - **Files**:
    - `docs/03-features/financial_data/fixed_income_comparison/implementation-backlog.md`

#### Acceptance Criteria

- [ ] Docs match implementation reality.
- [ ] Release checklist is complete and executable.

---

## 5) Dependency Graph (High Level)

### Epic-level ordering

1. `FIC-E01` before `FIC-E03` simulation submit integration.
2. `FIC-E02` can start after frontend scaffolding begins (see story-level below).
3. `FIC-E03` before `FIC-E04` E2E smoke completion.

### Story-level ordering (critical path)

```
FIC-S011 (golden scenarios + sign-off)
  └─► FIC-S012 (calculation engine)
        └─► FIC-S013 (GraphQL contract)

FIC-S031-T01 (route + page scaffold)   ← must come first in FIC-E03
  └─► FIC-S032-T01 (form schema + SimulationForm.tsx created here)
        ├─► FIC-S022-T01/T02 (bind market defaults into form)
        └─► FIC-S032-T02 (submit hook)

FIC-S021-T01/T02/T03 (market defaults hook)
  └─► FIC-S022-T01 (integrate defaults into form)  ← depends on FIC-S032-T01 too

FIC-S033 (dashboard components) ← can run in parallel with FIC-S022 once FIC-S032-T01 is done

FIC-S013 + FIC-S033 + FIC-S022
  └─► FIC-S042-T03 (E2E smoke)
        └─► FIC-S043 (docs + release checklist)
```

### Parallel tracks

- `FIC-S012` (backend engine) and `FIC-S031-T01` + `FIC-S021` (frontend scaffolding + defaults) can run in parallel once `FIC-S011` is complete.
- `FIC-S033` (dashboard components) and `FIC-S022` (defaults binding) can run in parallel once `FIC-S032-T01` is done.

## 6) Validation Command Set

- `cd service/kotlin && ./gradlew :financialdata:test`
- `./neotool graphql sync`
- `./neotool graphql generate --all`
- `cd web && pnpm run codegen`
- `cd web && pnpm run typecheck`
- `cd web && pnpm run lint`
- `cd web && pnpm run test:unit`
- `cd web && pnpm run test:e2e -- fixed-income-comparison`

## 7) Feature Definition of Done

- [ ] Hierarchy implemented and traceable (`Epic -> Story -> Task -> Subtask`).
- [ ] CDI/IPCA defaults fetched from current API query and safely handled on failure.
- [ ] Backend calculation + GraphQL contract completed and tested.
- [ ] Frontend form + dashboard completed and tested.
- [ ] Observability, docs, and release checklist completed.
