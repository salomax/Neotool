package io.github.salomax.neotool.financialdata.domain

import java.math.BigDecimal

/**
 * Domain object for BACEN institution summary with financial metrics.
 * Used for full search results with aggregated financial data.
 */
data class BacenInstitutionSummary(
    // bacen_cod_inst
    val id: String,
    // nome_instituicao from bacen_institution
    val name: String,
    // legal_name from financial_institution
    val legalName: String?,
    // trade_name from financial_institution
    val tradeName: String?,
    // asset_id from financial_institution - used for GraphQL federation
    val assetId: java.util.UUID?,
    // Lucro Líquido (conta 78187) from bacen_bank_report_data
    val profit: BigDecimal?,
    // Patrimônio Líquido (conta 78186) from bacen_bank_report_data
    val equity: BigDecimal?,
    // Year-over-year equity growth percentage (latest vs same period a year ago)
    val yoyEquityGrowth: BigDecimal?,
    // Indice Basileia calculation
    val basileiaIndex: BigDecimal?,
    // tipo_instituicao from bacen_bank_report
    val bacenInstitutionType: Int?,
    // Calculated institution type (INDIVIDUAL_INSTITUTION, PRUDENTIAL_GROUP, FINANCIAL_GROUP)
    val institutionType: BacenInstitutionType?,
    // tipo_consolidado_bancario from bacen_institution
    val tipoConsolidadoBancario: String?,
)
