package io.github.salomax.neotool.banking.service.investment

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Pure Brazilian fixed income tax rules.
 *
 * Covers:
 * - IR (Imposto de Renda) — regressive table based on calendar days from issue.
 * - IOF (Imposto sobre Operações Financeiras) — 30-day regressive table on gross gain.
 *
 * Tax-exempt products (LCI, LCA, CRI, CRA, incentivized debentures) have [isTaxExempt] = true;
 * in that case [computeIr] returns zero.
 *
 * This object is intentionally stateless and dependency-free so it can be reused
 * by valuation, simulation, and any other domain component that needs Brazilian tax math.
 */
object BrazilianFixedIncomeTaxRules {

    private val MC = MathContext(12, RoundingMode.HALF_UP)

    // ── IR ────────────────────────────────────────────────────────────────────

    /**
     * Returns the IR rate applicable to a redemption after [calendarDays] from issue.
     * Based on the regressive table (Lei 11.033/2004).
     */
    fun irRate(calendarDays: Int): BigDecimal =
        when {
            calendarDays <= 180 -> BigDecimal("0.225")
            calendarDays <= 360 -> BigDecimal("0.200")
            calendarDays <= 720 -> BigDecimal("0.175")
            else -> BigDecimal("0.150")
        }

    /**
     * Computes the IR amount on [taxableGain] (gain already net of IOF).
     * Returns zero when [isTaxExempt] is true or [taxableGain] ≤ 0.
     */
    fun computeIr(
        taxableGain: BigDecimal,
        calendarDays: Int,
        isTaxExempt: Boolean,
    ): BigDecimal {
        if (isTaxExempt || taxableGain <= BigDecimal.ZERO) return BigDecimal.ZERO
        return taxableGain.multiply(irRate(calendarDays), MC)
    }

    // ── IOF ───────────────────────────────────────────────────────────────────

    /**
     * Returns the IOF rate for a redemption on day [calendarDays] from issue.
     * Returns zero for day ≥ 30 (IOF no longer applies).
     */
    fun iofRate(calendarDays: Int): BigDecimal {
        if (calendarDays >= 30) return BigDecimal.ZERO
        val index = (calendarDays - 1).coerceIn(0, 29)
        return IOF_TABLE[index]
    }

    /**
     * Computes the IOF amount on [gain].
     * Returns zero when [calendarDays] ≥ 30 or [gain] ≤ 0.
     */
    fun computeIof(
        gain: BigDecimal,
        calendarDays: Int,
    ): BigDecimal {
        if (calendarDays >= 30 || gain <= BigDecimal.ZERO) return BigDecimal.ZERO
        return gain.multiply(iofRate(calendarDays), MC)
    }

    // ── IOF table (day 1–30) ─────────────────────────────────────────────────

    /** Regressive IOF rates indexed by (calendarDay - 1). Day 30+ → 0%. */
    private val IOF_TABLE =
        listOf(
            BigDecimal("0.96"), // day 1
            BigDecimal("0.93"), // day 2
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
            BigDecimal("0.03"), // day 29
            BigDecimal.ZERO,    // day 30
        )
}
