package io.github.salomax.neotool.financialdata.repo

/**
 * Custom query contract for quarter-related operations.
 * Provides queries for quarter information.
 */
interface QuarterRepositoryCustom {
    /**
     * Get the latest quarter (max ano_mes_ref) globally across all institutions.
     *
     * @return Latest quarter in "YYYYMM" format or null if no data exists
     */
    fun getLatestQuarter(): String?
}
