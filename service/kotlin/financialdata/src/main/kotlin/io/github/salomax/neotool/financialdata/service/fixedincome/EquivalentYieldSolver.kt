package io.github.salomax.neotool.financialdata.service.fixedincome

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeComparisonInput
import io.github.salomax.neotool.financialdata.domain.fixedincome.RateModel
import io.github.salomax.neotool.financialdata.domain.fixedincome.bpsToDecimalRate
import io.github.salomax.neotool.financialdata.domain.fixedincome.decimalRateToBps
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.temporal.ChronoUnit
import kotlin.math.pow

private const val SCALE = 10
private val NEGATIVE_ONE = BigDecimal("-1")

/**
 * Finds the contracted rate in a target rate model that produces the same net value at maturity as the base scenario.
 * Uses closed-form inversion of the growth and tax formulas (no iterative search).
 */
@TraceExecution
@Singleton
class EquivalentYieldSolver(
    private val taxPolicyService: TaxPolicyService,
    private val dayCountService: DayCountService,
    private val rateModelCalculator: RateModelCalculator,
) {
    /**
     * Find contracted rate in [targetRateModel] that yields [targetNet] at maturity, given base scenario context.
     * @return the contracted rate (interpretation depends on targetRateModel: CDI %, FIXED decimal, IPCA+ spread %), or null
     */
    fun solveEquivalentRate(
        baseInput: FixedIncomeComparisonInput,
        targetNet: BigDecimal,
        targetRateModel: RateModel,
    ): Int? {
        val calendarDays = ChronoUnit.DAYS.between(baseInput.startDate, baseInput.endDate).toInt()
        if (calendarDays <= 0) {
            return 0
        }
        val businessDays = dayCountService.businessDays(calendarDays)
        val grossTarget = grossFromNet(baseInput, targetNet, calendarDays) ?: return null
        val effectiveAnnualRate = effectiveRateFromGross(baseInput.principal, grossTarget, businessDays) ?: return null
        return contractedRateFromEffective(baseInput, targetRateModel, effectiveAnnualRate)
    }

    private fun grossFromNet(
        baseInput: FixedIncomeComparisonInput,
        targetNet: BigDecimal,
        calendarDays: Int,
    ): BigDecimal? {
        if (!taxPolicyService.isTaxable(baseInput.productGroup)) {
            return targetNet.setScale(SCALE, RoundingMode.HALF_UP)
        }
        if (targetNet.compareTo(baseInput.principal) <= 0) {
            return targetNet.setScale(SCALE, RoundingMode.HALF_UP)
        }
        val taxRate = taxPolicyService.irBracketRate(calendarDays)
        val denominator = BigDecimal.ONE.subtract(taxRate)
        if (denominator.compareTo(BigDecimal.ZERO) == 0) return null
        val numerator = targetNet.subtract(baseInput.principal.multiply(taxRate))
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP)
    }

    private fun effectiveRateFromGross(
        principal: BigDecimal,
        gross: BigDecimal,
        businessDays: Int,
    ): BigDecimal? {
        if (principal <= BigDecimal.ZERO || businessDays <= 0 || gross <= BigDecimal.ZERO) return null
        val growthFactor = gross.divide(principal, SCALE, RoundingMode.HALF_UP)
        if (growthFactor <= BigDecimal.ZERO) return null
        val exponent = 252.0 / businessDays.toDouble()
        val onePlusRate = BigDecimal.valueOf(growthFactor.toDouble().pow(exponent))
        return onePlusRate.subtract(BigDecimal.ONE).setScale(SCALE, RoundingMode.HALF_UP)
    }

    private fun contractedRateFromEffective(
        baseInput: FixedIncomeComparisonInput,
        targetRateModel: RateModel,
        effectiveAnnualRate: BigDecimal,
    ): Int? =
        when (targetRateModel) {
            RateModel.FIXED -> decimalRateToBps(effectiveAnnualRate.setScale(SCALE, RoundingMode.HALF_UP))
            RateModel.CDI -> contractedCdiBps(baseInput.cdiAnnualRateBps, effectiveAnnualRate)
            RateModel.IPCA_PLUS -> ipcaSpreadBps(baseInput.ipcaAnnualRateBps, effectiveAnnualRate)
        }

    private fun contractedCdiBps(
        cdiAnnualRateBps: Int?,
        effectiveAnnualRate: BigDecimal,
    ): Int? {
        val cdiAnnualDecimal = bpsToDecimalRate(cdiAnnualRateBps ?: 0, SCALE)
        if (cdiAnnualDecimal.compareTo(BigDecimal.ZERO) == 0) {
            return if (effectiveAnnualRate.compareTo(BigDecimal.ZERO) == 0) {
                0
            } else {
                null
            }
        }
        val contractedMultiplierDecimal =
            effectiveAnnualRate
                .divide(cdiAnnualDecimal, SCALE, RoundingMode.HALF_UP)
                .setScale(SCALE, RoundingMode.HALF_UP)
        return decimalRateToBps(contractedMultiplierDecimal)
    }

    private fun ipcaSpreadBps(
        ipcaAnnualRateBps: Int?,
        effectiveAnnualRate: BigDecimal,
    ): Int? {
        val ipcaAnnualDecimal = bpsToDecimalRate(ipcaAnnualRateBps ?: 0, SCALE)
        val baseFactor = BigDecimal.ONE.add(ipcaAnnualDecimal)
        if (baseFactor.compareTo(BigDecimal.ZERO) == 0) {
            return if (effectiveAnnualRate.compareTo(NEGATIVE_ONE) == 0) {
                0
            } else {
                null
            }
        }
        val spreadDecimal =
            BigDecimal.ONE
                .add(effectiveAnnualRate)
                .divide(baseFactor, SCALE, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
        return decimalRateToBps(spreadDecimal.setScale(SCALE, RoundingMode.HALF_UP))
    }

    /**
     * Returns (gross, ir, net) for the given input. Used by orchestrator for base and equivalent rows.
     */
    fun computeGrossIrNet(input: FixedIncomeComparisonInput): Triple<BigDecimal, BigDecimal, BigDecimal> {
        val calendarDays = ChronoUnit.DAYS.between(input.startDate, input.endDate).toInt()
        if (calendarDays <= 0) {
            val p = input.principal.setScale(SCALE, RoundingMode.HALF_UP)
            return Triple(p, BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP), p)
        }
        val businessDays = dayCountService.businessDays(calendarDays)
        val rate = rateModelCalculator.effectiveAnnualRate(input)
        val gross = rateModelCalculator.gross(input.principal, rate, businessDays)
        val taxable = taxPolicyService.isTaxable(input.productGroup)
        val tax =
            if (taxable) {
                val profit = gross.subtract(input.principal)
                val bracketRate = taxPolicyService.irBracketRate(calendarDays)
                if (profit.compareTo(BigDecimal.ZERO) > 0) {
                    profit.multiply(bracketRate).setScale(SCALE, RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
                }
            } else {
                BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
            }
        val net = gross.subtract(tax).setScale(SCALE, RoundingMode.HALF_UP)
        return Triple(gross, tax, net)
    }
}
