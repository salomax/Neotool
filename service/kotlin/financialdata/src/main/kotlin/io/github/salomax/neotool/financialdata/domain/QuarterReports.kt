package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object representing reports available for a specific quarter.
 */
data class QuarterReports(
    /**
     * Quarter in "YYYYMM" format
     */
    val quarter: String,
    /**
     * List of report descriptors available for this quarter
     */
    val reports: List<ReportDescriptor>,
)
