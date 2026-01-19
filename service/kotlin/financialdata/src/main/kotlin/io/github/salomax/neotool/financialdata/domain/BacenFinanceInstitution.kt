package io.github.salomax.neotool.financialdata.domain

import java.math.BigDecimal
import java.util.UUID

/**
 * Domain object for enriched BACEN institution data with financial metrics and report capabilities.
 * Extends BacenInstitutionSummary with additional fields from financial_institution and bacen_institution.
 */
data class BacenFinanceInstitution(
    // Base fields from BacenInstitutionSummary
    // bacen_cod_inst
    val id: String,
    // nome_instituicao from bacen_institution
    val name: String,
    val legalName: String?,
    val tradeName: String?,
    val assetId: UUID?,
    val profit: BigDecimal?,
    val equity: BigDecimal?,
    val yoyEquityGrowth: BigDecimal?,
    val basileiaIndex: BigDecimal?,
    val institutionType: BacenInstitutionType?,
    val tipoConsolidadoBancario: BacenInstitutionBankType?,
    // Enriched summary fields
    // Latest ano_mes from bacen_bank_report
    val quarter: String?,
    // from financial_institution
    val website: String?,
    // from financial_institution.social_network (JSON array)
    val socialNetworks: List<String>?,
    // from financial_institution.receita_data.mainActivity.text
    val mainActivity: String?,
    // Formatted as "city, state" from receita_data.address
    val address: String?,
    // from financial_institution.receita_data.founded (formatted to locale date)
    val founded: String?,
    // from bacen_institution.tipo_controle (1, 2, 3)
    val controlType: BacenControlType?,
    // from bacen_institution.tipo_consolidado_bancario
    val institutionBankType: BacenInstitutionBankType?,
    val mobilizacaoIndex: BigDecimal?,
    val totalAssets: BigDecimal?,
    val funding: BigDecimal?,
    val loanPortfolio: BigDecimal?,
    val rwaReferenceEquity: BigDecimal?,
)
