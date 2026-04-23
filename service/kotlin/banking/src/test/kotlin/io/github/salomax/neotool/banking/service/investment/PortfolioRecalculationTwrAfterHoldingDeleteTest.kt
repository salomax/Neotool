package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.IndicatorRateProvider
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioValueWindow
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.portfolio.AssetDailyPriceRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioFlowEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Regression: after adding a third holding (with PURCHASE [PortfolioFlowEventType.DEPOSIT]) and then deleting it,
 * portfolio quota history and derived TWR must match the state with only the first two holdings — same as the
 * manual check (save metrics after holding 2, add 3, delete 3, metrics return).
 */
class PortfolioRecalculationTwrAfterHoldingDeleteTest {

    private val financialAccountId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    private val assetId1 = UUID.randomUUID()
    private val assetId2 = UUID.randomUUID()
    private val assetId3 = UUID.randomUUID()
    private val holdingId1 = UUID.randomUUID()
    private val holdingId2 = UUID.randomUUID()
    private val holdingId3 = UUID.randomUUID()
    private val txId1 = UUID.randomUUID()
    private val txId2 = UUID.randomUUID()
    private val txId3 = UUID.randomUUID()

    private val today = LocalDate.of(2026, 4, 5)
    private val purchase1 = LocalDate.of(2026, 1, 2)
    private val purchase2 = LocalDate.of(2026, 2, 1)
    private val purchase3 = LocalDate.of(2026, 3, 15)

    private val assets =
        listOf(
            Asset(id = assetId1, name = "A1", assetType = AssetType.STOCK),
            Asset(id = assetId2, name = "A2", assetType = AssetType.STOCK),
            Asset(id = assetId3, name = "A3", assetType = AssetType.STOCK),
        )

    private class InMemoryPortfolioQuotaRepository : PortfolioQuotaRepository {
        private val rows = mutableListOf<InvestmentPortfolioQuota>()

        override fun upsert(quota: InvestmentPortfolioQuota): InvestmentPortfolioQuota {
            rows.removeAll {
                it.financialAccountId == quota.financialAccountId &&
                    it.dimensionType == quota.dimensionType &&
                    it.dimensionKey == quota.dimensionKey &&
                    it.quotaDate == quota.quotaDate
            }
            rows.add(quota)
            return quota
        }

        override fun batchUpsert(quotas: List<InvestmentPortfolioQuota>): List<InvestmentPortfolioQuota> {
            val saved = ArrayList<InvestmentPortfolioQuota>(quotas.size)
            for (q in quotas) {
                saved.add(upsert(q))
            }
            return saved
        }

        override fun findLatestBefore(
            financialAccountId: UUID,
            dimensionType: PortfolioDimensionType,
            dimensionKey: String?,
            date: LocalDate,
        ): InvestmentPortfolioQuota? =
            rows
                .asSequence()
                .filter {
                    it.financialAccountId == financialAccountId &&
                        it.dimensionType == dimensionType &&
                        it.dimensionKey == dimensionKey &&
                        it.quotaDate < date
                }
                .maxByOrNull { it.quotaDate }

        override fun findEarliestPortfolioDate(financialAccountId: UUID): LocalDate? =
            rows
                .asSequence()
                .filter {
                    it.financialAccountId == financialAccountId &&
                        it.dimensionType == PortfolioDimensionType.PORTFOLIO &&
                        (it.dimensionKey == null || it.dimensionKey == "")
                }
                .minByOrNull { it.quotaDate }
                ?.quotaDate

        override fun findEarliestPortfolioDates(financialAccountIds: Collection<UUID>): Map<UUID, LocalDate> =
            financialAccountIds
                .mapNotNull { id -> findEarliestPortfolioDate(id)?.let { id to it } }
                .toMap()

