package io.github.salomax.neotool.banking.domain.investment

import io.github.salomax.neotool.common.finance.tax.brazil.fixedincome.BrazilianFixedIncomeTaxRules
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("BrazilianFixedIncomeTaxRules")
class BrazilianFixedIncomeTaxRulesTest {
    // ── IR ────────────────────────────────────────────────────────────────────

    @Test
    fun `IR rate brackets follow regressive table`() {
        assertThat(BrazilianFixedIncomeTaxRules.irRate(180).toDouble()).isEqualTo(0.225)
        assertThat(BrazilianFixedIncomeTaxRules.irRate(181).toDouble()).isEqualTo(0.200)
        assertThat(BrazilianFixedIncomeTaxRules.irRate(360).toDouble()).isEqualTo(0.200)
        assertThat(BrazilianFixedIncomeTaxRules.irRate(361).toDouble()).isEqualTo(0.175)
        assertThat(BrazilianFixedIncomeTaxRules.irRate(720).toDouble()).isEqualTo(0.175)
        assertThat(BrazilianFixedIncomeTaxRules.irRate(721).toDouble()).isEqualTo(0.150)
    }

    @Test
    fun `computeIr returns zero for tax-exempt holding`() {
        val ir =
            BrazilianFixedIncomeTaxRules.computeIr(
                taxableGain = BigDecimal("500.00"),
                calendarDays = 200,
                isTaxExempt = true,
            )
        assertThat(ir).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `computeIr returns zero when taxable gain is zero or negative`() {
        assertThat(
            BrazilianFixedIncomeTaxRules.computeIr(BigDecimal.ZERO, 200, false),
        ).isEqualByComparingTo(BigDecimal.ZERO)

        assertThat(
            BrazilianFixedIncomeTaxRules.computeIr(BigDecimal("-10.00"), 200, false),
        ).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `computeIr applies correct bracket rate`() {
        val gain = BigDecimal("1000.00")
        // 200 days → 20%
        val ir = BrazilianFixedIncomeTaxRules.computeIr(gain, 200, false)
        assertThat(ir.toDouble()).isCloseTo(200.0, within(0.01))
    }

    // ── IOF ───────────────────────────────────────────────────────────────────

    @Test
    fun `IOF rate table boundary values`() {
        assertThat(BrazilianFixedIncomeTaxRules.iofRate(1).toDouble()).isCloseTo(0.96, within(0.001))
        assertThat(BrazilianFixedIncomeTaxRules.iofRate(29).toDouble()).isCloseTo(0.03, within(0.001))
        assertThat(BrazilianFixedIncomeTaxRules.iofRate(30).toDouble()).isCloseTo(0.0, within(0.001))
        assertThat(BrazilianFixedIncomeTaxRules.iofRate(31).toDouble()).isCloseTo(0.0, within(0.001))
    }

    @Test
    fun `computeIof returns zero at day 30 and beyond`() {
        val gain = BigDecimal("100.00")
        assertThat(BrazilianFixedIncomeTaxRules.computeIof(gain, 30)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(BrazilianFixedIncomeTaxRules.computeIof(gain, 45)).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `computeIof applies 96 percent on day 1`() {
        val gain = BigDecimal("100.00")
        assertThat(BrazilianFixedIncomeTaxRules.computeIof(gain, 1)).isEqualByComparingTo(BigDecimal("96.00"))
    }

    @Test
    fun `computeIof returns zero when gain is zero or negative`() {
        assertThat(BrazilianFixedIncomeTaxRules.computeIof(BigDecimal.ZERO, 5)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(
            BrazilianFixedIncomeTaxRules.computeIof(BigDecimal("-1.00"), 5),
        ).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `computeIof mid-table day 15 is between 0 and gain`() {
        val gain = BigDecimal("100.00")
        val iof = BrazilianFixedIncomeTaxRules.computeIof(gain, 15)
        assertThat(iof).isGreaterThan(BigDecimal.ZERO).isLessThan(gain)
    }
}
