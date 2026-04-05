package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("PortfolioQuotaCalculationService")
class PortfolioQuotaCalculationServiceTest {

    private val service = PortfolioQuotaCalculationService()

    private val financialAccountId: UUID = UUID.randomUUID()
    private val accountId: UUID = UUID.randomUUID()
    private val date: LocalDate = LocalDate.of(2025, 1, 2)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun quota(
        value: String,
        totalPortfolioValue: String,
        date: LocalDate = this.date.minusDays(1),
        dimensionType: PortfolioDimensionType = PortfolioDimensionType.PORTFOLIO,
        dimensionKey: String? = null,
    ) = InvestmentPortfolioQuota(
        financialAccountId = financialAccountId,
        dimensionType = dimensionType,
        dimensionKey = dimensionKey,
        quotaDate = date,
        quotaValue = BigDecimal(value),
        totalQuotas = BigDecimal("1000.00000000"),
        totalPortfolioValue = BigDecimal(totalPortfolioValue),
    )

    private fun flowEvent(
        type: PortfolioFlowEventType,
        amount: String,
        holdingId: UUID? = null,
    ) = InvestmentPortfolioFlowEvent(
        financialAccountId = financialAccountId,
        accountId = accountId,
        eventDate = date,
        eventType = type,
        grossAmount = BigDecimal(amount),
        portfolioValueBefore = BigDecimal.ZERO,
        quotaValueAtEvent = BigDecimal.ONE,
        quotasTransacted = BigDecimal.ZERO,
        relatedHoldingId = holdingId,
        createdAt = Instant.now(),
    )

    private fun groupValuation(value: String, estimated: Boolean = false) =
        DailyGroupValuation(totalValue = BigDecimal(value), hasEstimatedValues = estimated)

    private fun portfolioResult(quotas: List<InvestmentPortfolioQuota>) =
        quotas.first { it.dimensionType == PortfolioDimensionType.PORTFOLIO }

    private fun groupResult(quotas: List<InvestmentPortfolioQuota>, key: String) =
        quotas.first { it.dimensionType == PortfolioDimensionType.ASSET_GROUP && it.dimensionKey == key }

    // ── Portfolio quota: first day ────────────────────────────────────────────