        override fun findLatestBeforeAllDimensions(
            financialAccountId: UUID,
            date: LocalDate,
        ): Map<PortfolioDimensionType, Map<String, InvestmentPortfolioQuota>> {
            val filtered =
                rows.filter {
                    it.financialAccountId == financialAccountId && it.quotaDate < date
                }
            return filtered
                .groupBy { it.dimensionType }
                .mapValues { (_, quotasForType) ->
                    quotasForType
                        .groupBy { it.dimensionKey ?: "" }
                        .mapNotNull { (key, sameKey) ->
                            val best = sameKey.maxByOrNull { it.quotaDate } ?: return@mapNotNull null
                            key to best
                        }
                        .toMap()
                }
        }

        override fun findByAccountAndDateRange(
            financialAccountId: UUID,
            dimensionType: PortfolioDimensionType,
            dimensionKey: String?,
            from: LocalDate,
            to: LocalDate,
        ): List<InvestmentPortfolioQuota> =
            rows
                .filter {
                    it.financialAccountId == financialAccountId &&
                        it.dimensionType == dimensionType &&
                        it.dimensionKey == dimensionKey &&
                        !it.quotaDate.isBefore(from) &&
                        !it.quotaDate.isAfter(to)
                }
                .sortedBy { it.quotaDate }

        override fun findByAccountsAndDateRange(
            financialAccountIds: List<UUID>,
            dimensionType: PortfolioDimensionType,
            dimensionKey: String?,
            from: LocalDate,
            to: LocalDate,
        ): List<InvestmentPortfolioQuota> =
            rows
                .filter {
                    financialAccountIds.contains(it.financialAccountId) &&
                        it.dimensionType == dimensionType &&
                        it.dimensionKey == dimensionKey &&
                        !it.quotaDate.isBefore(from) &&
                        !it.quotaDate.isAfter(to)
                }
                .sortedBy { it.quotaDate }

        override fun findByTenantAndDateRange(
            accountId: UUID,
            dimensionType: PortfolioDimensionType,
            dimensionKey: String?,
            from: LocalDate,
            to: LocalDate,
        ): List<InvestmentPortfolioQuota> =
            rows
                .filter {
                    it.dimensionType == dimensionType &&
                        it.dimensionKey == dimensionKey &&
                        !it.quotaDate.isBefore(from) &&
                        !it.quotaDate.isAfter(to)
                }
                .sortedBy { it.quotaDate }

        override fun findOpeningAndClosingPortfolioValues(
            accountId: UUID,
            financialAccountId: UUID?,
            startYear: Int,
            startMonth: Int,
            endYear: Int,
            endMonth: Int,
        ): PortfolioValueWindow {
            val from = LocalDate.of(startYear, startMonth, 1)
            val to = LocalDate.of(endYear, endMonth, 1).plusMonths(1).minusDays(1)

            val portfolioRows =
                rows.filter {
                    it.dimensionType == PortfolioDimensionType.PORTFOLIO &&
                        (it.dimensionKey == null || it.dimensionKey == "") &&
                        (financialAccountId == null || it.financialAccountId == financialAccountId)
                }

            val byAccount = portfolioRows.groupBy { it.financialAccountId }
            val opening =
                byAccount.values.fold(BigDecimal.ZERO) { acc, quotas ->
                    acc + (quotas.filter { it.quotaDate < from }.maxByOrNull { it.quotaDate }?.totalPortfolioValue
                        ?: BigDecimal.ZERO)
                }
            val closing =
                byAccount.values.fold(BigDecimal.ZERO) { acc, quotas ->
                    acc + (quotas.filter { !it.quotaDate.isAfter(to) }.maxByOrNull { it.quotaDate }?.totalPortfolioValue
                        ?: BigDecimal.ZERO)
                }
            return PortfolioValueWindow(
                openingPortfolioValue = opening,
                closingPortfolioValue = closing,
            )
        }

        override fun deleteFromDate(financialAccountId: UUID, from: LocalDate): Int {
            val before = rows.size
            rows.removeAll { it.financialAccountId == financialAccountId && !it.quotaDate.isBefore(from) }
            return before - rows.size
        }

        fun portfolioRows(): List<InvestmentPortfolioQuota> =
            rows
                .filter { it.dimensionType == PortfolioDimensionType.PORTFOLIO && it.dimensionKey == null }
                .sortedBy { it.quotaDate }
    }

