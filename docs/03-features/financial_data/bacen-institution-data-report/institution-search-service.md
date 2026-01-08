---
title: Institution Search Service Layer
type: feature
category: financial_data
status: draft
version: 1.0.0
tags: [service, repository, graphql, search, pagination, financial-data]
ai_optimized: true
search_keywords: [institution, search, autocomplete, bacen_institution, bank_report, relay, pagination]
related:
  - 03-features/financial_data/repository-requirements.md
  - 04-patterns/backend-patterns/service-pattern.md
  - 04-patterns/backend-patterns/repository-pattern.md
  - 04-patterns/backend-patterns/resolver-pattern.md
  - 04-patterns/backend-patterns/pagination-pattern.md
---

# Institution Search Service Layer

> **Purpose**: Service layer outline for implementing GraphQL queries to search financial institutions with autocomplete and full search capabilities, including financial metrics aggregation.

## Overview

This document outlines the service layer implementation for two main GraphQL queries:

1. **Autocomplete Search** (`searchBacenInstitutionsAutocomplete`) - Real-time suggestions as user types
2. **Full Search** (`searchBacenInstitutions`) - Complete search with financial metrics and Relay pagination

Both queries search `BacenInstitutionEntity` data, with the full search query aggregating financial metrics from `BacenBankReportEntity` and `BacenBankReportDataEntity`.

## GraphQL Queries

### Query A: Autocomplete Search

**Query Name**: `searchBacenInstitutionsAutocomplete`

**Purpose**: Provides real-time suggestions as the user types in the search bar.

**Inputs**:
- `query: String!` - The text the user is typing (can be name, CNPJ, code, etc.)

**Response**:
```graphql
type BacenInstitutionAutocompleteConnection {
  suggestions: [BacenInstitutionAutocompleteItem!]!
}

type BacenInstitutionAutocompleteItem {
  id: ID!
  name: String!  # nome_instituicao from bacen_institution
  cnpj: String
  logoUrl: String
}
```

**Characteristics**:
- No pagination (limited to top N results, e.g., 10-20)
- Fast response time (< 100ms target)
- Case-insensitive partial matching
- Searches: `bacen_institution.nome_instituicao`, `bacen_institution.bacen_cod_inst` (non-C prefix)

### Query B: Full Search

**Query Name**: `searchBacenInstitutions`

**Purpose**: Complete search with financial metrics, supporting Relay pagination.

**Inputs**:
- `query: String` - Optional search query (name, code)
- `first: Int` - Page size (default: 20, max: 100)
- `after: String` - Cursor for pagination

**Response**:
```graphql
type BacenInstitutionConnection {
  edges: [BacenInstitutionEdge!]!
  pageInfo: PageInfo!
  totalCount: Int
}

type BacenInstitutionEdge {
  node: BacenInstitutionSummaryItem!
  cursor: String!
}

type BacenInstitutionSummaryItem {
  id: ID!
  name: String!
  logoUrl: String
  profit: Float  # Lucro - formatted as R$ Xbn/tn
  equity: Float  # Patrimonio - formatted as R$ Xbn/tn
  yoyGrowth: Float  # Crescimento YoY - in %, with sign and color coding
  basileiaIndex: Float  # Indice Basileia - with value and status indicator
  bacenInstitutionType: String  # BACEN institution type
}
```

**Characteristics**:
- Relay pagination (cursor-based)
- Full text search on institution name and code
- Aggregates financial metrics from latest `bacen_bank_report` data using specific account codes
- Returns `totalCount` for filtered results
- Results are ordered by `bacen_cod_inst` (deterministic ordering for pagination)

## Architecture Layers

### Domain Layer

#### Domain Objects

**BacenInstitutionAutocomplete** (domain object for autocomplete results):
```kotlin
package io.github.salomax.neotool.financialdata.domain

data class BacenInstitutionAutocomplete(
    val id: String,  // bacen_cod_inst
    val name: String,  // nome_instituicao from bacen_institution
    val cnpj: String?,  // cnpj_instituicao_lider (if available)
    val logoUrl: String?,  // Derived from financial_institution.logo_storage_key (if linked)
)
```

