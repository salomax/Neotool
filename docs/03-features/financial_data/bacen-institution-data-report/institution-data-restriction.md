---
title: Institution Financial Data Restriction
type: feature-guide
module: financial-data
feature_slug: institution-data-restriction
status: draft
version: 1.0.0
tags: [financial-data, bacen, privacy, data-restriction]
search_keywords: [institution restriction, hide financial data, data privacy, opt-out]
related:
  - ../bacen-ifdata-ingestion/bacen-ifdata-ingestion.md
  - ../../../../05-backend/patterns/repository-pattern.md
  - ../../../../06-contracts/graphql-standards.md
---

# Institution Financial Data Restriction

> **One-line summary**: Allow institutions to opt-out of exposing their financial data while still appearing in search results, with clear indication of data unavailability and the reason.

## Overview

**Problem Context**: Some financial institutions may request that their detailed financial data (reports, balances, metrics) not be publicly exposed through the platform. While BACEN data is public, institutions can request removal from third-party platforms. The platform must honor these requests while maintaining transparency about why data is unavailable.

**Goals**:
1. Allow marking specific institutions as having restricted financial data
2. Continue returning institutions in search results (they exist and are valid)
3. Clearly communicate to users that financial data is restricted and why
4. Implement restrictions in a timeless manner (applies to all quarters)
5. Provide administrative capability to manage restrictions

**Target Users**:
- End users searching for institutions (will see restriction notices)
- System administrators managing restriction requests
- API consumers integrating with the platform

**Success Criteria**:
- Restricted institutions appear in search results with appropriate indicators
- No financial data (reports, balances, metrics) is returned for restricted institutions
- Clear reason codes are exposed via API and UI
- Restriction management is auditable

## Functional Requirements

### Core Functionality

**FR-1: Data Restriction Storage**
- **Description**: Store institution restrictions with reason codes in a dedicated table
- **Priority**: Critical
- **Acceptance Criteria**:
  - Restrictions are stored by `bacen_cod_inst` (institution identifier)
  - Supports multiple reason codes (extensible enum)
  - Includes audit fields (created_at, updated_at, created_by)
  - Restrictions are timeless (apply to all quarters)

**FR-2: API Filtering**
- **Description**: Filter out financial data for restricted institutions in all API responses
- **Priority**: Critical
- **Acceptance Criteria**:
  - `bacenFinanceInstitution` query returns institution metadata but no financial data
  - `bacenInstitutions` search returns restricted institutions with indicator flag
  - Report queries (`reportsSummary`, `reportDetails`) return empty results for restricted institutions
  - Financial metrics (profit, equity, etc.) return null for restricted institutions

**FR-3: Restriction Indicator**
- **Description**: Expose restriction status and reason in GraphQL API
- **Priority**: Critical
- **Acceptance Criteria**:
  - New `dataRestriction` field on `BacenFinanceInstitution` and `BacenInstitutionSummaryItem` types
  - Includes `isRestricted: Boolean` and `reason: DataRestrictionReason` enum
  - Reason enum includes `INSTITUTION_REQUEST` (and extensible for future reasons)

**FR-4: Frontend Display**
- **Description**: Display appropriate UI for restricted institutions
- **Priority**: High
- **Acceptance Criteria**:
  - Institution detail page shows restriction notice instead of financial data
  - Search result cards show visual indicator for restricted institutions
  - Clear messaging explaining why data is unavailable

### User Flows

**Flow 1: User Views Restricted Institution**
- **Goal**: User understands why financial data is unavailable
- **Steps**:
  1. User searches for or navigates to an institution
  2. System checks if institution has data restriction
  3. If restricted, system returns institution metadata with restriction info
  4. UI displays institution name, logo, basic info
  5. UI shows restriction notice instead of financial data sections
- **Expected Outcome**: User sees institution exists but understands data is restricted
- **Error Handling**: If restriction check fails, fail-safe to showing no financial data

**Flow 2: Admin Adds Restriction (via Database)**
- **Goal**: Administrator restricts financial data for an institution
- **Steps**:
  1. Admin receives restriction request (email, ticket, etc.)
  2. Admin identifies institution by `bacen_cod_inst`
  3. Admin executes SQL INSERT with reason code and notes
  4. Restriction takes effect on next query (no cache invalidation)
- **Expected Outcome**: Institution's financial data is no longer returned
- **Error Handling**: Database constraint validates institution code format

**Flow 3: Admin Removes Restriction (via Database)**
- **Goal**: Administrator lifts restriction on an institution
- **Steps**:
  1. Admin identifies restricted institution
  2. Admin executes SQL UPDATE to set `is_active = FALSE` (or DELETE)
  3. Financial data becomes available on next query
- **Expected Outcome**: Institution's financial data is returned normally
- **Error Handling**: None - standard SQL operation

### Business Rules

