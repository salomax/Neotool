package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioValueWindow
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.transaction.MonthlyBalanceSnapshotRepository
import io.github.salomax.neotool.banking.service.investment.InvestmentPortfolioSummaryService
import io.github.salomax.neotool.banking.service.transaction.DashboardAnalyticsCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DisplayName("DashboardAnalyticsCalculator")
class DashboardAnalyticsCalculatorTest {
    private lateinit var snapshotRepository: MonthlyBalanceSnapshotRepository
    private lateinit var portfolioQuotaRepository: PortfolioQuotaRepository
    private lateinit var investmentPortfolioSummaryService: InvestmentPortfolioSummaryService
    private lateinit var calculator: DashboardAnalyticsCalculator

    private val accountId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        snapshotRepository = mock()
        portfolioQuotaRepository = mock()
        investmentPortfolioSummaryService = mock()
        calculator =
            DashboardAnalyticsCalculator(
                snapshotRepository = snapshotRepository,
                portfolioQuotaRepository = portfolioQuotaRepository,
                investmentPortfolioSummaryService = investmentPortfolioSummaryService,
            )
    }

    @Test
    fun `computes avgMonthlySavings as net divided by month count`() {
        whenever(snapshotRepository.findByRangeForAccount(eq(accountId), eq(2025), eq(1), eq(2025), eq(2)))
            .thenReturn(
                listOf(
                    snapshot(
                        year = 2025,
                        month = 1,
                        totalIncome = BigDecimal("1000.00"),
                        totalExpenses = BigDecimal("400.00"),
                    ),
                    snapshot(
                        year = 2025,
                        month = 2,
                        totalIncome = BigDecimal("500.00"),
                        totalExpenses = BigDecimal("300.00"),
                    ),
                ),
            )
        whenever(
            portfolioQuotaRepository.findOpeningAndClosingPortfolioValues(
                accountId = accountId,
                financialAccountId = null,
                startYear = 2025,
                startMonth = 1,
                endYear = 2025,
                endMonth = 2,
            ),
        ).thenReturn(
            PortfolioValueWindow(
                openingPortfolioValue = BigDecimal.ZERO,
                closingPortfolioValue = BigDecimal.ZERO,
            ),
        )
        whenever(investmentPortfolioSummaryService.getHeadlineSummary(financialAccountId = null, accountId = accountId))
            .thenReturn(
                InvestmentPortfolioSummaryService.PortfolioHeadlineSummary(
                    totalContributed = 0.0,
                    totalReturn = 0.0,
                    returnPct = null,
                ),
            )

        val summary = calculator.getDashboardPeriodSummary(accountId, null, 2025, 1, 2025, 2)

        assertThat(summary.net).isEqualTo(800.0)
        assertThat(summary.monthCount).isEqualTo(2)
        assertThat(summary.avgMonthlySavings).isEqualTo(400.0)
    }

    @Test
    fun `uses investment headline summary as single source of truth for invested and return`() {
        whenever(snapshotRepository.findByRange(eq(financialAccountId), eq(2025), eq(3), eq(2025), eq(3)))
            .thenReturn(emptyList())
        whenever(
            portfolioQuotaRepository.findOpeningAndClosingPortfolioValues(
                accountId = accountId,
                financialAccountId = financialAccountId,
                startYear = 2025,
                startMonth = 3,
                endYear = 2025,
                endMonth = 3,
            ),
        ).thenReturn(
            PortfolioValueWindow(
                openingPortfolioValue = BigDecimal.ZERO,
                closingPortfolioValue = BigDecimal("1200.00"),
            ),
        )
        whenever(
            investmentPortfolioSummaryService.getHeadlineSummary(
                financialAccountId = financialAccountId,
                accountId = accountId,
            ),
        ).thenReturn(
            InvestmentPortfolioSummaryService.PortfolioHeadlineSummary(
                totalContributed = 6269.0,
                totalReturn = 8366.39,
                returnPct = 39.98,
            ),
        )

        val summary = calculator.getDashboardPeriodSummary(accountId, financialAccountId, 2025, 3, 2025, 3)

        assertThat(summary.totalInvested).isEqualTo(6269.0)
        assertThat(summary.totalReturn).isEqualTo(8366.39)
        assertThat(summary.avgMonthlyReturn).isEqualTo(8366.39)
        assertThat(summary.returnPct).isEqualTo(39.98)
    }

    private fun snapshot(
        year: Int,
        month: Int,
        totalIncome: BigDecimal,
        totalExpenses: BigDecimal,
    ) = MonthlyBalanceSnapshot(
        financialAccountId = financialAccountId,
        accountId = accountId,
        year = year,
        month = month,
        openingBalance = BigDecimal.ZERO,
        closingBalance = BigDecimal.ZERO,
        totalIncome = totalIncome,
        totalExpenses = totalExpenses,
        computedAt = Instant.now(),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
