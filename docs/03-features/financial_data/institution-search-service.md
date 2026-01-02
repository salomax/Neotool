---
title: Institution Search Service Layer
type: feature
category: financial_data
status: draft
version: 1.0.0
tags: [service, repository, graphql, search, pagination, financial-data]
ai_optimized: true
search_keywords: [institution, search, autocomplete, parent_institution, bank_report, relay, pagination]
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

1. **Autocomplete Search** (`searchInstitutionsAutocomplete`) - Real-time suggestions as user types
2. **Full Search** (`searchInstitutions`) - Complete search with financial metrics and Relay pagination

Both queries search `ParentInstitutionEntity` and related `InstitutionEntity` data, with the full search query aggregating financial metrics from `BankReportEntity` and `BankReportDataEntity`.

## GraphQL Queries

### Query A: Autocomplete Search

**Query Name**: `searchInstitutionsAutocomplete`

**Purpose**: Provides real-time suggestions as the user types in the search bar.

**Inputs**:
- `query: String!` - The text the user is typing (can be name, CNPJ, code, etc.)

**Response**:
```graphql
type InstitutionAutocompleteConnection {
  suggestions: [InstitutionAutocompleteItem!]!
}

type InstitutionAutocompleteItem {
  id: ID!
  name: String!  # Combined trade name and legal name
  cnpj: String!
  logoUrl: String
}
```

**Characteristics**:
- No pagination (limited to top N results, e.g., 10-20)
- Fast response time (< 100ms target)
- Case-insensitive partial matching
- Searches: `parent_institution.trade_name`, `parent_institution.legal_name`, `parent_institution.parent_cnpj`, `institution.nome_instituicao`, `institution.bacen_cod_inst` (non-C prefix)

### Query B: Full Search

**Query Name**: `searchInstitutions`

**Purpose**: Complete search with financial metrics, supporting Relay pagination.

**Inputs**:
- `query: String` - Optional search query (name, CNPJ, code)
- `first: Int` - Page size (default: 20, max: 100)
- `after: String` - Cursor for pagination
- `orderBy: [InstitutionOrderBy!]` - Optional sorting

**Response**:
```graphql
type InstitutionConnection {
  edges: [InstitutionEdge!]!
  pageInfo: PageInfo!
  totalCount: Int
}

type InstitutionEdge {
  node: InstitutionSummaryItem!
  cursor: String!
}

type InstitutionSummaryItem {
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
- Full text search on trade name, legal name, CNPJ
- Aggregates financial metrics from latest `bank_report` data
- Supports sorting by various fields
- Returns `totalCount` for filtered results

## Architecture Layers

### Domain Layer

#### Domain Objects

**InstitutionAutocomplete** (domain object for autocomplete results):
```kotlin
package io.github.salomax.neotool.financialdata.domain

data class InstitutionAutocomplete(
    val id: String,  // parent_cnpj or bacen_cod_inst
    val name: String,  // Combined: trade name or legal name
    val cnpj: String,  // parent_cnpj
    val logoUrl: String?,  // Derived from logo_storage_key
)
```

**InstitutionSummary** (domain object for full search results):
```kotlin
package io.github.salomax.neotool.financialdata.domain

import java.math.BigDecimal

data class InstitutionSummary(
    val id: String,  // parent_cnpj or bacen_cod_inst
    val name: String,  // Combined: trade name or legal name
    val logoUrl: String?,
    val profit: BigDecimal?,  // Lucro from bank_report_data
    val equity: BigDecimal?,  // Patrimonio from bank_report_data
    val yoyGrowth: BigDecimal?,  // Crescimento YoY calculation
    val basileiaIndex: BigDecimal?,  // Indice Basileia calculation
    val bacenInstitutionType: Int?,  // tipo_instituicao from bank_report
)
```

### Repository Layer

#### Custom Repository Interface

**ParentInstitutionRepositoryCustom**:
```kotlin
package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.financialdata.model.ParentInstitutionEntity

/**
 * Custom query contract for ParentInstitution search operations.
 * Declared separately so Micronaut Data can generate the default repository implementation
 * while these methods are provided via a manual bean.
 */
