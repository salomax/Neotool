# Institution Discovery Dashboard

**Type**: Full-Stack (Backend + Frontend)
**Module**: financial_data
**Status**: Specification
**Route**: `/financial-data/bacen-institutions`

---

## 1. Problem / Context

The `/financial-data/bacen-institutions` search page requires users to already know an institution's name before they can start exploring. This creates a barrier for users who are still learning about the Brazilian banking ecosystem — particularly retail investors who want to discover and compare institutions by relevant financial metrics.

### Goals

- Turn the page into a discovery hub, not just a search box
- Surface the most relevant institutions to a broad audience with accessible descriptions
- Enable quick comparison by regulatory metrics (profit, solvency, mobilization)
- Every card and row links to the institution's detail page

---

## 2. Solution / Approach

Add a **discovery panel** immediately below the existing search bar. The panel is shown while the user has **no active search** (empty field).

The panel has three sections:

```
┌─────────────────────────────────────────────────────────┐
│  [    Search bar (existing)                           ]  │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  Most Popular                               ← → scroll  │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐         │
│  │ logo │ │ logo │ │ logo │ │ logo │ │ logo │  ···     │
│  │      │ │      │ │      │ │      │ │      │          │
│  │ Name │ │ Name │ │ Name │ │ Name │ │ Name │          │
│  │ desc │ │ desc │ │ desc │ │ desc │ │ desc │          │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘         │
└─────────────────────────────────────────────────────────┘

┌────────────────────┐ ┌────────────────────┐ ┌──────────────────────┐
│  Net Profit     ▼  │ │  Basileia Index  ▼ │ │  Mobilization Index  │
│  1. Itaú Unibanco  │ │  1. Banco Safra    │ │  1. BTG Pactual      │
│  2. Bradesco       │ │  2. Daycoval       │ │  2. Banco ABC        │
│  3. Banco do Brasil│ │  3. BTG Pactual    │ │  3. ...              │
│  ...               │ │  ...               │ │  ...                 │
└────────────────────┘ └────────────────────┘ └──────────────────────┘
```

### Visibility behavior

| Search state      | Discovery panel                          |
|-------------------|------------------------------------------|
| Empty field       | Visible                                  |
| Active search     | Hidden (smooth fade-out, no layout shift)|

---

## 3. Components

### 3.1 PopularInstitutionsCarousel (Frontend Widget)

- **Location**: `web/src/widgets/financial-data/PopularInstitutionsCarousel/`
- **Type**: Client Component
- **Layout**: horizontal scroll with `overflow-x: auto`, per-item snap, no visible scrollbar
- **Each item displays**:
  - Circular logo (via `logoAsset`, with initial-letter avatar fallback)
  - Display name (trade name or name)
  - Short description (≤ 120 chars)
  - Rank (optional number shown as a discrete chip)
- **Behavior**: click → navigates to `/financial-data/bacen-institutions/[slug]`
- **Responsive**: 2 items visible on mobile, 4–5 on desktop; navigation arrows on desktop only

### 3.2 InstitutionRankingTable (Frontend Widget)

- **Location**: `web/src/widgets/financial-data/InstitutionRankingTable/`
- **Type**: Client Component
- **Props**: `metric: 'PROFIT' | 'BASILEIA_INDEX' | 'MOBILIZATION_INDEX'`
- **Layout**: numbered vertical list (1–10), compact rows
- **Each row displays**:
  - Position (rank)
  - Small logo (24px) + name
  - Formatted metric value
  - Trend indicator (reuses existing `TrendIndicator`)
- **Behavior**: click on row → navigates to the institution detail page
- **Loading state**: 10-row skeleton

### 3.3 InstitutionDiscoveryPanel (Frontend Feature Component)

- **Location**: `web/src/features/financial-data/institution-discovery/`
- **Type**: Client Component orchestrator
- **Responsibility**: wraps Carousel + 3 × RankingTable, manages visibility based on search state
- **Animation**: `opacity` transition (400ms ease) on show/hide

---

## 4. Technical Decisions

### 4.1 Popular institutions data — new seed table

**Decision**: static table `financial_data.popular_institution`, loaded via Flyway migration (seed), **no write API**.

**Rationale**:
- The list changes at most once a year (editorial review)
- Avoids admin panel complexity and caching overhead
- Follows the pattern already established in `V1_7__seed_rating_issuers.sql`

**Schema**:

