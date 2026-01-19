package io.github.salomax.neotool.financialdata.graphql.resolver

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.financialdata.domain.QuarterRange
import io.github.salomax.neotool.financialdata.graphql.dto.BacenFinanceInstitutionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportDetailsDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ReportsAvailabilityDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.BacenInstitutionDetailMapper
import io.github.salomax.neotool.financialdata.service.BacenInstitutionDetailService
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * GraphQL resolver for BacenInstitution detail operations.
 * Provides queries for enriched institution data and report information.
 */
@Singleton
class BacenInstitutionDetailResolver(
    private val institutionDetailService: BacenInstitutionDetailService,
    private val mapper: BacenInstitutionDetailMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get enriched BacenFinanceInstitution data for a single institution.
     * Filters by quarter (ano_mes_ref). If anoMes is null, uses latest quarter globally.
     *
     * @param id Institution ID (bacen_cod_inst)
     * @param anoMes Quarter filter (YYYYMM format). If null, uses latest quarter globally.
     * @return BacenFinanceInstitutionDTO or null if not found
     */
    fun bacenFinanceInstitution(
        id: String,
        anoMes: String?,
    ): BacenFinanceInstitutionDTO? {
        val institution = institutionDetailService.getBacenFinanceInstitution(id, anoMes)
        return institution?.let { mapper.toBacenFinanceInstitutionDTO(it) }
    }

    /**
     * Get available reports grouped by quarter for an institution.
     * Field resolver for reportsSummary on BacenFinanceInstitution.
     *
     * @param environment GraphQL data fetching environment
     * @return ReportsAvailabilityDTO
     */
    fun reportsSummary(environment: DataFetchingEnvironment): ReportsAvailabilityDTO {
        val institution =
            environment.getSource<BacenFinanceInstitutionDTO>()
                ?: throw IllegalStateException("Institution source is required")
        val rangeInput =
            environment.getArgument<Map<String, Any?>>("range")
                ?: throw IllegalArgumentException("range parameter is required")

        val range = parseQuarterRange(rangeInput)
        val availability = institutionDetailService.getReportsSummary(institution.id, range)
        return mapper.toReportsAvailabilityDTO(availability)
    }

    /**
     * Get detailed report values by quarter for a specific report.
     * Field resolver for reportDetails on BacenFinanceInstitution.
     *
     * @param environment GraphQL data fetching environment
     * @return ReportDetailsDTO
     */
    fun reportDetails(environment: DataFetchingEnvironment): ReportDetailsDTO {
        val institution =
            environment.getSource<BacenFinanceInstitutionDTO>()
                ?: throw IllegalStateException("Institution source is required")
        val reportId =
            environment.getArgument<String>("reportId")
                ?: throw IllegalArgumentException("reportId parameter is required")
        val rangeInput =
            environment.getArgument<Map<String, Any?>>("range")
                ?: throw IllegalArgumentException("range parameter is required")

        val range = parseQuarterRange(rangeInput)
        val details =
            institutionDetailService.getReportDetails(institution.id, reportId, range)
                ?: throw IllegalArgumentException("Report not found: $reportId")

        return mapper.toReportDetailsDTO(details)
    }

    /**
     * Parse QuarterRangeInput from GraphQL arguments.
     */
    private fun parseQuarterRange(rangeInput: Map<String, Any?>): QuarterRange {
        val from = rangeInput["from"] as? String
        val to = rangeInput["to"] as? String
        val last = (rangeInput["last"] as? Number)?.toInt()

        return QuarterRange(
            from = from,
            to = to,
            last = last,
        )
    }
}
