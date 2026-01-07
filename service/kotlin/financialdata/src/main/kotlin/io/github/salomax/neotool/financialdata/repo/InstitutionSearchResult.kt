package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.domain.BacenInstitutionType
import java.math.BigDecimal

/**
 * Result DTO from institution search query with aggregated financial metrics.
 * This is returned by the custom repository implementation to avoid complex entity relationships.
 */
data class InstitutionSearchResult(
    // Institution identification
    val bacenCodInst: String,
    val nomeInstituicao: String?,
    val cnpjInstituicaoLider: String?,
    // Enhanced data (from financial_institution if linked)
    val legalName: String?,
    val tradeName: String?,
    val logoStorageKey: String?,
    // Financial metrics (aggregated from bacen_bank_report_data using specific account codes)
    // Lucro Líquido (conta 78187)
    val profit: BigDecimal?,
    // Patrimônio Líquido (conta 78186) - latest period
    val equity: BigDecimal?,
    // Patrimônio Líquido (conta 78186) - same period a year ago
    val equityYearAgo: BigDecimal?,
    // Year-over-year equity growth percentage
    val yoyEquityGrowth: BigDecimal?,
    // Indice Basileia calculation
    val basileiaIndex: BigDecimal?,
    // tipo_instituicao from bacen_bank_report
    val bacenInstitutionType: Int?,
    // Institution type classification fields (for calculation)
    val tipoConsolidacao: Char?,
    val codConglomeradoPrudencial: String?,
    val codConglomeradoFinanceiro: String?,
    // tipo_consolidado_bancario from bacen_institution
    val tipoConsolidadoBancario: String?,
    // Calculated institution type
    val institutionType: BacenInstitutionType?,
)