**BacenInstitutionSummary** (domain object for full search results):
```kotlin
package io.github.salomax.neotool.financialdata.domain

import java.math.BigDecimal

data class BacenInstitutionSummary(
    val id: String,  // bacen_cod_inst
    val name: String,  // nome_instituicao from bacen_institution
    val logoUrl: String?,
    val profit: BigDecimal?,  // Lucro Líquido (conta 78187) from bacen_bank_report_data
    val equity: BigDecimal?,  // Patrimônio Líquido (conta 78186) from bacen_bank_report_data
    val yoyGrowth: BigDecimal?,  // Crescimento YoY calculation
    val basileiaIndex: BigDecimal?,  // Indice Basileia calculation
    val bacenInstitutionType: Int?,  // tipo_instituicao from bacen_bank_report
)
```

### Repository Layer

#### Custom Repository Interface

**BacenInstitutionRepositoryCustom**:
```kotlin
package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.model.BacenInstitutionEntity

/**
 * Custom query contract for BacenInstitution search operations.
 * Declared separately so Micronaut Data can generate the default repository implementation
 * while these methods are provided via a manual bean.
 */
interface BacenInstitutionRepositoryCustom {
    /**
     * Search institutions for autocomplete suggestions.
     * Performs case-insensitive partial matching on nome_instituicao and bacen_cod_inst.
     * Returns limited results (e.g., top 20) for fast autocomplete response.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param limit Maximum number of results to return (default: 20)
     * @return List of matching BACEN institutions for autocomplete
     */
    fun searchBacenInstitutionsAutocomplete(
        query: String,
        limit: Int = 20,
    ): List<BacenInstitutionEntity>

    /**
     * Search institutions with full text search and financial metrics aggregation.
     * Performs case-insensitive partial matching on nome_instituicao and bacen_cod_inst.
     * When query is null or empty, returns all institutions (list behavior).
     * Aggregates financial metrics from latest bacen_bank_report data using specific account codes.
     * Results are ordered by bacen_cod_inst for deterministic pagination.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, returns all institutions.
     * @param first Maximum number of results to return
     * @param after Composite cursor to start after (exclusive), or null for first page
     * @return List of matching institutions with aggregated financial metrics
     */
    fun searchBacenInstitutions(
        query: String?,
        first: Int,
        after: CompositeCursor?,
    ): List<InstitutionSearchResult>

    /**
     * Count institutions matching the search query.
     * Performs case-insensitive partial matching on nome_instituicao and bacen_cod_inst.
     * When query is null or empty, returns total count of all institutions.
     * This count matches the same WHERE clause as searchBacenInstitutions but without pagination.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, counts all institutions.
     * @return Total count of matching institutions (or all institutions if query is null/empty)
     */
    fun countBacenInstitutions(query: String?): Long
}
```

**InstitutionSearchResult** (DTO for repository results):
```kotlin
package io.github.salomax.neotool.financialdata.repo

import java.math.BigDecimal

/**
 * Result DTO from institution search query with aggregated financial metrics.
 * This is returned by the custom repository implementation to avoid complex entity relationships.
 */
data class InstitutionSearchResult(
    // Institution identification
    val bacenCodInst: String,
    val nomeInstituicao: String?,
    val cnpjInstituicaoLider: String?,
    
    // Logo (from financial_institution if linked)
    val logoStorageKey: String?,
    
    // Financial metrics (aggregated from bacen_bank_report_data using specific account codes)
    val profit: BigDecimal?,  // Lucro Líquido (conta 78187)
    val equity: BigDecimal?,  // Patrimônio Líquido (conta 78186)
    val yoyGrowth: BigDecimal?,  // Crescimento YoY calculation
    val basileiaIndex: BigDecimal?,  // Indice Basileia calculation
    val bacenInstitutionType: Int?,  // tipo_instituicao from bacen_bank_report
)
```

#### Repository Implementation