```sql
CREATE TABLE IF NOT EXISTS financial_data.popular_institution (
    id                UUID PRIMARY KEY DEFAULT uuidv7(),
    rank              SMALLINT NOT NULL UNIQUE,
    parent_cnpj       TEXT NOT NULL,   -- logical FK to financial_institution.parent_cnpj
    short_description TEXT NOT NULL CHECK (length(short_description) <= 200),
    active            BOOLEAN NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Migration**: `V1_12__seed_popular_institutions.sql` (see section 7)

### 4.2 Ranking data — dedicated new API query

**Decision**: new dedicated GraphQL query `topBacenInstitutions` for rankings, separate from the search query.

**Rationale**:
- The existing `bacenInstitutions` query is cursor-paginated; adding dynamic `ORDER BY` to it would increase complexity and affect its execution plan
- A dedicated query allows native SQL `ORDER BY` without impacting search
- Can be independently cached (data changes only per quarter)
- The mobilization index (`basileiaIndex` already exists; `mobilizationIndex` requires a specific report account)

**New GraphQL query**:

```graphql
topBacenInstitutions(
  metric: TopInstitutionMetric!
  limit: Int         # default: 10, max: 20
  anoMes: String     # default: latest quarter
): TopBacenInstitutionsResult!
```

### 4.3 Mobilization Index

`basileiaIndex` is already returned by the existing API. The `mobilizationIndex` (Índice de Imobilização) is a BACEN regulatory metric:

> Fixed Assets ÷ Reference Equity × 100

This value is stored in the report data (`bacen_institution_report_data`) under a canonical account name to be confirmed — likely `INDICE_IMOBILIZACAO` or derived from `ATIVO_PERMANENTE` / `PATRIMONIO_REFERENCIA`. The backend must verify this during implementation by inspecting available accounts.

---

## 5. GraphQL Schema (additions)

```graphql
# Added to schema.graphqls

extend type Query {
  # Static editorial list of the most popular institutions (seed data)
  popularInstitutions: [PopularInstitution!]!

  # Top N institutions ranked by a financial metric
  topBacenInstitutions(
    metric: TopInstitutionMetric!
    limit: Int
    anoMes: String
  ): TopBacenInstitutionsResult!
}

enum TopInstitutionMetric {
  PROFIT             # Net Profit (Lucro Líquido)
  BASILEIA_INDEX     # Capital Adequacy Index (Basileia III)
  MOBILIZATION_INDEX # Mobilization Index (Índice de Imobilização)
}

type PopularInstitution {
  rank: Int!
  shortDescription: String!
  # Institution data (same structure as summary)
  institution: BacenInstitutionSummaryItem!
}

type TopBacenInstitutionsResult {
  metric: TopInstitutionMetric!
  quarter: String!
  items: [TopBacenInstitutionItem!]!
}

type TopBacenInstitutionItem {
  rank: Int!
  institution: BacenInstitutionSummaryItem!
  metricValue: Float
}
```

---

## 6. Backend — Implementation Structure

### New files

| File | Responsibility |
|------|----------------|
| `model/PopularInstitutionEntity.kt` | JPA entity for `popular_institution` |
| `repo/PopularInstitutionRepository.kt` | `findAllByActiveTrueOrderByRank` |
| `repo/TopInstitutionRankingRepository.kt` | Native SQL queries with dynamic ORDER BY and JOIN on report_data |
| `service/InstitutionDiscoveryService.kt` | Orchestrates popular + rankings; applies data restriction |
| `graphql/dto/InstitutionDiscoveryDTOs.kt` | DTOs: PopularInstitutionDTO, TopBacenInstitutionItemDTO, etc. |
| `graphql/resolver/InstitutionDiscoveryResolver.kt` | `@QueryMapping` for `popularInstitutions` and `topBacenInstitutions` |

### Reuse of existing components

- `InstitutionSearchMapper` → reused to build `BacenInstitutionSummaryItem` from the JOIN result
- `QuarterService` → resolves the default quarter (latest global quarter)
- `InstitutionDataRestrictionService` → nulls out metrics for restricted institutions
- `BacenInstitutionRepositoryCustom` → can serve as a reference for ranking queries

### Ranking SQL query (sketch)

```sql
SELECT
    bi.bacen_cod_inst,
    fi.id              AS financial_institution_id,
    fi.trade_name,
    fi.asset_id,
    rd.value           AS metric_value
