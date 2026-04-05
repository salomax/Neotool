package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.IndicatorRateProvider
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.portfolio.AssetDailyPriceRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioFlowEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Singleton
class PortfolioRecalculationService(
    private val portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository,
    private val portfolioQuotaRepository: PortfolioQuotaRepository,
    private val portfolioFlowEventRepository: PortfolioFlowEventRepository,
    private val portfolioQuotaCalculationService: PortfolioQuotaCalculationService,
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val investmentTransactionRepository: InvestmentTransactionRepository,
    private val assetDailyPriceRepository: AssetDailyPriceRepository,
    private val fixedIncomeValuationService: FixedIncomeValuationService,
    private val indicatorRateProvider: IndicatorRateProvider,
    private val portfolioRecalculationEventBus: PortfolioRecalculationEventBus,
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    private val logger = KotlinLogging.logger {}
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    /**
     * Rebuilds TWR quota history for [jobId] from [PortfolioRecalculationJob.recalculateFrom] through today.
     *
     * Outline: load holdings, trades, bank ledger, and flow events → anchor "previous day" quotas → delete stored
     * rows from the start date → walk each calendar day: cash as of that date, simulated positions from trades,
     * mark-to-market per holding → persist portfolio + per-asset-group quotas → mark job COMPLETED (or FAILED).
     */
    fun process(jobId: UUID) {
        // Idempotency: skip unknown jobs, finished jobs, and duplicate Kafka deliveries while already running.
        val job = portfolioRecalculationJobRepository.findById(jobId) ?: return
        if (job.status == PortfolioRecalculationStatus.COMPLETED || job.status == PortfolioRecalculationStatus.PROCESSING) {
            return
        }

        val financialAccountId = job.financialAccountId
        if (financialAccountId == null) {
            failJob(job, "Consolidated recalculation is not implemented yet.")
            return
        }

        val started =
            portfolioRecalculationJobRepository.update(
                job.copy(
                    status = PortfolioRecalculationStatus.PROCESSING,
                    startedAt = job.startedAt ?: Instant.now(),
                    errorDetails = null,
                    updatedAt = Instant.now(),
                ),
            )
        portfolioRecalculationEventBus.emit(PortfolioRecalculationStatusPayload.from(started))

        try {
            val today = clock()

            // --- Load everything needed for a day-by-day simulation (no DB round-trips inside the inner loop) ---
            val holdings = holdingRepository.findByFinancialAccount(financialAccountId, started.accountId)
            val assetsById = assetRepository.findByIds(holdings.map { it.assetId }.toSet()).associateBy { it.id!! }
            val transactionsByHolding =
                investmentTransactionRepository.findByHoldingIds(holdings.mapNotNull { it.id }, started.accountId)
                    .groupBy { it.holdingId }
                    .mapValues { (_, value) -> value.sortedWith(compareBy({ it.tradeDate }, { it.createdAt })) }
            val flowEventsByDate =
                portfolioFlowEventRepository.findByAccountAndDateRange(financialAccountId, started.recalculateFrom, today)
                    .groupBy { it.eventDate }
            val rateMap = safeGetIndicatorRates()
            val monthlySeries = safeGetMonthlySeries(holdings, today)

            // Last known quotas *before* the first day we rewrite — chain links to history outside the window.
            var previousPortfolioQuota =
                portfolioQuotaRepository.findLatestBefore(
                    financialAccountId = financialAccountId,
                    dimensionType = PortfolioDimensionType.PORTFOLIO,
                    dimensionKey = null,
                    date = started.recalculateFrom.minusDays(1),
                )
            val groupKeys = holdings.map { AssetGroupMapping.groupKeyFor(assetsById[it.assetId]?.assetType) }.toSet()
            val previousGroupQuotas =
                groupKeys.associateWith { groupKey ->
                    portfolioQuotaRepository.findLatestBefore(
                        financialAccountId = financialAccountId,
                        dimensionType = PortfolioDimensionType.ASSET_GROUP,
                        dimensionKey = groupKey,
                        date = started.recalculateFrom.minusDays(1),
                    )
                }.filterValues { it != null }
                    .mapValues { it.value!! }
                    .toMutableMap()

            // Drop persisted rows in [recalculateFrom, ∞) so this run is the single writer for that range.
            portfolioQuotaRepository.deleteFromDate(financialAccountId, started.recalculateFrom)

            // One timeline per holding: replays investment transactions up to "current" day for quantity/cost, then prices.
            val timelines = holdings.mapNotNull { holding ->
                val asset = assetsById[holding.assetId] ?: return@mapNotNull null
                HistoricalHoldingTimeline(
                    baseHolding = holding,
                    asset = asset,
                    transactions = transactionsByHolding[holding.id].orEmpty(),
                )
            }

            var daysProcessed = 0
            var currentJob = started

            var date = started.recalculateFrom
            while (!date.isAfter(today)) {
                // Holdings MTM for this day, rolled up by asset group (e.g. equity vs fixed income).
                val holdingsValueByGroup = mutableMapOf<String, DailyGroupValuation>()
                timelines.forEach { timeline ->
                    timeline.advanceTo(date)
                    val valuation = timeline.valueAt(date, rateMap, monthlySeries, assetDailyPriceRepository, fixedIncomeValuationService)
                    if (valuation.totalValue <= BigDecimal.ZERO) return@forEach

                    val groupKey = AssetGroupMapping.groupKeyFor(timeline.asset.assetType)
                    val existing = holdingsValueByGroup[groupKey]
                    holdingsValueByGroup[groupKey] =
                        if (existing == null) {
                            valuation
                        } else {
                            DailyGroupValuation(
                                totalValue = existing.totalValue + valuation.totalValue,
                                hasEstimatedValues = existing.hasEstimatedValues || valuation.hasEstimatedValues,
                            )
                        }
                }

                // TWR quota math: portfolio row uses flow events; group rows track sub-portfolio performance.
                val dayQuotas =
                    portfolioQuotaCalculationService.calculateDayQuota(
                        financialAccountId = financialAccountId,
                        date = date,
                        previousPortfolioQuota = previousPortfolioQuota,
                        previousGroupQuotas = previousGroupQuotas,
                        holdingsValueByGroup = holdingsValueByGroup,
                        flowEvents = flowEventsByDate[date].orEmpty(),
                    )

                dayQuotas.forEach { quota ->
                    val saved = portfolioQuotaRepository.upsert(quota)
                    if (saved.dimensionType == PortfolioDimensionType.PORTFOLIO) {
                        previousPortfolioQuota = saved
                    } else {
                        previousGroupQuotas[saved.dimensionKey.orEmpty()] = saved
                    }
                }

                daysProcessed += 1
                // Long backfills: heartbeat for SSE / UI without hammering the DB every day.
                if (daysProcessed % 30 == 0) {
                    currentJob =
                        portfolioRecalculationJobRepository.update(
                            currentJob.copy(
                                daysProcessed = daysProcessed,
                                updatedAt = Instant.now(),
                            ),
                        )
                    portfolioRecalculationEventBus.emit(PortfolioRecalculationStatusPayload.from(currentJob))
                }

                date = date.plusDays(1)
            }

            val completed =
                portfolioRecalculationJobRepository.update(
                    currentJob.copy(
                        status = PortfolioRecalculationStatus.COMPLETED,
                        daysProcessed = daysProcessed,
                        completedAt = Instant.now(),
                        updatedAt = Instant.now(),
                    ),
                )
            portfolioRecalculationEventBus.emit(PortfolioRecalculationStatusPayload.from(completed))
        } catch (e: Exception) {
            logger.error(e) { "Portfolio recalculation failed for jobId=$jobId" }
            failJob(started, e.message ?: e::class.simpleName ?: "Unknown recalculation error")
        }
    }

    private fun failJob(
        job: io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob,
        errorDetails: String,
    ) {
        val failed =
            portfolioRecalculationJobRepository.update(
                job.copy(
                    status = PortfolioRecalculationStatus.FAILED,
                    errorDetails = errorDetails,
                    completedAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        portfolioRecalculationEventBus.emit(PortfolioRecalculationStatusPayload.from(failed))
    }

    private fun safeGetIndicatorRates(): Map<Indexer, BigDecimal> =
        try {
            indicatorRateProvider.getLatestRates()
        } catch (_: Exception) {
            emptyMap()
        }

    private fun safeGetMonthlySeries(
        holdings: List<Holding>,
        today: LocalDate,
    ): Map<Indexer, List<Pair<LocalDate, BigDecimal>>> {
        val byIndexer =
            holdings.filter { it.indexer != null && it.issueDate != null }
                .groupBy { it.indexer!! }

        return byIndexer.mapValues { (_, indexedHoldings) ->
            val from = indexedHoldings.mapNotNull { it.issueDate }.minOrNull() ?: return@mapValues emptyList()
            try {
                indicatorRateProvider.getMonthlySeriesRange(indexedHoldings.first().indexer!!, from, today)
            } catch (_: Exception) {
                emptyList()
            }
        }.filterValues { it.isNotEmpty() }
    }

    private data class HistoricalHoldingTimeline(
        val baseHolding: Holding,
        val asset: Asset,
        val transactions: List<InvestmentTransaction>,
        var cursor: Int = 0,
        var currentState: Holding = if (transactions.isEmpty()) baseHolding else baseHolding.copy(quantity = BigDecimal.ZERO, averageCost = BigDecimal.ZERO),
    ) {
        fun advanceTo(date: LocalDate) {
            while (cursor < transactions.size && !transactions[cursor].tradeDate.isAfter(date)) {
                currentState = applyTransaction(currentState, transactions[cursor])
                cursor += 1
            }
        }

        fun valueAt(
            date: LocalDate,
            rateMap: Map<Indexer, BigDecimal>,
            monthlySeries: Map<Indexer, List<Pair<LocalDate, BigDecimal>>>,
            assetDailyPriceRepository: AssetDailyPriceRepository,
            fixedIncomeValuationService: FixedIncomeValuationService,
        ): DailyGroupValuation {
            if (currentState.quantity <= BigDecimal.ZERO) {
                return DailyGroupValuation(BigDecimal.ZERO, false)
            }

            val exactPrice = assetDailyPriceRepository.findByAssetAndDate(asset.id!!, date)
            if (exactPrice != null) {
                return DailyGroupValuation(
                    totalValue = currentState.quantity.multiply(exactPrice.closePrice, MATH_CONTEXT).multiply(asset.priceFactor, MATH_CONTEXT),
                    hasEstimatedValues = false,
                )
            }

            val fallbackPrice = assetDailyPriceRepository.findLatestBefore(asset.id!!, date)
            if (fallbackPrice != null) {
                return DailyGroupValuation(
                    totalValue = currentState.quantity.multiply(fallbackPrice.closePrice, MATH_CONTEXT).multiply(asset.priceFactor, MATH_CONTEXT),
                    hasEstimatedValues = true,
                )
            }

            if (asset.assetType in FIXED_INCOME_ASSET_TYPES) {
                val result =
                    fixedIncomeValuationService.estimate(
                        holding = currentState,
                        indicatorRates = rateMap,
                        valuationReferenceDate = date,
                        monthlySeries = monthlySeries,
                        asOfDate = date,
                        allowRateProjectionPastSeries = true,
                    )
                result.grossValue?.let {
                    return DailyGroupValuation(totalValue = it, hasEstimatedValues = result.valuationSource == ValuationSource.ESTIMATED)
                }
            }

            val manualGrossValue = currentState.manualGrossValue
            val manualValuedAt = currentState.manualValuedAt
            if (
                currentState.pricingMode == HoldingPricingMode.MANUAL &&
                manualGrossValue != null &&
                (manualValuedAt == null || !manualValuedAt.isAfter(date))
            ) {
                return DailyGroupValuation(totalValue = manualGrossValue, hasEstimatedValues = true)
            }

            val providerDate = currentState.lastValuedAt?.atZone(ZoneOffset.UTC)?.toLocalDate()
            val providerGrossValue = currentState.grossValue
            if (providerGrossValue != null && providerDate != null && !providerDate.isAfter(date)) {
                return DailyGroupValuation(totalValue = providerGrossValue, hasEstimatedValues = true)
            }

            return DailyGroupValuation(
                totalValue = currentState.quantity.multiply(currentState.averageCost, MATH_CONTEXT),
                hasEstimatedValues = true,
            )
        }

        private fun applyTransaction(
            state: Holding,
            transaction: InvestmentTransaction,
        ): Holding {
            val qty = transaction.quantity
            val price = transaction.unitPrice
            return when (transaction.type) {
                InvestmentTransactionType.PURCHASE,
                InvestmentTransactionType.SUBSCRIPTION,
                -> {
                    if (qty == null || price == null) return state
                    val newQty = state.quantity + qty
                    val newCost =
                        if (state.quantity.signum() == 0) {
                            price
                        } else {
                            (state.quantity * state.averageCost + qty * price).divide(newQty, 8, RoundingMode.HALF_UP)
                        }
                    state.copy(quantity = newQty, averageCost = newCost)
                }
                InvestmentTransactionType.SALE,
                InvestmentTransactionType.MATURITY,
                InvestmentTransactionType.CUSTODY_TRANSFER,
                InvestmentTransactionType.OWNERSHIP_TRANSFER,
                InvestmentTransactionType.QUOTA_TRANSFER,
                -> {
                    val reduction =
                        if (
                            transaction.type in
                            setOf(
                                InvestmentTransactionType.CUSTODY_TRANSFER,
                                InvestmentTransactionType.OWNERSHIP_TRANSFER,
                                InvestmentTransactionType.QUOTA_TRANSFER,
                            ) && transaction.direction == InvestmentTransactionDirection.INFLOW
                        ) {
                            BigDecimal.ZERO
                        } else {
                            qty ?: state.quantity
                        }
                    if (
                        transaction.type in
                        setOf(
                            InvestmentTransactionType.CUSTODY_TRANSFER,
                            InvestmentTransactionType.OWNERSHIP_TRANSFER,
                            InvestmentTransactionType.QUOTA_TRANSFER,
                        ) && transaction.direction == InvestmentTransactionDirection.INFLOW && qty != null && price != null
                    ) {
                        val newQty = state.quantity + qty
                        val newCost =
                            if (state.quantity.signum() == 0) {
                                price
                            } else {
                                (state.quantity * state.averageCost + qty * price).divide(newQty, 8, RoundingMode.HALF_UP)
                            }
                        state.copy(quantity = newQty, averageCost = newCost)
                    } else {
                        state.copy(quantity = (state.quantity - reduction).max(BigDecimal.ZERO))
                    }
                }
                InvestmentTransactionType.STOCK_SPLIT,
                InvestmentTransactionType.BONUS_SHARES,
                -> {
                    if (qty == null || qty <= BigDecimal.ZERO) return state
                    val newQty = state.quantity + qty
                    val newCost =
                        if (newQty.signum() == 0) BigDecimal.ZERO
                        else (state.quantity * state.averageCost).divide(newQty, 8, RoundingMode.HALF_UP)
                    state.copy(quantity = newQty, averageCost = newCost)
                }
                InvestmentTransactionType.REVERSE_SPLIT -> {
                    if (qty == null || qty <= BigDecimal.ZERO) return state
                    val newQty = (state.quantity - qty).max(BigDecimal.ZERO)
                    val newCost =
                        if (newQty.signum() == 0) BigDecimal.ZERO
                        else (state.quantity * state.averageCost).divide(newQty, 8, RoundingMode.HALF_UP)
                    state.copy(quantity = newQty, averageCost = newCost)
                }
                else -> state
            }
        }
    }

    private companion object {
        val FIXED_INCOME_ASSET_TYPES: Set<AssetType> =
            setOf(
                AssetType.CDB,
                AssetType.RDB,
                AssetType.LCI,
                AssetType.LCA,
                AssetType.LC,
                AssetType.LF,
                AssetType.LIG,
                AssetType.DEBENTURES,
                AssetType.CRI,
                AssetType.CRA,
                AssetType.TREASURY,
            )
        val MATH_CONTEXT: MathContext = MathContext(12, RoundingMode.HALF_UP)
    }
}