interface ParentInstitutionRepositoryCustom {
    /**
     * Search institutions for autocomplete suggestions.
     * Performs case-insensitive partial matching on trade_name, legal_name, CNPJ, nome_instituicao, and bacen_cod_inst.
     * Returns limited results (e.g., top 20) for fast autocomplete response.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param limit Maximum number of results to return (default: 20)
     * @return List of matching parent institutions for autocomplete
     */
    fun searchAutocomplete(
        query: String,
        limit: Int = 20,
    ): List<ParentInstitutionEntity>

    /**
     * Search institutions with full text search and financial metrics aggregation.
     * Performs case-insensitive partial matching on trade_name, legal_name, CNPJ, nome_instituicao, and bacen_cod_inst.
     * When query is null or empty, returns all institutions (list behavior).
     * Aggregates financial metrics from latest bank_report data.
     * Results are ordered according to the orderBy parameter.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, returns all institutions.
     * @param first Maximum number of results to return
     * @param after Composite cursor to start after (exclusive), or null for first page
     * @param orderBy List of order by specifications (must include ID as final sort)
     * @return List of matching institutions with aggregated financial metrics
     */
    fun searchInstitutions(
        query: String?,
        first: Int,
        after: CompositeCursor?,
        orderBy: List<InstitutionOrderBy>,
    ): List<InstitutionSearchResult>

    /**
     * Count institutions matching the search query.
     * Performs case-insensitive partial matching on trade_name, legal_name, CNPJ, nome_instituicao, and bacen_cod_inst.
     * When query is null or empty, returns total count of all institutions.
     * This count matches the same WHERE clause as searchInstitutions but without pagination.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, counts all institutions.
     * @return Total count of matching institutions (or all institutions if query is null/empty)
     */
    fun countInstitutions(query: String?): Long
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
    val parentCnpj: String,
    val bacenCodInst: String?,
    
    // Institution names
    val tradeName: String?,
    val legalName: String?,
    val nomeInstituicao: String?,
    
    // Logo
    val logoStorageKey: String?,
    
    // Financial metrics (aggregated from bank_report_data)
    val profit: BigDecimal?,
    val equity: BigDecimal?,
    val yoyGrowth: BigDecimal?,
    val basileiaIndex: BigDecimal?,
    val bacenInstitutionType: Int?,
)
```

#### Order By Enums

**InstitutionOrderField**:
```kotlin
package io.github.salomax.neotool.financialdata.service

enum class InstitutionOrderField {
    NAME,           // Sort by trade_name or legal_name
    CNPJ,           // Sort by parent_cnpj
    PROFIT,         // Sort by profit (lucro)
    EQUITY,         // Sort by equity (patrimonio)
    YOY_GROWTH,     // Sort by YoY growth
    BASILEIA,       // Sort by Basileia index
    ID,             // Sort by parent_cnpj (for deterministic ordering)
}
```

**InstitutionOrderBy**:
```kotlin
package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.common.graphql.pagination.OrderDirection

data class InstitutionOrderBy(
    val field: InstitutionOrderField,
    val direction: OrderDirection,
)
```

#### Repository Implementation

**ParentInstitutionRepositoryImpl**:
- Uses Micronaut Query Builder (EntityManager + JPA Criteria API)
- Implements complex JOINs between `parent_institution`, `institution`, `bank_report`, and `bank_report_data`
- Builds dynamic WHERE clauses based on query parameter
- Implements cursor-based pagination using `CompositeCursor`
- Aggregates financial metrics using SQL aggregation functions
- All read-only methods annotated with `@ReadOnly`

**Key Implementation Details**:
1. **JOIN Strategy**:
   - LEFT JOIN `institution` on `institution.cnpj_instituicao_lider = parent_institution.parent_cnpj`
   - LEFT JOIN `bank_report` on `bank_report.cod_inst = institution.bacen_cod_inst` (latest report only)
   - LEFT JOIN `bank_report_data` on `bank_report_data.bank_report_id = bank_report.id`

2. **Search Fields**:
   - `parent_institution.trade_name` (ILIKE)
   - `parent_institution.legal_name` (ILIKE)
   - `parent_institution.parent_cnpj` (ILIKE)
   - `institution.nome_instituicao` (ILIKE)
   - `institution.bacen_cod_inst` (ILIKE, but exclude codes starting with 'C')

3. **Financial Metrics Aggregation**:
   - Profit (Lucro): SUM of relevant `bank_report_data.saldo` accounts
   - Equity (Patrimonio): SUM of relevant `bank_report_data.saldo` accounts
   - YoY Growth: Comparison of current period vs same period previous year
   - Basileia Index: Calculation from capital adequacy ratio
   - Latest report: Use MAX(`bank_report.ano_mes`) per institution

4. **Pagination**:
   - Uses `CompositeCursor` for multi-field sorting
   - Implements cursor predicates based on `orderBy` fields
   - Always includes ID (parent_cnpj) as final sort for deterministic ordering

### Service Layer

#### Service Interface

**InstitutionSearchService**:
```kotlin
package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.financialdata.domain.InstitutionAutocomplete
import io.github.salomax.neotool.financialdata.domain.InstitutionSummary

