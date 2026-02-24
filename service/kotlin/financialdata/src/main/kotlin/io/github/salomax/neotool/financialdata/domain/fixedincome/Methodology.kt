package io.github.salomax.neotool.financialdata.domain.fixedincome

import java.math.BigDecimal

/**
 * Methodology and transparency info used by the UI.
 */
data class Methodology(
    val dayCount: DayCountMethodology,
    val tax: TaxMethodology,
)

data class DayCountMethodology(
    val accrualConvention: String,
    val calendarDays: Int,
    val businessDays: Int,
)

data class TaxMethodology(
    val appliesOnProfitOnly: Boolean,
    val activeBracket: TaxBracket,
    val bracketTable: List<TaxBracket>,
)

data class TaxBracket(
    val fromCalendarDays: Int,
    val toCalendarDays: Int?,
    val rateDecimal: BigDecimal,
)
