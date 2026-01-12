package io.github.salomax.neotool.financialdata.repo.result

import java.util.UUID

/**
 * Result object for financial_institution and basic bacen_institution data queries.
 * Contains data from financial_data.financial_institution and bacen_institution tables.
 */
data class FinancialInstitutionDataResult(
    // From bacen_institution
    val nomeInstituicao: String?,
    val tipoControle: String?, // "1", "2", or "3"
    val tipoConsolidacao: String?, // "I" or "C"
    val tipoConsolidadoBancario: String?, // "b1", "B2", etc.
    // From financial_institution
    val legalName: String?,
    val tradeName: String?,
    val assetId: UUID?,
    val website: String?,
    val socialNetworks: List<String>?,
    val mainActivity: String?,
    val address: String?,
    val founded: String?,
)