    @Nested
    @DisplayName("First day — no previous quota")
    inner class FirstDay {

        @Test
        fun `initializes portfolio quota to 1 when portfolio has value`() {
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = null,
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("10000.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            val portfolio = portfolioResult(result)
            assertThat(portfolio.quotaValue).isEqualByComparingTo("1.00000000")
            assertThat(portfolio.totalPortfolioValue).isEqualByComparingTo("10000.00")
            assertThat(portfolio.quotaDate).isEqualTo(date)
        }

        @Test
        fun `returns no portfolio quota when portfolio is empty on first day`() {
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = null,
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = emptyMap(),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            assertThat(result.none { it.dimensionType == PortfolioDimensionType.PORTFOLIO }).isTrue()
        }

        @Test
        fun `initializes with cash balance included in portfolio value`() {
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = null,
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("8000.00")),
                cashBalance = BigDecimal("2000.00"),
                flowEvents = emptyList(),
            )

            val portfolio = portfolioResult(result)
            assertThat(portfolio.quotaValue).isEqualByComparingTo("1.00000000")
            assertThat(portfolio.totalPortfolioValue).isEqualByComparingTo("10000.00")
        }
    }

    // ── Portfolio quota: steady state ─────────────────────────────────────────

    @Nested
    @DisplayName("Steady-state growth — no flows")
    inner class SteadyState {

        @Test
        fun `quota grows proportionally when portfolio value increases with no flows`() {
            // Previous: quotaValue = 1.0, portfolio = 10000
            // Today: portfolio = 10100 (+1%)
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("10100.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            val portfolio = portfolioResult(result)
            assertThat(portfolio.quotaValue.toDouble()).isCloseTo(1.01, Percentage.withPercentage(0.001))
            assertThat(portfolio.totalPortfolioValue).isEqualByComparingTo("10100.00")
        }

        @Test
        fun `quota stays flat when portfolio value does not change`() {
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.05000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("10000.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            val portfolio = portfolioResult(result)
            assertThat(portfolio.quotaValue).isEqualByComparingTo("1.05000000")
        }

        @Test
        fun `quota accumulates compounded returns over multiple days`() {
            // Day 1: 10000 → 10100 (+1%), quota: 1.0 → 1.01
            val day1 = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("10100.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )
            val quotaAfterDay1 = portfolioResult(day1)

            // Day 2: 10100 → 10201 (+1%), quota: 1.01 → ~1.0201
            val day2 = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date.plusDays(1),
                previousPortfolioQuota = quotaAfterDay1,
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("10201.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )
            val quotaAfterDay2 = portfolioResult(day2)

            assertThat(quotaAfterDay2.quotaValue.toDouble()).isCloseTo(1.0201, Percentage.withPercentage(0.001))
        }
    }

    // ── Portfolio quota: flow events ──────────────────────────────────────────

    @Nested
    @DisplayName("Flow events — deposits and withdrawals")
    inner class FlowEvents {

        @Test
        fun `deposit flow event is excluded from return calculation`() {
            // Portfolio was 10000. Today: deposit of 2000 + portfolio grew by 100 (1%)
            // portfolioValueEnd = 10000 + 2000 + 100 = 12100
            // Expected: quota reflects only the 1% return, not the deposit
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("12100.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = listOf(flowEvent(PortfolioFlowEventType.DEPOSIT, "2000.00")),
            )

            val portfolio = portfolioResult(result)
            // dailyReturn = (12100 - 10000 - 2000) / 10000 = 1%
            assertThat(portfolio.quotaValue.toDouble()).isCloseTo(1.01, Percentage.withPercentage(0.001))
            assertThat(portfolio.totalPortfolioValue).isEqualByComparingTo("12100.00")
        }

        @Test
        fun `quota stays flat when portfolio increase is entirely explained by deposit`() {
            // Portfolio was 10000. Deposit of 2000, no return on existing holdings.
            // portfolioValueEnd = 12000
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("12000.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = listOf(flowEvent(PortfolioFlowEventType.DEPOSIT, "2000.00")),
            )

            val portfolio = portfolioResult(result)
            // dailyReturn = (12000 - 10000 - 2000) / 10000 = 0
            assertThat(portfolio.quotaValue).isEqualByComparingTo("1.00000000")
        }

        @Test
        fun `withdrawal flow event is excluded from return calculation`() {
            // Portfolio was 10000. Withdrawal of 3000, existing portfolio gained 100 (1% on remaining 7000).
            // portfolioValueEnd = 10000 - 3000 + 70 = 7070
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("7070.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = listOf(flowEvent(PortfolioFlowEventType.WITHDRAWAL, "3000.00")),
            )

            val portfolio = portfolioResult(result)
            // dailyReturn = (7070 - 10000 - (-3000)) / 10000 = 70/10000 = 0.7%
            assertThat(portfolio.quotaValue.toDouble()).isCloseTo(1.007, Percentage.withPercentage(0.01))
        }

        @Test
        fun `dividend flow event does not inflate quota`() {
            // Portfolio was 10000. Dividend received of 500 added to portfolio.
            // portfolioValueEnd = 10000 + 500 = 10500 (no investment return beyond the dividend)
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("stock" to groupValuation("10500.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = listOf(flowEvent(PortfolioFlowEventType.DIVIDEND, "500.00")),
            )

            val portfolio = portfolioResult(result)
            // dailyReturn = (10500 - 10000 - 500) / 10000 = 0
            assertThat(portfolio.quotaValue).isEqualByComparingTo("1.00000000")
        }

        @Test
        fun `income tax flow event reduces quota correctly`() {
            // Portfolio was 10000. Income tax of 200 paid out, no underlying return.
            // portfolioValueEnd = 10000 - 200 = 9800
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("9800.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = listOf(flowEvent(PortfolioFlowEventType.INCOME_TAX, "200.00")),
            )

            val portfolio = portfolioResult(result)
            // income tax is a WITHDRAWAL, signed negatively
            // dailyReturn = (9800 - 10000 - (-200)) / 10000 = 0
            assertThat(portfolio.quotaValue).isEqualByComparingTo("1.00000000")
        }

        @Test
        fun `multiple flow events on same day are combined`() {
            // Portfolio was 10000. Deposit 1000, income tax 200. Net flow = +800.
            // portfolioValueEnd = 10000 + 1000 - 200 + 50 (return on 10000) = 10850
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("10850.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = listOf(
                    flowEvent(PortfolioFlowEventType.DEPOSIT, "1000.00"),
                    flowEvent(PortfolioFlowEventType.INCOME_TAX, "200.00"),
                ),
            )

            val portfolio = portfolioResult(result)
            // netFlows = +1000 - 200 = +800
            // dailyReturn = (10850 - 10000 - 800) / 10000 = 50/10000 = 0.5%
            assertThat(portfolio.quotaValue.toDouble()).isCloseTo(1.005, Percentage.withPercentage(0.01))
        }
    }

    // ── Bug scenario: holding deleted without flow event ──────────────────────

    @Nested
    @DisplayName("Portfolio value drop — holding deleted without flow event")
    inner class HoldingDeletedWithoutFlowEvent {

        @Test
        fun `quota drops sharply when portfolio value decreases with no flow event — reproduces the TWR corruption bug`() {
            // This scenario matches the user's real data:
            // Previous (Dec 31 2025): quotaValue = 1.2234, totalPortfolioValue = 17739
            // Today (Apr 3 2026): only the RF pré remains (others deleted without WITHDRAWAL events)
            // Expected: quota drops from 1.2234 to ~0.2156 → causes -82% YTD TWR
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.22341305", "17739.47"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("3126.53")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(), // BUG: no WITHDRAWAL event was created for deleted holdings
            )

            val portfolio = portfolioResult(result)
            // dailyReturn = (3126.53 - 17739.47 - 0) / 17739.47 ≈ -82.4%
            // quotaValue = 1.2234 × (1 - 0.824) ≈ 0.2156
            assertThat(portfolio.quotaValue.toDouble()).isCloseTo(0.2156, Percentage.withPercentage(0.5))

            // This broken quota then causes YTD TWR: (0.2156 / 1.2234) - 1 ≈ -82.4%
            // FIX: deleteHolding should create a WITHDRAWAL flow event before deleting the holding.
        }

        @Test
        fun `quota is preserved when holding deletion is accompanied by a WITHDRAWAL flow event`() {
            // Correct scenario: a WITHDRAWAL event is created when a holding is deleted.
            // Previous: quotaValue = 1.2234, totalPortfolioValue = 17739.47
            // Deleted holdings contributed 14612.94. Remaining RF pré: 3126.53.
            // Withdrawal event covers the departed value.
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.22341305", "17739.47"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("3126.53")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = listOf(flowEvent(PortfolioFlowEventType.WITHDRAWAL, "14612.94")),
            )

            val portfolio = portfolioResult(result)
            // dailyReturn = (3126.53 - 17739.47 - (-14612.94)) / 17739.47 ≈ 0
            assertThat(portfolio.quotaValue.toDouble()).isCloseTo(1.2234, Percentage.withPercentage(0.01))
            assertThat(portfolio.totalPortfolioValue).isEqualByComparingTo("3126.53")
        }
    }

    // ── Re-initialization ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Re-initialization after zero portfolio value")
    inner class ReInitialization {

        @Test
        fun `reinitializes quota to 1 when previous portfolio value was zero`() {
            // Portfolio was empty, now has value again (new holding added)
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("0.00000000", "0.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("5000.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            val portfolio = portfolioResult(result)
            assertThat(portfolio.quotaValue).isEqualByComparingTo("1.00000000")
            assertThat(portfolio.totalPortfolioValue).isEqualByComparingTo("5000.00")
        }

        @Test
        fun `returns no portfolio quota when portfolio drops to zero with no flow event`() {
            // Portfolio had value, now all holdings are gone (no flow event)
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = emptyMap(),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            assertThat(result.none { it.dimensionType == PortfolioDimensionType.PORTFOLIO }).isTrue()
        }
    }

    // ── Group quotas ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Group (asset class) quotas")
    inner class GroupQuotas {

        @Test
        fun `group quota initializes to 1 when first appearing`() {
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("10000.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            val group = groupResult(result, "bankFixedIncome")
            assertThat(group.quotaValue).isEqualByComparingTo("1.00000000")
            assertThat(group.dimensionKey).isEqualTo("bankFixedIncome")
            assertThat(group.totalPortfolioValue).isEqualByComparingTo("10000.00")
        }

        @Test
        fun `group quota grows proportionally without flow adjustment`() {
            // Group quotas do not receive flow events — they track pure return
            val prevGroup = quota("1.00000000", "10000.00", dimensionType = PortfolioDimensionType.ASSET_GROUP, dimensionKey = "bankFixedIncome")
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = mapOf("bankFixedIncome" to prevGroup),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("10500.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            val group = groupResult(result, "bankFixedIncome")
            assertThat(group.quotaValue.toDouble()).isCloseTo(1.05, Percentage.withPercentage(0.001))
        }

        @Test
        fun `group quota is not created when group value is zero`() {
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("0.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            assertThat(result.none { it.dimensionType == PortfolioDimensionType.ASSET_GROUP }).isTrue()
        }

        @Test
        fun `multiple groups are tracked independently`() {
            val prevFI = quota("1.10000000", "6000.00", dimensionType = PortfolioDimensionType.ASSET_GROUP, dimensionKey = "bankFixedIncome")
            val prevStock = quota("0.90000000", "4000.00", dimensionType = PortfolioDimensionType.ASSET_GROUP, dimensionKey = "stock")

            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = mapOf(
                    "bankFixedIncome" to prevFI,
                    "stock" to prevStock,
                ),
                holdingsValueByGroup = mapOf(
                    "bankFixedIncome" to groupValuation("6060.00"), // +1%
                    "stock" to groupValuation("3960.00"),           // -1%
                ),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            val fi = groupResult(result, "bankFixedIncome")
            val stock = groupResult(result, "stock")

            assertThat(fi.quotaValue.toDouble()).isCloseTo(1.111, Percentage.withPercentage(0.1))
            assertThat(stock.quotaValue.toDouble()).isCloseTo(0.891, Percentage.withPercentage(0.1))
        }

        @Test
        fun `group quota reinitializes to 1 when previous group value was zero`() {
            val prevGroup = quota("0.00000000", "0.00", dimensionType = PortfolioDimensionType.ASSET_GROUP, dimensionKey = "bankFixedIncome")

            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "5000.00"),
                previousGroupQuotas = mapOf("bankFixedIncome" to prevGroup),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("5000.00")),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            val group = groupResult(result, "bankFixedIncome")
            assertThat(group.quotaValue).isEqualByComparingTo("1.00000000")
        }

        @Test
        fun `group with no previous quota gets value from portfolio quota date for hasEstimatedValues`() {
            val result = service.calculateDayQuota(
                financialAccountId = financialAccountId,
                date = date,
                previousPortfolioQuota = quota("1.00000000", "10000.00"),
                previousGroupQuotas = emptyMap(),
                holdingsValueByGroup = mapOf("bankFixedIncome" to groupValuation("10000.00", estimated = true)),
                cashBalance = BigDecimal.ZERO,
                flowEvents = emptyList(),
            )

            val group = groupResult(result, "bankFixedIncome")
            assertThat(group.hasEstimatedValues).isTrue()
        }
    }
}
