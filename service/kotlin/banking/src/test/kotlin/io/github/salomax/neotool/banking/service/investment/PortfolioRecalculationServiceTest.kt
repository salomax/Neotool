package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.IndicatorRateProvider
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.portfolio.AssetDailyPriceRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioFlowEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import io.github.salomax.neotool.banking.service.investment.event.PortfolioRecalculationEventBus
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.UUID

class PortfolioRecalculationServiceTest {
    private val portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository = mock()
    private val portfolioQuotaRepository: PortfolioQuotaRepository = mock()
    private val portfolioFlowEventRepository: PortfolioFlowEventRepository = mock()
    private val portfolioQuotaCalculationService = PortfolioQuotaCalculationService()
    private val holdingRepository: HoldingRepository = mock()
    private val assetRepository: AssetRepository = mock()
    private val investmentTransactionRepository: InvestmentTransactionRepository = mock()
    private val assetDailyPriceRepository: AssetDailyPriceRepository = mock()
    private val fixedIncomeValuationService: FixedIncomeValuationService = mock()
    private val indicatorRateProvider: IndicatorRateProvider = mock()
    private val portfolioRecalculationEventBus: PortfolioRecalculationEventBus = mock()

    private fun service(clock: () -> LocalDate): PortfolioRecalculationService =
        PortfolioRecalculationService(
            portfolioRecalculationJobRepository = portfolioRecalculationJobRepository,
            portfolioQuotaRepository = portfolioQuotaRepository,
            portfolioFlowEventRepository = portfolioFlowEventRepository,
            portfolioQuotaCalculationService = portfolioQuotaCalculationService,
            holdingRepository = holdingRepository,
            assetRepository = assetRepository,
            investmentTransactionRepository = investmentTransactionRepository,
            assetDailyPriceRepository = assetDailyPriceRepository,
            fixedIncomeValuationService = fixedIncomeValuationService,
            indicatorRateProvider = indicatorRateProvider,
            portfolioRecalculationEventBus = portfolioRecalculationEventBus,
            clock = clock,
        )

    @Test
    fun `process uses recalculateFrom minus one day for delete anchor and flow range`() {
        val jobId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val recalculateFrom = LocalDate.of(2026, 3, 1)
        val startDate = recalculateFrom.minusDays(1)
        val today = startDate

        val job =
            PortfolioRecalculationJob(
                id = jobId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                status = PortfolioRecalculationStatus.PENDING,
                recalculateFrom = recalculateFrom,
                triggeredBy = "test",
            )

        whenever(portfolioRecalculationJobRepository.findById(jobId)).thenReturn(job)
        whenever(portfolioRecalculationJobRepository.update(any())).thenAnswer { it.arguments[0] }
        whenever(holdingRepository.findByFinancialAccount(financialAccountId, accountId)).thenReturn(emptyList())
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(
            portfolioQuotaRepository.findLatestBeforeAllDimensions(
                financialAccountId = eq(financialAccountId),
                date = eq(startDate.minusDays(1)),
            ),
        ).thenReturn(emptyMap())
        whenever(
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountId = eq(financialAccountId),
                from = eq(startDate),
                to = eq(today),
            ),
        ).thenReturn(emptyList())

        service(clock = { today }).process(jobId)

        verify(portfolioQuotaRepository).deleteFromDate(financialAccountId, startDate)
        verify(portfolioFlowEventRepository).findByAccountAndDateRange(financialAccountId, startDate, today)
        verify(portfolioQuotaRepository).findLatestBeforeAllDimensions(
            financialAccountId = financialAccountId,
            date = startDate.minusDays(1),
        )
    }
}
