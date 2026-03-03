# Backlog — Institution Discovery Dashboard

**Feature**: [institution-discovery-dashboard.md](./institution-discovery-dashboard.md)
**Module**: financial_data
**Total estimate**: ~4 sprints (2 weeks each)

---

## Epics

| ID    | Epic                         |
|-------|------------------------------|
| EP-1  | Data Infrastructure (backend)|
| EP-2  | GraphQL API                  |
| EP-3  | Frontend Components          |
| EP-4  | Integration & Polish         |

---

## EP-1 — Data Infrastructure

### DISC-001 · Create `popular_institution` table and seed

- **Type**: Backend / DB
- **Effort**: S (< 1 day)
- **Description**: Create migration `V1_12__seed_popular_institutions.sql` with the table definition and insertion of the 20 popular institutions.
- **Acceptance criteria**:
  - [ ] Migration applies without errors in the development environment
  - [ ] 20 records inserted correctly
  - [ ] `CHECK (length(short_description) <= 200)` constraint works
  - [ ] Index on `rank WHERE active = true` created
  - [ ] Migration is idempotent (ON CONFLICT DO UPDATE)
- **Pre-validation**: confirm the exact format of `parent_cnpj` in the `financial_institution` table (8-digit root or full format `XX.XXX.XXX/0001-YY`) and adjust seed values accordingly

---

### DISC-002 · Validate canonical account name for Mobilization Index

- **Type**: Research / Backend
- **Effort**: XS (< 2h)
- **Description**: Query `bacen_institution_report_data.canonical_name` in the development database to identify the canonical account name representing the Mobilization Index (or its components: fixed assets and reference equity).
- **Expected output**: confirmed canonical name (e.g. `INDICE_IMOBILIZACAO` or `IMOBILIZACAO_TOTAL`) documented in this backlog and the feature doc
- **Acceptance criteria**:
  - [x] Canonical name identified and documented
  - [x] If the index does not exist as a direct account, calculation strategy documented (e.g. `ATIVO_PERMANENTE / PATRIMONIO_REFERENCIA`)
- **Resolved**: Canonical name **`INDICE_DE_IMOBILIZACAO`** is defined in `CanonicalAccountNames.kt` and used in `BacenInstitutionDetailService` for the Mobilization Index (Índice de Imobilização). The value is stored in `bacen_inst_report_account_canonical.normalized_name` and joined via `bacen_inst_report_account` / `bacen_inst_report_data`. No calculation strategy needed — it exists as a direct account.

---

### DISC-003 · Create `PopularInstitutionEntity` and repository

- **Type**: Backend
- **Effort**: S
- **Description**: Create the JPA entity `PopularInstitutionEntity.kt` mapping the `popular_institution` table, and the repository `PopularInstitutionRepository.kt` with a `findAllByActiveTrueOrderByRank()` method.
- **Files**:
  - `model/PopularInstitutionEntity.kt`
  - `repo/PopularInstitutionRepository.kt`
- **Acceptance criteria**:
  - [ ] Entity correctly mapped (fields, constraints)
  - [ ] Repository returns list ordered by rank
  - [ ] Unit test for the repository (mock/integration)

---

### DISC-004 · Create `TopInstitutionRankingRepository` with native queries

- **Type**: Backend
- **Effort**: M (1–2 days)
- **Description**: Create a repository with native SQL queries to fetch the top N institutions sorted by each metric (`PROFIT`, `BASILEIA_INDEX`, `MOBILIZATION_INDEX`). Requires JOIN across `bacen_institution`, `financial_institution_bacen_institution`, `financial_institution`, `bacen_institution_report`, `bacen_institution_report_data`.
- **File**: `repo/TopInstitutionRankingRepository.kt`
- **Acceptance criteria**:
  - [ ] PROFIT query functional (uses canonical_name `LUCRO_LIQUIDO`)
  - [ ] BASILEIA_INDEX query functional (uses validated canonical_name)
  - [ ] MOBILIZATION_INDEX query functional (uses canonical_name from DISC-002)
  - [ ] Limit is parameterizable (default 10, max 20)
  - [ ] `anoMes` is parameterizable (with fallback to latest quarter via `QuarterService`)
  - [ ] Only active institutions (`situacao = 'A'`) and individual type (`tipo_consolidacao = 'I'`)
  - [ ] Integration tests with test data

---

## EP-2 — GraphQL API

### DISC-005 · Add types and queries to GraphQL schema

- **Type**: Backend
- **Effort**: S
- **Description**: Add to `schema.graphqls`:
  - Enum `TopInstitutionMetric`
  - Types `PopularInstitution`, `TopBacenInstitutionsResult`, `TopBacenInstitutionItem`
  - Queries `popularInstitutions` and `topBacenInstitutions`
- **File**: `src/main/resources/graphql/schema.graphqls`
- **Acceptance criteria**:
  - [ ] Schema compiles without errors
  - [ ] Types reuse the existing `BacenInstitutionSummaryItem`
  - [ ] `limit` argument has a default value of 10

