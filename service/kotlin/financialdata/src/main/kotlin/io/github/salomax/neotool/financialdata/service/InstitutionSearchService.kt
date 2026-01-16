package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionSummary
import io.github.salomax.neotool.financialdata.repo.BacenInstitutionRepositoryCustom
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Service for searching financial institutions with autocomplete and full search capabilities.
 */
@Singleton
open class InstitutionSearchService(
    private val institutionSearchRepository: BacenInstitutionRepositoryCustom,
    private val quarterService: QuarterService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Search institutions for autocomplete suggestions.
     * Returns limited results for fast response time.
     * Simplified to only return institution names for better performance.
     * Filters by quarter (ano_mes_ref). If anoMes is null, uses latest quarter globally.
     *
     * @param query Search query (required, non-empty)
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @param limit Maximum number of results (default: 20)
     * @return List of institution names ordered by similarity score
     * @throws IllegalArgumentException if query is null or empty
     */
    fun searchBacenInstitutionsAutocomplete(
        query: String,
        anoMes: String? = null,
        limit: Int = 20,
    ): List<String> {
        logger.debug { "Autocomplete search: query='$query', anoMes=$anoMes, limit=$limit" }

        // Validate query
        if (query.isBlank()) {
            throw IllegalArgumentException("Query cannot be empty for autocomplete search")
        }

        val targetAnoMes = anoMes ?: quarterService.getLatestQuarter()
        if (targetAnoMes == null) {
            return emptyList()
        }

        return try {
            // Query repository - returns List<String> of institution names
            val results =
                institutionSearchRepository.searchBacenInstitutionsAutocomplete(
                    query.trim(),
                    targetAnoMes,
                    limit,
                )
            logger.debug { "Autocomplete search returned ${results.size} results" }
            results
        } catch (e: Exception) {
            logger.error(e) { "Error during autocomplete search: query='$query', anoMes=$anoMes" }
            throw e
        }
    }

    /**
     * Search institutions with full text search, financial metrics, and Relay pagination.
     * Unified method that handles both list (no query) and search (with query) operations.
     * When query is null or empty, returns all institutions. When query is provided, returns filtered institutions.
     * totalCount is always calculated.
     * Results are ordered by bacen_cod_inst for deterministic pagination.
     * Filters by quarter (ano_mes_ref). If anoMes is null, uses latest quarter globally.
     *
     * @param query Optional search query (partial match, case-insensitive). If null or empty, returns all institutions.
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @param first Maximum number of results to return (default: 20, max: 100)
     * @param after Cursor string for pagination (base64-encoded composite cursor with bacen_cod_inst)
     * @return Connection containing paginated institutions with totalCount
     */
    fun bacenInstitutions(
        query: String?,
        anoMes: String? = null,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<BacenInstitutionSummary> {
        logger.debug { "Searching institutions: query='$query', anoMes=$anoMes, first=$first, after=$after" }

        // Validate and normalize pagination
        val pageSize = PaginationConstants.validatePageSize(first)

        // Decode cursor (bacen_cod_inst is String, so we decode without type validation)
        val afterCompositeCursor: CompositeCursor? =
            after?.let {
                try {
                    CursorEncoder.decodeCompositeCursorToString(it)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid cursor format: $after", e)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Failed to decode cursor: $after", e)
                }
            }

        // Normalize query
        val normalizedQuery =
            if (query.isNullOrBlank()) null else query.trim()

        val targetAnoMes = anoMes ?: quarterService.getLatestQuarter()
        if (targetAnoMes == null) {
            return ConnectionBuilder.buildConnection(
                items = emptyList(),
                hasMore = false,
                encodeCursor = { _ -> "" },
                totalCount = 0,
            )
        }

        return try {
            // Query repository (one extra to check for more results)
            val results =
                institutionSearchRepository.bacenInstitutions(
                    normalizedQuery,
                    targetAnoMes,
                    pageSize + 1,
                    afterCompositeCursor,
                )
            val hasMore = results.size > pageSize

            // Always get total count
            val totalCount = institutionSearchRepository.countBacenInstitutions(normalizedQuery, targetAnoMes)

            // Take only requested number and convert to domain
            val institutions =
                results
                    .take(pageSize)
                    .map { result ->
                        BacenInstitutionSummary(
                            id = result.bacenCodInst,
                            name = result.nomeInstituicao ?: "Unknown",
                            legalName = result.legalName,
                            tradeName = result.tradeName,
                            assetId = result.assetId,
                            profit = result.profit,
                            equity = result.equity,
                            yoyEquityGrowth = result.yoyEquityGrowth,
                            basileiaIndex = result.basileiaIndex,
                            bacenInstitutionType = result.bacenInstitutionType,
                            institutionType = result.institutionType,
                            tipoConsolidadoBancario = result.tipoConsolidadoBancario,
                        )
                    }

            logger.debug { "Institution search returned ${institutions.size} results, totalCount=$totalCount, hasMore=$hasMore" }

            // Build connection with composite cursor encoding (using bacen_cod_inst)
            ConnectionBuilder.buildConnection(
                items = institutions,
                hasMore = hasMore,
                encodeCursor =
                    { institution ->
                        CursorEncoder.encodeCompositeCursor(
                            fieldValues = mapOf("id" to institution.id),
                            id = institution.id,
                        )
                    },
                totalCount = totalCount,
            )
        } catch (e: Exception) {
            logger.error(e) { "Error during institution search: query='$query', anoMes=$anoMes" }
            throw e
        }
    }
}
