package io.github.salomax.neotool.banking.service.investment.tax

import io.github.salomax.neotool.common.finance.tax.brazil.fixedincome.BrazilianFixedIncomeTaxRules
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

class FundTaxRegimesTest {
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 30, 900])
    fun `fund no-tax regime reuses tax-exempt regime behavior explicitly`(elapsedDays: Int) {
        assertThat(FundNoTaxRegime.finalRate(elapsedDays)).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(FundNoTaxRegime.periodicWithholdingRate(elapsedDays)).isNull()
        assertThat(FundNoTaxRegime.iofRate(elapsedDays))
            .isEqualTo(TaxExemptRegime.iofRate(elapsedDays))
    }

    @ParameterizedTest
    @ValueSource(ints = [180, 360, 720, 721])
    fun `come-cotas long final rate delegates across every IR bracket`(elapsedDays: Int) {
        assertThat(ComeCotasLongRegime.finalRate(elapsedDays))
            .isEqualTo(BrazilianFixedIncomeTaxRules.irRate(elapsedDays))
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 180, 360, 900])
    fun `come-cotas long periodic withholding is always fifteen percent and iof delegates`(elapsedDays: Int) {
        assertThat(ComeCotasLongRegime.periodicWithholdingRate(elapsedDays))
            .isEqualByComparingTo(BigDecimal("0.15"))
        assertThat(ComeCotasLongRegime.iofRate(elapsedDays))
            .isEqualTo(BrazilianFixedIncomeTaxRules.iofRate(elapsedDays))
    }

    @ParameterizedTest
    @ValueSource(ints = [180, 181])
    fun `come-cotas short final rate flips at the one hundred eighty day boundary`(elapsedDays: Int) {
        val expected =
            if (elapsedDays <= 180) {
                BigDecimal("0.225")
            } else {
                BigDecimal("0.20")
            }

        assertThat(ComeCotasShortRegime.finalRate(elapsedDays)).isEqualByComparingTo(expected)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 180, 181, 900])
    fun `come-cotas short periodic withholding is always twenty percent and iof delegates`(elapsedDays: Int) {
        assertThat(ComeCotasShortRegime.periodicWithholdingRate(elapsedDays))
            .isEqualByComparingTo(BigDecimal("0.20"))
        assertThat(ComeCotasShortRegime.iofRate(elapsedDays))
            .isEqualTo(BrazilianFixedIncomeTaxRules.iofRate(elapsedDays))
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 180, 181, 900])
    fun `variable income fund final rate is flat fifteen percent with no periodic withholding`(elapsedDays: Int) {
        assertThat(VariableIncomeFundRegime.finalRate(elapsedDays)).isEqualByComparingTo(BigDecimal("0.15"))
        assertThat(VariableIncomeFundRegime.periodicWithholdingRate(elapsedDays)).isNull()
        assertThat(VariableIncomeFundRegime.iofRate(elapsedDays))
            .isEqualTo(BrazilianFixedIncomeTaxRules.iofRate(elapsedDays))
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 29, 30, 900])
    fun `no periodic withholding decorator delegates final rate and iof for come-cotas long`(elapsedDays: Int) {
        val decorated = NoPeriodicWithholding(ComeCotasLongRegime)

        assertThat(decorated.periodicWithholdingRate(elapsedDays)).isNull()
        assertThat(decorated.finalRate(elapsedDays))
            .isEqualTo(ComeCotasLongRegime.finalRate(elapsedDays))
        assertThat(decorated.iofRate(elapsedDays))
            .isEqualTo(ComeCotasLongRegime.iofRate(elapsedDays))
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 29, 30, 900])
    fun `no periodic withholding decorator delegates final rate and iof for fixed income regressive`(elapsedDays: Int) {
        val decorated = NoPeriodicWithholding(FixedIncomeRegressiveRegime)

        assertThat(decorated.periodicWithholdingRate(elapsedDays)).isNull()
        assertThat(decorated.finalRate(elapsedDays))
            .isEqualTo(FixedIncomeRegressiveRegime.finalRate(elapsedDays))
        assertThat(decorated.iofRate(elapsedDays))
            .isEqualTo(FixedIncomeRegressiveRegime.iofRate(elapsedDays))
    }
}
