package io.github.salomax.neotool.financialdata.test.unit.service.fixedincome

import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeComparisonInput
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeProduct
import io.github.salomax.neotool.financialdata.domain.fixedincome.ProductGroup
import io.github.salomax.neotool.financialdata.domain.fixedincome.RateModel
import io.github.salomax.neotool.financialdata.domain.fixedincome.productGroup
import io.github.salomax.neotool.financialdata.service.fixedincome.DayCountService
import io.github.salomax.neotool.financialdata.service.fixedincome.EquivalentYieldSolver
import io.github.salomax.neotool.financialdata.service.fixedincome.FixedIncomeComparisonService
import io.github.salomax.neotool.financialdata.service.fixedincome.RateModelCalculator
import io.github.salomax.neotool.financialdata.service.fixedincome.TaxPolicyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Golden vector tests: assert engine output is deterministic and within acceptable tolerance.
 * Uses wider tolerance (order of 1 BRL) to allow for rounding and day-count conventions.
 */
class FixedIncomeComparisonGoldenTest {
    private val service =
        FixedIncomeComparisonService(
            TaxPolicyService(),
            DayCountService(),
            EquivalentYieldSolver(TaxPolicyService(), DayCountService(), RateModelCalculator()),
        )

    private fun input(
        product: FixedIncomeProduct,
        rateModel: RateModel,
        calendarDays: Int,
        contractedRateBps: Int,
        cdiAnnualRateBps: Int? = 1_200,
        ipcaAnnualRateBps: Int? = 600,
    ): FixedIncomeComparisonInput {
        val start = LocalDate.of(2024, 1, 1)
        val maturity = start.plusDays(calendarDays.toLong())
        return FixedIncomeComparisonInput(
            productGroup = productGroup(product),
            product = product,
            principal = BigDecimal("10000"),
            startDate = start,
            endDate = maturity,
            rateModel = rateModel,
            contractedRateBps = contractedRateBps,
            cdiAnnualRateBps = cdiAnnualRateBps,
            ipcaAnnualRateBps = ipcaAnnualRateBps,
        )
    }

    @Test
    fun `scenario 1 CDB CDI 360 days`() {
        val result =
            service.compare(
                input(
                    product = FixedIncomeProduct.CDB,
                    rateModel = RateModel.CDI,
                    calendarDays = 360,
                    contractedRateBps = 10_000,
                    cdiAnnualRateBps = 1_200,
                ),
            )
        val base = result.rows.first()
        assertThat(base.gross).isBetween(BigDecimal("11160"), BigDecimal("11196"))
        assertThat(base.ir).isBetween(BigDecimal("225"), BigDecimal("270"))
        assertThat(base.net).isBetween(BigDecimal("10925"), BigDecimal("10950"))
    }

    @Test
    fun `scenario 2 LCI CDI 360 days`() {
        val result =
            service.compare(
                input(
                    product = FixedIncomeProduct.LCI,
                    rateModel = RateModel.CDI,
                    calendarDays = 360,
                    contractedRateBps = 10_000,
                    cdiAnnualRateBps = 1_200,
                ),
            )
        val base = result.rows.first()
        assertThat(base.gross).isBetween(BigDecimal("11180"), BigDecimal("11190"))
        assertThat(base.ir).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(base.net).isBetween(BigDecimal("11180"), BigDecimal("11190"))
    }

    @Test
    fun `scenario 3 CDB FIXED 181 days`() {
        val result =
            service.compare(
                input(
                    product = FixedIncomeProduct.CDB,
                    rateModel = RateModel.FIXED,
                    calendarDays = 181,
                    contractedRateBps = 1_000,
                ),
            )
        val base = result.rows.first()
        assertThat(base.gross).isBetween(BigDecimal("10483.50"), BigDecimal("10484.50"))
        assertThat(base.ir).isBetween(BigDecimal("96.50"), BigDecimal("97.10"))
        assertThat(base.net).isBetween(BigDecimal("10387.00"), BigDecimal("10387.40"))
    }

    @Test
    fun `scenario 4 LCA IPCA_PLUS 365 days`() {
        val result =
            service.compare(
                input(
                    product = FixedIncomeProduct.LCA,
                    rateModel = RateModel.IPCA_PLUS,
                    calendarDays = 365,
                    contractedRateBps = 500,
                    ipcaAnnualRateBps = 600,
                ),
            )
        val base = result.rows.first()
        assertThat(base.gross).isBetween(BigDecimal("11129.99"), BigDecimal("11130.01"))
        assertThat(base.ir).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(base.net).isBetween(BigDecimal("11129.99"), BigDecimal("11130.01"))
    }

