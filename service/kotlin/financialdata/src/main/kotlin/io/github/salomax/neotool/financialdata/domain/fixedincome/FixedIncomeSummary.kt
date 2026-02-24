package io.github.salomax.neotool.financialdata.domain.fixedincome

import java.math.BigDecimal

/**
 * Structured summary focused on the base scenario selected by the user.
 */
data class FixedIncomeSummary(
    val baseProduct: FixedIncomeProduct,
    val baseProductGroup: ProductGroup,
    val baseRateModel: RateModel,
    val baseEquivalentRateBps: Int,
    val baseRateDisplay: String,
    val principal: BigDecimal,
    val gross: BigDecimal,
    val ir: BigDecimal,
    val net: BigDecimal,
    val realGainPercent: BigDecimal,
)
