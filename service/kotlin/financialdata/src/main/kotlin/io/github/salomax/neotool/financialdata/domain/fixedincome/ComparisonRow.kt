package io.github.salomax.neotool.financialdata.domain.fixedincome

import java.math.BigDecimal

/**
 * One row in the comparison table: base or equivalent scenario.
 * All amounts and rates use scale 10 internally.
 */
data class ComparisonRow(
    val label: String,
    val productGroup: ProductGroup,
    val rateModel: RateModel,
    /** Contracted/equivalent rate in bps (100% = 10_000 bps). */
    val equivalentRateBps: Int,
    /** Contracted rate (display form: e.g. "100% CDI", "10%", "IPCA+5%") or equivalent solved rate. */
    val contractedRateDisplay: String,
    val gross: BigDecimal,
    val ir: BigDecimal,
    val net: BigDecimal,
    val realGainPercent: BigDecimal,
    val isExempt: Boolean,
    val isBase: Boolean = false,
)