**BR-1: Restriction Scope**
- **Description**: Restrictions apply to all financial data across all time periods
- **When**: Institution is marked as restricted
- **Then**: No financial data returned regardless of `ano_mes` parameter
- **Exceptions**: None - restriction is absolute for financial data
- **Examples**:
  - Institution restricted -> queries for 202403, 202312, etc. all return no financial data
  - Basic metadata (name, logo, website) still returned

**BR-2: Search Visibility**
- **Description**: Restricted institutions remain visible in search
- **When**: User searches for institutions
- **Then**: Restricted institutions appear in results with restriction indicator
- **Exceptions**: None
- **Examples**:
  - Search "Banco XYZ" -> appears in results with restricted flag
  - User can navigate to institution page (but sees restriction notice)

**BR-3: Reason Code Requirement**
- **Description**: All restrictions must have a reason code
- **When**: Adding a restriction
- **Then**: Reason code is required, defaults not allowed
- **Exceptions**: None
- **Examples**:
  - Valid: `INSTITUTION_REQUEST` - institution formally requested removal
  - Future: `LEGAL_REQUIREMENT`, `DATA_QUALITY_ISSUE`, etc.

### Validations

**Field-Level Validations**:
- **bacen_cod_inst**: Required, must exist in `bacen_institution` table
- **reason**: Required, must be valid enum value

**Business Rule Validations**:
- **Duplicate Prevention**: Cannot add restriction if already active for institution
- **Existence Check**: Institution must exist before restriction can be added

## Non-Functional Requirements

### Security

**Permissions**:
- No API permissions needed - restriction data is public (part of institution response)
- Database modifications require direct database access (admin-only)

