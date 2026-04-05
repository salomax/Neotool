package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.FinancialDataMarketDataSyncClient
import io.github.salomax.neotool.banking.client.investment.financialdata.FundDailyNavSyncNode
import io.github.salomax.neotool.banking.client.investment.messaging.MarketDataJobSucceededEvent
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncControl
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncJobName
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncStatus
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.portfolio.AssetDailyPriceRepository
import io.github.salomax.neotool.banking.repo.portfolio.InvestmentAssetCorporateEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.MarketBenchmarkDailyRepository
import io.github.salomax.neotool.banking.repo.portfolio.MarketDataSyncControlRepository
import io.github.salomax.neotool.banking.repo.portfolio.MarketIndicatorPointRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class MarketDataSyncServiceTest {
    private val syncControlRepository: MarketDataSyncControlRepository = mock()
    private val financialDataSyncClient: FinancialDataMarketDataSyncClient = mock()
    private val assetRepository: AssetRepository = mock()
    private val assetDailyPriceRepository: AssetDailyPriceRepository = mock()
    private val marketIndicatorPointRepository: MarketIndicatorPointRepository = mock()
    private val marketBenchmarkDailyRepository: MarketBenchmarkDailyRepository = mock()
    private val investmentAssetCorporateEventRepository: InvestmentAssetCorporateEventRepository = mock()
    private val marketDataReadinessService: MarketDataReadinessService = mock()
    private val financialAccountRepository: FinancialAccountRepository = mock()
    private val portfolioQuotaRepository: PortfolioQuotaRepository = mock()
    private val holdingRepository: HoldingRepository = mock()
    private val investmentTransactionRepository: InvestmentTransactionRepository = mock()
    private val transactionRepository: TransactionRepository = mock()
    private val portfolioFlowEventService: PortfolioFlowEventService = mock()

    private val service =
        MarketDataSyncService(
            syncControlRepository = syncControlRepository,
            financialDataSyncClient = financialDataSyncClient,
            assetRepository = assetRepository,
            assetDailyPriceRepository = assetDailyPriceRepository,
            marketIndicatorPointRepository = marketIndicatorPointRepository,
            marketBenchmarkDailyRepository = marketBenchmarkDailyRepository,
            investmentAssetCorporateEventRepository = investmentAssetCorporateEventRepository,
            marketDataReadinessService = marketDataReadinessService,
            financialAccountRepository = financialAccountRepository,
            portfolioQuotaRepository = portfolioQuotaRepository,
            holdingRepository = holdingRepository,
            investmentTransactionRepository = investmentTransactionRepository,
            transactionRepository = transactionRepository,
            portfolioFlowEventService = portfolioFlowEventService,
        )

    @Test
    fun `stores actual synced coverage instead of optimistic event lastDataDate`() {
        val event =
            MarketDataJobSucceededEvent(
                eventId = UUID.randomUUID(),
                jobName = MarketDataSyncJobName.CVM_NAV.jobName,
                runId = UUID.randomUUID(),
                fromDate = LocalDate.of(2026, 4, 1),
                toDate = LocalDate.of(2026, 4, 30),
                lastDataDate = LocalDate.of(2026, 4, 30),
                lastRunAt = Instant.parse("2026-04-03T10:00:00Z"),
                status = "SUCCESS",
                isBackfill = false,
                publishedAt = Instant.parse("2026-04-03T10:00:05Z"),
            )
        val assetId = UUID.randomUUID()

        whenever(syncControlRepository.findByJobName(MarketDataSyncJobName.CVM_NAV)).thenReturn(null)
        whenever(syncControlRepository.upsert(any())).thenAnswer { it.arguments.first() }
        whenever(assetRepository.findByFundCnpj("12345678000199")).thenReturn(asset(assetId))
        whenever(marketDataReadinessService.valuationReadiness()).thenReturn(
            MarketDataValuationReadiness(
                safeValuationDate = null,
                missingJobs = listOf(MarketDataSyncJobName.B3_COTAHIST),
            ),
        )
        doAnswer { invocation ->
            val consumer = invocation.getArgument<(List<FundDailyNavSyncNode>) -> Unit>(5)
            consumer(
                listOf(
                    FundDailyNavSyncNode(
                        cnpj = "12345678000199",
                        date = LocalDate.of(2026, 4, 1),
                        closePrice = BigDecimal("100.00"),
                    ),
                    FundDailyNavSyncNode(
                        cnpj = "12345678000199",
                        date = LocalDate.of(2026, 4, 2),
                        closePrice = BigDecimal("101.00"),
                    ),
                ),
            )
            Unit
        }.whenever(financialDataSyncClient).forEachFundDailyNav(
            from = eq(LocalDate.of(2026, 4, 1)),
            to = eq(LocalDate.of(2026, 4, 30)),
            updatedSince = isNull(),
            cnpjs = isNull(),
            pageSize = eq(250),
            consumer = any(),
        )

        service.handleJobSucceeded(event)

        val controlsCaptor = argumentCaptor<MarketDataSyncControl>()
        verify(syncControlRepository, org.mockito.kotlin.atLeastOnce()).upsert(controlsCaptor.capture())
        val finalControl = controlsCaptor.allValues.last()
        assertThat(finalControl.lastSyncedDate).isEqualTo(LocalDate.of(2026, 4, 2))
        assertThat(finalControl.sourceLastDataDate).isEqualTo(LocalDate.of(2026, 4, 30))
        verify(portfolioFlowEventService, never()).requestRecalculation(any(), any(), any(), any())
    }

    @Test
    fun `bootstraps recalculation from earliest relevant account date when no quota exists`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val event =
            MarketDataJobSucceededEvent(
                eventId = UUID.randomUUID(),
                jobName = MarketDataSyncJobName.CVM_NAV.jobName,
                runId = UUID.randomUUID(),
                fromDate = LocalDate.of(2026, 4, 1),
                toDate = LocalDate.of(2026, 4, 30),
                lastDataDate = LocalDate.of(2026, 4, 30),
                lastRunAt = Instant.parse("2026-04-03T10:00:00Z"),
                status = "SUCCESS",
                isBackfill = false,
                publishedAt = Instant.parse("2026-04-03T10:00:05Z"),
            )
        val assetId = UUID.randomUUID()
        val safeDate = LocalDate.of(2026, 4, 1)
        val earliestTradeDate = LocalDate.of(2026, 2, 10)

        whenever(syncControlRepository.findByJobName(MarketDataSyncJobName.CVM_NAV)).thenReturn(null)
        whenever(syncControlRepository.upsert(any())).thenAnswer { it.arguments.first() }
        whenever(assetRepository.findByFundCnpj("12345678000199")).thenReturn(asset(assetId))
        whenever(marketDataReadinessService.valuationReadiness()).thenReturn(
            MarketDataValuationReadiness(
                safeValuationDate = safeDate,
                missingJobs = emptyList(),
            ),
        )
        whenever(financialAccountRepository.findAllInvestmentAccounts()).thenReturn(
            listOf(
                io.github.salomax.neotool.banking.domain.account.FinancialAccount(
                    id = financialAccountId,
                    accountId = accountId,
                    createdByUserId = UUID.randomUUID(),
                    type = io.github.salomax.neotool.banking.domain.account.FinancialAccountType.INVESTMENT,
                    subtype = io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype.BROKERAGE,
                    name = "Broker",
                    createdAt = Instant.parse("2026-02-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-02-01T00:00:00Z"),
                ),
            ),
        )
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = LocalDate.of(9999, 12, 31),
            ),
        ).thenReturn(null)
        whenever(holdingRepository.findByFinancialAccount(financialAccountId, accountId)).thenReturn(
            listOf(
                Holding(
                    id = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    assetId = assetId,
                    quantity = BigDecimal.ONE,
                    averageCost = BigDecimal("100.00"),
                    issueDate = LocalDate.of(2026, 2, 12),
                ),
            ),
        )
        whenever(investmentTransactionRepository.findByFinancialAccount(financialAccountId, accountId)).thenReturn(
            listOf(
                InvestmentTransaction(
                    id = UUID.randomUUID(),
                    holdingId = UUID.randomUUID(),
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    createdByUserId = UUID.randomUUID(),
                    type = InvestmentTransactionType.PURCHASE,
                    direction = InvestmentTransactionDirection.INFLOW,
                    quantity = BigDecimal.ONE,
                    unitPrice = BigDecimal("100.00"),
                    grossValue = BigDecimal("100.00"),
                    tradeDate = earliestTradeDate,
                ),
            ),
        )
        whenever(transactionRepository.findEarliestTransactionDate(financialAccountId)).thenReturn(LocalDate.of(2026, 2, 15))
        doAnswer { invocation ->
            val consumer = invocation.getArgument<(List<FundDailyNavSyncNode>) -> Unit>(5)
            consumer(
                listOf(
                    FundDailyNavSyncNode(
                        cnpj = "12345678000199",
                        date = safeDate,
                        closePrice = BigDecimal("100.00"),
                    ),
                ),
            )
            Unit
        }.whenever(financialDataSyncClient).forEachFundDailyNav(
            from = eq(LocalDate.of(2026, 4, 1)),
            to = eq(LocalDate.of(2026, 4, 30)),
            updatedSince = isNull(),
            cnpjs = isNull(),
            pageSize = eq(250),
            consumer = any(),
        )

        service.handleJobSucceeded(event)

        verify(portfolioFlowEventService, times(1)).requestRecalculation(
            financialAccountId = eq(financialAccountId),
            accountId = eq(accountId),
            recalculateFrom = eq(earliestTradeDate),
            triggeredBy = eq("market_data_sync:cvm_nav:2026-04-01"),
        )
    }

    private fun asset(id: UUID) =
        Asset(
            id = id,
            name = "Test Fund",
            assetType = AssetType.MUTUAL_FUND,
            ticker = "TEST11",
            fundCnpj = "12345678000199",
        )
}
