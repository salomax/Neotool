package io.github.salomax.neotool.banking.service.investment.recalculation

import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class HoldingPositionEvolutionTest {
    private val accountId = UUID.randomUUID()
    private val faId = UUID.randomUUID()
    private val assetId = UUID.randomUUID()

    private fun baseHolding(
        qty: String,
        avg: String,
    ) = Holding(
        financialAccountId = faId,
        accountId = accountId,
        assetId = assetId,
        quantity = BigDecimal(qty),
        averageCost = BigDecimal(avg),
        source = InvestmentSource.MANUAL,
    )

    @Test
    fun `purchase increases quantity and weighted average cost`() {
        val state = baseHolding("100", "10.00")
        val input =
            PositionEvolutionInput(
                type = InvestmentTransactionType.PURCHASE,
                direction = InvestmentTransactionDirection.INFLOW,
                quantity = BigDecimal("50"),
                unitPrice = BigDecimal("12.00"),
            )
        val next = HoldingPositionEvolution.evolve(state, input)
        assertThat(next.quantity).isEqualByComparingTo("150")
        assertThat(next.averageCost).isEqualByComparingTo("10.66666667")
    }

    @Test
    fun `sale reduces quantity and leaves average cost unchanged`() {
        val state = baseHolding("100", "10.00")
        val input =
            PositionEvolutionInput(
                type = InvestmentTransactionType.SALE,
                direction = InvestmentTransactionDirection.OUTFLOW,
                quantity = BigDecimal("40"),
                unitPrice = BigDecimal("15.00"),
            )
        val next = HoldingPositionEvolution.evolve(state, input)
        assertThat(next.quantity).isEqualByComparingTo("60")
        assertThat(next.averageCost).isEqualByComparingTo("10.00")
    }

    @Test
    fun `withholding tax reduces quantity and leaves average cost unchanged`() {
        val state = baseHolding("100", "10.00")
        val input =
            PositionEvolutionInput(
                type = InvestmentTransactionType.WITHHOLDING_TAX,
                direction = InvestmentTransactionDirection.OUTFLOW,
                quantity = BigDecimal("2.5"),
                unitPrice = BigDecimal("15.00"),
            )
        val next = HoldingPositionEvolution.evolve(state, input)
        assertThat(next.quantity).isEqualByComparingTo("97.5")
        assertThat(next.averageCost).isEqualByComparingTo("10.00")
    }

    @Test
    fun `maturity with null quantity closes full position`() {
        val state = baseHolding("100", "10.00")
        val input =
            PositionEvolutionInput(
                type = InvestmentTransactionType.MATURITY,
                direction = InvestmentTransactionDirection.INFLOW,
                quantity = null,
                unitPrice = null,
            )
        val next = HoldingPositionEvolution.evolve(state, input)
        assertThat(next.quantity).isEqualByComparingTo("0")
    }

    @Test
    fun `stock split dilutes average cost preserving total basis`() {
        val state = baseHolding("100", "10.00")
        val input =
            PositionEvolutionInput(
                type = InvestmentTransactionType.STOCK_SPLIT,
                direction = InvestmentTransactionDirection.INFLOW,
                quantity = BigDecimal("100"),
                unitPrice = null,
            )
        val next = HoldingPositionEvolution.evolve(state, input)
        assertThat(next.quantity).isEqualByComparingTo("200")
        assertThat(next.averageCost).isEqualByComparingTo("5.00000000")
    }

    @Test
    fun `dividend leaves position unchanged`() {
        val state = baseHolding("100", "10.00")
        val input =
            PositionEvolutionInput(
                type = InvestmentTransactionType.DIVIDEND,
                direction = InvestmentTransactionDirection.INFLOW,
                quantity = null,
                unitPrice = null,
            )
        val next = HoldingPositionEvolution.evolve(state, input)
        assertThat(next).isSameAs(state)
    }
}