**BacenInstitutionRepositoryImpl**:
- Uses Micronaut Query Builder (EntityManager + JPA Criteria API)
- Implements complex JOINs between `bacen_institution`, `bacen_bank_report`, `bacen_bank_report_data`, and `bacen_bank_report_account`
- Optionally LEFT JOINs with `financial_institution` for logo data
- Builds dynamic WHERE clauses based on query parameter
- Implements cursor-based pagination using `CompositeCursor` (based on `bacen_cod_inst`)
- Aggregates financial metrics using SQL aggregation functions with specific account codes
- All read-only methods annotated with `@ReadOnly`

**Key Implementation Details**:
1. **JOIN Strategy**:
   - Base table: `bacen_institution`
   - LEFT JOIN `bacen_bank_report` on `bacen_bank_report.cod_inst = bacen_institution.bacen_cod_inst` (latest report only, using MAX(`ano_mes`))
   - LEFT JOIN `bacen_bank_report_data` on `bacen_bank_report_data.bank_report_id = bacen_bank_report.id`
   - LEFT JOIN `bacen_bank_report_account` on `bacen_bank_report_data.account_id = bacen_bank_report_account.id`
   - LEFT JOIN `financial_institution` on `financial_institution.parent_cnpj = bacen_institution.cnpj_instituicao_lider` (for logo)

2. **Search Fields**:
   - `bacen_institution.nome_instituicao` (ILIKE)
   - `bacen_institution.bacen_cod_inst` (ILIKE, but exclude codes starting with 'C')

3. **Financial Metrics Aggregation** (using specific account codes):
   - Profit (Lucro Líquido): SUM of `bacen_bank_report_data.saldo` WHERE `bacen_bank_report_account.conta = '78187'`
   - Equity (Patrimônio Líquido): SUM of `bacen_bank_report_data.saldo` WHERE `bacen_bank_report_account.conta = '78186'`
   - Resultado Operacional: SUM of `bacen_bank_report_data.saldo` WHERE `bacen_bank_report_account.conta = '78225'`
   - Ativo Total: SUM of `bacen_bank_report_data.saldo` WHERE `bacen_bank_report_account.conta = '78182'`
   - YoY Growth: Comparison of current period vs same period previous year (calculation TBD)
   - Basileia Index: Calculation from capital adequacy ratio (calculation TBD)
   - Latest report: Use MAX(`bacen_bank_report.ano_mes`) per institution

4. **Pagination**:
   - Uses `CompositeCursor` with single field: `bacen_cod_inst`
   - Results ordered by `bacen_cod_inst ASC` for deterministic ordering
   - Cursor encodes the `bacen_cod_inst` value

### Service Layer

#### Service Interface

