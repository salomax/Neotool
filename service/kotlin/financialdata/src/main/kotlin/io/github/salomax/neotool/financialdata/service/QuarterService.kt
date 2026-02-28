package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.financialdata.repo.QuarterRepositoryCustom
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Service for quarter-related operations in the financial_data module.
 * Provides shared functionality for determining latest quarters.
 */
@TraceExecution
@Singleton
open class QuarterService(
    private val quarterRepository: QuarterRepositoryCustom,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Gets the latest quarter (max ano_mes_ref) globally across all institutions.
     * Used as default when anoMes parameter is not provided.
     *
     * @return Latest quarter in "YYYYMM" format or null if no data exists
     */
    open fun getLatestQuarter(): String? {
        return try {
            val latestQuarter = quarterRepository.getLatestQuarter()
            logger.debug { "Retrieved latest quarter: $latestQuarter" }
            latestQuarter
        } catch (e: Exception) {
            logger.error(e) { "Failed to get latest quarter" }
            null
        }
    }
}
