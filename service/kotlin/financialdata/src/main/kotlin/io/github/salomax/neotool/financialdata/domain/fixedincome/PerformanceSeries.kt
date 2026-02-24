package io.github.salomax.neotool.financialdata.domain.fixedincome

/**
 * One chart line for a product tax regime.
 */
data class PerformanceSeries(
    val productGroup: ProductGroup,
    val points: List<PerformancePoint>,
)