**InstitutionSearchService**:
```kotlin
package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionAutocomplete
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionSummary

/**
 * Service for searching financial institutions with autocomplete and full search capabilities.
 */
@Singleton
open class InstitutionSearchService(
    private val bacenInstitutionRepository: BacenInstitutionRepository,
    private val institutionSearchRepository: BacenInstitutionRepositoryCustom,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Search institutions for autocomplete suggestions.
     * Returns limited results for fast response time.
     *
     * @param query Search query (required, non-empty)
     * @param limit Maximum number of results (default: 20)
     * @return List of institution autocomplete suggestions
     * @throws IllegalArgumentException if query is null or empty
     */
    fun searchBacenInstitutionsAutocomplete(
        query: String,
        limit: Int = 20,
    ): List<BacenInstitutionAutocomplete> {
        // Validate query
        if (query.isBlank()) {
            throw IllegalArgumentException("Query cannot be empty for autocomplete search")
        }

        // Query repository
        val entities = institutionSearchRepository.searchBacenInstitutionsAutocomplete(query.trim(), limit)
        
        // Convert to domain
        return entities.map { entity ->
            BacenInstitutionAutocomplete(
                id = entity.bacenCodInst,
                name = entity.nomeInstituicao ?: "Unknown",
                cnpj = entity.cnpjInstituicaoLider,
                logoUrl = null, // TODO: Join with financial_institution for logo
            )
        }
    }

    /**
     * Search institutions with full text search, financial metrics, and Relay pagination.
     * Unified method that handles both list (no query) and search (with query) operations.
     * When query is null or empty, returns all institutions. When query is provided, returns filtered institutions.
     * totalCount is always calculated.
     * Results are ordered by bacen_cod_inst for deterministic pagination.
     *
     * @param query Optional search query (partial match, case-insensitive). If null or empty, returns all institutions.
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded composite cursor with bacen_cod_inst)
     * @return Connection containing paginated institutions with totalCount
     */
    fun searchBacenInstitutions(
        query: String?,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<BacenInstitutionSummary> {
        // Validate and normalize pagination
        val pageSize = when {
            first < 1 -> throw IllegalArgumentException("Parameter 'first' must be at least 1, got: $first")
            first > PaginationConstants.MAX_PAGE_SIZE -> throw IllegalArgumentException(
                "Parameter 'first' must be at most ${PaginationConstants.MAX_PAGE_SIZE}, got: $first"
            )
            else -> first
        }

        // Decode cursor
        val afterCompositeCursor: CompositeCursor? = after?.let {
            try {
                CursorEncoder.decodeCompositeCursor(it)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }
        }

        // Normalize query
        val normalizedQuery = if (query.isNullOrBlank()) null else query.trim()

        // Query repository (one extra to check for more results)
        val results = institutionSearchRepository.searchBacenInstitutions(
            normalizedQuery,
            pageSize + 1,
            afterCompositeCursor,
        )
        val hasMore = results.size > pageSize

        // Always get total count
        val totalCount = institutionSearchRepository.countBacenInstitutions(normalizedQuery)

        // Take only requested number and convert to domain
        val institutions = results
            .take(pageSize)
            .map { result ->
                BacenInstitutionSummary(
                    id = result.bacenCodInst,
                    name = result.nomeInstituicao ?: "Unknown",
                    logoUrl = result.logoStorageKey?.let { deriveLogoUrl(it) },
                    profit = result.profit,
                    equity = result.equity,
                    yoyGrowth = result.yoyGrowth,
                    basileiaIndex = result.basileiaIndex,
                    bacenInstitutionType = result.bacenInstitutionType,
                )
            }

        // Build connection with composite cursor encoding (using bacen_cod_inst)
        return ConnectionBuilder.buildConnection(
            items = institutions,
            hasMore = hasMore,
            encodeCursor = { institution ->
                CursorEncoder.encodeCompositeCursor(
                    CompositeCursor(
                        fieldValues = mapOf("id" to institution.id),
                        id = institution.id,
                    )
                )
            },
            totalCount = totalCount,
        )
    }

    /**
     * Derive logo URL from storage key.
     * TODO: Integrate with asset management service.
     */
    private fun deriveLogoUrl(storageKey: String): String {
        // Placeholder: return storage key as URL
        // Future: integrate with asset management service to get actual URL
        return "/assets/logos/$storageKey"
    }
}
```

### GraphQL Layer

#### Resolver

