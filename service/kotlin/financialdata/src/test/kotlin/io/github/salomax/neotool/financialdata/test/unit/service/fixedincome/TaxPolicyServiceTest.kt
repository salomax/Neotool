package io.github.salomax.neotool.financialdata.test.unit.service.fixedincome

import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeProduct
import io.github.salomax.neotool.financialdata.service.fixedincome.TaxPolicyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TaxPolicyServiceTest {
    private val service = TaxPolicyService()

    @Test
    fun `CDB and LC and DEBENTURE are taxable`() {
        assertThat(service.isTaxable(FixedIncomeProduct.CDB)).isTrue()
        assertThat(service.isTaxable(FixedIncomeProduct.LC)).isTrue()
        assertThat(service.isTaxable(FixedIncomeProduct.DEBENTURE)).isTrue()
    }

    @Test
    fun `LCI and LCA and FREE_TAX_DEBENTURE are exempt`() {
        assertThat(service.isTaxable(FixedIncomeProduct.LCI)).isFalse()
        assertThat(service.isTaxable(FixedIncomeProduct.LCA)).isFalse()
        assertThat(service.isTaxable(FixedIncomeProduct.FREE_TAX_DEBENTURE)).isFalse()
    }

    @Test
    fun `IR bracket 180 days is 22_5 percent`() {
        val rate = service.irBracketRate(180)
        assertThat(rate).isEqualByComparingTo("0.225")
    }

    @Test
    fun `IR bracket 181 days is 20 percent`() {
        val rate = service.irBracketRate(181)
        assertThat(rate).isEqualByComparingTo("0.20")
    }

    @Test
    fun `IR bracket 360 days is 20 percent`() {
        assertThat(service.irBracketRate(360)).isEqualByComparingTo("0.20")
    }

    @Test
    fun `IR bracket 361 days is 17_5 percent`() {
        assertThat(service.irBracketRate(361)).isEqualByComparingTo("0.175")
    }

    @Test
    fun `IR bracket 720 days is 17_5 percent`() {
        assertThat(service.irBracketRate(720)).isEqualByComparingTo("0.175")
    }

    @Test
    fun `IR bracket 721 days is 15 percent`() {
        assertThat(service.irBracketRate(721)).isEqualByComparingTo("0.15")
    }
}
