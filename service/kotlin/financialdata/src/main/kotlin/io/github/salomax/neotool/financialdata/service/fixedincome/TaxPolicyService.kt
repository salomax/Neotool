package io.github.salomax.neotool.financialdata.service.fixedincome

import io.github.salomax.neotool.common.tracing.TraceExecution

import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeProduct
import io.github.salomax.neotool.financialdata.domain.fixedincome.ProductGroup
import io.github.salomax.neotool.financialdata.domain.fixedincome.productGroup
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode

private const val SCALE = 10

/**
 * Tax policy for fixed income: taxable vs exempt by product, IR bracket by calendar days.
 * TAXABLE_IR products (CDB, LC, DEBENTURE) → taxable; EXEMPT_IR products (LCI, LCA, FREE_TAX_DEBENTURE) → exempt.
 */
@TraceExecution
@Singleton
class TaxPolicyService {
    fun isTaxable(productGroup: ProductGroup): Boolean = productGroup == ProductGroup.TAXABLE_IR

    fun isTaxable(product: FixedIncomeProduct): Boolean = isTaxable(productGroup(product))

    /**
     * IR bracket rate for Brazilian regressive table by calendar-day holding period.
     * 1–180 → 22.5%; 181–360 → 20%; 361–720 → 17.5%; above 720 → 15%.
     */
    fun irBracketRate(calendarDays: Int): BigDecimal {
        require(calendarDays > 0) { "calendarDays must be positive" }
        val rate =
            when {
                calendarDays <= 180 -> "0.225"
                calendarDays <= 360 -> "0.20"
                calendarDays <= 720 -> "0.175"
                else -> "0.15"
            }
        return BigDecimal(rate).setScale(SCALE, RoundingMode.HALF_UP)
    }
}