**InstitutionSearchResolver**:
```kotlin
package io.github.salomax.neotool.financialdata.graphql.resolver

import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.BacenInstitutionConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.InstitutionSearchMapper
import io.github.salomax.neotool.financialdata.service.InstitutionSearchService
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * GraphQL resolver for institution search operations.
 * Provides autocomplete and full search queries with Relay pagination.
 */
@Singleton
class InstitutionSearchResolver(
    private val institutionSearchService: InstitutionSearchService,
    private val mapper: InstitutionSearchMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Autocomplete search for institutions.
     * Returns suggestions as user types.
     *
     * @param query Search query (required)
     * @return BacenInstitutionAutocompleteConnectionDTO
     * @throws IllegalArgumentException if query is null or empty
     */
    fun searchBacenInstitutionsAutocomplete(
        query: String,
    ): BacenInstitutionAutocompleteConnectionDTO {
        // Validate query
        if (query.isBlank()) {
            throw IllegalArgumentException("Query cannot be empty for autocomplete search")
        }

        // Call service
        val suggestions = institutionSearchService.searchBacenInstitutionsAutocomplete(query)
        
        // Convert to DTO
        return mapper.toBacenInstitutionAutocompleteConnectionDTO(suggestions)
    }

    /**
     * Full search for institutions with financial metrics and Relay pagination.
     *
     * @param query Optional search query
     * @param first Maximum number of results (default: 20, min: 1, max: 100)
     * @param after Cursor for pagination
     * @return BacenInstitutionConnectionDTO
     * @throws IllegalArgumentException if first is invalid
     */
    fun searchBacenInstitutions(
        query: String?,
        first: Int?,
        after: String?,
    ): BacenInstitutionConnectionDTO {
        // Validate pagination parameter
        val pageSize = when {
            first == null -> PaginationConstants.DEFAULT_PAGE_SIZE
            first < 1 -> throw IllegalArgumentException("Parameter 'first' must be at least 1, got: $first")
            first > PaginationConstants.MAX_PAGE_SIZE -> throw IllegalArgumentException(
                "Parameter 'first' must be at most ${PaginationConstants.MAX_PAGE_SIZE}, got: $first"
            )
            else -> first
        }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val connection = institutionSearchService.searchBacenInstitutions(
            query,
            pageSize,
            after,
        )
        
        // Convert to DTO
        return mapper.toBacenInstitutionConnectionDTO(connection)
    }
}
```

#### Mapper

**InstitutionSearchMapper**:
```kotlin
package io.github.salomax.neotool.financialdata.graphql.mapper

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionAutocomplete
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionSummary
import io.github.salomax.neotool.financialdata.graphql.dto.*
import jakarta.inject.Singleton

@Singleton
class InstitutionSearchMapper {
    fun toBacenInstitutionAutocompleteConnectionDTO(
        suggestions: List<BacenInstitutionAutocomplete>,
    ): BacenInstitutionAutocompleteConnectionDTO {
        return BacenInstitutionAutocompleteConnectionDTO(
            suggestions = suggestions.map { suggestion ->
                BacenInstitutionAutocompleteItemDTO(
                    id = suggestion.id,
                    name = suggestion.name,
                    cnpj = suggestion.cnpj,
                    logoUrl = suggestion.logoUrl,
                )
            },
        )
    }

    fun toBacenInstitutionConnectionDTO(
        connection: Connection<BacenInstitutionSummary>,
    ): BacenInstitutionConnectionDTO {
        return BacenInstitutionConnectionDTO(
            edges = connection.edges.map { edge ->
                BacenInstitutionEdgeDTO(
                    node = toBacenInstitutionSummaryItemDTO(edge.node),
                    cursor = edge.cursor,
                )
            },
            pageInfo = PageInfoDTO(
                hasNextPage = connection.pageInfo.hasNextPage,
                hasPreviousPage = connection.pageInfo.hasPreviousPage,
                startCursor = connection.pageInfo.startCursor,
                endCursor = connection.pageInfo.endCursor,
            ),
            totalCount = connection.totalCount?.toInt(),
        )
    }

    fun toBacenInstitutionSummaryItemDTO(
        summary: BacenInstitutionSummary,
    ): BacenInstitutionSummaryItemDTO {
        return BacenInstitutionSummaryItemDTO(
            id = summary.id,
            name = summary.name,
            logoUrl = summary.logoUrl,
            profit = summary.profit?.toDouble(),
            equity = summary.equity?.toDouble(),
            yoyGrowth = summary.yoyGrowth?.toDouble(),
            basileiaIndex = summary.basileiaIndex?.toDouble(),
            bacenInstitutionType = summary.bacenInstitutionType?.toString(),
        )
    }

}
```

#### DTOs

