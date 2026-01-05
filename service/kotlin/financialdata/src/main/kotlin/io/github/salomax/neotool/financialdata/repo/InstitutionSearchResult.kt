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
    val profit: BigDecimal?, // Lucro Líquido (conta 78187)
    val equity: BigDecimal?, // Patrimônio Líquido (conta 78186) - latest period
    val equityYearAgo: BigDecimal?, // Patrimônio Líquido (conta 78186) - same period a year ago
    val yoyEquityGrowth: BigDecimal?, // Year-over-year equity growth percentage
    val basileiaIndex: BigDecimal?, // Indice Basileia calculation
    val bacenInstitutionType: Int?, // tipo_instituicao from bacen_bank_report

    // Institution type classification fields (for calculation)
    val tipoConsolidacao: Char?,
    val codConglomeradoPrudencial: String?,
    val codConglomeradoFinanceiro: String?,
    val tipoConsolidadoBancario: String?, // tipo_consolidado_bancario from bacen_institution

    // Calculated institution type
    val institutionType: BacenInstitutionType?,
)

