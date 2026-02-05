# Financialdata Economic Indicator API Refactor Backlog

## Goal
Unify economic indicator queries into a grouped structure:
`EconomicIndicator -> EconomicIndicatorVariation -> EconomicIndicatorVariationSeries`.
Remove legacy queries (`indicatorSeries`, `indicatorLatest`, `indicatorsLatest`).
Enforce a default range of “last 5 years” when no explicit range is provided.
Avoid N+1 by batching in repository queries.

## Mandatory references (read before implementation)
- GraphQL standards: `docs/06-contracts/graphql-standards.md`
- GraphQL query patterns: `docs/06-contracts/graphql-query-pattern.md`
- Backend Kotlin coding standards: `docs/05-backend/kotlin/coding-standards.md`
- Backend Kotlin linting standards: `docs/05-backend/kotlin/linting-standards.md`
- Backend Kotlin testing standards: `docs/05-backend/kotlin/testing-standards.md`
- Backend repository pattern: `docs/05-backend/patterns/repository-pattern.md`
- Backend service pattern: `docs/05-backend/patterns/service-pattern.md`
- Backend GraphQL resolver pattern: `docs/05-backend/patterns/graphql-resolver-pattern.md`
- Database schema standards: `docs/04-domain/database-schema-standards.md`
- Economic indicators spec: `docs/03-features/financial_data/brazilian_economic_indicators/brazilian-economic-indicators.md`
- Economic indicators DB schema: `docs/03-features/financial_data/brazilian_economic_indicators/database-schema.md`

## Backlog

### 1) Finalize new GraphQL schema shape
- [x] Define new query field (example name): `economicIndicatorSeries` or `economicIndicatorValues`.
- [x] Decide exact schema:
  - `EconomicIndicator` includes `variations`.
  - `EconomicIndicatorVariation` includes `series(range: IndicatorRangeInput, limit: Int): EconomicIndicatorVariationSeries`.
  - `EconomicIndicatorVariationSeries { unit, values: [EconomicIndicatorPoint!]! }`.
- [x] Add/confirm `IndicatorRangeInput` stays as `{ from, to, last }`.
- [x] Remove old query fields and types:
  - `indicatorSeries`, `indicatorLatest`, `indicatorsLatest`, `EconomicIndicatorSeries`, `EconomicIndicatorLatest`.
- [x] Follow GraphQL standards and patterns:
  - `docs/06-contracts/graphql-standards.md`
  - `docs/06-contracts/graphql-query-pattern.md`

### 2) Repository refactor (avoid N+1)
- [x] Replace single-indicator queries with batched query returning all required series data in one DB call.
- [x] New repository method (example):
  - `findSeriesGrouped(indicatorLabel: String?, variationLabel: String?, from: LocalDate, to: LocalDate, limit: Int?): List<IndicatorSeriesRow>`
- [x] Return rows including indicator label, variation label, unit, reference_date, value.
- [x] Ensure SQL filters by `economic_indicator_value.reference_date`.
- [x] If `limit` is used, apply per indicator/variation (requires window functions) or document global limit.
- [x] Align with repository pattern and DB standards:
  - `docs/05-backend/patterns/repository-pattern.md`
  - `docs/04-domain/database-schema-standards.md`

### 3) Service layer defaults + grouping
- [x] Enforce default range when no `range` is provided:
  - `from = today - 5 years` (by date), `to = today`.
- [x] Decide precedence between `range.last` and `limit` (recommend: `range.last` wins).
- [x] Group repository rows into series per indicator/variation.
- [x] Validate `indicator`/`variation` when provided (non-blank, length).
- [x] Follow service pattern and Kotlin standards:
  - `docs/05-backend/patterns/service-pattern.md`
  - `docs/05-backend/kotlin/coding-standards.md`

### 4) Resolver + wiring
- [x] Implement new resolver method for the unified field.
- [x] Update `FinancialDataWiringFactory` to wire the new field.
- [x] Remove wiring for old fields.
- [x] Follow GraphQL resolver pattern:
  - `docs/05-backend/patterns/graphql-resolver-pattern.md`

### 5) DTO/model updates
- [x] Add DTOs for `EconomicIndicatorVariationSeries` if needed.
- [x] Remove unused DTOs (`EconomicIndicatorSeriesDTO`, `EconomicIndicatorLatestDTO`, etc.).
- [x] Update indicator DTOs if `series` is nested under variations.
- [x] Follow Kotlin standards:
  - `docs/05-backend/kotlin/coding-standards.md`

### 6) Contracts + Bruno collection updates
- [x] Update `contracts/graphql/subgraphs/financialdata/schema.graphqls`.
- [x] Update `service/kotlin/financialdata/src/main/resources/graphql/schema.graphqls`.
- [x] Update Bruno collection queries to new API.
- [x] Follow GraphQL standards and Bruno conventions:
  - `docs/06-contracts/graphql-standards.md`
  - `contracts/api-collections/bruno/README.md`

### 7) Tests and verification
- [ ] Repository tests for range filtering and default “last 5 years”.
- [ ] Service tests for grouping and limit precedence.
- [ ] Integration test for new query shape.
- [ ] Regression check: no leftover references to removed fields/types.
- [ ] Follow testing standards:
  - `docs/05-backend/kotlin/testing-standards.md`

### 8) Migration notes / client communication
- [ ] Document breaking change and new query usage.
- [ ] Provide sample query and response shape.

## Open Decisions
- Default date range: “last 5 years” computed from `LocalDate.now()`.
- Limit semantics: per indicator/variation vs global limit (recommend per series).
- Final naming of the new query field and series type.

## Implementation notes for the LLM
- Remove old GraphQL fields and types in both schema locations:
  - `contracts/graphql/subgraphs/financialdata/schema.graphqls`
