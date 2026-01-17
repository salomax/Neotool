package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.domain.QuarterRange
import io.github.salomax.neotool.financialdata.repo.result.BacenInstitutionReportDataResult
import io.github.salomax.neotool.financialdata.repo.result.FinancialInstitutionDataResult
import io.github.salomax.neotool.financialdata.repo.result.ReportDescriptorResult
import io.github.salomax.neotool.financialdata.repo.result.ReportValueResult

/**
 * Custom query contract for BacenInstitution detail operations.
 * Provides queries for enriched institution data and report information.
 */
interface BacenInstitutionDetailRepositoryCustom {
    /**
     * Get financial institution data from financial_data.financial_institution table.
     * Filters by quarter (ano_mes_ref). Caller must resolve anoMes before invoking.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @return FinancialInstitutionDataResult or null if not found
     */
    fun getFinancialInstitutionData(
        id: String,
        anoMes: String? = null,
    ): FinancialInstitutionDataResult?

    /**
     * Get BACEN institution report data from bacen_inst_report and related tables.
     * Filters by quarter (ano_mes_ref). Caller must resolve anoMes before invoking.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @return List of BacenInstitutionReportDataResult, empty list if no data found
     */
    fun getBacenInstitutionReportData(
        id: String,
        anoMes: String? = null,
    ): List<BacenInstitutionReportDataResult>

    /**
     * Get available reports grouped by quarter for an institution within a quarter range.
     * Returns only metadata (no detailed values) for performance.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @param range Quarter range filter
     * @return List of ReportDescriptorResult grouped by quarter
     */
    fun getReportsByQuarterRange(
        id: String,
        range: QuarterRange,
    ): List<ReportDescriptorResult>

    /**
     * Get detailed report values by quarter for a specific report and institution.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @param reportId Report ID (numero_relatorio)
     * @param range Quarter range filter
     * @return List of ReportValueResult with values by quarter
     */
    fun getReportDetailsByQuarterRange(
        id: String,
        reportId: String,
        range: QuarterRange,
    ): List<ReportValueResult>
}
