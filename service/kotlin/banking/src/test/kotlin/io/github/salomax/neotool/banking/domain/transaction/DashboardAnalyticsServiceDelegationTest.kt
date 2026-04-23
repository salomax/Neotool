package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.transaction.DashboardAnalyticsCalculator
import io.github.salomax.neotool.banking.service.transaction.DashboardAnalyticsService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

@DisplayName("DashboardAnalyticsService delegation")
class DashboardAnalyticsServiceDelegationTest {
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var dashboardAnalyticsCalculator: DashboardAnalyticsCalculator
    private lateinit var service: DashboardAnalyticsService

    private val accountId = UUID.randomUUID()
    private val financialAccountId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        transactionRepository = mock()
        dashboardAnalyticsCalculator = mock()
        service = DashboardAnalyticsService(
            transactionRepository = transactionRepository,
            dashboardAnalyticsCalculator = dashboardAnalyticsCalculator,
        )
    }

    @Test
    fun `delegates period summary calculation to shared calculator`() {
        val expected =
            DashboardPeriodSummary(
                totalIncome = 1000.0,
                totalExpenses = 200.0,
                monthCount = 1,
                avgMonthlyIncome = 1000.0,
                avgMonthlyExpenses = 200.0,
                avgMonthlySavings = 800.0,
                savingsRate = 80.0,
                closingCashBalance = 500.0,
                closingPortfolioValue = 750.0,
                totalNetWorth = 1250.0,
                totalInvested = 400.0,
                avgMonthlyInvested = 400.0,
                totalReturn = 50.0,
                avgMonthlyReturn = 50.0,
                openingPortfolioValue = 700.0,
                returnPct = 12.5,
                openingBalance = 450.0,
            )
        whenever(
            dashboardAnalyticsCalculator.getDashboardPeriodSummary(
                accountId = accountId,
                financialAccountId = financialAccountId,
                startYear = 2025,
                startMonth = 1,
                endYear = 2025,
                endMonth = 1,
            ),
        ).thenReturn(expected)

        val result = service.getDashboardPeriodSummary(accountId, financialAccountId, 2025, 1, 2025, 1)

        assertThat(result).isEqualTo(expected)
        verify(dashboardAnalyticsCalculator).getDashboardPeriodSummary(
            accountId = accountId,
            financialAccountId = financialAccountId,
            startYear = 2025,
            startMonth = 1,
            endYear = 2025,
            endMonth = 1,
        )
    }

    @Test
    fun `keeps category percentage calculation in service layer`() {
        val groceriesId = UUID.randomUUID()
        val transportId = UUID.randomUUID()
        whenever(
            transactionRepository.getCategoryBreakdownForRange(
                accountId = eq(accountId),
                financialAccountId = eq(financialAccountId),
                startYear = eq(2025),
                startMonth = eq(1),
                endYear = eq(2025),
                endMonth = eq(1),
                applicableTo = eq(CategoryApplicableTo.EXPENSE),
            ),
        ).thenReturn(
            listOf(
                CategoryBreakdownRow(
                    categoryId = groceriesId,
                    categoryName = "Groceries",
                    categoryIcon = "shopping-cart",
                    categoryColor = "#00AA00",
                    parentId = null,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    isSystem = false,
                    sortOrder = 1,
                    uncategorized = false,
                    totalAmount = BigDecimal("300.00"),
                    transactionCount = 6,
                ),
                CategoryBreakdownRow(
                    categoryId = transportId,
                    categoryName = "Transport",
                    categoryIcon = "car",
                    categoryColor = "#0055AA",
                    parentId = null,
                    applicableTo = CategoryApplicableTo.EXPENSE,
                    isSystem = false,
                    sortOrder = 2,
                    uncategorized = false,
                    totalAmount = BigDecimal("100.00"),
                    transactionCount = 2,
                ),
            ),
        )

        val result = service.getCategoryBreakdownForRange(
            accountId = accountId,
            financialAccountId = financialAccountId,
            startYear = 2025,
            startMonth = 1,
            endYear = 2025,
            endMonth = 1,
            applicableTo = CategoryApplicableTo.EXPENSE,
        )

        assertThat(result).hasSize(2)
        assertThat(result[0].percentage).isEqualTo(75.0)
        assertThat(result[1].percentage).isEqualTo(25.0)
    }
}
