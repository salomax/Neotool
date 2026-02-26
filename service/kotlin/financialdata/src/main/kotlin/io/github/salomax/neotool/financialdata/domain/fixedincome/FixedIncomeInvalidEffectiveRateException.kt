package io.github.salomax.neotool.financialdata.domain.fixedincome

import java.math.BigDecimal

/**
 * Raised when annual effective rate is invalid for compounding ((1 + rate) must be > 0).
 */
class FixedIncomeInvalidEffectiveRateException(
    val effectiveAnnualRateDecimal: BigDecimal,
) : IllegalArgumentException(
        DEFAULT_MESSAGE,
    ) {
    companion object {
        const val CODE = "FIXED_INCOME_INVALID_EFFECTIVE_RATE"
        const val DEFAULT_MESSAGE = "Invalid effective annual rate"
    }
}