FROM financial_data.bacen_institution bi
JOIN financial_data.financial_institution_bacen_institution rel
    ON rel.bacen_cod_inst = bi.bacen_cod_inst
    AND rel.ano_mes_ref   = :anoMes
JOIN financial_data.financial_institution fi
    ON fi.id = rel.financial_institution_id
JOIN financial_data.bacen_institution_report_data rd
    ON rd.bacen_cod_inst  = bi.bacen_cod_inst
    AND rd.ano_mes_ref    = :anoMes
    AND rd.canonical_name = :metricAccountName   -- e.g. 'LUCRO_LIQUIDO'
JOIN financial_data.bacen_institution_report r
    ON r.id = rd.report_id
    AND r.bacen_cod_inst = bi.bacen_cod_inst
    AND r.ano_mes_ref    = :anoMes
WHERE bi.situacao = 'A'
  AND bi.tipo_consolidacao = 'I'   -- individual institutions only (avoid double-counting)
ORDER BY rd.value DESC NULLS LAST
LIMIT :limit;
```

> **Note**: the exact canonical account name for the mobilization index must be validated against the report data available in the development environment.

---

## 7. Database — Seed Migration

**File**: `service/kotlin/financialdata/src/main/resources/db/migration/V1_12__seed_popular_institutions.sql`

```sql
-- Migration: Seed popular institutions for discovery dashboard
-- Version: V1_12
-- Description: Static list of the 20 most popular Brazilian financial institutions
--              for the discovery carousel. Updated manually, once a year.
--              Rankings are editorial, not derived from financial metrics.

SET search_path TO financial_data, public;

-- ============================================================================
-- Table: popular_institution
-- ============================================================================

