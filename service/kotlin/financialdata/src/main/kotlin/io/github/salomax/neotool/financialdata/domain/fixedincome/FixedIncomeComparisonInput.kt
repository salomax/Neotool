package io.github.salomax.neotool.financialdata.domain.fixedincome

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Input for a single fixed income comparison simulation.
 * All rates are canonicalized in bps (basis points), where 100% = 10_000 bps.
 * contractedRateBps is model-specific:
 * CDI -> contracted % of CDI in bps (e.g. 10000 for 100% CDI)
 * FIXED -> annual fixed rate in bps (e.g. 1000 for 10%)
 * IPCA_PLUS -> annual spread in bps (e.g. 500 for IPCA+5%)
 */
data class FixedIncomeComparisonInput(
    val productGroup: ProductGroup,
    val product: FixedIncomeProduct? = null,
    val principal: BigDecimal,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rateModel: RateModel,
    val contractedRateBps: Int? = null,
    /** Market projection: CDI annual rate in bps (e.g. 1200 for 12%). Always required. */
    val cdiAnnualRateBps: Int? = null,
    /** Market projection: IPCA annual rate in bps (e.g. 600 for 6%). Always required. */
    val ipcaAnnualRateBps: Int? = null,
) {
    init {
        require(principal > BigDecimal.ZERO) { "principal must be positive" }
        require(endDate.isAfter(startDate)) { "endDate must be after startDate" }
        require(cdiAnnualRateBps != null) { "cdiAnnualRateBps is required" }
        require(ipcaAnnualRateBps != null) { "ipcaAnnualRateBps is required" }
        if (product != null) {
            val actualProductGroup = productGroup(product)
            require(
                actualProductGroup == productGroup ||
                    (productGroup == ProductGroup.TAXABLE_IR_NO_IOF && actualProductGroup == ProductGroup.TAXABLE_IR),
            ) { "product does not match productGroup" }
        }
        require(contractedRateBps != null) { "contractedRateBps is required" }
        validateEffectiveAnnualRate()
    }

    private fun validateEffectiveAnnualRate() {
        val one = BigDecimal.ONE
        val scale = 10
        val effectiveAnnualRate =
            when (rateModel) {
                RateModel.CDI -> {
                    val cdi = bpsToDecimalRate(cdiAnnualRateBps!!, scale)
                    val contracted = bpsToDecimalRate(contractedRateBps!!, scale)
                    cdi.multiply(contracted).setScale(scale, RoundingMode.HALF_UP)
                }
                RateModel.FIXED -> bpsToDecimalRate(contractedRateBps!!, scale)
                RateModel.IPCA_PLUS -> {
                    val ipca = bpsToDecimalRate(ipcaAnnualRateBps!!, scale)
                    val spread = bpsToDecimalRate(contractedRateBps!!, scale)
                    one.add(ipca).multiply(one.add(spread)).subtract(one).setScale(scale, RoundingMode.HALF_UP)
                }
            }

        if (effectiveAnnualRate.compareTo(BigDecimal("-1")) <= 0) {
            throw FixedIncomeInvalidEffectiveRateException(effectiveAnnualRate)
        }
    }

    fun baseProduct(): FixedIncomeProduct = product ?: defaultProductForGroup(productGroup)
}