    private fun holding(id: UUID, assetId: UUID) =
        Holding(
            id = id,
            financialAccountId = financialAccountId,
            accountId = accountId,
            assetId = assetId,
            quantity = BigDecimal.ZERO,
            averageCost = BigDecimal.ZERO,
            source = InvestmentSource.MANUAL,
        )

    private fun purchaseTx(
        id: UUID,
        holdingId: UUID,
        tradeDate: LocalDate,
        qty: String,
        unitPrice: String,
    ) = InvestmentTransaction(
        id = id,
        holdingId = holdingId,
        financialAccountId = financialAccountId,
        accountId = accountId,
        createdByUserId = userId,
        direction = InvestmentTransactionDirection.INFLOW,
        type = InvestmentTransactionType.PURCHASE,
        quantity = BigDecimal(qty),
        unitPrice = BigDecimal(unitPrice),
        grossValue = BigDecimal(qty).multiply(BigDecimal(unitPrice)),
        tradeDate = tradeDate,
        source = InvestmentSource.MANUAL,
    )

    private fun depositFlow(
        eventDate: LocalDate,
        gross: BigDecimal,
        relatedHoldingId: UUID,
    ) = InvestmentPortfolioFlowEvent(
        financialAccountId = financialAccountId,
        accountId = accountId,
        eventDate = eventDate,
        eventType = PortfolioFlowEventType.DEPOSIT,
        grossAmount = gross,
        portfolioValueBefore = BigDecimal.ZERO,
        quotaValueAtEvent = BigDecimal.ONE,
        quotasTransacted = BigDecimal.ZERO,
        relatedHoldingId = relatedHoldingId,
        createdAt = Instant.now(),
    )