CREATE TABLE IF NOT EXISTS financial_data.popular_institution (
    id                UUID         PRIMARY KEY DEFAULT uuidv7(),
    rank              SMALLINT     NOT NULL UNIQUE,
    parent_cnpj       TEXT         NOT NULL,
    short_description TEXT         NOT NULL CHECK (length(short_description) <= 200),
    active            BOOLEAN      NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_popular_institution_rank
    ON financial_data.popular_institution(rank) WHERE active = true;

COMMENT ON TABLE financial_data.popular_institution IS
    'Static editorial list of the most popular Brazilian financial institutions '
    'shown in the discovery carousel. Updated manually once a year via migration.';

-- ============================================================================
-- Seed data (2025 – editorial ranking)
-- parent_cnpj values refer to the root CNPJ in the financial_institution table.
-- Validate the exact format against financial_institution.parent_cnpj before
-- applying to production (may be 8-digit root or full format XX.XXX.XXX/0001-YY).
-- ============================================================================

INSERT INTO financial_data.popular_institution (rank, parent_cnpj, short_description) VALUES
( 1, '18236120', 'Digital leader focused on user experience, no-fee cards, and automatic yield.'),
( 2, '00360305', 'Social bank focused on housing (Minha Casa Minha Vida), FGTS, and government programs.'),
( 3, '00000000', 'Solid institution with a strong presence in agribusiness, rural credit, and institutional security.'),
( 4, '60872504', 'Largest private bank in Latin America, a reference in technology, wealth management, and premium cards.'),
( 5, '60746948', 'Retail bank with a vast branch network, a tradition in insurance, and nationwide accessibility.'),
( 6, '90400888', 'Specialist in vehicle financing, global credit cards, and real estate credit.'),
( 7, '00416968', 'Financial super app with a free account, integrated shopping platform, and own brokerage.'),
( 8, '22896431', 'Super app focused on fast payments, social transfers, and wallet yield.'),
( 9, '10573521', 'Financial arm of Mercado Livre; focused on seller credit and QR Code payments.'),
(10, '08561701', 'Pioneer in payment terminals, offering a full digital account focused on small entrepreneurs.'),
(11, '30306294', 'Largest investment bank in Latin America, focused on high-net-worth clients, asset management, and financial strength.'),
(12, '31872495', 'Digital bank focused on high-net-worth and business clients, with a points program (Átomos) and global account.'),
(13, '33264668', 'Integrated with Brazil''s largest brokerage; focused on investment advisory and cashback cards.'),
(14, '58160789', 'One of the most conservative and safe banks, focused on high-net-worth (Private) and large corporations.'),
(15, '62232889', 'Reference in payroll credit and corporate financing, with very solid balance sheets.'),
(16, '59285411', 'Specialist in credit for lower-income segments, motorcycle and used vehicle financing.'),
(17, '28195667', 'Corporate bank focused on large companies, known as a reliable dividend payer.'),
(18, '59588111', 'Leader in vehicle financing and personal credit, with strong backing from Banco do Brasil.'),
(19, '61186680', 'Specialist in payroll credit for retirees and popular credit cards.'),
(20, '60889128', 'Specialist in fixed income (CDBs) with daily liquidity and competitive rates for investors.')
ON CONFLICT (rank) DO UPDATE SET
    parent_cnpj       = EXCLUDED.parent_cnpj,
    short_description = EXCLUDED.short_description,
    active            = EXCLUDED.active,
    updated_at        = NOW();
```

> **Warning**: the `parent_cnpj` values above use the 8-digit root CNPJ and must be **validated against `financial_institution.parent_cnpj`** before applying to production. Adjust the format to match the convention adopted in that table.

---

## 8. Frontend — Implementation Structure

### New files

| File | Responsibility |
|------|----------------|
| `features/financial-data/institution-discovery/api/index.ts` | GraphQL queries: `POPULAR_INSTITUTIONS`, `TOP_BACEN_INSTITUTIONS` |
| `features/financial-data/institution-discovery/hooks/usePopularInstitutions.ts` | Hook to fetch the carousel data |
| `features/financial-data/institution-discovery/hooks/useTopInstitutions.ts` | Hook to fetch rankings (3 parallel calls) |
| `widgets/financial-data/PopularInstitutionsCarousel/PopularInstitutionsCarousel.tsx` | Horizontal carousel |
| `widgets/financial-data/InstitutionRankingTable/InstitutionRankingTable.tsx` | Reusable ranking table |
| `features/financial-data/institution-discovery/InstitutionDiscoveryPanel.tsx` | Orchestrator panel (carousel + 3 rankings) |

### Modified existing files

| File | Change |
|------|--------|
| `app/financial-data/bacen-institutions/page.tsx` | Add `<InstitutionDiscoveryPanel>` below the sticky header, controlled by `shouldSkipQuery` |
| `features/financial-data/index.ts` | Export new feature hooks |
| `widgets/financial-data/index.ts` | Export new widgets |
| `shared/i18n/domains/financial-data.ts` | New strings: section titles, metric tooltips |

### GraphQL queries (frontend)

```graphql
query PopularInstitutions {
  popularInstitutions {
    rank
    shortDescription
    institution {
      id
      name
      tradeName
      logoAsset { id }
      dataRestriction { isRestricted }
    }
  }
}

query TopBacenInstitutions($metric: TopInstitutionMetric!, $limit: Int) {
  topBacenInstitutions(metric: $metric, limit: $limit) {
    metric
    quarter
    items {
      rank
      metricValue
      institution {
        id
        name
        tradeName
        logoAsset { id }
        dataRestriction { isRestricted }
      }
    }
  }
}
```

---

## 9. Acceptance Criteria

1. **Carousel**
   - Displays all 20 popular institutions with logo, name, and description
   - Horizontal scroll works on both mobile and desktop
   - Each item links to the institution detail page
   - Loading state shows skeleton items

2. **Rankings**
   - 3 tables display top 10 by Net Profit, Basileia Index, and Mobilization Index
   - Sorted best to worst
   - Values formatted (BRL currency for profit, percentage for indexes)
   - Restricted institutions have their metrics hidden (lock icon, existing pattern)
   - Each row links to the institution detail page

3. **Visibility**
   - Panel hidden when search is active
   - Smooth fade transition on hide/show
   - Existing search grid is unaffected

4. **Performance**
   - The 3 ranking queries fire **in parallel** (multiple `useQuery` calls)
   - Data is static per quarter → cached by Apollo default in-memory cache
   - Carousel loads with `suspense: false` — does not block page rendering

5. **Responsiveness**
   - Carousel: 2 items visible on mobile, 4–5 on desktop
   - Rankings: vertical stack on mobile (1 col), 3-column grid on desktop (`md+`)

---

## 10. Technical Constraints

- No REST endpoints; GraphQL only
- No admin interface for the popular list (updates via migration)
- `mobilizationIndex`: validate canonical account name before implementation
- All institution links must use `createSemanticPath(name, id)` (existing pattern)
- Respect `dataRestriction.isRestricted` — hide metrics for restricted institutions

---

## 11. Dependencies

- `financial_institution` table populated with records for the 20 institutions (via existing consumer)
- Logo assets available in the assets service (via federation)
- Canonical report accounts populated for the latest quarter
