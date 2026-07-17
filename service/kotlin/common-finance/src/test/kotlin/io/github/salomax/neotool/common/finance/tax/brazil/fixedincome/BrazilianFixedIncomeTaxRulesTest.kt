package io.github.salomax.neotool.common.finance.tax.brazil.fixedincome

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("BrazilianFixedIncomeTaxRules")
class BrazilianFixedIncomeTaxRulesTest {
    @Test
    fun `returns correct IR brackets`() {
        assertThat(BrazilianFixedIncomeTaxRules.irRate(180).toDouble()).isEqualTo(0.225)
        assertThat(BrazilianFixedIncomeTaxRules.irRate(181).toDouble()).isEqualTo(0.200)
        assertThat(BrazilianFixedIncomeTaxRules.irRate(360).toDouble()).isEqualTo(0.200)
        assertThat(BrazilianFixedIncomeTaxRules.irRate(361).toDouble()).isEqualTo(0.175)
        assertThat(BrazilianFixedIncomeTaxRules.irRate(720).toDouble()).isEqualTo(0.175)
        assertThat(BrazilianFixedIncomeTaxRules.irRate(721).toDouble()).isEqualTo(0.150)
    }

    @Test
    fun `returns zero IR for exempt or non-positive gain`() {
        assertThat(
            BrazilianFixedIncomeTaxRules.computeIr(BigDecimal("100.00"), 200, true),
        ).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(
            BrazilianFixedIncomeTaxRules.computeIr(BigDecimal.ZERO, 200, false),
        ).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(
            BrazilianFixedIncomeTaxRules.computeIr(BigDecimal("-10.00"), 200, false),
        ).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `computes IR over taxable gain`() {
        val ir = BrazilianFixedIncomeTaxRules.computeIr(BigDecimal("1000.00"), 200, false)
        assertThat(ir).isEqualByComparingTo(BigDecimal("200.000"))
    }

    @Test
    fun `returns correct IOF rates`() {
        assertThat(BrazilianFixedIncomeTaxRules.iofRate(1).toDouble()).isCloseTo(0.96, offset(0.001))
        assertThat(BrazilianFixedIncomeTaxRules.iofRate(29).toDouble()).isCloseTo(0.03, offset(0.001))
        assertThat(BrazilianFixedIncomeTaxRules.iofRate(30).toDouble()).isCloseTo(0.0, offset(0.001))
        assertThat(BrazilianFixedIncomeTaxRules.iofRate(31).toDouble()).isCloseTo(0.0, offset(0.001))
    }

    @Test
    fun `returns zero IOF after 30 days or for non-positive gain`() {
        val gain = BigDecimal("100.00")
        assertThat(BrazilianFixedIncomeTaxRules.computeIof(gain, 30)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(BrazilianFixedIncomeTaxRules.computeIof(gain, 45)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(BrazilianFixedIncomeTaxRules.computeIof(BigDecimal.ZERO, 5)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(
            BrazilianFixedIncomeTaxRules.computeIof(BigDecimal("-1.00"), 5),
        ).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `computes IOF over gain`() {
        assertThat(BrazilianFixedIncomeTaxRules.computeIof(BigDecimal("100.00"), 1))
            .isEqualByComparingTo(BigDecimal("96.0000"))
    }
}
