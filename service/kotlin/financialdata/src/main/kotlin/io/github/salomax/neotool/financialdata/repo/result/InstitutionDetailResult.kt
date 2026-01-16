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
    // "I" or "C"
    val institutionType: String?,
    // Enriched fields
    // Latest ano_mes
    val quarter: String?,
    val website: String?,
    val socialNetworks: List<String>?,
    val mainActivity: String?,
    // Formatted as "city, state"
    val address: String?,
    // Formatted date
    val founded: String?,
    // "1", "2", or "3"
    val tipoControle: String?,
    // "b1", "B2", etc.
    val tipoConsolidadoBancarioCode: String?,
    val mobilizacaoIndex: BigDecimal?,
    val totalAssets: BigDecimal?,
    val funding: BigDecimal?,
    val loanPortfolio: BigDecimal?,
    val rwaReferenceEquity: BigDecimal?,
)
