package io.github.salomax.neotool.financialdata.repo

/**
 * Custom queries for popular institutions: resolve parent_cnpj to bacen_cod_inst
 * and FI data for a given quarter so the discovery service can build summary items.
 */
interface PopularInstitutionCustomRepository {
    /**
     * Returns popular institutions (active, ordered by rank) with resolved
     * bacen_cod_inst and institution data for [anoMes]. Only rows where a
     * link exists for the quarter are returned.
     */
    fun findPopularWithResolvedInstitutions(anoMes: String): List<PopularInstitutionResolvedRow>
}
