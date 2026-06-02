package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.graphql.investment.PerformancePeriodDTO
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.model.dto.investment.UpdateInvestmentTransactionCommand
import io.github.salomax.neotool.banking.repo.portfolio.AssetDailyPriceRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioFlowEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import io.github.salomax.neotool.banking.service.investment.InvestmentTransactionService
import io.github.salomax.neotool.banking.service.investment.PortfolioFlowEventService
import io.github.salomax.neotool.banking.service.investment.PortfolioPerformanceQueryService
import io.github.salomax.neotool.banking.service.investment.PortfolioRecalculationService
import io.github.salomax.neotool.banking.service.investment.TwrTestOracle
import io.github.salomax.neotool.banking.service.investment.TwrTestTolerance
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

@MicronautTest(startApplication = true)
@DisplayName("Portfolio TWR pipeline integration")
class PortfolioTwrPipelineIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var investmentTransactionService: InvestmentTransactionService

    @Inject
    lateinit var portfolioRecalculationService: PortfolioRecalculationService

    @Inject
    lateinit var portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository

    @Inject
    lateinit var portfolioPerformanceQueryService: PortfolioPerformanceQueryService

    @Inject
    lateinit var portfolioFlowEventService: PortfolioFlowEventService

    @Inject
    lateinit var portfolioFlowEventRepository: PortfolioFlowEventRepository

    @Inject
    lateinit var portfolioQuotaRepository: PortfolioQuotaRepository

    @Inject
    lateinit var assetDailyPriceRepository: AssetDailyPriceRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountA: UUID
    private lateinit var financialAccountB: UUID
    private lateinit var stockAssetId: UUID

    @BeforeEach
    fun setUpFixture() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        financialAccountA = UUID.randomUUID()
        financialAccountB = UUID.randomUUID()
        stockAssetId = UUID.randomUUID()

        openConnection().use { conn ->
            insertFinancialAccount(conn, financialAccountA, "Broker A")
            insertFinancialAccount(conn, financialAccountB, "Broker B")
            insertAsset(conn, stockAssetId, "TWR TEST STOCK", "STOCK")
        }
    }

    @Test
    fun `purchase plus sale flows do not create artificial return`() {
        val d1 = LocalDate.now().minusDays(5)
        val d2 = d1.plusDays(1)
        upsertPrice(stockAssetId, d1, "10.00")
        upsertPrice(stockAssetId, d2, "10.00")

        recordTransaction(
            financialAccountId = financialAccountA,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "100.00",
            unitPrice = "10.00",
            grossValue = "1000.00",
        )
        recordTransaction(
            financialAccountId = financialAccountA,
            tradeDate = d2,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "50.00",
            unitPrice = "10.00",
            grossValue = "500.00",
        )
        recordTransaction(
            financialAccountId = financialAccountA,
            tradeDate = d2,
            direction = InvestmentTransactionDirection.OUTFLOW,
            type = InvestmentTransactionType.SALE,
            quantity = "20.00",
            unitPrice = "10.00",
            grossValue = "200.00",
        )

        processPendingJobs()

        val performance =
            portfolioPerformanceQueryService.getPortfolioPerformance(
                financialAccountId = financialAccountA,
                accountId = accountId,
                period = PerformancePeriodDTO.ALL,
                hasEstimatedValuesFallback = false,
            )

        assertThat(performance.twr).isNotNull()
        assertThat(abs(performance.twr!!)).isLessThanOrEqualTo(TwrTestTolerance.EPS_INTEGRATION)
        assertScopedPerformanceMatchesOracle(financialAccountA, performance.twr!!)
    }

    @Test
    fun `dividend with ex-dividend drop is neutral in TWR`() {
        val d1 = LocalDate.now().minusDays(6)
        val d2 = d1.plusDays(1)
        upsertPrice(stockAssetId, d1, "10.00")
        upsertPrice(stockAssetId, d2, "9.00")

        recordTransaction(
            financialAccountId = financialAccountA,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "100.00",
            unitPrice = "10.00",
            grossValue = "1000.00",
        )
        recordTransaction(
            financialAccountId = financialAccountA,
            tradeDate = d2,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.DIVIDEND,
            grossValue = "100.00",
        )

        processPendingJobs()

        val performance =
            portfolioPerformanceQueryService.getPortfolioPerformance(
                financialAccountId = financialAccountA,
                accountId = accountId,
                period = PerformancePeriodDTO.ALL,
                hasEstimatedValuesFallback = false,
            )

        assertThat(performance.twr).isNotNull()
        assertThat(abs(performance.twr!!)).isLessThanOrEqualTo(TwrTestTolerance.EPS_INTEGRATION)
        assertScopedPerformanceMatchesOracle(financialAccountA, performance.twr!!)
    }

    @Test
    fun `BANKING_TRANSACTION flow event is ignored by TWR engine`() {
        val d1 = LocalDate.now().minusDays(4)
        val d2 = d1.plusDays(1)
        upsertPrice(stockAssetId, d1, "10.00")
        upsertPrice(stockAssetId, d2, "11.00")

        recordTransaction(
            financialAccountId = financialAccountA,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "100.00",
            unitPrice = "10.00",
            grossValue = "1000.00",
        )
        processPendingJobs()
        val baseline =
            requireNotNull(
                portfolioPerformanceQueryService.getPortfolioPerformance(
                    financialAccountId = financialAccountA,
                    accountId = accountId,
                    period = PerformancePeriodDTO.ALL,
                    hasEstimatedValuesFallback = false,
                ).twr,
            )

        portfolioFlowEventRepository.save(
            InvestmentPortfolioFlowEvent(
                financialAccountId = financialAccountA,
                accountId = accountId,
                eventDate = d2,
                eventType = PortfolioFlowEventType.DEPOSIT,
                grossAmount = BigDecimal("5000.00"),
                portfolioValueBefore = BigDecimal("1100.00"),
                quotaValueAtEvent = BigDecimal("1.10000000"),
                quotasTransacted = BigDecimal("4545.45454545"),
                sourceReferenceId = UUID.randomUUID(),
                sourceReferenceType = BANKING_TRANSACTION_SOURCE,
                createdAt = Instant.now(),
            ),
        )
        portfolioFlowEventService.requestRecalculation(
            financialAccountId = financialAccountA,
            accountId = accountId,
            recalculateFrom = d2,
            triggeredBy = "integration:banking-flow-ignored",
        )
        processPendingJobs()

        val after =
            requireNotNull(
                portfolioPerformanceQueryService.getPortfolioPerformance(
                    financialAccountId = financialAccountA,
                    accountId = accountId,
                    period = PerformancePeriodDTO.ALL,
                    hasEstimatedValuesFallback = false,
                ).twr,
            )

        assertThat(abs(after - baseline)).isLessThanOrEqualTo(TwrTestTolerance.EPS_INTEGRATION)
        assertScopedPerformanceMatchesOracle(financialAccountA, after)
    }

    @Test
    fun `consolidated TWR equals scoped when accounts have equivalent strategy`() {
        val d1 = LocalDate.now().minusDays(3)
        val d2 = d1.plusDays(1)
        upsertPrice(stockAssetId, d1, "10.00")
        upsertPrice(stockAssetId, d2, "11.00")

        recordTransaction(
            financialAccountId = financialAccountA,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "60.00",
            unitPrice = "10.00",
            grossValue = "600.00",
        )
        processPendingJobs()
        recordTransaction(
            financialAccountId = financialAccountB,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "40.00",
            unitPrice = "10.00",
            grossValue = "400.00",
        )
        processPendingJobs()

        val scopedA =
            requireNotNull(
                portfolioPerformanceQueryService.getPortfolioPerformance(
                    financialAccountId = financialAccountA,
                    accountId = accountId,
                    period = PerformancePeriodDTO.ALL,
                    hasEstimatedValuesFallback = false,
                ).twr,
            )
        val scopedB =
            requireNotNull(
                portfolioPerformanceQueryService.getPortfolioPerformance(
                    financialAccountId = financialAccountB,
                    accountId = accountId,
                    period = PerformancePeriodDTO.ALL,
                    hasEstimatedValuesFallback = false,
                ).twr,
            )
        val consolidated =
            requireNotNull(
                portfolioPerformanceQueryService.getPortfolioPerformance(
                    financialAccountId = null,
                    accountId = accountId,
                    period = PerformancePeriodDTO.ALL,
                    hasEstimatedValuesFallback = false,
                ).twr,
            )

        assertThat(abs(scopedA - 0.1)).isLessThanOrEqualTo(TwrTestTolerance.EPS_INTEGRATION)
        assertThat(abs(scopedB - 0.1)).isLessThanOrEqualTo(TwrTestTolerance.EPS_INTEGRATION)
        assertThat(abs(consolidated - 0.1)).isLessThanOrEqualTo(TwrTestTolerance.EPS_INTEGRATION)
    }

    @Test
    fun `same-day equivalent purchase permutation is TWR-invariant`() {
        val d1 = LocalDate.now().minusDays(4)

        // Account A: 400 then 600
        recordTransaction(
            financialAccountId = financialAccountA,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "1.00",
            unitPrice = "400.00",
            grossValue = "400.00",
        )
        recordTransaction(
            financialAccountId = financialAccountA,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "1.00",
            unitPrice = "600.00",
            grossValue = "600.00",
        )
        processPendingJobs()

        // Account B: 600 then 400
        recordTransaction(
            financialAccountId = financialAccountB,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "1.00",
            unitPrice = "600.00",
            grossValue = "600.00",
        )
        recordTransaction(
            financialAccountId = financialAccountB,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "1.00",
            unitPrice = "400.00",
            grossValue = "400.00",
        )
        processPendingJobs()

        val snapshotA = scopedPortfolioSnapshot(financialAccountA)
        val snapshotB = scopedPortfolioSnapshot(financialAccountB)
        assertThat(snapshotA).isNotEmpty()
        assertThat(snapshotA).containsExactlyElementsOf(snapshotB)
    }

    @Test
    fun `same-day equivalent fixed-income purchase permutation is TWR-invariant`() {
        val cdbAssetId = UUID.randomUUID()
        openConnection().use { conn ->
            insertAsset(conn, cdbAssetId, "TWR TEST CDB", "CDB")
        }

        val tradeDate = LocalDate.now().minusDays(30)
        val maturityDate = LocalDate.now().plusDays(365)

        // Account A: 400 then 600
        recordTransaction(
            financialAccountId = financialAccountA,
            assetId = cdbAssetId,
            tradeDate = tradeDate,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "1.00",
            unitPrice = "400.00",
            grossValue = "400.00",
            indexer = Indexer.PRE_FIXADO,
            fixedAnnualRate = "0.10",
            issueDate = tradeDate,
            maturityDate = maturityDate,
        )
        recordTransaction(
            financialAccountId = financialAccountA,
            assetId = cdbAssetId,
            tradeDate = tradeDate,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "1.00",
            unitPrice = "600.00",
            grossValue = "600.00",
            indexer = Indexer.PRE_FIXADO,
            fixedAnnualRate = "0.10",
            issueDate = tradeDate,
            maturityDate = maturityDate,
        )
        processPendingJobs()

        // Account B: 600 then 400
        recordTransaction(
            financialAccountId = financialAccountB,
            assetId = cdbAssetId,
            tradeDate = tradeDate,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "1.00",
            unitPrice = "600.00",
            grossValue = "600.00",
            indexer = Indexer.PRE_FIXADO,
            fixedAnnualRate = "0.10",
            issueDate = tradeDate,
            maturityDate = maturityDate,
        )
        recordTransaction(
            financialAccountId = financialAccountB,
            assetId = cdbAssetId,
            tradeDate = tradeDate,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "1.00",
            unitPrice = "400.00",
            grossValue = "400.00",
            indexer = Indexer.PRE_FIXADO,
            fixedAnnualRate = "0.10",
            issueDate = tradeDate,
            maturityDate = maturityDate,
        )
        processPendingJobs()

        val snapshotA = scopedPortfolioSnapshot(financialAccountA)
        val snapshotB = scopedPortfolioSnapshot(financialAccountB)
        assertThat(snapshotA).isNotEmpty()
        assertThat(snapshotA).containsExactlyElementsOf(snapshotB)
    }

    @Test
    fun `mixed multi-asset timeline remains consistent with oracle`() {
        val fundAssetId = UUID.randomUUID()
        val cryptoAssetId = UUID.randomUUID()
        openConnection().use { conn ->
            insertAsset(conn, fundAssetId, "TWR TEST FUND", "MUTUAL_FUND")
            insertAsset(conn, cryptoAssetId, "TWR TEST CRYPTO", "CRYPTO")
        }

        val d1 = LocalDate.now().minusDays(9)
        val d2 = d1.plusDays(1)
        val d3 = d2.plusDays(1)
        upsertPrice(stockAssetId, d1, "10.00")
        upsertPrice(stockAssetId, d2, "10.60")
        upsertPrice(stockAssetId, d3, "10.60")
        upsertPrice(fundAssetId, d1, "10.00")
        upsertPrice(fundAssetId, d2, "10.10")
        upsertPrice(fundAssetId, d3, "10.10")
        upsertPrice(cryptoAssetId, d1, "20.00")
        upsertPrice(cryptoAssetId, d2, "22.00")
        upsertPrice(cryptoAssetId, d3, "21.00")

        recordTransaction(
            financialAccountId = financialAccountA,
            assetId = stockAssetId,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "50.00",
            unitPrice = "10.00",
            grossValue = "500.00",
        )
        recordTransaction(
            financialAccountId = financialAccountA,
            assetId = fundAssetId,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "30.00",
            unitPrice = "10.00",
            grossValue = "300.00",
        )
        recordTransaction(
            financialAccountId = financialAccountA,
            assetId = cryptoAssetId,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "10.00",
            unitPrice = "20.00",
            grossValue = "200.00",
        )
        recordTransaction(
            financialAccountId = financialAccountA,
            assetId = stockAssetId,
            tradeDate = d2,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.DIVIDEND,
            grossValue = "20.00",
        )
        recordTransaction(
            financialAccountId = financialAccountA,
            assetId = cryptoAssetId,
            tradeDate = d2,
            direction = InvestmentTransactionDirection.OUTFLOW,
            type = InvestmentTransactionType.SALE,
            quantity = "2.00",
            unitPrice = "22.00",
            grossValue = "44.00",
        )
        recordTransaction(
            financialAccountId = financialAccountA,
            assetId = fundAssetId,
            tradeDate = d2,
            direction = InvestmentTransactionDirection.OUTFLOW,
            type = InvestmentTransactionType.WITHHOLDING_TAX,
            quantity = "1.00",
            grossValue = "5.00",
        )
        processPendingJobs()

        val performance =
            portfolioPerformanceQueryService.getPortfolioPerformance(
                financialAccountId = financialAccountA,
                accountId = accountId,
                period = PerformancePeriodDTO.ALL,
                hasEstimatedValuesFallback = false,
            )
        assertThat(performance.twr).isNotNull()
        assertScopedPerformanceMatchesOracle(financialAccountA, performance.twr!!)
    }

    @Test
    fun `recalculation replay is idempotent`() {
        val d1 = LocalDate.now().minusDays(7)
        val d2 = d1.plusDays(1)
        upsertPrice(stockAssetId, d1, "10.00")
        upsertPrice(stockAssetId, d2, "12.00")

        recordTransaction(
            financialAccountId = financialAccountA,
            tradeDate = d1,
            direction = InvestmentTransactionDirection.INFLOW,
            type = InvestmentTransactionType.PURCHASE,
            quantity = "100.00",
            unitPrice = "10.00",
            grossValue = "1000.00",
        )
        processPendingJobs()

        val twrBefore =
            requireNotNull(
                portfolioPerformanceQueryService.getPortfolioPerformance(
                    financialAccountId = financialAccountA,
                    accountId = accountId,
                    period = PerformancePeriodDTO.ALL,
                    hasEstimatedValuesFallback = false,
                ).twr,
            )
        val snapshotBefore = scopedPortfolioSnapshot(financialAccountA)

        portfolioFlowEventService.requestRecalculation(
            financialAccountId = financialAccountA,
            accountId = accountId,
            recalculateFrom = d1,
            triggeredBy = "integration:idempotent-replay",
        )
        processPendingJobs()

        val twrAfter =
            requireNotNull(
                portfolioPerformanceQueryService.getPortfolioPerformance(
                    financialAccountId = financialAccountA,
                    accountId = accountId,
                    period = PerformancePeriodDTO.ALL,
                    hasEstimatedValuesFallback = false,
                ).twr,
            )
        val snapshotAfter = scopedPortfolioSnapshot(financialAccountA)

        assertThat(abs(twrAfter - twrBefore)).isLessThanOrEqualTo(TwrTestTolerance.EPS_INTEGRATION)
        assertThat(snapshotAfter).containsExactlyElementsOf(snapshotBefore)
    }

    @Test
    fun `holding create update date and delete keeps TWR consistent across recalculations`() {
        val today = LocalDate.now()
        val oneYearAgo = today.minusYears(1)
        val sixMonthsAgo = today.minusMonths(6)
        upsertPrice(stockAssetId, oneYearAgo, "10.00")
        upsertPrice(stockAssetId, sixMonthsAgo, "12.00")
        upsertPrice(stockAssetId, today, "15.00")

        val createdTx =
            recordTransaction(
                financialAccountId = financialAccountA,
                tradeDate = oneYearAgo,
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = "100.00",
                unitPrice = "10.00",
                grossValue = "1000.00",
            )
        processPendingJobs()

        val twrAfterCreate = getPortfolioTwr(financialAccountA)
        assertThat(twrAfterCreate.isFinite()).isTrue()

        investmentTransactionService.update(
            id = requireNotNull(createdTx.id),
            command =
                UpdateInvestmentTransactionCommand(
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("100.00"),
                    unitPrice = BigDecimal("10.00"),
                    grossValue = BigDecimal("1000.00"),
                    netValue = BigDecimal("1000.00"),
                    tradeDate = sixMonthsAgo,
                ),
            accountId = accountId,
            userId = userId,
        )
        processPendingJobs()

        val twrAfterUpdate = getPortfolioTwr(financialAccountA)
        assertThat(twrAfterUpdate.isFinite()).isTrue()
        assertThat(abs(twrAfterUpdate - twrAfterCreate)).isGreaterThan(TwrTestTolerance.EPS_INTEGRATION)

        investmentTransactionService.deleteHolding(
            id = createdTx.holdingId,
            accountId = accountId,
        )
        processPendingJobs()

        val twrAfterDelete = getPortfolioTwr(financialAccountA)
        assertThat(twrAfterDelete.isFinite()).isTrue()
        assertThat(abs(abs(twrAfterDelete) - 1.0)).isLessThanOrEqualTo(TwrTestTolerance.EPS_INTEGRATION)
    }

    private fun recordTransaction(
        financialAccountId: UUID,
        assetId: UUID = stockAssetId,
        tradeDate: LocalDate,
        direction: InvestmentTransactionDirection,
        type: InvestmentTransactionType,
        grossValue: String,
        quantity: String? = null,
        unitPrice: String? = null,
        netValue: String? = grossValue,
        indexer: Indexer? = null,
        fixedAnnualRate: String? = null,
        issueDate: LocalDate? = null,
        maturityDate: LocalDate? = null,
    ): InvestmentTransaction =
        investmentTransactionService.record(
            command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = direction,
                    type = type,
                    quantity = quantity?.let(::BigDecimal),
                    unitPrice = unitPrice?.let(::BigDecimal),
                    grossValue = BigDecimal(grossValue),
                    netValue = netValue?.let(::BigDecimal),
                    tradeDate = tradeDate,
                    indexer = indexer,
                    fixedAnnualRate = fixedAnnualRate?.let(::BigDecimal),
                    issueDate = issueDate,
                    maturityDate = maturityDate,
                    source = InvestmentSource.MANUAL,
                ),
            accountId = accountId,
            userId = userId,
        )

    private fun processPendingJobs() {
        while (true) {
            val pending = portfolioRecalculationJobRepository.findPendingByAccount(accountId) ?: break
            val jobId = requireNotNull(pending.id)
            portfolioRecalculationService.process(jobId)
        }
    }

    private fun assertScopedPerformanceMatchesOracle(
        financialAccountId: UUID,
        actualTwr: Double,
    ) {
        val allQuotas =
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = LocalDate.of(1970, 1, 1),
                to = LocalDate.now(),
            ).sortedBy { it.quotaDate }
        assertThat(allQuotas.size).isGreaterThanOrEqualTo(2)
        val from = allQuotas.first().quotaDate
        val to = allQuotas.last().quotaDate
        val flowRows =
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                from = from.plusDays(1),
                to = to,
            ).filterNot { it.sourceReferenceType == BANKING_TRANSACTION_SOURCE }

        val expected =
            TwrTestOracle.computeTwr(
                series =
                    allQuotas.map {
                        TwrTestOracle.OracleValuePoint(
                            date = it.quotaDate,
                            totalValue = it.totalPortfolioValue,
                        )
                    },
                netFlowByDate =
                    TwrTestOracle.aggregateNetFlowByDate(
                        flowRows.map {
                            TwrTestOracle.OracleFlowPoint(
                                date = it.eventDate,
                                eventType = it.eventType,
                                grossAmount = it.grossAmount,
                            )
                        },
                    ),
            )

        assertThat(expected).isNotNull()
        assertThat(abs(actualTwr - expected!!)).isLessThanOrEqualTo(TwrTestTolerance.EPS_INTEGRATION)
    }

    private fun getPortfolioTwr(financialAccountId: UUID): Double =
        requireNotNull(
            portfolioPerformanceQueryService.getPortfolioPerformance(
                financialAccountId = financialAccountId,
                accountId = accountId,
                period = PerformancePeriodDTO.ALL,
                hasEstimatedValuesFallback = false,
            ).twr,
        )

    private fun scopedPortfolioSnapshot(financialAccountId: UUID): List<String> =
        portfolioQuotaRepository.findByAccountAndDateRange(
            financialAccountId = financialAccountId,
            dimensionType = PortfolioDimensionType.PORTFOLIO,
            dimensionKey = null,
            from = LocalDate.of(1970, 1, 1),
            to = LocalDate.now(),
        ).sortedBy { it.quotaDate }.map { row ->
            "${row.quotaDate}|${row.quotaValue}|${row.totalQuotas}|${row.totalPortfolioValue}"
        }

    private fun upsertPrice(
        assetId: UUID,
        date: LocalDate,
        closePrice: String,
    ) {
        assetDailyPriceRepository.upsert(
            InvestmentAssetDailyPrice(
                assetId = assetId,
                priceDate = date,
                closePrice = BigDecimal(closePrice),
                source = "TEST",
            ),
        )
    }

    private fun insertFinancialAccount(
        conn: java.sql.Connection,
        id: UUID,
        name: String,
    ) {
        conn.createStatement().executeUpdate(
            """
            INSERT INTO banking.financial_accounts (
                id,
                account_id,
                created_by_user_id,
                type,
                subtype,
                name,
                currency_code,
                created_at,
                updated_at
            ) VALUES (
                '$id',
                '$accountId',
                '$userId',
                'INVESTMENT',
                'BROKERAGE',
                '$name',
                'BRL',
                NOW(),
                NOW()
            )
            """.trimIndent(),
        )
    }

    private fun insertAsset(
        conn: java.sql.Connection,
        id: UUID,
        name: String,
        type: String,
    ) {
        conn.createStatement().executeUpdate(
            """
            INSERT INTO banking.investment_assets (
                id,
                name,
                asset_type,
                currency_code,
                created_at,
                updated_at
            ) VALUES (
                '$id',
                '$name',
                '$type',
                'BRL',
                NOW(),
                NOW()
            )
            """.trimIndent(),
        )
    }

    private fun openConnection() =
        getPgContainer().let { container ->
            DriverManager.getConnection(
                buildJdbcUrlWithSslDisabled(container.jdbcUrl),
                container.username,
                container.password,
            )
        }

    private companion object {
        const val BANKING_TRANSACTION_SOURCE = "BANKING_TRANSACTION"
    }
}
