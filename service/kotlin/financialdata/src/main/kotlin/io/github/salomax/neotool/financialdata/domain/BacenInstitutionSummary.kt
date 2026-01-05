package io.github.salomax.neotool.financialdata.domain

import java.math.BigDecimal

/**
 * Domain object for BACEN institution summary with financial metrics.
 * Used for full search results with aggregated financial data.
 */
data class BacenInstitutionSummary(
    val id: String, // bacen_cod_inst
    val name: String, // nome_instituicao from bacen_institution
    val legalName: String?, // legal_name from financial_institution
    val tradeName: String?, // trade_name from financial_institution
    val logoUrl: String?, // Derived from financial_institution.logo_storage_key
    val profit: BigDecimal?, // Lucro Líquido (conta 78187) from bacen_bank_report_data
    val equity: BigDecimal?, // Patrimônio Líquido (conta 78186) from bacen_bank_report_data
    val yoyEquityGrowth: BigDecimal?, // Year-over-year equity growth percentage (latest vs same period a year ago)
    val basileiaIndex: BigDecimal?, // Indice Basileia calculation
    val bacenInstitutionType: Int?, // tipo_instituicao from bacen_bank_report
    val institutionType: BacenInstitutionType?, // Calculated institution type (INDIVIDUAL_INSTITUTION, PRUDENTIAL_GROUP, FINANCIAL_GROUP)
    val tipoConsolidadoBancario: String?, // tipo_consolidado_bancario from bacen_institution
)