**BacenInstitutionAutocompleteConnectionDTO**:
```kotlin
package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Introspected
@Serdeable
data class BacenInstitutionAutocompleteConnectionDTO(
    val suggestions: List<BacenInstitutionAutocompleteItemDTO>,
)

@Introspected
@Serdeable
data class BacenInstitutionAutocompleteItemDTO(
    val id: String,
    val name: String,
    val cnpj: String?,
    val logoUrl: String?,
)
```

**BacenInstitutionConnectionDTO**:
```kotlin
package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Introspected
@Serdeable
data class BacenInstitutionConnectionDTO(
    val edges: List<BacenInstitutionEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int?,
)

@Introspected
@Serdeable
data class BacenInstitutionEdgeDTO(
    val node: BacenInstitutionSummaryItemDTO,
    val cursor: String,
)

@Introspected
@Serdeable
data class BacenInstitutionSummaryItemDTO(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val profit: Double?,
    val equity: Double?,
    val yoyGrowth: Double?,
    val basileiaIndex: Double?,
    val bacenInstitutionType: String?,
)

@Introspected
@Serdeable
data class PageInfoDTO(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String?,
    val endCursor: String?,
)
```

## Implementation Phases

### Phase 1: Repository Layer (Custom Repository Implementation)

**Tasks**:
1. Create `BacenInstitutionRepositoryCustom` interface
2. Create `InstitutionSearchResult` DTO
3. Create `BacenInstitutionRepositoryImpl` with:
   - `searchAutocomplete()` method (simple query, no metrics)
   - `searchBacenInstitutions()` method (complex JOIN with metrics aggregation)
   - `countInstitutions()` method
4. Implement JOIN logic between `bacen_institution`, `bacen_bank_report`, `bacen_bank_report_data`, `bacen_bank_report_account`, and optionally `financial_institution`
5. Implement search filter predicate builder
6. Implement cursor-based pagination with `CompositeCursor` (using `bacen_cod_inst`)
7. Implement financial metrics aggregation using specific account codes:
   - Profit (Lucro Líquido): conta 78187
   - Equity (Patrimônio Líquido): conta 78186
   - Resultado Operacional: conta 78225
   - Ativo Total: conta 78182
8. Add unit tests for repository methods

**Dependencies**:
- `BacenInstitutionEntity`
- `BacenBankReportEntity`
- `BacenBankReportDataEntity`
- `BacenBankReportAccountEntity` (via JOIN)
- `FinancialInstitutionEntity` (optional, for logo)

### Phase 2: Domain Layer

**Tasks**:
1. Create `BacenInstitutionAutocomplete` domain object
2. Create `BacenInstitutionSummary` domain object
3. Add conversion methods from repository DTOs to domain objects

### Phase 3: Service Layer

**Tasks**:
1. Create `InstitutionSearchService` with:
   - `searchAutocomplete()` method
   - `searchBacenInstitutions()` method
2. Implement domain conversion
3. Implement cursor encoding/decoding
4. Implement connection building
5. Add logging and error handling
6. Add unit tests for service methods

**Dependencies**:
- `BacenInstitutionRepositoryCustom`
- Domain objects
- Pagination utilities (`ConnectionBuilder`, `CursorEncoder`)

### Phase 4: GraphQL Layer

**Tasks**:
1. Create `InstitutionSearchResolver`
2. Create `InstitutionSearchMapper`
3. Create DTOs:
   - `BacenInstitutionAutocompleteConnectionDTO`
   - `BacenInstitutionAutocompleteItemDTO`
   - `BacenInstitutionConnectionDTO`
   - `BacenInstitutionEdgeDTO`
   - `BacenInstitutionSummaryItemDTO`
   - `PageInfoDTO`
4. Implement GraphQL schema definitions
5. Wire resolvers in `GraphQLWiringFactory`
6. Add integration tests for GraphQL queries

**Dependencies**:
- `InstitutionSearchService`
- GraphQL wiring factory

### Phase 5: Financial Metrics Implementation