---

### DISC-006 · Create `InstitutionDiscoveryService`

- **Type**: Backend
- **Effort**: M
- **Description**: Service that orchestrates `PopularInstitutionRepository` and `TopInstitutionRankingRepository`, applies `InstitutionDataRestrictionService` to mask metrics for restricted institutions, and builds the response DTOs.
- **Files**:
  - `service/InstitutionDiscoveryService.kt`
  - `graphql/dto/InstitutionDiscoveryDTOs.kt`
- **Acceptance criteria**:
  - [ ] `getPopularInstitutions()` returns 20 items with resolved institution data
  - [ ] `getTopInstitutions(metric, limit, anoMes)` returns sorted list
  - [ ] Metrics for restricted institutions are `null` (existing pattern)
  - [ ] Unit tests with mocked repositories

---

### DISC-007 · Create `InstitutionDiscoveryResolver`

- **Type**: Backend
- **Effort**: S
- **Description**: GraphQL resolver with `@QueryMapping` for `popularInstitutions` and `topBacenInstitutions`.
- **File**: `graphql/resolver/InstitutionDiscoveryResolver.kt`
- **Acceptance criteria**:
  - [ ] `popularInstitutions` returns correct data via GraphQL playground
  - [ ] `topBacenInstitutions` returns correct top 10 for each metric
  - [ ] Validation errors (limit > 20) return appropriate GraphQL errors
  - [ ] Resolver tests with MockK

---

### DISC-008 · Generate TypeScript types (codegen)

- **Type**: Frontend / Infra
- **Effort**: XS
- **Description**: Run GraphQL codegen after schema changes to generate TypeScript types in `lib/graphql/types/__generated__/graphql.ts`.
- **Acceptance criteria**:
  - [ ] Types `PopularInstitution`, `TopBacenInstitutionsResult`, `TopBacenInstitutionItem`, enum `TopInstitutionMetric` generated
  - [ ] No TypeScript compilation errors

---

## EP-3 — Frontend Components

### DISC-009 · Create frontend GraphQL queries and hooks

- **Type**: Frontend
- **Effort**: S
- **Description**: Create the GraphQL operations and data hooks for the discovery panel.
- **Files**:
  - `features/financial-data/institution-discovery/api/index.ts` (queries `POPULAR_INSTITUTIONS`, `TOP_BACEN_INSTITUTIONS`)
  - `features/financial-data/institution-discovery/hooks/usePopularInstitutions.ts`
  - `features/financial-data/institution-discovery/hooks/useTopInstitutions.ts` (fires 3 metrics in parallel)
- **Acceptance criteria**:
  - [ ] `usePopularInstitutions` returns list with loading/error states
  - [ ] `useTopInstitutions` fires 3 parallel queries and returns an object with 3 rankings
  - [ ] Full TypeScript typing (no `any`)

---

### DISC-010 · Create `PopularInstitutionsCarousel` widget

- **Type**: Frontend
- **Effort**: M
- **Description**: Horizontal carousel of popular institutions with smooth scroll and arrow navigation on desktop.
- **File**: `widgets/financial-data/PopularInstitutionsCarousel/PopularInstitutionsCarousel.tsx`
- **Visual specs**:
  - Item: minimum 140px wide, 56px circular logo, name (1 line, ellipsis), description (2 lines, clamp)
  - Scroll: `overflowX: 'auto'`, `scrollSnapType: 'x mandatory'`, hidden scrollbar (`scrollbar-width: none`)
  - Navigation arrows: desktop only (`md+`), using `Box` with `position: absolute`
  - Rank chip: discrete number as `Badge` or chip in the top-left corner of the logo
- **Acceptance criteria**:
  - [ ] Horizontal scroll works on mobile (touch) and desktop
  - [ ] Arrow buttons work on desktop (scroll by 3 items)
  - [ ] Loading state shows 5 item skeletons
  - [ ] Click navigates to detail page via `createSemanticPath`
  - [ ] Accessible (role="list", items with role="listitem", links with aria-label)

---

### DISC-011 · Create `InstitutionRankingTable` widget

- **Type**: Frontend
- **Effort**: M
- **Description**: Compact, reusable ranking table for any metric.
- **File**: `widgets/financial-data/InstitutionRankingTable/InstitutionRankingTable.tsx`
- **Props**:
  ```typescript
  interface InstitutionRankingTableProps {
    title: string
    items: TopBacenInstitutionItem[]
    loading: boolean
    error?: ApolloError
    formatValue: (value: number | null) => string
    metricLabel: string
  }
  ```
- **Visual specs**:
  - Row: rank (chip), logo (24px), name (1 line, ellipsis), value right-aligned
  - Row height: 48px; table max 10 rows
  - Header with `title` and `metricLabel`
  - Divider between rows
