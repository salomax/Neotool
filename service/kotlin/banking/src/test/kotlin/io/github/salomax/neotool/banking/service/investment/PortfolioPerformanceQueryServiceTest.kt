package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkCode
import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkDaily
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.graphql.investment.GridClassTwrPeriod
import io.github.salomax.neotool.banking.graphql.investment.PerformancePeriodDTO
import io.github.salomax.neotool.banking.model.dto.investment.GridClassMapping
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.portfolio.MarketBenchmarkDailyRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioFlowEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class PortfolioPerformanceQueryServiceTest {
    private val portfolioQuotaRepository: PortfolioQuotaRepository = mock()
    private val marketBenchmarkDailyRepository: MarketBenchmarkDailyRepository = mock()
    private val portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository = mock()
    private val financialAccountRepository: FinancialAccountRepository = mock()
    private val portfolioFlowEventRepository: PortfolioFlowEventRepository = mock()
    private val holdingRepository: HoldingRepository = mock()
    private val assetRepository: AssetRepository = mock()

    private val service =
        PortfolioPerformanceQueryService(
            portfolioQuotaRepository = portfolioQuotaRepository,
            marketBenchmarkDailyRepository = marketBenchmarkDailyRepository,
            portfolioRecalculationJobRepository = portfolioRecalculationJobRepository,
            financialAccountRepository = financialAccountRepository,
            portfolioFlowEventRepository = portfolioFlowEventRepository,
            holdingRepository = holdingRepository,
            assetRepository = assetRepository,
        )

    @Test
    fun `returns TWR and benchmarks when historical quotas exist for account`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val today = LocalDate.now()
        val yearStartBoundary = today.withDayOfYear(1).minusDays(1)
        val endQuotaDate = if (today.isAfter(yearStartBoundary)) today.minusDays(1) else today

        val startQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = yearStartBoundary,
                quotaValue = "1.00000000",
                totalPortfolioValue = "1000.00",
            )
        val endQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = endQuotaDate,
                quotaValue = "1.15000000",
                totalPortfolioValue = "1500.00",
                hasEstimatedValues = true,
            )

        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = today,
            ),
        ).thenReturn(endQuota)
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = yearStartBoundary,
            ),
        ).thenReturn(startQuota)
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = yearStartBoundary,
                to = endQuotaDate,
            ),
        ).thenReturn(listOf(startQuota, endQuota))
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.CDI,
                yearStartBoundary.plusDays(1),
                endQuotaDate,
            ),
        ).thenReturn(
            listOf(
                benchmark(MarketBenchmarkCode.CDI, yearStartBoundary.plusDays(1), "1.0100000000"),
                benchmark(MarketBenchmarkCode.CDI, endQuotaDate, "1.0200000000"),
            ),
        )
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IBOV,
                yearStartBoundary.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IPCA,
                yearStartBoundary.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IFIX,
                yearStartBoundary.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
                hasEstimatedValuesFallback = false,
            )

        assertThat(performance.twr).isEqualTo(0.5)
        assertThat(performance.twrSince).isEqualTo(yearStartBoundary)
        assertThat(performance.hasEstimatedValues).isTrue()
        assertThat(performance.benchmarks).hasSize(4)
        assertThat(performance.benchmarks.first { it.code == "CDI" }.twr).isEqualTo(0.0302)
        assertThat(performance.benchmarks.first { it.code == "IBOV" }.twr).isNull()
    }

    @Test
    fun `falls back to first quota inside period when no boundary quota exists`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val today = LocalDate.now()
        val yearStartBoundary = today.withDayOfYear(1).minusDays(1)
        val startQuotaDate = yearStartBoundary.plusDays(10)
        val endQuotaDate = startQuotaDate.plusDays(5)

        val startQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = startQuotaDate,
                quotaValue = "1.00000000",
                totalPortfolioValue = "1000.00",
            )
        val endQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = endQuotaDate,
                quotaValue = "1.05000000",
                totalPortfolioValue = "1050.00",
            )

        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = today,
            ),
        ).thenReturn(endQuota)
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = yearStartBoundary,
            ),
        ).thenReturn(null)
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = yearStartBoundary.plusDays(1),
                to = endQuotaDate,
            ),
        ).thenReturn(listOf(startQuota, endQuota))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = startQuotaDate,
                to = endQuotaDate,
            ),
        ).thenReturn(listOf(startQuota, endQuota))
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.CDI,
                startQuotaDate.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IBOV,
                startQuotaDate.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IPCA,
                startQuotaDate.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IFIX,
                startQuotaDate.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
                hasEstimatedValuesFallback = false,
            )

        assertThat(performance.twr).isEqualTo(0.05)
        assertThat(performance.twrSince).isEqualTo(startQuotaDate)
    }

    @Test
    fun `returns null performance for consolidated summary without portfolio quota scope`() {
        whenever(financialAccountRepository.findByAccountId(any())).thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = null,
                accountId = UUID.randomUUID(),
                period = PerformancePeriodDTO.ALL,
                hasEstimatedValuesFallback = true,
            )

        assertThat(performance.twr).isNull()
        assertThat(performance.twrSince).isNull()
        assertThat(performance.hasEstimatedValues).isTrue()
        assertThat(performance.benchmarks).isEmpty()
    }

    @Test
    fun `returns consolidated TWR when aggregating multiple investment accounts`() {
        val accountId = UUID.randomUUID()
        val financialAccountIdA = UUID.randomUUID()
        val financialAccountIdB = UUID.randomUUID()
        val today = LocalDate.now()
        val yearStartBoundary = today.withDayOfYear(1).minusDays(1)
        val endQuotaDate = yearStartBoundary.plusDays(2)

        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
            listOf(
                financialAccount(accountId, financialAccountIdA),
                financialAccount(accountId, financialAccountIdB),
            ),
        )
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountIdA,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = today,
            ),
        ).thenReturn(quota(financialAccountIdA, endQuotaDate, "1.10000000", "110.00"))
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountIdB,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = today,
            ),
        ).thenReturn(quota(financialAccountIdB, endQuotaDate, "1.10000000", "220.00"))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountIdA,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = yearStartBoundary,
                to = endQuotaDate,
            ),
        ).thenReturn(
            listOf(
                quota(financialAccountIdA, yearStartBoundary, "1.00000000", "100.00"),
                quota(financialAccountIdA, yearStartBoundary.plusDays(1), "1.05000000", "105.00"),
                quota(financialAccountIdA, endQuotaDate, "1.10000000", "110.00"),
            ),
        )
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountIdB,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = yearStartBoundary,
                to = endQuotaDate,
            ),
        ).thenReturn(
            listOf(
                quota(financialAccountIdB, yearStartBoundary, "1.00000000", "200.00"),
                quota(financialAccountIdB, yearStartBoundary.plusDays(1), "1.05000000", "210.00"),
                quota(financialAccountIdB, endQuotaDate, "1.10000000", "220.00"),
            ),
        )
        whenever(
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountIdA,
                yearStartBoundary.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountIdB,
                yearStartBoundary.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.CDI,
                yearStartBoundary.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IBOV,
                yearStartBoundary.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IPCA,
                yearStartBoundary.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IFIX,
                yearStartBoundary.plusDays(1),
                endQuotaDate,
            ),
        )
            .thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = null,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
                hasEstimatedValuesFallback = false,
            )

        assertThat(performance.twr).isEqualTo(0.1)
        assertThat(performance.twrSince).isEqualTo(yearStartBoundary)
        assertThat(performance.benchmarks).hasSize(4)
    }

    @Test
    fun `returns recalculation status from pending job when present`() {
        val accountId = UUID.randomUUID()
        val pendingJob =
            PortfolioRecalculationJob(
                id = UUID.randomUUID(),
                accountId = accountId,
                status = PortfolioRecalculationStatus.PROCESSING,
                recalculateFrom = LocalDate.of(2026, 1, 1),
                triggeredBy = "import:123",
                startedAt = Instant.parse("2026-04-03T10:00:00Z"),
                createdAt = Instant.parse("2026-04-03T09:59:00Z"),
                updatedAt = Instant.parse("2026-04-03T10:00:00Z"),
            )
        whenever(portfolioRecalculationJobRepository.findActiveByAccount(accountId)).thenReturn(pendingJob)

        val status = service.getRecalculationStatus(accountId)

        assertThat(status.isCalculating).isTrue()
        assertThat(status.jobId).isEqualTo(pendingJob.id)
        assertThat(status.startedAt).isEqualTo(pendingJob.startedAt)
        assertThat(status.message).contains("in progress")
    }

    @Test
    fun `failed recalculation status exposes error code and catalog default message`() {
        val accountId = UUID.randomUUID()
        val failedJob =
            PortfolioRecalculationJob(
                id = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                accountId = accountId,
                status = PortfolioRecalculationStatus.FAILED,
                recalculateFrom = LocalDate.of(2026, 1, 1),
                triggeredBy = "test",
                errorCode = BankingErrorCode.PORTFOLIO_RECALCULATION_CONSOLIDATED_NOT_SUPPORTED.code,
                errorDetails = "RuntimeException: technical",
                completedAt = Instant.parse("2026-04-03T10:05:00Z"),
                updatedAt = Instant.parse("2026-04-03T10:05:00Z"),
            )
        whenever(portfolioRecalculationJobRepository.findActiveByAccount(accountId)).thenReturn(null)
        whenever(portfolioRecalculationJobRepository.findLatestByAccount(accountId)).thenReturn(failedJob)

        val status = service.getRecalculationStatus(accountId)

        assertThat(status.isCalculating).isFalse()
        assertThat(status.errorCode).isEqualTo(BankingErrorCode.PORTFOLIO_RECALCULATION_CONSOLIDATED_NOT_SUPPORTED.code)
        assertThat(
            status.message,
        ).isEqualTo(BankingErrorCode.PORTFOLIO_RECALCULATION_CONSOLIDATED_NOT_SUPPORTED.defaultMessage)
    }

    @Test
    fun `failed recalculation without error code falls back to stored error details`() {
        val accountId = UUID.randomUUID()
        val failedJob =
            PortfolioRecalculationJob(
                id = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                accountId = accountId,
                status = PortfolioRecalculationStatus.FAILED,
                recalculateFrom = LocalDate.of(2026, 1, 1),
                triggeredBy = "test",
                errorCode = null,
                errorDetails = "Legacy failure text",
                completedAt = Instant.parse("2026-04-03T10:05:00Z"),
                updatedAt = Instant.parse("2026-04-03T10:05:00Z"),
            )
        whenever(portfolioRecalculationJobRepository.findActiveByAccount(accountId)).thenReturn(null)
        whenever(portfolioRecalculationJobRepository.findLatestByAccount(accountId)).thenReturn(failedJob)

        val status = service.getRecalculationStatus(accountId)

        assertThat(status.errorCode).isNull()
        assertThat(status.message).isEqualTo("Legacy failure text")
    }

    @Test
    fun `computes asset group TWR using scoped flow events instead of raw quota ratio`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val boundaryDate = LocalDate.of(2025, 12, 31)
        val saleDate = LocalDate.of(2026, 1, 15)
        val repurchaseDate = LocalDate.of(2026, 2, 1)
        val endDate = LocalDate.of(2026, 4, 3)

        val startQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = boundaryDate,
                quotaValue = "1.00000000",
                totalPortfolioValue = "1000.00",
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
            )
        val afterSaleQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = saleDate,
                quotaValue = "0.00000000",
                totalPortfolioValue = "0.00",
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
            )
        val afterRepurchaseQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = repurchaseDate,
                quotaValue = "1.00000000",
                totalPortfolioValue = "1000.00",
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
            )
        val endQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = endDate,
                quotaValue = "1.10000000",
                totalPortfolioValue = "1100.00",
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
            )

        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
                date = LocalDate.now(),
            ),
        ).thenReturn(endQuota)
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
                date = boundaryDate,
            ),
        ).thenReturn(startQuota)
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
                from = boundaryDate,
                to = endDate,
            ),
        ).thenReturn(listOf(startQuota, afterSaleQuota, afterRepurchaseQuota, endQuota))

        whenever(holdingRepository.findByFinancialAccount(financialAccountId, accountId)).thenReturn(
            listOf(
                holding(
                    id = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    assetId = assetId,
                ),
            ),
        )
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(
            listOf(asset(assetId, AssetType.CDB)),
        )
        whenever(
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountId,
                boundaryDate.plusDays(1),
                endDate,
            ),
        )
            .thenReturn(
                listOf(
                    InvestmentPortfolioFlowEvent(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        eventDate = saleDate,
                        eventType = PortfolioFlowEventType.WITHDRAWAL,
                        grossAmount = BigDecimal("1000.00"),
                        portfolioValueBefore = BigDecimal("1000.00"),
                        quotaValueAtEvent = BigDecimal("1.00000000"),
                        quotasTransacted = BigDecimal("-1000.00000000"),
                        relatedHoldingId = holdingId,
                        sourceReferenceId = UUID.randomUUID(),
                        sourceReferenceType = "INVESTMENT_TRANSACTION",
                    ),
                    InvestmentPortfolioFlowEvent(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        eventDate = repurchaseDate,
                        eventType = PortfolioFlowEventType.DEPOSIT,
                        grossAmount = BigDecimal("1000.00"),
                        portfolioValueBefore = BigDecimal.ZERO,
                        quotaValueAtEvent = BigDecimal("1.00000000"),
                        quotasTransacted = BigDecimal("1000.00000000"),
                        relatedHoldingId = holdingId,
                        sourceReferenceId = UUID.randomUUID(),
                        sourceReferenceType = "INVESTMENT_TRANSACTION",
                    ),
                ),
            )

        val twr =
            service.getAssetGroupPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                groupKey = "fixedIncome",
                period = PerformancePeriodDTO.YTD,
            )

        assertThat(twr).isEqualTo(0.1)
    }

    @Test
    fun `consolidated TWR is distorted when holding deleted without withdrawal flow event — reproduces bug`() {
        // This test documents the exact bug reported by the user.
        // A holding was deleted but no WITHDRAWAL flow event was created.
        // The quota dropped from 1.2234 (Dec 31 2025) to 0.2156 (Apr 3 2026),
        // causing YTD TWR of -82.38%.
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val ytdBoundary = LocalDate.of(2025, 12, 31)
        val endDate = LocalDate.of(2026, 4, 3)

        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
            listOf(financialAccount(accountId, financialAccountId)),
        )
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId,
                PortfolioDimensionType.PORTFOLIO,
                null,
                LocalDate.now(),
            ),
        ).thenReturn(quota(financialAccountId, endDate, "0.21562325", "3126.53"))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId,
                PortfolioDimensionType.PORTFOLIO,
                null,
                ytdBoundary,
                endDate,
            ),
        ).thenReturn(
            listOf(
                quota(financialAccountId, ytdBoundary, "1.22341305", "17739.47"),
                quota(financialAccountId, endDate, "0.21562325", "3126.53"),
            ),
        )
        whenever(
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountId,
                ytdBoundary.plusDays(1),
                endDate,
            ),
        )
            .thenReturn(emptyList<InvestmentPortfolioFlowEvent>()) // No WITHDRAWAL event — this is the bug
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.CDI,
                ytdBoundary.plusDays(1),
                endDate,
            ),
        ).thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IBOV,
                ytdBoundary.plusDays(1),
                endDate,
            ),
        ).thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IPCA,
                ytdBoundary.plusDays(1),
                endDate,
            ),
        ).thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                MarketBenchmarkCode.IFIX,
                ytdBoundary.plusDays(1),
                endDate,
            ),
        ).thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = null,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
                hasEstimatedValuesFallback = false,
            )

        // TWR is -82.38% because the quota dropped from 1.2234 to 0.2156 without a flow event
        // FIX: deleteHolding should create a WITHDRAWAL flow event so quota is preserved
        assertThat(performance.twr!!).isLessThan(-0.80)
    }

    @Test
    fun `consolidated TWR is correct when holding deletion is accompanied by withdrawal flow event`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val ytdBoundary = LocalDate.of(2025, 12, 31)
        val deletionDate = LocalDate.of(2026, 1, 15)
        val endDate = LocalDate.of(2026, 4, 3)

        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
            listOf(financialAccount(accountId, financialAccountId)),
        )
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId,
                PortfolioDimensionType.PORTFOLIO,
                null,
                LocalDate.now(),
            ),
        ).thenReturn(quota(financialAccountId, endDate, "1.23500000", "3163.00"))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId,
                PortfolioDimensionType.PORTFOLIO,
                null,
                ytdBoundary,
                endDate,
            ),
        ).thenReturn(
            listOf(
                quota(financialAccountId, ytdBoundary, "1.22341305", "17739.47"),
                // After deletion + withdrawal
                quota(financialAccountId, deletionDate, "1.22600000", "3136.00"),
                quota(financialAccountId, endDate, "1.23500000", "3163.00"),
            ),
        )
        whenever(
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountId,
                ytdBoundary.plusDays(1),
                endDate,
            ),
        )
            .thenReturn(
                listOf(
                    io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        eventDate = deletionDate,
                        eventType = PortfolioFlowEventType.WITHDRAWAL,
                        grossAmount = BigDecimal("14603.47"),
                        portfolioValueBefore = BigDecimal("17739.47"),
                        quotaValueAtEvent = BigDecimal("1.22600000"),
                        quotasTransacted = BigDecimal("-11909.85"),
                    ),
                ),
            )
        whenever(marketBenchmarkDailyRepository.findByCodeAndDateRange(any(), any(), any())).thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = null,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
                hasEstimatedValuesFallback = false,
            )

        // With a proper WITHDRAWAL flow event, TWR correctly reflects only the investment return (~1%)
        assertThat(performance.twr!!).isGreaterThan(0.0)
        assertThat(performance.twr!!).isLessThan(0.05)
    }

    @Test
    fun `consolidated YTD returns null when period has fewer than 2 data points`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val ytdBoundary = LocalDate.of(2025, 12, 31)
        val endDate = ytdBoundary.plusDays(1)

        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
            listOf(financialAccount(accountId, financialAccountId)),
        )
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId,
                PortfolioDimensionType.PORTFOLIO,
                null,
                LocalDate.now(),
            ),
        ).thenReturn(quota(financialAccountId, endDate, "1.00000000", "10000.00"))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId,
                PortfolioDimensionType.PORTFOLIO,
                null,
                ytdBoundary,
                endDate,
            ),
        ).thenReturn(
            listOf(quota(financialAccountId, endDate, "1.00000000", "10000.00")),
        )

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = null,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
                hasEstimatedValuesFallback = false,
            )

        assertThat(performance.twr).isNull()
    }

    @Test
    fun `scoped ONE_YEAR TWR is positive for RF pre growing at 10 percent annually`() {
        // Simulates the user's exact scenario: RF pré 10% a.a. created Jan 1 2025
        // ONE_YEAR boundary = Apr 3 2025, end = Apr 3 2026
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val oneYearBoundary = LocalDate.of(2025, 4, 3)
        val endDate = LocalDate.of(2026, 4, 3)

        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId,
                PortfolioDimensionType.PORTFOLIO,
                null,
                LocalDate.now(),
            ),
        ).thenReturn(quota(financialAccountId, endDate, "1.10440000", "3126.53"))
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId,
                PortfolioDimensionType.PORTFOLIO,
                null,
                oneYearBoundary,
            ),
        ).thenReturn(quota(financialAccountId, oneYearBoundary, "1.02440000", "2900.00"))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId,
                PortfolioDimensionType.PORTFOLIO,
                null,
                oneYearBoundary,
                endDate,
            ),
        ).thenReturn(
            listOf(
                quota(financialAccountId, oneYearBoundary, "1.02440000", "2900.00"),
                quota(financialAccountId, endDate, "1.10440000", "3126.53"),
            ),
        )
        whenever(marketBenchmarkDailyRepository.findByCodeAndDateRange(any(), any(), any())).thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                period = PerformancePeriodDTO.ONE_YEAR,
                hasEstimatedValuesFallback = false,
            )

        // (1.1044 / 1.0244) - 1 ≈ 7.8%
        assertThat(performance.twr!!).isGreaterThan(0.07)
        assertThat(performance.twr!!).isLessThan(0.09)
        assertThat(performance.twrSince).isEqualTo(oneYearBoundary)
    }

    @Test
    fun `MTD uses quota exactly on last day of previous month as start when present`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val today = LocalDate.now()
        val endQuotaDate = today.minusDays(1)
        val mtdBoundary = endQuotaDate.withDayOfMonth(1).minusDays(1)
        assumeTrue(endQuotaDate.isAfter(mtdBoundary))

        val startQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = mtdBoundary,
                quotaValue = "1.00000000",
                totalPortfolioValue = "1000.00",
            )
        val endQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = endQuotaDate,
                quotaValue = "1.05000000",
                totalPortfolioValue = "1050.00",
            )
        val series = listOf(startQuota, endQuota)

        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = today,
            ),
        ).thenReturn(endQuota)
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = mtdBoundary,
            ),
        ).thenReturn(startQuota)
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = startQuota.quotaDate,
                to = endQuotaDate,
            ),
        ).thenReturn(series)
        val benchFrom = startQuota.quotaDate.plusDays(1)
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(MarketBenchmarkCode.CDI, benchFrom, endQuotaDate),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(MarketBenchmarkCode.IBOV, benchFrom, endQuotaDate),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(MarketBenchmarkCode.IPCA, benchFrom, endQuotaDate),
        )
            .thenReturn(emptyList())
        whenever(
            marketBenchmarkDailyRepository.findByCodeAndDateRange(MarketBenchmarkCode.IFIX, benchFrom, endQuotaDate),
        )
            .thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                period = PerformancePeriodDTO.MTD,
                hasEstimatedValuesFallback = false,
            )

        assertThat(performance.twrSince).isEqualTo(mtdBoundary)
        assertThat(performance.twr).isEqualTo(0.05)
    }

    @Test
    fun `ONE_YEAR falls back to first quota after boundary when account is younger than one year`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val endDate = LocalDate.of(2026, 4, 3)
        val oneYearBoundary = endDate.minusYears(1)
        val firstQuotaDate = oneYearBoundary.plusMonths(2)

        val startQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = firstQuotaDate,
                quotaValue = "1.00000000",
                totalPortfolioValue = "1000.00",
            )
        val endQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = endDate,
                quotaValue = "1.08000000",
                totalPortfolioValue = "1080.00",
            )

        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = LocalDate.now(),
            ),
        ).thenReturn(endQuota)
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = oneYearBoundary,
            ),
        ).thenReturn(null)
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = oneYearBoundary.plusDays(1),
                to = endDate,
            ),
        ).thenReturn(listOf(startQuota, endQuota))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = firstQuotaDate,
                to = endDate,
            ),
        ).thenReturn(listOf(startQuota, endQuota))
        whenever(marketBenchmarkDailyRepository.findByCodeAndDateRange(any(), any(), any())).thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                period = PerformancePeriodDTO.ONE_YEAR,
                hasEstimatedValuesFallback = false,
            )

        assertThat(performance.twrSince).isEqualTo(firstQuotaDate)
        assertThat(performance.twr).isEqualTo(0.08)
    }

    @Test
    fun `consolidated TWR merges flows when only one of multiple accounts has withdrawal`() {
        val accountId = UUID.randomUUID()
        val financialAccountIdA = UUID.randomUUID()
        val financialAccountIdB = UUID.randomUUID()
        val ytdBoundary = LocalDate.of(2025, 12, 31)
        val withdrawalDate = LocalDate.of(2026, 2, 10)
        val endDate = LocalDate.of(2026, 4, 3)

        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
            listOf(
                financialAccount(accountId, financialAccountIdA),
                financialAccount(accountId, financialAccountIdB),
            ),
        )
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountIdA,
                PortfolioDimensionType.PORTFOLIO,
                null,
                LocalDate.now(),
            ),
        ).thenReturn(quota(financialAccountIdA, endDate, "1.05000000", "105.00"))
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountIdB,
                PortfolioDimensionType.PORTFOLIO,
                null,
                LocalDate.now(),
            ),
        ).thenReturn(quota(financialAccountIdB, endDate, "1.05000000", "210.00"))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountIdA,
                PortfolioDimensionType.PORTFOLIO,
                null,
                ytdBoundary,
                endDate,
            ),
        ).thenReturn(
            listOf(
                quota(financialAccountIdA, ytdBoundary, "1.00000000", "100.00"),
                quota(financialAccountIdA, withdrawalDate, "1.02000000", "102.00"),
                quota(financialAccountIdA, endDate, "1.05000000", "105.00"),
            ),
        )
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountIdB,
                PortfolioDimensionType.PORTFOLIO,
                null,
                ytdBoundary,
                endDate,
            ),
        ).thenReturn(
            listOf(
                quota(financialAccountIdB, ytdBoundary, "1.00000000", "200.00"),
                quota(financialAccountIdB, withdrawalDate, "1.02000000", "180.00"),
                quota(financialAccountIdB, endDate, "1.05000000", "210.00"),
            ),
        )
        whenever(
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountIdA,
                ytdBoundary.plusDays(1),
                endDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountIdB,
                ytdBoundary.plusDays(1),
                endDate,
            ),
        )
            .thenReturn(
                listOf(
                    InvestmentPortfolioFlowEvent(
                        financialAccountId = financialAccountIdB,
                        accountId = accountId,
                        eventDate = withdrawalDate,
                        eventType = PortfolioFlowEventType.WITHDRAWAL,
                        grossAmount = BigDecimal("24.00"),
                        portfolioValueBefore = BigDecimal("204.00"),
                        quotaValueAtEvent = BigDecimal("1.02000000"),
                        quotasTransacted = BigDecimal("-23.53"),
                    ),
                ),
            )
        whenever(marketBenchmarkDailyRepository.findByCodeAndDateRange(any(), any(), any())).thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = null,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
                hasEstimatedValuesFallback = false,
            )

        assertThat(performance.twr).isNotNull()
        assertThat(performance.twr!!).isBetween(0.12, 0.16)
    }

    @Test
    fun `CDI benchmark TWR compounds daily factors over the window`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val startDate = LocalDate.of(2026, 1, 5)
        val endDate = LocalDate.of(2026, 1, 7)

        val startQuota =
            quota(financialAccountId, startDate, "1.00000000", "1000.00")
        val endQuota =
            quota(financialAccountId, endDate, "1.00000000", "1000.00")

        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = LocalDate.now(),
            ),
        ).thenReturn(endQuota)
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = LocalDate.of(1970, 1, 1),
                to = endDate,
            ),
        ).thenReturn(listOf(startQuota, endQuota))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = startDate,
                to = endDate,
            ),
        ).thenReturn(listOf(startQuota, endQuota))
        val from = startDate.plusDays(1)
        whenever(marketBenchmarkDailyRepository.findByCodeAndDateRange(MarketBenchmarkCode.CDI, from, endDate))
            .thenReturn(
                listOf(
                    benchmark(MarketBenchmarkCode.CDI, LocalDate.of(2026, 1, 6), "1.0100000000"),
                    benchmark(MarketBenchmarkCode.CDI, LocalDate.of(2026, 1, 7), "1.0200000000"),
                ),
            )
        whenever(marketBenchmarkDailyRepository.findByCodeAndDateRange(MarketBenchmarkCode.IBOV, from, endDate))
            .thenReturn(emptyList())
        whenever(marketBenchmarkDailyRepository.findByCodeAndDateRange(MarketBenchmarkCode.IPCA, from, endDate))
            .thenReturn(emptyList())
        whenever(marketBenchmarkDailyRepository.findByCodeAndDateRange(MarketBenchmarkCode.IFIX, from, endDate))
            .thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                period = PerformancePeriodDTO.ALL,
                hasEstimatedValuesFallback = false,
            )

        val expectedCdiTwr =
            BigDecimal("1.0100000000")
                .multiply(BigDecimal("1.0200000000"))
                .subtract(BigDecimal.ONE)
                .setScale(8, RoundingMode.HALF_UP)
                .toDouble()
        assertThat(performance.benchmarks.first { it.code == "CDI" }.twr).isEqualTo(expectedCdiTwr)
    }

    @Test
    fun `scoped performance returns TWR with estimated quotas when hasEstimatedValuesFallback is true`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val startDate = LocalDate.of(2026, 1, 1)
        val endDate = LocalDate.of(2026, 1, 10)

        val startQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = startDate,
                quotaValue = "1.00000000",
                totalPortfolioValue = "1000.00",
                hasEstimatedValues = true,
            )
        val endQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = endDate,
                quotaValue = "1.02000000",
                totalPortfolioValue = "1020.00",
                hasEstimatedValues = true,
            )

        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = LocalDate.now(),
            ),
        ).thenReturn(endQuota)
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = LocalDate.of(1970, 1, 1),
                to = endDate,
            ),
        ).thenReturn(listOf(startQuota, endQuota))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = startDate,
                to = endDate,
            ),
        ).thenReturn(listOf(startQuota, endQuota))
        whenever(marketBenchmarkDailyRepository.findByCodeAndDateRange(any(), any(), any())).thenReturn(emptyList())

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                period = PerformancePeriodDTO.ALL,
                hasEstimatedValuesFallback = true,
            )

        assertThat(performance.twr).isEqualTo(0.02)
        assertThat(performance.hasEstimatedValues).isTrue()
    }

    @Test
    fun `asset group TWR when group first appears after YTD boundary uses first in-range quota as start`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val ytdBoundary = LocalDate.of(2025, 12, 31)
        val firstGroupQuotaDate = LocalDate.of(2026, 2, 1)
        val endDate = LocalDate.of(2026, 4, 3)

        val startQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = firstGroupQuotaDate,
                quotaValue = "1.00000000",
                totalPortfolioValue = "500.00",
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
            )
        val endQuota =
            quota(
                financialAccountId = financialAccountId,
                quotaDate = endDate,
                quotaValue = "1.10000000",
                totalPortfolioValue = "550.00",
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
            )

        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
                date = LocalDate.now(),
            ),
        ).thenReturn(endQuota)
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
                date = ytdBoundary,
            ),
        ).thenReturn(null)
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
                from = ytdBoundary.plusDays(1),
                to = endDate,
            ),
        ).thenReturn(listOf(startQuota, endQuota))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "fixedIncome",
                from = firstGroupQuotaDate,
                to = endDate,
            ),
        ).thenReturn(listOf(startQuota, endQuota))
        whenever(holdingRepository.findByFinancialAccount(financialAccountId, accountId)).thenReturn(emptyList())
        whenever(
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountId,
                firstGroupQuotaDate.plusDays(1),
                endDate,
            ),
        )
            .thenReturn(emptyList())

        val twr =
            service.getAssetGroupPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                groupKey = "fixedIncome",
                period = PerformancePeriodDTO.YTD,
            )

        assertThat(twr).isEqualTo(0.1)
    }

    @Test
    fun `scoped portfolio returns null TWR when no quota exists yet`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()

        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = LocalDate.now(),
            ),
        ).thenReturn(null)

        val performance =
            service.getPortfolioPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
                hasEstimatedValuesFallback = false,
            )

        assertThat(performance.twr).isNull()
        assertThat(performance.twrSince).isNull()
        assertThat(performance.benchmarks).isEmpty()
    }

    @Test
    fun `consolidated path aggregates accounts while scoped path uses single account series`() {
        val accountId = UUID.randomUUID()
        val financialAccountIdA = UUID.randomUUID()
        val financialAccountIdB = UUID.randomUUID()
        val ytdBoundary = LocalDate.of(2025, 12, 31)
        val endDate = LocalDate.of(2026, 4, 3)

        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
            listOf(
                financialAccount(accountId, financialAccountIdA),
                financialAccount(accountId, financialAccountIdB),
            ),
        )
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountIdA,
                PortfolioDimensionType.PORTFOLIO,
                null,
                LocalDate.now(),
            ),
        ).thenReturn(quota(financialAccountIdA, endDate, "1.20000000", "120.00"))
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountIdB,
                PortfolioDimensionType.PORTFOLIO,
                null,
                LocalDate.now(),
            ),
        ).thenReturn(quota(financialAccountIdB, endDate, "1.05000000", "210.00"))
        whenever(
            portfolioQuotaRepository.findLatestBefore(
                financialAccountIdA,
                PortfolioDimensionType.PORTFOLIO,
                null,
                ytdBoundary,
            ),
        ).thenReturn(quota(financialAccountIdA, ytdBoundary, "1.00000000", "100.00"))
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountIdA,
                PortfolioDimensionType.PORTFOLIO,
                null,
                ytdBoundary,
                endDate,
            ),
        ).thenReturn(
            listOf(
                quota(financialAccountIdA, ytdBoundary, "1.00000000", "100.00"),
                quota(financialAccountIdA, endDate, "1.20000000", "120.00"),
            ),
        )
        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountIdB,
                PortfolioDimensionType.PORTFOLIO,
                null,
                ytdBoundary,
                endDate,
            ),
        ).thenReturn(
            listOf(
                quota(financialAccountIdB, ytdBoundary, "1.00000000", "200.00"),
                quota(financialAccountIdB, endDate, "1.05000000", "210.00"),
            ),
        )
        whenever(
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountIdA,
                ytdBoundary.plusDays(1),
                endDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountIdB,
                ytdBoundary.plusDays(1),
                endDate,
            ),
        )
            .thenReturn(emptyList())
        whenever(marketBenchmarkDailyRepository.findByCodeAndDateRange(any(), any(), any())).thenReturn(emptyList())

        val consolidated =
            service.getPortfolioPerformance(
                financialAccountId = null,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
                hasEstimatedValuesFallback = false,
            )
        val scopedA =
            service.getPortfolioPerformance(
                financialAccountId = financialAccountIdA,
                accountId = accountId,
                period = PerformancePeriodDTO.YTD,
                hasEstimatedValuesFallback = false,
            )

        assertThat(scopedA.twr).isEqualTo(0.2)
        assertThat(consolidated.twr).isNotEqualTo(scopedA.twr)
        assertThat(consolidated.twr!!).isBetween(0.10, 0.14)
    }

    @Test
    fun `grid class series returns empty for unknown key`() {
        val result =
            service.getGridClassTwrSeries(
                financialAccountId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                gridClassKey = "unknown",
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `grid class series returns scoped rows in ascending order`() {
        val financialAccountId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val d1 = LocalDate.of(2026, 1, 1)
        val d2 = LocalDate.of(2026, 1, 2)
        val expectedFrom = LocalDate.now().minusYears(1)

        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.GRID_CLASS,
                dimensionKey = GridClassMapping.FIXED_INCOME,
                from = expectedFrom,
                to = LocalDate.now(),
            ),
        ).thenReturn(
            listOf(
                quota(
                    financialAccountId,
                    d1,
                    "1.00000000",
                    "100.00",
                    dimensionType = PortfolioDimensionType.GRID_CLASS,
                    dimensionKey = GridClassMapping.FIXED_INCOME,
                ),
                quota(
                    financialAccountId,
                    d2,
                    "1.05000000",
                    "105.00",
                    dimensionType = PortfolioDimensionType.GRID_CLASS,
                    dimensionKey = GridClassMapping.FIXED_INCOME,
                ),
            ),
        )

        val result =
            service.getGridClassTwrSeries(
                financialAccountId = financialAccountId,
                accountId = accountId,
                gridClassKey = GridClassMapping.FIXED_INCOME,
            )

        assertThat(result).hasSize(2)
        assertThat(result.map { it.date }).containsExactly(d1, d2)
        assertThat(result.map { it.quotaValue }).containsExactly(1.0, 1.05)
    }

    @Test
    fun `grid class cash series returns empty because cash is outside holdings-only TWR universe`() {
        val result =
            service.getGridClassTwrSeries(
                financialAccountId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                gridClassKey = GridClassMapping.CASH,
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun `grid class series consolidates by summed value and renormalizes`() {
        val accountId = UUID.randomUUID()
        val financialAccountIdA = UUID.randomUUID()
        val financialAccountIdB = UUID.randomUUID()
        val d1 = LocalDate.of(2026, 1, 1)
        val d2 = LocalDate.of(2026, 1, 2)
        val expectedFrom = LocalDate.now().minusYears(1)

        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
            listOf(
                financialAccount(accountId, financialAccountIdA),
                financialAccount(accountId, financialAccountIdB),
            ),
        )
        whenever(
            portfolioQuotaRepository.findByAccountsAndDateRange(
                financialAccountIds = listOf(financialAccountIdA, financialAccountIdB),
                dimensionType = PortfolioDimensionType.GRID_CLASS,
                dimensionKey = GridClassMapping.STOCKS,
                from = expectedFrom,
                to = LocalDate.now(),
            ),
        ).thenReturn(
            listOf(
                quota(
                    financialAccountIdA,
                    d1,
                    "1.00000000",
                    "100.00",
                    dimensionType = PortfolioDimensionType.GRID_CLASS,
                    dimensionKey = GridClassMapping.STOCKS,
                ),
                quota(
                    financialAccountIdA,
                    d2,
                    "1.10000000",
                    "110.00",
                    dimensionType = PortfolioDimensionType.GRID_CLASS,
                    dimensionKey = GridClassMapping.STOCKS,
                ),
                quota(
                    financialAccountIdB,
                    d1,
                    "1.00000000",
                    "50.00",
                    dimensionType = PortfolioDimensionType.GRID_CLASS,
                    dimensionKey = GridClassMapping.STOCKS,
                ),
                quota(
                    financialAccountIdB,
                    d2,
                    "1.10000000",
                    "55.00",
                    dimensionType = PortfolioDimensionType.GRID_CLASS,
                    dimensionKey = GridClassMapping.STOCKS,
                ),
            ),
        )

        val result =
            service.getGridClassTwrSeries(
                financialAccountId = null,
                accountId = accountId,
                gridClassKey = GridClassMapping.STOCKS,
            )

        assertThat(result.map { it.date }).containsExactly(d1, d2)
        assertThat(result.map { it.quotaValue.firstDecimal() }).containsExactly(1.0, 1.1)
    }

    @Test
    fun `grid class series ONE_YEAR uses one-year window`() {
        val financialAccountId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val expectedFrom = LocalDate.now().minusYears(1)

        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.GRID_CLASS,
                dimensionKey = GridClassMapping.STOCKS,
                from = expectedFrom,
                to = LocalDate.now(),
            ),
        ).thenReturn(emptyList())

        service.getGridClassTwrSeries(
            financialAccountId = financialAccountId,
            accountId = accountId,
            gridClassKey = GridClassMapping.STOCKS,
            period = GridClassTwrPeriod.ONE_YEAR,
        )

        verify(portfolioQuotaRepository).findByAccountAndDateRange(
            financialAccountId = eq(financialAccountId),
            dimensionType = eq(PortfolioDimensionType.GRID_CLASS),
            dimensionKey = eq(GridClassMapping.STOCKS),
            from = eq(expectedFrom),
            to = eq(LocalDate.now()),
        )
    }

    @Test
    fun `grid class series SIX_MONTHS uses six-month window`() {
        val financialAccountId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val expectedFrom = LocalDate.now().minusMonths(6)

        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.GRID_CLASS,
                dimensionKey = GridClassMapping.STOCKS,
                from = expectedFrom,
                to = LocalDate.now(),
            ),
        ).thenReturn(emptyList())

        service.getGridClassTwrSeries(
            financialAccountId = financialAccountId,
            accountId = accountId,
            gridClassKey = GridClassMapping.STOCKS,
            period = GridClassTwrPeriod.SIX_MONTHS,
        )

        verify(portfolioQuotaRepository).findByAccountAndDateRange(
            financialAccountId = eq(financialAccountId),
            dimensionType = eq(PortfolioDimensionType.GRID_CLASS),
            dimensionKey = eq(GridClassMapping.STOCKS),
            from = eq(expectedFrom),
            to = eq(LocalDate.now()),
        )
    }

    @Test
    fun `grid class series ALL uses full history window`() {
        val financialAccountId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val expectedFrom = LocalDate.of(1970, 1, 1)

        whenever(
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.GRID_CLASS,
                dimensionKey = GridClassMapping.STOCKS,
                from = expectedFrom,
                to = LocalDate.now(),
            ),
        ).thenReturn(emptyList())

        service.getGridClassTwrSeries(
            financialAccountId = financialAccountId,
            accountId = accountId,
            gridClassKey = GridClassMapping.STOCKS,
            period = GridClassTwrPeriod.ALL,
        )

        verify(portfolioQuotaRepository).findByAccountAndDateRange(
            financialAccountId = eq(financialAccountId),
            dimensionType = eq(PortfolioDimensionType.GRID_CLASS),
            dimensionKey = eq(GridClassMapping.STOCKS),
            from = eq(expectedFrom),
            to = eq(LocalDate.now()),
        )
    }

    @Test
    fun `consolidated grid class normalization base uses first row inside requested period`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val expectedFrom = LocalDate.now().minusYears(1)
        val d1 = LocalDate.now().minusMonths(11)
        val d2 = LocalDate.now().minusMonths(10)

        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(
            listOf(financialAccount(accountId, financialAccountId)),
        )
        whenever(
            portfolioQuotaRepository.findByAccountsAndDateRange(
                financialAccountIds = listOf(financialAccountId),
                dimensionType = PortfolioDimensionType.GRID_CLASS,
                dimensionKey = GridClassMapping.STOCKS,
                from = expectedFrom,
                to = LocalDate.now(),
            ),
        ).thenReturn(
            listOf(
                quota(
                    financialAccountId,
                    d1,
                    "9.00000000",
                    "90.00",
                    dimensionType = PortfolioDimensionType.GRID_CLASS,
                    dimensionKey = GridClassMapping.STOCKS,
                ),
                quota(
                    financialAccountId,
                    d2,
                    "10.80000000",
                    "108.00",
                    dimensionType = PortfolioDimensionType.GRID_CLASS,
                    dimensionKey = GridClassMapping.STOCKS,
                ),
            ),
        )

        val result =
            service.getGridClassTwrSeries(
                financialAccountId = null,
                accountId = accountId,
                gridClassKey = GridClassMapping.STOCKS,
                period = GridClassTwrPeriod.ONE_YEAR,
            )

        assertThat(result.map { it.date }).containsExactly(d1, d2)
        assertThat(result.map { it.quotaValue.firstDecimal() }).containsExactly(1.0, 1.2)
    }

    private fun quota(
        financialAccountId: UUID,
        quotaDate: LocalDate,
        quotaValue: String,
        totalPortfolioValue: String,
        hasEstimatedValues: Boolean = false,
        dimensionType: PortfolioDimensionType = PortfolioDimensionType.PORTFOLIO,
        dimensionKey: String? = null,
    ) = InvestmentPortfolioQuota(
        financialAccountId = financialAccountId,
        dimensionType = dimensionType,
        dimensionKey = dimensionKey,
        quotaDate = quotaDate,
        quotaValue = BigDecimal(quotaValue),
        totalQuotas = BigDecimal("100.00000000"),
        totalPortfolioValue = BigDecimal(totalPortfolioValue),
        hasEstimatedValues = hasEstimatedValues,
    )

    private fun benchmark(
        code: MarketBenchmarkCode,
        date: LocalDate,
        dailyFactor: String,
    ) = MarketBenchmarkDaily(
        benchmarkCode = code,
        priceDate = date,
        dailyFactor = BigDecimal(dailyFactor),
        source = "TEST",
    )

    private fun financialAccount(
        accountId: UUID,
        financialAccountId: UUID,
    ) = FinancialAccount(
        id = financialAccountId,
        accountId = accountId,
        createdByUserId = UUID.randomUUID(),
        type = FinancialAccountType.INVESTMENT,
        subtype = FinancialAccountSubtype.BROKERAGE,
        name = "Broker",
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
    )

    private fun holding(
        id: UUID,
        financialAccountId: UUID,
        accountId: UUID,
        assetId: UUID,
    ) = Holding(
        id = id,
        financialAccountId = financialAccountId,
        accountId = accountId,
        assetId = assetId,
        quantity = BigDecimal.ONE,
        averageCost = BigDecimal("1000.00"),
        issueDate = LocalDate.of(2025, 1, 1),
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
    )

    private fun asset(
        id: UUID,
        assetType: AssetType,
    ) = Asset(
        id = id,
        name = "Asset",
        assetType = assetType,
        priceFactor = BigDecimal.ONE,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
    )
}

private fun Double.firstDecimal(): Double = (this * 10.0).toInt() / 10.0
