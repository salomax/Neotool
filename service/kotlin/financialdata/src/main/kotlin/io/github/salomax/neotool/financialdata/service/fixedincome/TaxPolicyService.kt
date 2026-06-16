package io.github.salomax.neotool.financialdata.service.fixedincome

import io.github.salomax.neotool.common.finance.tax.brazil.fixedincome.BrazilianFixedIncomeTaxRules
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
@Singleton
class TaxPolicyService {
    fun isTaxable(productGroup: ProductGroup): Boolean =
        productGroup == ProductGroup.TAXABLE_IR || productGroup == ProductGroup.TAXABLE_IR_NO_IOF

    fun isTaxable(product: FixedIncomeProduct): Boolean = isTaxable(productGroup(product))

    fun appliesIof(
        productGroup: ProductGroup,
        calendarDays: Int,
    ): Boolean = productGroup == ProductGroup.TAXABLE_IR && calendarDays < 30

    fun irBracketRate(calendarDays: Int): BigDecimal {
        require(calendarDays > 0) { "calendarDays must be positive" }
        return BrazilianFixedIncomeTaxRules.irRate(calendarDays).setScale(SCALE, RoundingMode.HALF_UP)
    }

    fun iofRate(calendarDays: Int): BigDecimal =
        BrazilianFixedIncomeTaxRules.iofRate(calendarDays).setScale(SCALE, RoundingMode.HALF_UP)

    fun taxBreakdown(
        principal: BigDecimal,
        gross: BigDecimal,
        productGroup: ProductGroup,
        calendarDays: Int,
    ): TaxBreakdown {
        val zero = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
        if (!isTaxable(productGroup)) {
            return TaxBreakdown(iof = zero, ir = zero, net = gross.setScale(SCALE, RoundingMode.HALF_UP))
        }

        val gain = gross.subtract(principal)
        if (gain <= BigDecimal.ZERO) {
            return TaxBreakdown(iof = zero, ir = zero, net = gross.setScale(SCALE, RoundingMode.HALF_UP))
        }

        val iofAmount =
            if (appliesIof(productGroup, calendarDays)) {
                BrazilianFixedIncomeTaxRules.computeIof(gain, calendarDays).setScale(SCALE, RoundingMode.HALF_UP)
            } else {
                zero
            }
        val taxableGain = gain.subtract(iofAmount).max(BigDecimal.ZERO)
        val irAmount =
            BrazilianFixedIncomeTaxRules
                .computeIr(taxableGain, calendarDays, isTaxExempt = false)
                .setScale(SCALE, RoundingMode.HALF_UP)
        val net = gross.subtract(iofAmount).subtract(irAmount).setScale(SCALE, RoundingMode.HALF_UP)
        return TaxBreakdown(iof = iofAmount, ir = irAmount, net = net)
    }
}

data class TaxBreakdown(
    val iof: BigDecimal,
    val ir: BigDecimal,
    val net: BigDecimal,
)
