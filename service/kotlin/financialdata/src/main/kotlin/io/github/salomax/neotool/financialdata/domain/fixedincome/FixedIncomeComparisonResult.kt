package io.github.salomax.neotool.financialdata.domain.fixedincome

/**
 * Result of a fixed income comparison simulation.
 * rows: one row per (product group × rate model), base first then equivalent rows.
 */
data class FixedIncomeComparisonResult(
    val summary: FixedIncomeSummary,
    val rows: List<ComparisonRow>,
    val performanceSeries: List<PerformanceSeries>,
    val methodology: Methodology,
)
