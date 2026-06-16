package io.github.salomax.neotool.common.finance.tax.brazil.fixedincome

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Pure Brazilian fixed income tax rules.
 *
 * Covers:
 * - IR (Imposto de Renda) — regressive table based on calendar days from issue.
 * - IOF (Imposto sobre Operações Financeiras) — 30-day regressive table on gross gain.
 */
object BrazilianFixedIncomeTaxRules {
    private val mc = MathContext(12, RoundingMode.HALF_UP)

    fun irRate(calendarDays: Int): BigDecimal =
        when {
            calendarDays <= 180 -> BigDecimal("0.225")
            calendarDays <= 360 -> BigDecimal("0.200")
            calendarDays <= 720 -> BigDecimal("0.175")
            else -> BigDecimal("0.150")
        }

    fun computeIr(
        taxableGain: BigDecimal,
        calendarDays: Int,
        isTaxExempt: Boolean,
    ): BigDecimal {
        if (isTaxExempt || taxableGain <= BigDecimal.ZERO) return BigDecimal.ZERO
        return taxableGain.multiply(irRate(calendarDays), mc)
    }

    fun iofRate(calendarDays: Int): BigDecimal {
        if (calendarDays >= 30) return BigDecimal.ZERO
        val index = (calendarDays - 1).coerceIn(0, 29)
        return iofTable[index]
    }

    fun computeIof(
        gain: BigDecimal,
        calendarDays: Int,
    ): BigDecimal {
        if (calendarDays >= 30 || gain <= BigDecimal.ZERO) return BigDecimal.ZERO
        return gain.multiply(iofRate(calendarDays), mc)
    }

    private val iofTable =
        listOf(
            BigDecimal("0.96"),
            BigDecimal("0.93"),
            BigDecimal("0.90"),
            BigDecimal("0.86"),
            BigDecimal("0.83"),
            BigDecimal("0.80"),
            BigDecimal("0.76"),
            BigDecimal("0.73"),
            BigDecimal("0.70"),
            BigDecimal("0.66"),
            BigDecimal("0.63"),
            BigDecimal("0.60"),
            BigDecimal("0.56"),
            BigDecimal("0.53"),
            BigDecimal("0.50"),
            BigDecimal("0.46"),
            BigDecimal("0.43"),
            BigDecimal("0.40"),
            BigDecimal("0.36"),
            BigDecimal("0.33"),
            BigDecimal("0.30"),
            BigDecimal("0.26"),
            BigDecimal("0.23"),
            BigDecimal("0.20"),
            BigDecimal("0.16"),
            BigDecimal("0.13"),
            BigDecimal("0.10"),
            BigDecimal("0.06"),
            BigDecimal("0.03"),
            BigDecimal.ZERO,
        )
}