/**
 * Service for searching financial institutions with autocomplete and full search capabilities.
 */
@Singleton
open class InstitutionSearchService(
    private val parentInstitutionRepository: ParentInstitutionRepository,
    private val institutionSearchRepository: ParentInstitutionRepositoryCustom,
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
    fun searchAutocomplete(
        query: String,
        limit: Int = 20,
    ): List<InstitutionAutocomplete> {
        // Validate query
        if (query.isBlank()) {
            throw IllegalArgumentException("Query cannot be empty for autocomplete search")
        }

        // Query repository
        val entities = institutionSearchRepository.searchAutocomplete(query.trim(), limit)
        
        // Convert to domain
        return entities.map { entity ->
            InstitutionAutocomplete(
                id = entity.parentCnpj,
                name = entity.tradeName ?: entity.legalName ?: "Unknown",
                cnpj = entity.parentCnpj,
                logoUrl = entity.logoStorageKey?.let { deriveLogoUrl(it) },
            )
        }
    }

    /**
     * Search institutions with full text search, financial metrics, and Relay pagination.
     * Unified method that handles both list (no query) and search (with query) operations.
     * When query is null or empty, returns all institutions. When query is provided, returns filtered institutions.
     * totalCount is always calculated.
     *
     * @param query Optional search query (partial match, case-insensitive). If null or empty, returns all institutions.
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded composite cursor)
     * @param orderBy Optional list of order by specifications. If null or empty, defaults to NAME ASC, ID ASC.
     * @return Connection containing paginated institutions with totalCount
     */
    fun searchInstitutions(
        query: String?,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
        orderBy: List<InstitutionOrderBy>? = null,
    ): Connection<InstitutionSummary> {
        // Validate and normalize pagination
        val pageSize = when {
            first < 1 -> throw IllegalArgumentException("Parameter 'first' must be at least 1, got: $first")
            first > PaginationConstants.MAX_PAGE_SIZE -> throw IllegalArgumentException(
                "Parameter 'first' must be at most ${PaginationConstants.MAX_PAGE_SIZE}, got: $first"
            )
            else -> first
        }

        // Determine effective orderBy
        val effectiveOrderBy = when {
            orderBy == null || orderBy.isEmpty() -> listOf(
                InstitutionOrderBy(InstitutionOrderField.NAME, OrderDirection.ASC),
                InstitutionOrderBy(InstitutionOrderField.ID, OrderDirection.ASC),
            )
            else -> {
                // Ensure ID is always last for deterministic ordering
                val withoutId = orderBy.filter { it.field != InstitutionOrderField.ID }
                withoutId + InstitutionOrderBy(InstitutionOrderField.ID, OrderDirection.ASC)
            }
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
        val results = institutionSearchRepository.searchInstitutions(
            normalizedQuery,
            pageSize + 1,
            afterCompositeCursor,
            effectiveOrderBy,
        )
        val hasMore = results.size > pageSize

        // Always get total count
        val totalCount = institutionSearchRepository.countInstitutions(normalizedQuery)

        // Take only requested number and convert to domain
        val institutions = results
            .take(pageSize)
            .map { result ->
                InstitutionSummary(
                    id = result.parentCnpj,
                    name = result.tradeName ?: result.legalName ?: result.nomeInstituicao ?: "Unknown",
                    logoUrl = result.logoStorageKey?.let { deriveLogoUrl(it) },
                    profit = result.profit,
                    equity = result.equity,
                    yoyGrowth = result.yoyGrowth,
                    basileiaIndex = result.basileiaIndex,
                    bacenInstitutionType = result.bacenInstitutionType,
                )
            }

        // Build connection with composite cursor encoding
        return ConnectionBuilder.buildConnection(
            items = institutions,
            hasMore = hasMore,
            encodeCursor = { institution ->
                // Build composite cursor from orderBy fields
                val fieldValues = buildMap {
                    effectiveOrderBy.forEach { order ->
                        when (order.field) {
                            InstitutionOrderField.NAME -> put("name", institution.name)
                            InstitutionOrderField.CNPJ -> put("cnpj", institution.id)
                            InstitutionOrderField.PROFIT -> put("profit", institution.profit)
                            InstitutionOrderField.EQUITY -> put("equity", institution.equity)
                            InstitutionOrderField.YOY_GROWTH -> put("yoyGrowth", institution.yoyGrowth)
                            InstitutionOrderField.BASILEIA -> put("basileia", institution.basileiaIndex)
                            InstitutionOrderField.ID -> put("id", institution.id)
                        }
                    }
                }
                CursorEncoder.encodeCompositeCursor(
                    CompositeCursor(
                        fieldValues = fieldValues,
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
import io.github.salomax.neotool.financialdata.graphql.dto.InstitutionAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.InstitutionConnectionDTO
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
     * @return InstitutionAutocompleteConnectionDTO
     * @throws IllegalArgumentException if query is null or empty
     */
    fun searchInstitutionsAutocomplete(
        query: String,
    ): InstitutionAutocompleteConnectionDTO {
        // Validate query
        if (query.isBlank()) {
            throw IllegalArgumentException("Query cannot be empty for autocomplete search")
        }

        // Call service
        val suggestions = institutionSearchService.searchAutocomplete(query)
        
        // Convert to DTO
        return mapper.toInstitutionAutocompleteConnectionDTO(suggestions)
    }

    /**
     * Full search for institutions with financial metrics and Relay pagination.
     *
     * @param query Optional search query
     * @param first Maximum number of results (default: 20, min: 1, max: 100)
     * @param after Cursor for pagination
     * @param orderBy Optional order by specification
     * @return InstitutionConnectionDTO
     * @throws IllegalArgumentException if first is invalid or orderBy contains invalid fields/directions
     */
    fun searchInstitutions(
        query: String?,
        first: Int?,
        after: String?,
        orderBy: List<Map<String, Any?>>?,
    ): InstitutionConnectionDTO {
        // Validate pagination parameter
        val pageSize = when {
            first == null -> PaginationConstants.DEFAULT_PAGE_SIZE
            first < 1 -> throw IllegalArgumentException("Parameter 'first' must be at least 1, got: $first")
            first > PaginationConstants.MAX_PAGE_SIZE -> throw IllegalArgumentException(
                "Parameter 'first' must be at most ${PaginationConstants.MAX_PAGE_SIZE}, got: $first"
            )
            else -> first
        }

        // Validate orderBy - mapper will throw IllegalArgumentException for invalid fields/directions
        val orderByList = try {
            mapper.toInstitutionOrderByList(orderBy)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid orderBy parameter: ${e.message}", e)
        }

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val connection = institutionSearchService.searchInstitutions(
            query,
            pageSize,
            after,
            orderByList,
        )
        
        // Convert to DTO
        return mapper.toInstitutionConnectionDTO(connection)
    }
}
```

#### Mapper

**InstitutionSearchMapper**:
```kotlin
package io.github.salomax.neotool.financialdata.graphql.mapper

import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.OrderDirection
import io.github.salomax.neotool.financialdata.domain.InstitutionAutocomplete
import io.github.salomax.neotool.financialdata.domain.InstitutionSummary
import io.github.salomax.neotool.financialdata.graphql.dto.*
import io.github.salomax.neotool.financialdata.service.InstitutionOrderBy
import io.github.salomax.neotool.financialdata.service.InstitutionOrderField
import jakarta.inject.Singleton

@Singleton
class InstitutionSearchMapper {
    fun toInstitutionAutocompleteConnectionDTO(
        suggestions: List<InstitutionAutocomplete>,
    ): InstitutionAutocompleteConnectionDTO {
        return InstitutionAutocompleteConnectionDTO(
            suggestions = suggestions.map { suggestion ->
                InstitutionAutocompleteItemDTO(
                    id = suggestion.id,
                    name = suggestion.name,
                    cnpj = suggestion.cnpj,
                    logoUrl = suggestion.logoUrl,
                )
            },
        )
    }

    fun toInstitutionConnectionDTO(
        connection: Connection<InstitutionSummary>,
    ): InstitutionConnectionDTO {
        return InstitutionConnectionDTO(
            edges = connection.edges.map { edge ->
                InstitutionEdgeDTO(
                    node = toInstitutionSummaryItemDTO(edge.node),
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

    fun toInstitutionSummaryItemDTO(
        summary: InstitutionSummary,
    ): InstitutionSummaryItemDTO {
        return InstitutionSummaryItemDTO(
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

    fun toInstitutionOrderByList(
        orderBy: List<Map<String, Any?>>?,
    ): List<InstitutionOrderBy>? {
        if (orderBy == null || orderBy.isEmpty()) {
            return null
        }

        return orderBy.map { orderByMap ->
            val fieldStr = orderByMap["field"] as? String
                ?: throw IllegalArgumentException("orderBy field is required")
            val directionStr = orderByMap["direction"] as? String
                ?: throw IllegalArgumentException("orderBy direction is required")

            val field = when (fieldStr) {
                "NAME" -> InstitutionOrderField.NAME
                "CNPJ" -> InstitutionOrderField.CNPJ
                "PROFIT" -> InstitutionOrderField.PROFIT
                "EQUITY" -> InstitutionOrderField.EQUITY
                "YOY_GROWTH" -> InstitutionOrderField.YOY_GROWTH
                "BASILEIA" -> InstitutionOrderField.BASILEIA
                "ID" -> InstitutionOrderField.ID
                else -> throw IllegalArgumentException(
                    "Invalid InstitutionOrderField: $fieldStr. Allowed: NAME, CNPJ, PROFIT, EQUITY, YOY_GROWTH, BASILEIA, ID"
                )
            }

            val direction = when (directionStr) {
                "ASC" -> OrderDirection.ASC
                "DESC" -> OrderDirection.DESC
                else -> throw IllegalArgumentException(
                    "Invalid OrderDirection: $directionStr. Allowed: ASC, DESC"
                )
            }

            InstitutionOrderBy(field, direction)
        }
    }
}
```

#### DTOs

**InstitutionAutocompleteConnectionDTO**:
```kotlin
package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Introspected
@Serdeable
data class InstitutionAutocompleteConnectionDTO(
    val suggestions: List<InstitutionAutocompleteItemDTO>,
)

@Introspected
@Serdeable
data class InstitutionAutocompleteItemDTO(
    val id: String,
    val name: String,
    val cnpj: String,
    val logoUrl: String?,
)
```

**InstitutionConnectionDTO**:
```kotlin
package io.github.salomax.neotool.financialdata.graphql.dto

import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable

@Introspected
@Serdeable
data class InstitutionConnectionDTO(
    val edges: List<InstitutionEdgeDTO>,
    val pageInfo: PageInfoDTO,
    val totalCount: Int?,
)

@Introspected
@Serdeable
data class InstitutionEdgeDTO(
    val node: InstitutionSummaryItemDTO,
    val cursor: String,
)

@Introspected
@Serdeable
data class InstitutionSummaryItemDTO(
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
1. Create `ParentInstitutionRepositoryCustom` interface
2. Create `InstitutionOrderBy` and `InstitutionOrderField` enums
3. Create `InstitutionSearchResult` DTO
4. Create `ParentInstitutionRepositoryImpl` with:
   - `searchAutocomplete()` method (simple JOIN, no metrics)
   - `searchInstitutions()` method (complex JOIN with metrics aggregation)
   - `countInstitutions()` method
5. Implement JOIN logic between `parent_institution`, `institution`, `bank_report`, `bank_report_data`
6. Implement search filter predicate builder
7. Implement cursor-based pagination with `CompositeCursor`
8. Implement financial metrics aggregation (placeholder calculations - detailed formulas TBD)
9. Add unit tests for repository methods

**Dependencies**:
- `ParentInstitutionEntity`
- `InstitutionEntity`
- `BankReportEntity`
- `BankReportDataEntity`

### Phase 2: Domain Layer

**Tasks**:
1. Create `InstitutionAutocomplete` domain object
2. Create `InstitutionSummary` domain object
3. Add conversion methods from repository DTOs to domain objects

### Phase 3: Service Layer

**Tasks**:
1. Create `InstitutionSearchService` with:
   - `searchAutocomplete()` method
   - `searchInstitutions()` method
2. Implement domain conversion
3. Implement cursor encoding/decoding
4. Implement connection building
5. Add logging and error handling
6. Add unit tests for service methods

**Dependencies**:
- `ParentInstitutionRepositoryCustom`
- Domain objects
- Pagination utilities (`ConnectionBuilder`, `CursorEncoder`)

### Phase 4: GraphQL Layer

**Tasks**:
1. Create `InstitutionSearchResolver`
2. Create `InstitutionSearchMapper`
3. Create DTOs:
   - `InstitutionAutocompleteConnectionDTO`
   - `InstitutionAutocompleteItemDTO`
   - `InstitutionConnectionDTO`
   - `InstitutionEdgeDTO`
   - `InstitutionSummaryItemDTO`
   - `PageInfoDTO`
4. Implement GraphQL schema definitions
5. Wire resolvers in `GraphQLWiringFactory`
6. Add integration tests for GraphQL queries

**Dependencies**:
- `InstitutionSearchService`
- GraphQL wiring factory

### Phase 5: Financial Metrics Implementation

**Tasks**:
1. Define detailed formulas for:
   - Profit (Lucro) calculation
   - Equity (Patrimonio) calculation
   - YoY Growth calculation
   - Basileia Index calculation
2. Update repository implementation with actual SQL aggregation
3. Add unit tests for metric calculations
4. Add integration tests with sample data

**Note**: This phase depends on detailed requirements for financial metric calculations, which will be provided later.

## Database Considerations

### Indexes

Ensure appropriate indexes exist for:
- `parent_institution.parent_cnpj` (PRIMARY KEY)
- `parent_institution.trade_name` (for search)
- `parent_institution.legal_name` (for search)
- `institution.cnpj_instituicao_lider` (for JOIN with parent_institution)
- `institution.bacen_cod_inst` (for search and JOIN)
- `institution.nome_instituicao` (for search)
- `bank_report.cod_inst` and `bank_report.ano_mes` (for metrics calculation)
- `bank_report_data.bank_report_id` (for JOIN)

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
- Test pagination with cursor
- Test count method matches search results
- Test sorting with different orderBy specifications
- Test financial metrics aggregation (with mock data)

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
- Test orderBy validation
- Test error handling

### Integration Tests

- Test JOIN between tables
- Test metric calculations accuracy
- Test pagination across large datasets
- Test performance with realistic data volumes
- Test GraphQL query execution end-to-end

## Notes

- The `bacen_cod_inst` field is a String (TEXT in database) to preserve leading zeros
- The relationship between `institution` and `parent_institution` is via `cnpj_instituicao_lider` field
- Financial metrics require joining with `bank_report` and `bank_report_data` tables
- Autocomplete query should be optimized for speed (limited results, simple JOINs)
- Full search query can be more complex (includes metrics aggregation)
- Consider creating a view or materialized view for complex metric calculations if performance becomes an issue
- Logo URL derivation is placeholder - will integrate with asset management service later

## Related Documentation

- [Repository Requirements](./repository-requirements.md) - Repository layer requirements
- [Service Pattern](../../04-patterns/backend-patterns/service-pattern.md) - Service layer patterns
- [Repository Pattern](../../04-patterns/backend-patterns/repository-pattern.md) - Repository layer patterns
- [Resolver Pattern](../../04-patterns/backend-patterns/resolver-pattern.md) - GraphQL resolver patterns
- [Pagination Pattern](../../04-patterns/backend-patterns/pagination-pattern.md) - Relay pagination patterns