- **Acceptance criteria**:
  - [ ] 10 rows rendered correctly
  - [ ] 10-row skeleton during loading
  - [ ] Values formatted (BRL for profit, `%` for indexes)
  - [ ] Restricted metrics show lock icon (existing pattern)
  - [ ] Click on row navigates to institution detail

---

### DISC-012 · Create `InstitutionDiscoveryPanel`

- **Type**: Frontend
- **Effort**: M
- **Description**: Orchestrator component that combines the carousel and 3 ranking tables. Manages visibility based on search state.
- **File**: `features/financial-data/institution-discovery/InstitutionDiscoveryPanel.tsx`
- **Props**: `{ visible: boolean }` — controlled by the page
- **Layout**:
  - "Most Popular" section: title + `<PopularInstitutionsCarousel>`
  - "Rankings" section: title + 3-column grid (1 column on mobile) with `<InstitutionRankingTable>` × 3
- **Animation**: `opacity: visible ? 1 : 0`, `pointerEvents: visible ? 'auto' : 'none'`, 400ms ease transition
- **Acceptance criteria**:
  - [ ] Panel visible when search field is empty
  - [ ] Panel hidden (opacity 0) when search is active — no layout shift
  - [ ] Section titles and metric labels use i18n strings
  - [ ] Responsive layout: rankings in 1 col on mobile, 3 col on desktop

---

## EP-4 — Integration & Polish

### DISC-013 · Integrate panel into `BacenInstitutionsPage`

- **Type**: Frontend
- **Effort**: S
- **Description**: Add `<InstitutionDiscoveryPanel>` to the existing `bacen-institutions/page.tsx`, controlling visibility via the `useBacenInstitutionsSearch` hook.
- **Modified file**: `app/financial-data/bacen-institutions/page.tsx`
- **Acceptance criteria**:
  - [ ] Panel renders correctly below the sticky header
  - [ ] `visible={!selectedInstitution && !institutions.length}` — visibility logic is correct
  - [ ] No regression in existing search behavior
  - [ ] Next.js build passes without errors

---

### DISC-014 · Add i18n strings

- **Type**: Frontend
- **Effort**: XS
- **Description**: Add translations for all new panel strings.
- **File**: `shared/i18n/domains/financial-data.ts`
- **Strings to add**:
  - `discovery.popularTitle`: "Most Popular"
  - `discovery.rankingsTitle`: "Rankings"
  - `discovery.metricProfit`: "Net Profit"
  - `discovery.metricBasileia`: "Basileia Index"
  - `discovery.metricMobilization`: "Mobilization Index"
  - `discovery.rankLabel`: "#{rank}"
  - `discovery.quarterLabel`: "Reference: {quarter}"
- **Acceptance criteria**:
  - [ ] All strings consumed via `useTranslation` in components
  - [ ] No hardcoded strings in new components

---

### DISC-015 · End-to-end testing and visual validation

- **Type**: QA / Frontend
- **Effort**: M
- **Description**: Validate the full panel behavior across key breakpoints and error scenarios.
- **Scenarios**:
  - [ ] Carousel with real data (20 items)
  - [ ] Rankings with real data (10 items each)
  - [ ] Smooth transition when typing in the search bar
  - [ ] Panel returns when search is cleared
  - [ ] Restricted institution in rankings (hidden metric)
  - [ ] Breakpoints: 375px (mobile), 768px (tablet), 1280px (desktop), 1920px (wide)
  - [ ] Keyboard navigation (Tab + Enter on carousel cards)

---

### DISC-016 · Barrel exports and cleanup

- **Type**: Frontend
- **Effort**: XS
- **Description**: Update barrel exports for widgets and features.
- **Modified files**:
  - `widgets/financial-data/index.ts` — export `PopularInstitutionsCarousel`, `InstitutionRankingTable`
  - `features/financial-data/index.ts` — export hooks and panel
- **Acceptance criteria**:
  - [ ] Build passes without errors
  - [ ] No direct file imports (barrel exports only)

---

## Suggested Implementation Order

```
DISC-002 (validate canonical name)
  └─> DISC-001 (seed migration)
  └─> DISC-003 (popular entity + repo)
  └─> DISC-004 (ranking repo)
        └─> DISC-005 (GraphQL schema)
        └─> DISC-006 (discovery service)
        └─> DISC-007 (resolver)
              └─> DISC-008 (TS codegen)
                    └─> DISC-009 (frontend hooks)
                    └─> DISC-010 (carousel widget)
                    └─> DISC-011 (ranking table widget)
                          └─> DISC-012 (discovery panel)
                                └─> DISC-013 (page integration)
                                └─> DISC-014 (i18n)
                                      └─> DISC-015 (QA)
                                      └─> DISC-016 (exports)
```

---

## Out of Scope (v1)

- Admin interface to edit the popular list
- Rankings by additional indicators (ROE, NIM, liquidity)
- Institution type filter in the panel
- Metric tooltip explanations (can be added as an increment)
- EN locale (PT-BR only for now)
