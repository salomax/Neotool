package io.github.salomax.neotool.financialdata.test.unit.service.fixedincome

import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeComparisonInput
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeProduct
import io.github.salomax.neotool.financialdata.domain.fixedincome.ProductGroup
import io.github.salomax.neotool.financialdata.domain.fixedincome.RateModel
import io.github.salomax.neotool.financialdata.service.fixedincome.DayCountService
import io.github.salomax.neotool.financialdata.service.fixedincome.EquivalentYieldSolver
import io.github.salomax.neotool.financialdata.service.fixedincome.RateModelCalculator
import io.github.salomax.neotool.financialdata.service.fixedincome.TaxPolicyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class EquivalentYieldSolverTest {
    private val taxPolicyService = TaxPolicyService()
    private val dayCountService = DayCountService()
    private val rateModelCalculator = RateModelCalculator()
    private val solver = EquivalentYieldSolver(taxPolicyService, dayCountService, rateModelCalculator)

    @Test
    fun `solveEquivalentRate returns base rate when target same as base model`() {
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
        val (_, _, net) = solver.computeGrossIrNet(input)
        val result = solver.solveEquivalentRate(input, net, RateModel.CDI)
        assertThat(result).isNotNull
        assertThat(result).isEqualTo(10_000)
    }

    @Test
    fun `solveEquivalentRate finds FIXED equivalent for CDI base`() {
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
        val (_, _, net) = solver.computeGrossIrNet(input)
        val equivalentFixed = solver.solveEquivalentRate(input, net, RateModel.FIXED)
        assertThat(equivalentFixed).isNotNull
        val synthetic =
            input.copy(
                rateModel = RateModel.FIXED,
                contractedRateBps = equivalentFixed,
            )
        val (_, _, netFromSynthetic) = solver.computeGrossIrNet(synthetic)
        assertThat(netFromSynthetic).isBetween(net.subtract(BigDecimal("0.50")), net.add(BigDecimal("0.50")))
    }

    @Test
    fun `solveEquivalentRate finds CDI above previous cap when required`() {
        val input =
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.EXEMPT_IR,
                product = FixedIncomeProduct.LCI,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 12, 26),
                rateModel = RateModel.FIXED,
                contractedRateBps = 3_000,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        val (_, _, targetNet) = solver.computeGrossIrNet(input)
        val equivalentCdi = solver.solveEquivalentRate(input, targetNet, RateModel.CDI)
        assertThat(equivalentCdi).isNotNull
        assertThat(equivalentCdi).isGreaterThan(20_000)

        val synthetic =
            input.copy(
                rateModel = RateModel.CDI,
                contractedRateBps = equivalentCdi,
            )
        val (_, _, netFromSynthetic) = solver.computeGrossIrNet(synthetic)
        assertThat(
            netFromSynthetic,
        ).isBetween(targetNet.subtract(BigDecimal("0.50")), targetNet.add(BigDecimal("0.50")))
    }

    @Test
    fun `solveEquivalentRate finds IPCA spread above previous cap when required`() {
        val input =
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.EXEMPT_IR,
                product = FixedIncomeProduct.LCI,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 12, 26),
                rateModel = RateModel.FIXED,
                contractedRateBps = 4_500,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 1_000,
            )
        val (_, _, targetNet) = solver.computeGrossIrNet(input)
        val equivalentSpread = solver.solveEquivalentRate(input, targetNet, RateModel.IPCA_PLUS)
        assertThat(equivalentSpread).isNotNull
        assertThat(equivalentSpread).isGreaterThan(2_000)

        val synthetic =
            input.copy(
                rateModel = RateModel.IPCA_PLUS,
                contractedRateBps = equivalentSpread,
            )
        val (_, _, netFromSynthetic) = solver.computeGrossIrNet(synthetic)
        assertThat(
            netFromSynthetic,
        ).isBetween(targetNet.subtract(BigDecimal("0.50")), targetNet.add(BigDecimal("0.50")))
    }

    @Test
    fun `computeNet for exempt product has zero tax`() {
        val input =
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.EXEMPT_IR,
                product = FixedIncomeProduct.LCI,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 12, 26),
                rateModel = RateModel.CDI,
                contractedRateBps = 10_000,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        val (gross, ir, net) = solver.computeGrossIrNet(input)
        assertThat(ir).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(net).isEqualByComparingTo(gross)
    }

    @Test
    fun `computeGrossIrNet does not apply negative tax when gross is below principal`() {
        val input =
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.TAXABLE_IR,
                product = FixedIncomeProduct.CDB,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 12, 26),
                rateModel = RateModel.FIXED,
                contractedRateBps = -2_000,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        val (gross, ir, net) = solver.computeGrossIrNet(input)
        assertThat(gross).isLessThan(BigDecimal("10000"))
        assertThat(ir).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(net).isEqualByComparingTo(gross)
    }

    @Test
    fun `computeGrossIrNet applies IOF only for taxable group with IOF`() {
        val withIof =
            FixedIncomeComparisonInput(
                productGroup = ProductGroup.TAXABLE_IR,
                product = FixedIncomeProduct.CDB,
                principal = BigDecimal("10000"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 1, 11),
                rateModel = RateModel.FIXED,
                contractedRateBps = 1_000,
                cdiAnnualRateBps = 1_200,
                ipcaAnnualRateBps = 600,
            )
        val noIof = withIof.copy(productGroup = ProductGroup.TAXABLE_IR_NO_IOF)

        val (_, irWithIof, netWithIof) = solver.computeGrossIrNet(withIof)
        val (_, irNoIof, netNoIof) = solver.computeGrossIrNet(noIof)

        assertThat(netWithIof).isLessThan(netNoIof)
        assertThat(irWithIof).isLessThan(irNoIof)
    }
}
