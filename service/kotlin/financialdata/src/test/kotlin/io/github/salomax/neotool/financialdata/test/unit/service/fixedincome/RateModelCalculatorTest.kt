package io.github.salomax.neotool.financialdata.test.unit.service.fixedincome

import io.github.salomax.neotool.financialdata.service.fixedincome.RateModelCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RateModelCalculatorTest {
    private val calculator = RateModelCalculator()

    @Test
    fun `CDI effective rate 12 percent at 100 percent gives 12 percent`() {
        val rate =
            calculator.effectiveAnnualRateCdi(
                cdiAnnualRateBps = 1_200,
                contractedRateBps = 10_000,
            )
        assertThat(rate).isEqualByComparingTo("0.12")
    }

    @Test
    fun `FIXED effective rate is the decimal rate`() {
        val rate = calculator.effectiveAnnualRateFixed(1_000)
        assertThat(rate).isEqualByComparingTo("0.10")
    }

    @Test
    fun `IPCA_PLUS 6 percent and 5 percent spread gives 11_3 percent`() {
        val rate =
            calculator.effectiveAnnualRateIpcaPlus(
                ipcaAnnualRateBps = 600,
                spreadAnnualRateBps = 500,
            )
        assertThat(rate).isEqualByComparingTo("0.113")
    }

    @Test
    fun `gross for 360 days CDB 12 percent matches golden scenario 1`() {
        val principal = BigDecimal("10000")
        val effectiveRate = BigDecimal("0.12")
        val businessDays = 249
        val gross = calculator.gross(principal, effectiveRate, businessDays)
        assertThat(gross).isBetween(BigDecimal("11184.50"), BigDecimal("11185.50"))
    }

    @Test
    fun `gross for 181 days FIXED 10 percent matches golden scenario 3`() {
        val principal = BigDecimal("10000")
        val effectiveRate = BigDecimal("0.10")
        val businessDays = 125
        val gross = calculator.gross(principal, effectiveRate, businessDays)
        assertThat(gross).isBetween(BigDecimal("10483.50"), BigDecimal("10484.50"))
    }

    @Test
    fun `gross for 365 days IPCA_PLUS 11_3 percent matches golden scenario 4`() {
        val principal = BigDecimal("10000")
        val effectiveRate = BigDecimal("0.113")
        val businessDays = 252
        val gross = calculator.gross(principal, effectiveRate, businessDays)
        assertThat(gross).isBetween(BigDecimal("11129.99"), BigDecimal("11130.01"))
    }
}
