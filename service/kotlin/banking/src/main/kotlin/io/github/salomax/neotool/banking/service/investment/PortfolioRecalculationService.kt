package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.IndicatorRateProvider
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.model.dto.investment.PortfolioRecalculationStatusPayload
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.portfolio.AssetDailyPriceRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioFlowEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import io.github.salomax.neotool.banking.service.investment.event.PortfolioRecalculationEventBus
import io.github.salomax.neotool.banking.service.investment.recalculation.HistoricalHoldingTimeline
import io.github.salomax.neotool.banking.service.investment.recalculation.mergeDimensionValuations
import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.common.error.ErrorCode
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.net.InetAddress
import java.time.Instant
import java.time.LocalDate
import java.util.TreeMap
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

    /**
     * Immutable inputs for the day-by-day simulation (everything except quota cursor state, which mutates per day).
     * Date bounds live in [process] (`startDate`…`today`); not duplicated here.
     */
    private data class RecalculationSimulationInput(
        val flowEventsByDate: Map<LocalDate, List<InvestmentPortfolioFlowEvent>>,
        val rateMap: Map<Indexer, BigDecimal>,
        val monthlySeries: Map<Indexer, List<Pair<LocalDate, BigDecimal>>>,
        val timelines: List<HistoricalHoldingTimeline>,
        val pricesByAsset: Map<UUID, TreeMap<LocalDate, InvestmentAssetDailyPrice>>,
    )

    /**
     * Rebuilds TWR quota history for [jobId] from [PortfolioRecalculationJob.recalculateFrom] through today.
     *
     * The job stores the semantic [PortfolioRecalculationJob.recalculateFrom]; processing uses **startDate** =
     * that date minus one day so the first rewritten row's "previous day" anchor is recomputed in the same universe
     * (e.g. after a holding delete removes flows from D onward, D−1 must be rewritten too).
     *
     * Outline: load holdings, trades, and flow events → anchor quotas strictly before [startDate] → delete stored
     * rows from [startDate] → walk each calendar day: simulated positions from trades, mark-to-market per holding →
     * persist portfolio + per-asset-group quotas → mark job COMPLETED (or FAILED).
     */
    fun process(jobId: UUID) {
        // Atomic claim: only the first pod to win the UPDATE proceeds; others see null and exit.
        val claimed = portfolioRecalculationJobRepository.tryClaimJob(jobId, NODE_ID) ?: return

        val financialAccountId = claimed.financialAccountId
        if (financialAccountId == null) {
            failJob(claimed, BankingErrorCode.PORTFOLIO_RECALCULATION_CONSOLIDATED_NOT_SUPPORTED)
            return
        }

        // tryClaimJob already persisted PROCESSING — emit SSE without a redundant DB update.
        portfolioRecalculationEventBus.emit(PortfolioRecalculationStatusPayload.from(claimed))

        // Entire execution is inside try so any failure — including early setup — is captured and persisted.
        var daysProcessed = 0
        try {
            val today = clock()
            val startDate = claimed.recalculateFrom.minusDays(1)
            val anchorDate = startDate.minusDays(1)

            logger.info {
                "Portfolio recalculation started: jobId=$jobId financialAccountId=$financialAccountId " +
                    "node=$NODE_ID recalculateFrom=${claimed.recalculateFrom} startDate=$startDate today=$today"
            }

            val anchorQuotas = portfolioQuotaRepository.findLatestBeforeAllDimensions(financialAccountId, anchorDate)
            var previousPortfolioQuota = anchorQuotas[PortfolioDimensionType.PORTFOLIO]?.get("")
            val previousGroupQuotas =
                anchorQuotas[PortfolioDimensionType.ASSET_GROUP]?.toMutableMap()
                    ?: mutableMapOf()
            val previousGridClassQuotas =
                anchorQuotas[PortfolioDimensionType.GRID_CLASS]?.toMutableMap()
                    ?: mutableMapOf()

            portfolioQuotaRepository.deleteFromDate(financialAccountId, startDate)

            val simulationInput =
                loadRecalculationSimulationInput(
                    financialAccountId = financialAccountId,
                    accountId = claimed.accountId,
                    startDate = startDate,
                    today = today,
                )

            logger.info {
                "Portfolio recalculation context loaded: jobId=$jobId " +
                    "holdings=${simulationInput.timelines.size} " +
                    "days=${startDate.until(today, java.time.temporal.ChronoUnit.DAYS)}"
            }

            var currentJob = claimed
            var date = startDate
            while (!date.isAfter(today)) {
                val dailyDimensionValuation =
                    mergeDimensionValuations(
                        timelines = simulationInput.timelines,
                        date = date,
                        rateMap = simulationInput.rateMap,
                        monthlySeries = simulationInput.monthlySeries,
                        pricesByAsset = simulationInput.pricesByAsset,
                    )

                val dayQuotas =
                    portfolioQuotaCalculationService.calculateDayQuota(
                        financialAccountId = financialAccountId,
                        date = date,
                        previousPortfolioQuota = previousPortfolioQuota,
                        previousGroupQuotas = previousGroupQuotas,
                        holdingsValueByGroup = dailyDimensionValuation.holdingsValueByGroup,
                        flowEvents = simulationInput.flowEventsByDate[date].orEmpty(),
                    )
                val gridClassDayQuotas =
                    portfolioQuotaCalculationService.calculateGridClassDayQuotas(
                        financialAccountId = financialAccountId,
                        date = date,
                        previousGridClassQuotas = previousGridClassQuotas,
                        holdingsValueByGridClass = dailyDimensionValuation.holdingsValueByGridClass,
                    )

                portfolioQuotaRepository.batchUpsert(dayQuotas + gridClassDayQuotas).forEach { saved ->
                    if (saved.dimensionType == PortfolioDimensionType.PORTFOLIO) {
                        previousPortfolioQuota = saved
                    } else if (saved.dimensionType == PortfolioDimensionType.ASSET_GROUP) {
                        previousGroupQuotas[saved.dimensionKey.orEmpty()] = saved
                    } else if (saved.dimensionType == PortfolioDimensionType.GRID_CLASS) {
                        previousGridClassQuotas[saved.dimensionKey.orEmpty()] = saved
                    }
                }

                daysProcessed += 1
                if (daysProcessed % HEARTBEAT_INTERVAL_DAYS == 0) {
                    currentJob =
                        updateJobAndEmit(
                            currentJob.copy(
                                daysProcessed = daysProcessed,
                                lastHeartbeatAt = Instant.now(),
                                updatedAt = Instant.now(),
                            ),
                        )
                }

                date = date.plusDays(1)
            }

            updateJobAndEmit(
                currentJob.copy(
                    status = PortfolioRecalculationStatus.COMPLETED,
                    daysProcessed = daysProcessed,
                    errorCode = null,
                    errorDetails = null,
                    completedAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )

            logger.info { "Portfolio recalculation completed: jobId=$jobId daysProcessed=$daysProcessed" }
        } catch (e: DomainException) {
            logger.error(e) {
                "Portfolio recalculation failed: jobId=$jobId financialAccountId=$financialAccountId " +
                    "node=$NODE_ID code=${e.errorCode.code} daysProcessed=$daysProcessed"
            }
            failJob(claimed, e.errorCode, e.cause)
        } catch (e: Exception) {
            logger.error(e) {
                "Portfolio recalculation failed: jobId=$jobId financialAccountId=$financialAccountId " +
                    "node=$NODE_ID daysProcessed=$daysProcessed cause=${e::class.simpleName}: ${e.message}"
            }
            failJob(claimed, BankingErrorCode.PORTFOLIO_RECALCULATION_FAILED, e)
        }
    }

    private fun loadRecalculationSimulationInput(
        financialAccountId: UUID,
        accountId: UUID,
        startDate: LocalDate,
        today: LocalDate,
    ): RecalculationSimulationInput {
        val holdings = holdingRepository.findByFinancialAccount(financialAccountId, accountId)
        val assetsById = assetRepository.findByIds(holdings.map { it.assetId }.toSet()).associateBy { it.id!! }
        val transactionsByHolding =
            investmentTransactionRepository.findByHoldingIds(holdings.mapNotNull { it.id }, accountId)
                .groupBy { it.holdingId }
                .mapValues { (_, value) ->
                    value.sortedWith(compareBy({ it.tradeDate }, { it.createdAt }, { it.id.toString() }))
                }
        val flowEventsByDate =
            portfolioFlowEventRepository.findByAccountAndDateRange(financialAccountId, startDate, today)
                .asSequence()
                .filterNot { it.sourceReferenceType == PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION }
                .groupBy { it.eventDate }
        val rateMap = safeGetIndicatorRates()
        val monthlySeries = safeGetMonthlySeries(holdings, today)

        val timelines =
            holdings.mapNotNull { holding ->
                val asset = assetsById[holding.assetId] ?: return@mapNotNull null
                HistoricalHoldingTimeline(
                    baseHolding = holding,
                    asset = asset,
                    transactions = transactionsByHolding[holding.id].orEmpty(),
                    fixedIncomeValuationService = fixedIncomeValuationService,
                )
            }

        val assetIds = timelines.map { it.asset.id!! }.toSet()
        val pricesByAsset: Map<UUID, TreeMap<LocalDate, InvestmentAssetDailyPrice>> =
            buildMap {
                assetIds.forEach { put(it, TreeMap()) }
                assetDailyPriceRepository.findLatestBeforeForAssets(assetIds, startDate)
                    .forEach { price -> getValue(price.assetId)[price.priceDate] = price }
                assetDailyPriceRepository.findByAssetsAndDateRange(assetIds, startDate, today)
                    .forEach { price -> getValue(price.assetId)[price.priceDate] = price }
            }

        return RecalculationSimulationInput(
            flowEventsByDate = flowEventsByDate,
            rateMap = rateMap,
            monthlySeries = monthlySeries,
            timelines = timelines,
            pricesByAsset = pricesByAsset,
        )
    }

    private fun updateJobAndEmit(job: PortfolioRecalculationJob): PortfolioRecalculationJob {
        val saved = portfolioRecalculationJobRepository.update(job)
        portfolioRecalculationEventBus.emit(PortfolioRecalculationStatusPayload.from(saved))
        return saved
    }

    private fun failJob(
        job: PortfolioRecalculationJob,
        errorCode: ErrorCode,
        cause: Throwable? = null,
    ) {
        updateJobAndEmit(
            job.copy(
                status = PortfolioRecalculationStatus.FAILED,
                errorCode = errorCode.code,
                errorDetails = buildTechnicalDetail(cause),
                completedAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun buildTechnicalDetail(cause: Throwable?, maxLen: Int = 2000): String? {
        if (cause == null) return null
        val root = generateSequence(cause) { it.cause }.lastOrNull() ?: return null
        val raw = "${root.javaClass.simpleName}: ${root.message ?: ""}".trim()
        return raw.takeIf { it.isNotEmpty() }?.take(maxLen)
    }

    private fun safeGetIndicatorRates(): Map<Indexer, BigDecimal> =
        try {
            indicatorRateProvider.getLatestRates()
        } catch (e: Exception) {
            logger.warn(e) {
                "Failed to fetch indicator rates — fixed income valuations will fall back to averageCost where applicable"
            }
            emptyMap()
        }

    private fun safeGetMonthlySeries(
        holdings: List<Holding>,
        today: LocalDate,
    ): Map<Indexer, List<Pair<LocalDate, BigDecimal>>> {
        val byIndexer =
            holdings.filter { it.indexer != null && it.issueDate != null }
                .groupBy { it.indexer!! }

        return byIndexer.mapValues { (indexer, indexedHoldings) ->
            val from = indexedHoldings.mapNotNull { it.issueDate }.minOrNull() ?: return@mapValues emptyList()
            try {
                indicatorRateProvider.getMonthlySeriesRange(indexer, from, today)
            } catch (e: Exception) {
                logger.warn(e) {
                    "Failed to fetch monthly series for indexer=$indexer — that indexer will use averageCost fallback in recalculation"
                }
                emptyList()
            }
        }.filterValues { it.isNotEmpty() }
    }

    private companion object {
        const val HEARTBEAT_INTERVAL_DAYS: Int = 30
        private const val PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION = "BANKING_TRANSACTION"

        /** Stable identity for this JVM instance — in k8s this resolves to the pod name. */
        val NODE_ID: String = runCatching { InetAddress.getLocalHost().hostName }
            .getOrDefault(UUID.randomUUID().toString())
    }
}
