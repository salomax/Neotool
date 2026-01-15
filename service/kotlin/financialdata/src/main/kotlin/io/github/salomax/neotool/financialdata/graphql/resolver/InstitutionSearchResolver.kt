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
     * Filters by quarter (ano_mes_ref). If anoMes is null, uses latest quarter globally.
     *
     * @param query Search query (required)
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @return BacenInstitutionAutocompleteConnectionDTO
     * @throws IllegalArgumentException if query is null or empty
     */
    fun searchBacenInstitutionsAutocomplete(query: String, anoMes: String?): BacenInstitutionAutocompleteConnectionDTO {
        // Validate query
        if (query.isBlank()) {
            throw IllegalArgumentException("Query cannot be empty for autocomplete search")
        }

        // Call service
        val suggestions = institutionSearchService.searchBacenInstitutionsAutocomplete(query, anoMes)

        // Convert to DTO
        return mapper.toBacenInstitutionAutocompleteConnectionDTO(suggestions)
    }

    /**
     * Full search for institutions with financial metrics and Relay pagination.
     * Filters by quarter (ano_mes_ref). If anoMes is null, uses latest quarter globally.
     *
     * @param query Optional search query
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @param first Maximum number of results (default: 20, min: 1, max: 100)
     * @param after Cursor for pagination
     * @return BacenInstitutionConnectionDTO
     * @throws IllegalArgumentException if first is invalid
     */
    fun bacenInstitutions(
        query: String?,
        anoMes: String?,
        first: Int?,
        after: String?,
    ): BacenInstitutionConnectionDTO {
        // Validate pagination parameter
        val pageSize = PaginationConstants.validatePageSize(first)

        // Call service - propagate any exceptions (will be converted to GraphQL errors)
        val connection =
            institutionSearchService.bacenInstitutions(
                query,
                anoMes,
                pageSize,
                after,
            )

        // Convert to DTO
        return mapper.toBacenInstitutionConnectionDTO(connection)
    }
}