    @Test
    fun `scenario 5 CDB CDI 180 days boundary`() {
        val result =
            service.compare(
                input(
                    product = FixedIncomeProduct.CDB,
                    rateModel = RateModel.CDI,
                    calendarDays = 180,
                    contractedRateBps = 10_000,
                    cdiAnnualRateBps = 1_200,
                ),
            )
        val base = result.rows.first()
        assertThat(base.gross).isBetween(BigDecimal("10570"), BigDecimal("10580"))
        assertThat(base.ir).isBetween(BigDecimal("125"), BigDecimal("132"))
        assertThat(base.net).isBetween(BigDecimal("10440"), BigDecimal("10450"))
    }

    @Test
    fun `scenario 6 CDB CDI 361 days boundary`() {
        val result =
            service.compare(
                input(
                    product = FixedIncomeProduct.CDB,
                    rateModel = RateModel.CDI,
                    calendarDays = 361,
                    contractedRateBps = 10_000,
                    cdiAnnualRateBps = 1_200,
                ),
            )
        val base = result.rows.first()
        assertThat(base.gross).isBetween(BigDecimal("11180"), BigDecimal("11190"))
        assertThat(base.ir).isBetween(BigDecimal("203"), BigDecimal("210"))
        assertThat(base.net).isBetween(BigDecimal("10973"), BigDecimal("10982"))
    }

    @Test
    fun `scenario 7 CDB FIXED 720 days boundary`() {
        val result =
            service.compare(
                input(
                    product = FixedIncomeProduct.CDB,
                    rateModel = RateModel.FIXED,
                    calendarDays = 720,
                    contractedRateBps = 1_000,
                ),
            )
        val base = result.rows.first()
        assertThat(base.gross).isBetween(BigDecimal("12065"), BigDecimal("12075"))
        assertThat(base.ir).isBetween(BigDecimal("358"), BigDecimal("368"))
        assertThat(base.net).isBetween(BigDecimal("11700"), BigDecimal("11715"))
    }

    @Test
    fun `scenario 8 CDB FIXED 721 days boundary`() {
        val result =
            service.compare(
                input(
                    product = FixedIncomeProduct.CDB,
                    rateModel = RateModel.FIXED,
                    calendarDays = 721,
                    contractedRateBps = 1_000,
                ),
            )
        val base = result.rows.first()
        assertThat(base.gross).isBetween(BigDecimal("12065"), BigDecimal("12075"))
        assertThat(base.ir).isBetween(BigDecimal("308"), BigDecimal("318"))
        assertThat(base.net).isBetween(BigDecimal("11752"), BigDecimal("11765"))
    }

    @Test
    fun `result has 2x3 rows with base first`() {
        val result =
            service.compare(
                input(
                    product = FixedIncomeProduct.CDB,
                    rateModel = RateModel.CDI,
                    calendarDays = 360,
                    contractedRateBps = 10_000,
                    cdiAnnualRateBps = 1_200,
                ),
            )
        assertThat(result.rows).hasSize(6)
        assertThat(result.rows.map { it.productGroup }.toSet())
            .containsExactlyInAnyOrder(ProductGroup.TAXABLE_IR, ProductGroup.EXEMPT_IR)
        assertThat(result.rows.map { it.rateModel }.toSet())
            .containsExactlyInAnyOrder(RateModel.CDI, RateModel.FIXED, RateModel.IPCA_PLUS)
        assertThat(result.rows.count { it.isBase }).isEqualTo(1)
        assertThat(result.rows.first().isBase).isTrue()
        assertThat(result.rows.first().productGroup).isEqualTo(ProductGroup.TAXABLE_IR)
        assertThat(result.rows.first().rateModel).isEqualTo(RateModel.CDI)
        assertThat(result.rows.first().equivalentRateBps).isEqualTo(10_000)
        assertThat(result.performanceSeries).hasSize(2)
        assertThat(result.performanceSeries.map { it.productGroup }.toSet())
            .containsExactlyInAnyOrder(ProductGroup.TAXABLE_IR, ProductGroup.EXEMPT_IR)
        result.performanceSeries.forEach { series ->
            assertThat(series.points).isNotEmpty
            assertThat(series.points.first().date).isEqualTo(LocalDate.of(2024, 1, 1))
            assertThat(series.points.last().date).isEqualTo(LocalDate.of(2024, 12, 26))
        }

        val taxableFinal =
            result.performanceSeries
                .first { it.productGroup == ProductGroup.TAXABLE_IR }
                .points.last().netValue
        val exemptFinal =
            result.performanceSeries
                .first { it.productGroup == ProductGroup.EXEMPT_IR }
                .points.last().netValue
        assertThat(
            taxableFinal,
        ).isBetween(exemptFinal.subtract(BigDecimal("0.50")), exemptFinal.add(BigDecimal("0.50")))
    }
}