    private fun assertPortfolioSeriesEqual(
        expected: List<InvestmentPortfolioQuota>,
        actual: List<InvestmentPortfolioQuota>,
    ) {
        assertThat(actual).hasSameSizeAs(expected)
        expected.zip(actual).forEach { (e, a) ->
            assertThat(a.quotaDate).isEqualTo(e.quotaDate)
            assertThat(a.quotaValue).isEqualByComparingTo(e.quotaValue)
            assertThat(a.totalPortfolioValue).isEqualByComparingTo(e.totalPortfolioValue)
            assertThat(a.totalQuotas).isEqualByComparingTo(e.totalQuotas)
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `recalculation after deleting third holding restores portfolio quota series to two-holding baseline`() {
        val portfolioQuotaRepository = InMemoryPortfolioQuotaRepository()
        val portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository = mock()
        val portfolioFlowEventRepository: PortfolioFlowEventRepository = mock()
        val holdingRepository: HoldingRepository = mock()
        val assetRepository: AssetRepository = mock()
        val investmentTransactionRepository: InvestmentTransactionRepository = mock()
        val assetDailyPriceRepository: AssetDailyPriceRepository = mock()
        val fixedIncomeValuationService: FixedIncomeValuationService = mock()
        val indicatorRateProvider: IndicatorRateProvider = mock()
        val portfolioRecalculationEventBus: PortfolioRecalculationEventBus = mock()

        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(assetRepository.findByIds(any())).thenAnswer { invocation ->
            val ids = invocation.arguments[0] as Collection<UUID>
            assets.filter { it.id in ids }
        }
        whenever(assetDailyPriceRepository.findByAssetAndDate(any(), any())).thenAnswer { invocation ->
            val assetId = invocation.arguments[0] as UUID
            val date = invocation.arguments[1] as LocalDate
            InvestmentAssetDailyPrice(
                assetId = assetId,
                priceDate = date,
                closePrice = BigDecimal.ONE,
                source = "test",
            )
        }

        var holdings: List<Holding> = emptyList()
        var transactions: List<InvestmentTransaction> = emptyList()
        var flows: List<InvestmentPortfolioFlowEvent> = emptyList()

        whenever(holdingRepository.findByFinancialAccount(financialAccountId, accountId)).thenAnswer { holdings }
        whenever(investmentTransactionRepository.findByHoldingIds(any(), any())).thenAnswer {
            val ids = (it.arguments[0] as Collection<UUID>).toSet()
            transactions.filter { tx -> tx.holdingId in ids }
        }
        whenever(portfolioFlowEventRepository.findByAccountAndDateRange(any(), any(), any())).thenAnswer {
            val from = it.arguments[1] as LocalDate
            val to = it.arguments[2] as LocalDate
            flows.filter { ev -> !ev.eventDate.isBefore(from) && !ev.eventDate.isAfter(to) }
        }

        whenever(portfolioRecalculationJobRepository.update(any())).thenAnswer { it.arguments[0] }

        val service =
            PortfolioRecalculationService(
                portfolioRecalculationJobRepository = portfolioRecalculationJobRepository,
                portfolioQuotaRepository = portfolioQuotaRepository,
                portfolioFlowEventRepository = portfolioFlowEventRepository,
                portfolioQuotaCalculationService = PortfolioQuotaCalculationService(),
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                investmentTransactionRepository = investmentTransactionRepository,
                assetDailyPriceRepository = assetDailyPriceRepository,
                fixedIncomeValuationService = fixedIncomeValuationService,
                indicatorRateProvider = indicatorRateProvider,
                portfolioRecalculationEventBus = portfolioRecalculationEventBus,
                clock = { today },
            )

        fun runJob(recalculateFrom: LocalDate) {
            val jobId = UUID.randomUUID()
            whenever(portfolioRecalculationJobRepository.findById(jobId)).thenReturn(
                PortfolioRecalculationJob(
                    id = jobId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    status = PortfolioRecalculationStatus.PENDING,
                    recalculateFrom = recalculateFrom,
                    triggeredBy = "test",
                ),
            )
            service.process(jobId)
        }

        // ── Baseline: two holdings, DEPOSIT on each purchase (same as InvestmentTransactionService) ──
        holdings =
            listOf(
                holding(holdingId1, assetId1),
                holding(holdingId2, assetId2),
            )
        transactions =
            listOf(
                purchaseTx(txId1, holdingId1, purchase1, "1000", "1"),
                purchaseTx(txId2, holdingId2, purchase2, "500", "1"),
            )
        flows =
            listOf(
                depositFlow(purchase1, BigDecimal("1000"), holdingId1),
                depositFlow(purchase2, BigDecimal("500"), holdingId2),
            )

        runJob(LocalDate.of(2026, 1, 1))
        val baselinePortfolio = portfolioQuotaRepository.portfolioRows()

        // ── Add third holding + purchase + flow; partial recalc from third purchase (mirrors real enqueue) ──
        holdings =
            listOf(
                holding(holdingId1, assetId1),
                holding(holdingId2, assetId2),
                holding(holdingId3, assetId3),
            )
        transactions =
            listOf(
                purchaseTx(txId1, holdingId1, purchase1, "1000", "1"),
                purchaseTx(txId2, holdingId2, purchase2, "500", "1"),
                purchaseTx(txId3, holdingId3, purchase3, "300", "1"),
            )
        flows =
            listOf(
                depositFlow(purchase1, BigDecimal("1000"), holdingId1),
                depositFlow(purchase2, BigDecimal("500"), holdingId2),
                depositFlow(purchase3, BigDecimal("300"), holdingId3),
            )

        runJob(purchase3)

        // ── Delete third holding: same universe as baseline, recalc from removed holding's trade date ──
        holdings =
            listOf(
                holding(holdingId1, assetId1),
                holding(holdingId2, assetId2),
            )
        transactions =
            listOf(
                purchaseTx(txId1, holdingId1, purchase1, "1000", "1"),
                purchaseTx(txId2, holdingId2, purchase2, "500", "1"),
            )
        flows =
            listOf(
                depositFlow(purchase1, BigDecimal("1000"), holdingId1),
                depositFlow(purchase2, BigDecimal("500"), holdingId2),
            )

        runJob(purchase3)

        assertPortfolioSeriesEqual(baselinePortfolio, portfolioQuotaRepository.portfolioRows())
    }
}
