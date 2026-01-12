package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object representing available reports grouped by quarter.
 */
data class ReportsAvailability(
    /**
     * List of quarters with their available reports
     */
    val quarters: List<QuarterReports>,
)
