package io.github.salomax.neotool.financialdata.repo.result

import java.math.BigDecimal
import java.util.UUID

/**
 * Result object for institution detail queries.
 * Contains data from financial_institution, bacen_institution, and latest bacen_bank_report.
 */
data class InstitutionDetailResult(
    // Base fields
    val bacenCodInst: String,
    val nomeInstituicao: String?,
    val legalName: String?,
    val tradeName: String?,
    val assetId: UUID?,
    val profit: BigDecimal?,
    val equity: BigDecimal?,
    val yoyEquityGrowth: BigDecimal?,
    val basileiaIndex: BigDecimal?,
    val bacenInstitutionType: Int?,
    val tipoConsolidadoBancario: String?,
    val institutionType: String?, // "I" or "C"

    // Enriched fields
    val quarter: String?, // Latest ano_mes
    val website: String?,
    val socialNetworks: List<String>?,
    val mainActivity: String?,
    val address: String?, // Formatted as "city, state"
    val founded: String?, // Formatted date
    val tipoControle: String?, // "1", "2", or "3"
    val tipoConsolidadoBancarioCode: String?, // "b1", "B2", etc.
    val mobilizacaoIndex: BigDecimal?,
    val totalAssets: BigDecimal?,
    val funding: BigDecimal?,
    val loanPortfolio: BigDecimal?,
    val rwaReferenceEquity: BigDecimal?,
)