**Authorization Checks**:
- Public users can see restriction status (it's part of the response)
- Database access is controlled via infrastructure (not application-level)

**Audit Trail**:
- `created_at`, `updated_at`, `created_by` columns in database table
- No application-level audit events (database-level audit is sufficient)

### Observability

**Key Performance Indicators (KPIs)**:
- **Restricted Institution Count**: Total number of restricted institutions
- **Restriction Lookup Latency**: Time to check restriction status (<5ms target)

**Metrics to Track**:
- `financial_data.restriction.total`: Gauge of total active restrictions
- `financial_data.restriction.checks`: Counter of restriction lookups

## Definitions

### Domain Terms
- **Data Restriction**: A flag indicating that an institution's financial data should not be exposed
- **Reason Code**: Enum value explaining why data is restricted
- **bacen_cod_inst**: BACEN institution identifier (primary key in BACEN data)

### Data Models

**Entity: InstitutionDataRestriction**
- `id` (UUID): Primary key
- `bacen_cod_inst` (TEXT): Institution identifier (unique constraint)
- `reason` (TEXT/ENUM): Reason code for restriction
- `notes` (TEXT, nullable): Internal notes about the restriction
- `is_active` (BOOLEAN): Whether restriction is currently active
- `created_at` (TIMESTAMPTZ): When restriction was created
- `updated_at` (TIMESTAMPTZ): When restriction was last modified
- `created_by` (TEXT, nullable): Who created the restriction

**Enum: DataRestrictionReason**
- `INSTITUTION_REQUEST`: Institution formally requested data removal
  - User-facing message (PT-BR): "Dado não disponível. Razão: A instituição solicitou a remoção dos seus dados"

### API Contracts

**GraphQL Type: DataRestriction**
```graphql
type DataRestriction {
  isRestricted: Boolean!
  reason: DataRestrictionReason
}

enum DataRestrictionReason {
  INSTITUTION_REQUEST
}
```

**Frontend Message Mapping** (in component or constants file):
```typescript
const RESTRICTION_MESSAGES: Record<DataRestrictionReason, string> = {
  INSTITUTION_REQUEST: 'Dado não disponível. Razão: A instituição solicitou a remoção dos seus dados',
};
```

**Updated Types**:
```graphql
type BacenFinanceInstitution {
  # ... existing fields ...
  dataRestriction: DataRestriction
}

type BacenInstitutionSummaryItem {
  # ... existing fields ...
  dataRestriction: DataRestriction
}
```

## Implementation Details

### Dependencies
- **Internal Dependencies**:
  - `financial_data` schema tables
  - GraphQL federation setup
  - Existing institution search and detail services
- **External Dependencies**: None

### Data Requirements

**Database Changes**:

```sql
-- New table for data restrictions
CREATE TABLE financial_data.institution_data_restriction (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    bacen_cod_inst TEXT NOT NULL UNIQUE,
    reason TEXT NOT NULL,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Constraint: reason must be valid enum value
    CONSTRAINT chk_reason CHECK (reason IN ('INSTITUTION_REQUEST'))
);

-- Index for quick lookups
CREATE INDEX idx_institution_data_restriction_active
    ON financial_data.institution_data_restriction(bacen_cod_inst)
    WHERE is_active = TRUE;

-- Comments
COMMENT ON TABLE financial_data.institution_data_restriction IS
    'Stores restrictions on institution financial data exposure. Institutions in this table (with is_active=true) will not have their financial data exposed via API.';
```

**Migration File**: `V1_X__create_institution_data_restriction.sql`

### Integration Points

**Service Integrations**:
- `BacenInstitutionDetailService`: Add restriction check before returning financial data
- `BacenInstitutionRepositoryImpl`: Join with restriction table in search queries

**API Integrations**: None (internal feature)

**Event Integrations**: None initially (could add Kafka events for audit trail later)

## Implementation Tasks

### Phase 1: Database Layer

**Task 1.1: Create Migration File**
- Create `V1_X__create_institution_data_restriction.sql`
- Define table with all columns, constraints, and indexes
- Add table and column comments
- **Location**: `service/kotlin/financialdata/src/main/resources/db/migration/`

**Task 1.2: Create Entity Class**
- Create `InstitutionDataRestrictionEntity.kt`
- Map all columns with JPA annotations
- Use `@Id` with UUID type
- **Location**: `service/kotlin/financialdata/src/main/kotlin/.../model/`

**Task 1.3: Create Repository**
- Create `InstitutionDataRestrictionRepository.kt` interface
- Add method: `findActiveByBacenCodInst(bacenCodInst: String): InstitutionDataRestrictionEntity?`
- Add method: `findActiveByBacenCodInstIn(bacenCodInsts: List<String>): List<InstitutionDataRestrictionEntity>`
- **Location**: `service/kotlin/financialdata/src/main/kotlin/.../repo/`

### Phase 2: Domain Layer

**Task 2.1: Create Domain Models**
- Create `DataRestriction.kt` data class with `isRestricted: Boolean` and `reason: DataRestrictionReason?`
- Create `DataRestrictionReason.kt` enum with `INSTITUTION_REQUEST`
- **Location**: `service/kotlin/financialdata/src/main/kotlin/.../domain/`

**Task 2.2: Create Mapper**
- Create mapper function to convert entity to domain model
- Handle null cases (no restriction = not restricted)
- **Location**: Can be inline or in mapper package

### Phase 3: Service Layer

**Task 3.1: Create Restriction Service**
- Create `InstitutionDataRestrictionService.kt`
- Method: `isRestricted(bacenCodInst: String): DataRestriction`
- Method: `getRestrictionsForInstitutions(bacenCodInsts: List<String>): Map<String, DataRestriction>`
- Simple database lookups (no caching needed - restrictions are rare and queries are fast with index)
- **Location**: `service/kotlin/financialdata/src/main/kotlin/.../service/`

**Task 3.2: Update BacenInstitutionDetailService**
- Inject `InstitutionDataRestrictionService`
- Check restriction before returning financial data
- If restricted: return institution metadata only, null out financial data
- Add `dataRestriction` field to response
- **Location**: `service/kotlin/financialdata/src/main/kotlin/.../service/BacenInstitutionDetailService.kt`

**Task 3.3: Update Institution Search Service**
- Add restriction lookup for search results (batch query for performance)
- Include `dataRestriction` field in summary items
- Null out financial metrics (profit, equity, etc.) for restricted institutions
- **Location**: Repository implementation files

### Phase 4: GraphQL Layer

**Task 4.1: Update GraphQL Schema**
- Add `DataRestriction` type
- Add `DataRestrictionReason` enum
- Add `dataRestriction: DataRestriction` field to `BacenFinanceInstitution`
- Add `dataRestriction: DataRestriction` field to `BacenInstitutionSummaryItem`
- **Location**: `service/kotlin/financialdata/src/main/resources/graphql/schema.graphqls`

**Task 4.2: Update DTOs**
- Add `dataRestriction` field to `BacenFinanceInstitutionDTO`
- Add `dataRestriction` field to `BacenInstitutionSummaryItemDTO`
- Create `DataRestrictionDTO.kt`
- **Location**: DTO package

**Task 4.3: Update Resolvers**
- Update `BacenInstitutionDetailResolver` to include restriction data
- Update search resolvers to include restriction data
- **Location**: Resolver package

### Phase 5: Frontend Layer

**Task 5.1: Update GraphQL Queries**
- Add `dataRestriction { isRestricted reason }` to institution detail query
- Add `dataRestriction { isRestricted reason }` to institution search query
- Regenerate TypeScript types
- **Location**: `web/src/` GraphQL query files

**Task 5.2: Create Restriction Notice Component**
- Create `DataRestrictionNotice.tsx` component
- Display user-facing message based on reason code:
  - `INSTITUTION_REQUEST` → "Dado não disponível. Razão: A instituição solicitou a remoção dos seus dados"
- Style as alert/notice banner (consider warning or info variant)
- **Location**: `web/src/widgets/financial-data/` or shared components

**Task 5.3: Update Institution Detail Page**
- Check `dataRestriction.isRestricted` flag
- If restricted: hide financial data sections, show notice
- Still show: name, logo, basic metadata, website, social links
- **Location**: `web/src/app/financial-data/bacen-institutions/[bacen-cod-inst]/page.tsx`

**Task 5.4: Update Institution Card Component**
- Show visual indicator for restricted institutions
- Consider: muted styling, badge, or icon
- Hide financial metrics display
- **Location**: `web/src/widgets/financial-data/BacenInstitutionCard/`

**Task 5.5: Update Search Results Page**
- Handle restricted institutions in list view
- Show appropriate indicator
- **Location**: `web/src/app/financial-data/bacen-institutions/page.tsx`

### Phase 6: Testing

**Task 6.1: Unit Tests - Service Layer**
- Test `InstitutionDataRestrictionService` methods
- Test restriction check logic in detail service
- Mock repository responses
- **Location**: `service/kotlin/financialdata/src/test/kotlin/.../unit/`

**Task 6.2: Unit Tests - Repository Layer**
- Test repository queries with restrictions
- Test batch lookup method
- **Location**: `service/kotlin/financialdata/src/test/kotlin/.../unit/`

**Task 6.3: Integration Tests**
- Test full flow: add restriction -> query institution -> verify no financial data
- Test search with mix of restricted/unrestricted institutions
- **Location**: `service/kotlin/financialdata/src/test/kotlin/.../integration/`

**Task 6.4: Frontend Tests**
- Test restriction notice component rendering
- Test institution detail page with restricted institution
- Test search results with restricted institutions
- **Location**: Frontend test files

### Phase 7: Operational Documentation

**Task 7.1: Create SQL Templates for Operations**
- Document SQL commands for common operations
- Store in runbook or operations documentation

**Add Restriction:**
```sql
INSERT INTO financial_data.institution_data_restriction
    (bacen_cod_inst, reason, notes, created_by)
VALUES
    ('12345678', 'INSTITUTION_REQUEST', 'Requested via email on 2024-01-15, ticket #ABC-123', 'admin@invistus.com');
```

**Remove Restriction (soft delete):**
```sql
UPDATE financial_data.institution_data_restriction
SET is_active = FALSE, updated_at = now()
WHERE bacen_cod_inst = '12345678';
```

**Query Active Restrictions:**
```sql
SELECT bacen_cod_inst, reason, notes, created_at, created_by
FROM financial_data.institution_data_restriction
WHERE is_active = TRUE
ORDER BY created_at DESC;
```

## Testing Strategy

**Test Coverage Requirements**:
- Unit tests: Service logic, repository queries, mappers
- Integration tests: Full API flow with database
- E2E tests: User journey from search to detail page

**Feature-Specific Test Scenarios**:
- Query restricted institution detail -> returns metadata, no financial data
- Query unrestricted institution detail -> returns all data
- Search with mixed results -> restricted shown with indicator
- Add restriction -> subsequent queries return no financial data
- Remove restriction -> financial data returns

## Code References

**Key Files**:
- **Backend Service**: `service/kotlin/financialdata/src/main/kotlin/.../service/BacenInstitutionDetailService.kt`
- **Backend Repository**: `service/kotlin/financialdata/src/main/kotlin/.../repo/BacenInstitutionDetailRepositoryImpl.kt`
- **GraphQL Schema**: `service/kotlin/financialdata/src/main/resources/graphql/schema.graphqls`
- **Database Migrations**: `service/kotlin/financialdata/src/main/resources/db/migration/`
- **Frontend Detail Page**: `web/src/app/financial-data/bacen-institutions/[bacen-cod-inst]/page.tsx`
- **Frontend Card**: `web/src/widgets/financial-data/BacenInstitutionCard/BacenInstitutionCard.tsx`

## Design Decisions

1. **No Admin API**: Restrictions are managed directly via database SQL. This is an extremely rare event, so no API overhead is needed.

2. **User-Facing Reason Display**: The reason is displayed to end users in Portuguese:
   - `INSTITUTION_REQUEST` → "Dado não disponível. Razão: A instituição solicitou a remoção dos seus dados"

3. **No Cache Invalidation**: Given the rarity of restriction changes, no real-time cache invalidation is implemented. Service restart or natural cache expiry handles updates.

## References

- **Related Features**: [BACEN IFData Ingestion](../bacen-ifdata-ingestion/bacen-ifdata-ingestion.md)
- **Patterns**: [Repository Pattern](../../../../05-backend/patterns/repository-pattern.md)
- **Standards**: [GraphQL Standards](../../../../06-contracts/graphql-standards.md)

## Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2026-02-02 | Claude | Initial version |
