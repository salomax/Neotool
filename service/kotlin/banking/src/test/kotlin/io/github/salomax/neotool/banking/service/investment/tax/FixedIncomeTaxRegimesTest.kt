package io.github.salomax.neotool.banking.service.investment.tax

import io.github.salomax.neotool.common.finance.tax.brazil.fixedincome.BrazilianFixedIncomeTaxRules
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

class FixedIncomeTaxRegimesTest {
    @ParameterizedTest
    @ValueSource(ints = [180, 360, 720, 721])
    fun `regressive final rate delegates across every IR bracket`(elapsedDays: Int) {
        assertThat(FixedIncomeRegressiveRegime.finalRate(elapsedDays))
            .isEqualTo(BrazilianFixedIncomeTaxRules.irRate(elapsedDays))
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 29, 30, 900])
    fun `regressive regime has IOF but no periodic withholding`(elapsedDays: Int) {
        assertThat(FixedIncomeRegressiveRegime.iofRate(elapsedDays))
            .isEqualTo(BrazilianFixedIncomeTaxRules.iofRate(elapsedDays))
        assertThat(FixedIncomeRegressiveRegime.periodicWithholdingRate(elapsedDays)).isNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 30, 900])
    fun `tax-exempt regime always has zero final rate and no periodic withholding`(elapsedDays: Int) {
        assertThat(TaxExemptRegime.finalRate(elapsedDays)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(TaxExemptRegime.periodicWithholdingRate(elapsedDays)).isNull()
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 29, 30, 900])
    fun `tax-exempt regime delegates IOF independently of IR exemption`(elapsedDays: Int) {
        assertThat(TaxExemptRegime.iofRate(elapsedDays))
            .isEqualTo(BrazilianFixedIncomeTaxRules.iofRate(elapsedDays))
    }
}
