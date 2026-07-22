package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@DisplayName("ComeCotasSupport")
class ComeCotasSupportTest {
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)
    private val holdingId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @Test
    fun `computes the next checkpoint on the quantity that survived the prior checkpoint`() {
        val transactions =
            listOf(
                purchase(
                    quantity = "100.00000000",
                    unitPrice = "1.00",
                    tradeDate = LocalDate.of(2025, 6, 2),
                ),
                withholdingTax(
                    quantity = "5.00000000",
                    amount = "1.50",
                    tradeDate = LocalDate.of(2025, 11, 28),
                ),
            )

        val openLots = ComeCotasSupport.reconstructOpenLots(transactions)

        val remainingLot = openLots.single()
        assertThat(remainingLot.quantity).isEqualByComparingTo("95.00000000")
        assertThat(remainingLot.withholdingPaid).isEqualByComparingTo("1.50")
        val gain =
            remainingLot.quantity.multiply(BigDecimal("1.20"), mathContext)
                .subtract(remainingLot.quantity.multiply(remainingLot.unitPrice, mathContext), mathContext)
        val projected =
            ComeCotasSupport.estimateProjectedComeCotas(
                taxProfile = FundTaxProfile.COME_COTAS_LONG,
                gain = gain,
                tradeDate = remainingLot.tradeDate,
                referenceDate = LocalDate.of(2026, 5, 29),
            )
        val incrementalCheckpointIr = (projected - remainingLot.withholdingPaid).max(BigDecimal.ZERO)

        assertThat(incrementalCheckpointIr).isEqualByComparingTo("1.35")
    }

    @Test
    fun `multiple withholding events keep reducing already-reduced lots proportionally`() {
        val transactions =
            listOf(
                purchase(
                    quantity = "60.00000000",
                    unitPrice = "1.00",
                    tradeDate = LocalDate.of(2025, 6, 2),
                ),
                purchase(
                    quantity = "40.00000000",
                    unitPrice = "1.05",
                    tradeDate = LocalDate.of(2025, 7, 15),
                ),
                withholdingTax(
                    quantity = "4.00000000",
                    amount = "1.20",
                    tradeDate = LocalDate.of(2025, 11, 28),
                ),
                withholdingTax(
                    quantity = "2.00000000",
                    amount = "0.60",
                    tradeDate = LocalDate.of(2026, 5, 29),
                ),
            )

        val openLots = ComeCotasSupport.reconstructOpenLots(transactions)

        assertThat(openLots).hasSize(2)
        assertThat(openLots[0].quantity).isEqualByComparingTo("56.40000000")
        assertThat(openLots[0].withholdingPaid).isEqualByComparingTo("1.08")
        assertThat(openLots[1].quantity).isEqualByComparingTo("37.60000000")
        assertThat(openLots[1].withholdingPaid).isEqualByComparingTo("0.72")
    }

    private fun purchase(
        quantity: String,
        unitPrice: String,
        tradeDate: LocalDate,
    ) = InvestmentTransaction(
        id = UUID.randomUUID(),
        holdingId = holdingId,
        financialAccountId = financialAccountId,
        accountId = accountId,
        createdByUserId = userId,
        direction = InvestmentTransactionDirection.INFLOW,
        type = InvestmentTransactionType.PURCHASE,
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal(unitPrice),
        grossValue = BigDecimal(quantity).multiply(BigDecimal(unitPrice)),
        tradeDate = tradeDate,
    )

    private fun withholdingTax(
        quantity: String,
        amount: String,
        tradeDate: LocalDate,
    ) = InvestmentTransaction(
        id = UUID.randomUUID(),
        holdingId = holdingId,
        financialAccountId = financialAccountId,
        accountId = accountId,
        createdByUserId = userId,
        direction = InvestmentTransactionDirection.OUTFLOW,
        type = InvestmentTransactionType.WITHHOLDING_TAX,
        quantity = BigDecimal(quantity),
        unitPrice = BigDecimal(amount).divide(BigDecimal(quantity), 8, RoundingMode.HALF_UP),
        grossValue = BigDecimal(amount),
        tradeDate = tradeDate,
    )
}