**Tasks**:
1. Implement SQL aggregation for specific account codes:
   - Profit (Lucro Líquido): SUM of `saldo` WHERE `conta = '78187'`
   - Equity (Patrimônio Líquido): SUM of `saldo` WHERE `conta = '78186'`
   - Resultado Operacional: SUM of `saldo` WHERE `conta = '78225'`
   - Ativo Total: SUM of `saldo` WHERE `conta = '78182'`
2. Define detailed formulas for:
   - YoY Growth calculation (comparison of current period vs same period previous year)
   - Basileia Index calculation (from capital adequacy ratio)
3. Update repository implementation with actual SQL aggregation using account codes
4. Add unit tests for metric calculations
5. Add integration tests with sample data

**Note**: YoY Growth and Basileia Index calculations depend on detailed requirements which will be provided later.

## Database Considerations

### Indexes

Ensure appropriate indexes exist for:
- `bacen_institution.bacen_cod_inst` (PRIMARY KEY)
- `bacen_institution.nome_instituicao` (for search)
- `bacen_institution.cnpj_instituicao_lider` (for JOIN with financial_institution)
- `bacen_bank_report.cod_inst` and `bacen_bank_report.ano_mes` (for metrics calculation)
- `bacen_bank_report_data.bank_report_id` (for JOIN)
- `bacen_bank_report_data.account_id` (for JOIN with bacen_bank_report_account)
- `bacen_bank_report_account.conta` (for filtering by account code)
- `financial_institution.parent_cnpj` (for JOIN with bacen_institution, if needed for logo)

### Performance

- Consider using materialized views for complex metric calculations
- Cache frequently accessed metric calculations
- Use appropriate JOIN types (LEFT JOIN vs INNER JOIN) based on requirements
- Optimize autocomplete query for fast response (< 100ms)

## Testing Requirements

### Unit Tests

**Repository Tests**:
- Test autocomplete search with various queries
- Test full search with null/empty query (returns all)
- Test full search with query matching different fields
- Test pagination with cursor (based on bacen_cod_inst)
- Test count method matches search results
- Test financial metrics aggregation using specific account codes (with mock data)

**Service Tests**:
- Test autocomplete service method
- Test full search service method
- Test cursor encoding/decoding
- Test connection building
- Test error handling

**Resolver Tests**:
- Test autocomplete resolver
- Test full search resolver
- Test pagination parameter validation
- Test error handling

### Integration Tests

- Test JOIN between tables
- Test metric calculations accuracy
- Test pagination across large datasets
- Test performance with realistic data volumes
- Test GraphQL query execution end-to-end

## Notes

- The `bacen_cod_inst` field is a String (TEXT in database) to preserve leading zeros
- The search is based on `BacenInstitutionEntity` - each result represents one BACEN institution record
- Financial metrics are aggregated from `bacen_bank_report_data` using specific account codes:
  - 78186: Patrimônio Líquido (Equity)
  - 78187: Lucro Líquido (Profit)
  - 78225: Resultado Operacional
  - 78182: Ativo Total
- The relationship between `bacen_institution` and `financial_institution` is via `cnpj_instituicao_lider` field (optional, for logo)
- Financial metrics require joining with `bacen_bank_report`, `bacen_bank_report_data`, and `bacen_bank_report_account` tables
- Autocomplete query should be optimized for speed (limited results, simple query)
- Full search query includes metrics aggregation with account code filtering
- Results are ordered by `bacen_cod_inst` for deterministic pagination (no custom sorting)
- Consider creating a view or materialized view for complex metric calculations if performance becomes an issue
- Logo URL derivation is placeholder - will integrate with asset management service later

## Related Documentation

- [Repository Requirements](./repository-requirements.md) - Repository layer requirements
- [Service Pattern](../../04-patterns/backend-patterns/service-pattern.md) - Service layer patterns
- [Repository Pattern](../../04-patterns/backend-patterns/repository-pattern.md) - Repository layer patterns
- [Resolver Pattern](../../04-patterns/backend-patterns/resolver-pattern.md) - GraphQL resolver patterns
- [Pagination Pattern](../../04-patterns/backend-patterns/pagination-pattern.md) - Relay pagination patterns

