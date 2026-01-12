package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.domain.QuarterRange
import io.github.salomax.neotool.financialdata.repo.result.InstitutionDetailResult
import io.github.salomax.neotool.financialdata.repo.result.ReportDescriptorResult
import io.github.salomax.neotool.financialdata.repo.result.ReportValueResult

/**
 * Custom query contract for BacenInstitution detail operations.
 * Provides queries for enriched institution data and report information.
 */
interface BacenInstitutionDetailRepositoryCustom {
    /**
     * Get enriched institution detail data including financial_institution and bacen_institution data.
     * Filters by quarter (ano_mes_ref). If anoMes is null, uses latest quarter globally.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @return InstitutionDetailResult or null if not found
     */
    fun getInstitutionDetail(id: String, anoMes: String? = null): InstitutionDetailResult?

    /**
     * Get latest quarter (ano_mes) for an institution.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @return Latest quarter in "YYYYMM" format or null if no reports exist
     */
    fun getLatestQuarter(id: String): String?

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
