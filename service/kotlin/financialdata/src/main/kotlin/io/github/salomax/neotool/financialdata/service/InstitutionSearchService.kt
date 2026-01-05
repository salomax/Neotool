package io.github.salomax.neotool.financialdata.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor
import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import java.util.Base64
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionAutocomplete
import io.github.salomax.neotool.financialdata.domain.BacenInstitutionSummary
import io.github.salomax.neotool.financialdata.repo.BacenInstitutionRepository
import io.github.salomax.neotool.financialdata.repo.BacenInstitutionRepositoryCustom
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Service for searching financial institutions with autocomplete and full search capabilities.
 */
@Singleton
open class InstitutionSearchService(
    private val bacenInstitutionRepository: BacenInstitutionRepository,
    private val institutionSearchRepository: BacenInstitutionRepositoryCustom,
) {
    private val logger = KotlinLogging.logger {}
    private val objectMapper = ObjectMapper()

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
        // Note: For autocomplete, we load the financial_institution relationship if available
        // This requires fetching the relationship, which may impact performance
        return entities.map { entity ->
            val financialInstitution = entity.financialInstitution
            BacenInstitutionAutocomplete(
                id = entity.bacenCodInst,
                name = entity.nomeInstituicao ?: "Unknown",
                cnpj = entity.cnpjInstituicaoLider,
                legalName = financialInstitution?.legalName,
                tradeName = financialInstitution?.tradeName,
                logoUrl = financialInstitution?.logoStorageKey?.let { deriveLogoUrl(it) },
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
    fun bacenInstitutions(
        query: String?,
        first: Int = PaginationConstants.DEFAULT_PAGE_SIZE,
        after: String?,
    ): Connection<BacenInstitutionSummary> {
        // Validate and normalize pagination
        val pageSize = when {
            first < 1 -> throw IllegalArgumentException("Parameter 'first' must be at least 1, got: $first")
            first > PaginationConstants.MAX_PAGE_SIZE -> throw IllegalArgumentException(
                "Parameter 'first' must be at most ${PaginationConstants.MAX_PAGE_SIZE}, got: $first",
            )
            else -> first
        }

        // Decode cursor (bacen_cod_inst is String, so we decode without type validation)
        val afterCompositeCursor: CompositeCursor? = after?.let {
            try {
                // Decode composite cursor - id is String (bacen_cod_inst), not UUID
                val decoded = Base64.getUrlDecoder().decode(it)
                val json = String(decoded)
                @Suppress("UNCHECKED_CAST")
                val map = objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
                val fieldValues = (map["fieldValues"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                    ?.mapValues { it.value } as? Map<String, Any?> ?: emptyMap()
                val id = map["id"] as? String ?: throw IllegalArgumentException("Missing id in cursor")
                CompositeCursor(fieldValues = fieldValues, id = id)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid cursor: $after", e)
            }
        }

        // Normalize query
        val normalizedQuery = if (query.isNullOrBlank()) null else query.trim()

        // Query repository (one extra to check for more results)
        val results = institutionSearchRepository.bacenInstitutions(
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
                    legalName = result.legalName,
                    tradeName = result.tradeName,
                    logoUrl = result.logoStorageKey?.let { deriveLogoUrl(it) },
                    profit = result.profit,
                    equity = result.equity,
                    yoyEquityGrowth = result.yoyEquityGrowth,
                    basileiaIndex = result.basileiaIndex,
                    bacenInstitutionType = result.bacenInstitutionType,
                    institutionType = result.institutionType,
                    tipoConsolidadoBancario = result.tipoConsolidadoBancario,
                )
            }

        // Build connection with composite cursor encoding (using bacen_cod_inst)
        return ConnectionBuilder.buildConnection(
            items = institutions,
            hasMore = hasMore,
            encodeCursor = { institution ->
                val composite = CompositeCursor(
                    fieldValues = mapOf("id" to institution.id),
                    id = institution.id,
                )
                val json = objectMapper.writeValueAsString(composite)
                Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
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

