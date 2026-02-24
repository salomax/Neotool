package io.github.salomax.neotool.financialdata.test.unit.domain.fixedincome

import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeComparisonInput
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeProduct
import io.github.salomax.neotool.financialdata.domain.fixedincome.ProductGroup
import io.github.salomax.neotool.financialdata.domain.fixedincome.RateModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

class FixedIncomeComparisonInputTest {
    @Test
    fun `creates valid CDI input`() {
        val input =
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.TAXABLE_IR,
                product = FixedIncomeProduct.CDB,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 12, 26),
                rateModel = RateModel.CDI,
                contractedRateBps = 10_000,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        assertThat(input.product).isEqualTo(FixedIncomeProduct.CDB)
        assertThat(input.productGroup).isEqualTo(ProductGroup.TAXABLE_IR)
        assertThat(input.rateModel).isEqualTo(RateModel.CDI)
        assertThat(input.contractedRateBps).isEqualTo(10_000)
        assertThat(input.cdiAnnualRateBps).isEqualTo(1_200)
    }

    @Test
    fun `creates valid FIXED input`() {
        val input =
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.EXEMPT_IR,
                product = FixedIncomeProduct.LCI,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2025, 1, 1),
                rateModel = RateModel.FIXED,
                contractedRateBps = 1_000,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        assertThat(input.rateModel).isEqualTo(RateModel.FIXED)
        assertThat(input.contractedRateBps).isEqualTo(1_000)
    }

    @Test
    fun `creates valid IPCA_PLUS input`() {
        val input =
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.EXEMPT_IR,
                product = FixedIncomeProduct.LCA,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2025, 1, 1),
                rateModel = RateModel.IPCA_PLUS,
                contractedRateBps = 500,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        assertThat(input.rateModel).isEqualTo(RateModel.IPCA_PLUS)
        assertThat(input.contractedRateBps).isEqualTo(500)
        assertThat(input.ipcaAnnualRateBps).isEqualTo(600)
    }

    @Test
    fun `throws when principal is zero`() {
        assertThrows<IllegalArgumentException> {
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.TAXABLE_IR,
                product = FixedIncomeProduct.CDB,
                principal = BigDecimal.ZERO,
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 12, 26),
                rateModel = RateModel.CDI,
                contractedRateBps = 10_000,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        }
    }

    @Test
    fun `throws when maturity before start`() {
        assertThrows<IllegalArgumentException> {
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.TAXABLE_IR,
                product = FixedIncomeProduct.CDB,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 12, 26),
                endDate = LocalDate.of(2024, 1, 1),
                rateModel = RateModel.CDI,
                contractedRateBps = 10_000,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        }
    }

    @Test
    fun `throws when maturity equals start`() {
        assertThrows<IllegalArgumentException> {
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.TAXABLE_IR,
                product = FixedIncomeProduct.CDB,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 1, 1),
                rateModel = RateModel.CDI,
                contractedRateBps = 10_000,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        }
    }

    @Test
    fun `throws when CDI model missing contractedRateBps`() {
        assertThrows<IllegalArgumentException> {
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.TAXABLE_IR,
                product = FixedIncomeProduct.CDB,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 12, 26),
                rateModel = RateModel.CDI,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        }
    }

    @Test
    fun `throws when cdiAnnualRateBps is missing`() {
        assertThrows<IllegalArgumentException> {
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.TAXABLE_IR,
                product = FixedIncomeProduct.CDB,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 12, 26),
                rateModel = RateModel.FIXED,
                contractedRateBps = 1_000,
                ipcaAnnualRateBps = 600,
            )
        }
    }

    @Test
    fun `throws when ipcaAnnualRateBps is missing`() {
        assertThrows<IllegalArgumentException> {
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.TAXABLE_IR,
                product = FixedIncomeProduct.CDB,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 12, 26),
                rateModel = RateModel.FIXED,
                contractedRateBps = 1_000,
                cdiAnnualRateBps = 1_200,
            )
        }
    }

    @Test
    fun `throws when effective annual rate is less than or equal to minus one`() {
        assertThrows<IllegalArgumentException> {
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.TAXABLE_IR,
                product = FixedIncomeProduct.CDB,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 12, 26),
                rateModel = RateModel.FIXED,
                contractedRateBps = -10_000,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        }
    }

    @Test
    fun `throws when product does not match productGroup`() {
        assertThrows<IllegalArgumentException> {
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.EXEMPT_IR,
                product = FixedIncomeProduct.CDB,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 12, 26),
                rateModel = RateModel.FIXED,
                contractedRateBps = 1_000,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        }
    }
}
