package io.github.salomax.neotool.financialdata.service.fixedincome

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeComparisonInput
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeInvalidEffectiveRateException
import io.github.salomax.neotool.financialdata.domain.fixedincome.RateModel
import io.github.salomax.neotool.financialdata.domain.fixedincome.bpsToDecimalRate
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode

private const val SCALE = 10

/**
 * Effective annual rate and gross value for each rate model.
 * 252 business-day convention; all values scale 10, HALF_UP.
 */
@TraceExecution
@Singleton
class RateModelCalculator {
    /**
     * Effective annual rate (decimal) for the given input's rate model.
     */
    fun effectiveAnnualRate(input: FixedIncomeComparisonInput): BigDecimal =
        when (input.rateModel) {
            RateModel.CDI ->
                effectiveAnnualRateCdi(
                    cdiAnnualRateBps = input.cdiAnnualRateBps!!,
                    contractedRateBps = input.contractedRateBps!!,
                )
            RateModel.FIXED -> effectiveAnnualRateFixed(input.contractedRateBps!!)
            RateModel.IPCA_PLUS ->
                effectiveAnnualRateIpcaPlus(
                    ipcaAnnualRateBps = input.ipcaAnnualRateBps!!,
                    spreadAnnualRateBps = input.contractedRateBps!!,
                )
        }

    /**
     * %CDI: effective_annual = CDI_annual_decimal * contracted_cdi_multiplier_decimal.
     */
    fun effectiveAnnualRateCdi(
        cdiAnnualRateBps: Int,
        contractedRateBps: Int,
    ): BigDecimal =
        bpsToDecimalRate(cdiAnnualRateBps, SCALE)
            .multiply(bpsToDecimalRate(contractedRateBps, SCALE))
            .setScale(SCALE, java.math.RoundingMode.HALF_UP)

    /**
     * FIXED: contracted rate is annual decimal in bps.
     */
    fun effectiveAnnualRateFixed(contractedRateBps: Int): BigDecimal = bpsToDecimalRate(contractedRateBps, SCALE)

    /**
     * IPCA+: (1 + ipca_annual) * (1 + spread_annual) - 1.
     * Inputs as bps (e.g. 600 for 6%).
     */
    fun effectiveAnnualRateIpcaPlus(
        ipcaAnnualRateBps: Int,
        spreadAnnualRateBps: Int,
    ): BigDecimal {
        val ipca = bpsToDecimalRate(ipcaAnnualRateBps, SCALE)
        val spread = bpsToDecimalRate(spreadAnnualRateBps, SCALE)
        val one = BigDecimal.ONE
        val factor = (one.add(ipca)).multiply(one.add(spread)).setScale(SCALE, java.math.RoundingMode.HALF_UP)
        return factor.subtract(one).setScale(SCALE, java.math.RoundingMode.HALF_UP)
    }

    /**
     * Gross value at maturity: principal * (1 + effectiveAnnualRate)^(businessDays/252).
     */
    fun gross(
        principal: BigDecimal,
        effectiveAnnualRate: BigDecimal,
        businessDays: Int,
    ): BigDecimal {
        val onePlusRate = BigDecimal.ONE.add(effectiveAnnualRate)
        if (onePlusRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw FixedIncomeInvalidEffectiveRateException(effectiveAnnualRate)
        }
        val exponent = businessDays / 252.0
        val factor =
            BigDecimal.valueOf(
                java.lang.Math.pow(onePlusRate.toDouble(), exponent),
            )
        return principal.multiply(factor).setScale(SCALE, RoundingMode.HALF_UP)
    }
}
