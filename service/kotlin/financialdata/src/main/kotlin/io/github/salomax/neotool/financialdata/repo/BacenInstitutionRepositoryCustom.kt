package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.common.graphql.pagination.CompositeCursor

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
     * Results are ordered by similarity score (DESC) for best matches first.
     * Filters by ano_mes_ref (quarter). Caller must resolve anoMes before invoking.
     *
     * @param query Search query (partial match, case-insensitive)
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @param limit Maximum number of results to return (default: 20)
     * @return List of institution names (nome_instituicao) ordered by similarity
     */
    fun searchBacenInstitutionsAutocomplete(
        query: String,
        anoMes: String? = null,
        limit: Int = 20,
    ): List<String>

    /**
     * Search institutions with full text search and financial metrics aggregation.
     * Performs case-insensitive partial matching on nome_instituicao and bacen_cod_inst.
     * When query is null or empty, returns all institutions (list behavior).
     * Aggregates financial metrics from latest bacen_bank_report data using specific account codes.
     * Calculates YoY equity growth by comparing latest equity with equity from same period a year ago.
     * Results are ordered by bacen_cod_inst for deterministic pagination.
     * Filters by ano_mes_ref (quarter). Caller must resolve anoMes before invoking.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, returns all institutions.
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @param first Maximum number of results to return
     * @param after Composite cursor to start after (exclusive), or null for first page
     * @return List of matching institutions with aggregated financial metrics
     */
    fun bacenInstitutions(
        query: String?,
        anoMes: String? = null,
        first: Int,
        after: CompositeCursor?,
    ): List<InstitutionSearchResult>

    /**
     * Count institutions matching the search query.
     * Performs case-insensitive partial matching on nome_instituicao and bacen_cod_inst.
     * When query is null or empty, returns total count of all institutions.
     * This count matches the same WHERE clause as searchBacenInstitutions but without pagination.
     * Filters by ano_mes_ref (quarter). Caller must resolve anoMes before invoking.
     *
     * @param query Search query (partial match, case-insensitive). If null or empty, counts all institutions.
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @return Total count of matching institutions (or all institutions if query is null/empty)
     */
    fun countBacenInstitutions(
        query: String?,
        anoMes: String? = null,
    ): Long
}
