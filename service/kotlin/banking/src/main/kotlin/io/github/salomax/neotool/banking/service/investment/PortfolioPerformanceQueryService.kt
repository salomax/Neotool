package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkCode
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.graphql.investment.BenchmarkPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.GridClassTwrPeriod
import io.github.salomax.neotool.banking.graphql.investment.GridClassTwrPointDTO
import io.github.salomax.neotool.banking.graphql.investment.PerformancePeriodDTO
import io.github.salomax.neotool.banking.graphql.investment.PortfolioPerformanceDTO
import io.github.salomax.neotool.banking.model.dto.investment.AssetGroupMapping
import io.github.salomax.neotool.banking.model.dto.investment.GridClassMapping
import io.github.salomax.neotool.banking.error.StoredRecalculationFailure
import io.github.salomax.neotool.banking.graphql.investment.RecalculationStatusDTO
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.portfolio.MarketBenchmarkDailyRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioFlowEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Singleton
class PortfolioPerformanceQueryService(
    private val portfolioQuotaRepository: PortfolioQuotaRepository,
    private val marketBenchmarkDailyRepository: MarketBenchmarkDailyRepository,
    private val portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val portfolioFlowEventRepository: PortfolioFlowEventRepository,
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
) {
    fun getGridClassTwrSeries(
        financialAccountId: UUID?,
        accountId: UUID,
        gridClassKey: String,
        period: GridClassTwrPeriod = GridClassTwrPeriod.ONE_YEAR,
    ): List<GridClassTwrPointDTO> {
        if (gridClassKey !in GridClassMapping.ALL_KEYS) return emptyList()
        // Cash is outside the TWR universe (holdings-only).
        if (gridClassKey == GridClassMapping.CASH) return emptyList()
        val today = LocalDate.now()
        val from = gridClassSeriesStartDate(period, today)
        val effectiveDimensionType = PortfolioDimensionType.GRID_CLASS

        return if (financialAccountId != null) {
            val series =
                portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = effectiveDimensionType,
                dimensionKey = gridClassKey,
                from = from,
                to = today,
            )
            if (series.size < 2) return emptyList()
            val aggregatedSeries = series.map { it.toAggregatedPoint() }
            val flowAmountsByDate =
                buildScopedDimensionFlowAmounts(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    dimensionType = effectiveDimensionType,
                    dimensionKey = gridClassKey,
                    from = aggregatedSeries.first().date.plusDays(1),
                    to = aggregatedSeries.last().date,
                )
            val twrIndex = buildTimeWeightedIndexSeries(aggregatedSeries, flowAmountsByDate) ?: return emptyList()
            twrIndex.map { point ->
                GridClassTwrPointDTO(
                    date = point.date,
                    quotaValue = point.indexValue.toDouble(),
                )
            }
        } else {
            buildConsolidatedGridClassSeries(accountId, gridClassKey, effectiveDimensionType, from, today)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun getPortfolioPerformance(
        financialAccountId: UUID?,
        accountId: UUID,
        period: PerformancePeriodDTO,
        hasEstimatedValuesFallback: Boolean,
    ): PortfolioPerformanceDTO =
        if (financialAccountId != null) {
            getScopedPortfolioPerformance(financialAccountId, period, hasEstimatedValuesFallback)
        } else {
            getConsolidatedPortfolioPerformance(accountId, period, hasEstimatedValuesFallback)
        }

    private fun getScopedPortfolioPerformance(
        financialAccountId: UUID,
        period: PerformancePeriodDTO,
        hasEstimatedValuesFallback: Boolean,
    ): PortfolioPerformanceDTO {
        val window =
            resolvePerformanceWindow(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                period = period,
            )

        if (window == null) {
            return PortfolioPerformanceDTO(
                twr = null,
                twrSince = null,
                twrAbsolute = null,
                period = period,
                benchmarks = emptyList(),
                hasEstimatedValues = hasEstimatedValuesFallback,
            )
        }

        return PortfolioPerformanceDTO(
            twr =
                computeTimeWeightedReturn(
                    series = window.series.map { it.toAggregatedPoint() },
                    flowAmountsByDate =
                        buildScopedDimensionFlowAmounts(
                            financialAccountId = financialAccountId,
                            accountId = null,
                            dimensionType = PortfolioDimensionType.PORTFOLIO,
                            dimensionKey = null,
                            from = window.start.quotaDate.plusDays(1),
                            to = window.end.quotaDate,
                        ),
                ),
            twrSince = window.start.quotaDate,
            twrAbsolute = null,
            period = period,
            benchmarks = buildBenchmarkPerformance(window),
            hasEstimatedValues = window.series.any { it.hasEstimatedValues } || hasEstimatedValuesFallback,
        )
    }

    private fun getConsolidatedPortfolioPerformance(
        accountId: UUID,
        period: PerformancePeriodDTO,
        hasEstimatedValuesFallback: Boolean,
    ): PortfolioPerformanceDTO {
        val window = resolveConsolidatedValueWindow(accountId, PortfolioDimensionType.PORTFOLIO, null, period)

        if (window == null) {
            return PortfolioPerformanceDTO(
                twr = null,
                twrSince = null,
                twrAbsolute = null,
                period = period,
                benchmarks = emptyList(),
                hasEstimatedValues = hasEstimatedValuesFallback,
            )
        }

        return PortfolioPerformanceDTO(
            twr =
                computeTimeWeightedReturn(
                    series = window.series,
                    flowAmountsByDate =
                        buildConsolidatedDimensionFlowAmounts(
                            accountId = accountId,
                            dimensionType = PortfolioDimensionType.PORTFOLIO,
                            dimensionKey = null,
                            from = window.startDate.plusDays(1),
                            to = window.endDate,
                        ),
                ),
            twrSince = window.startDate,
            twrAbsolute = null,
            period = period,
            benchmarks = buildBenchmarkPerformance(window.startDate, window.endDate),
            hasEstimatedValues = window.series.any { it.hasEstimatedValues } || hasEstimatedValuesFallback,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun getAssetGroupPerformance(
        financialAccountId: UUID?,
        accountId: UUID,
        groupKey: String,
        period: PerformancePeriodDTO,
    ): Double? {
        if (groupKey == AssetGroupMapping.CASH) return null
        return if (financialAccountId != null) {
            val window =
                resolvePerformanceWindow(
                    financialAccountId = financialAccountId,
                    dimensionType = PortfolioDimensionType.ASSET_GROUP,
                    dimensionKey = groupKey,
                    period = period,
                ) ?: return null

            computeTimeWeightedReturn(
                series = window.series.map { it.toAggregatedPoint() },
                flowAmountsByDate =
                    buildScopedDimensionFlowAmounts(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        dimensionType = PortfolioDimensionType.ASSET_GROUP,
                        dimensionKey = groupKey,
                        from = window.start.quotaDate.plusDays(1),
                        to = window.end.quotaDate,
                    ),
            )
        } else {
            val window =
                resolveConsolidatedValueWindow(
                accountId = accountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                    dimensionKey = groupKey,
                    period = period,
            ) ?: return null

            computeTimeWeightedReturn(
                series = window.series,
                flowAmountsByDate =
                    buildConsolidatedDimensionFlowAmounts(
                        accountId = accountId,
                        dimensionType = PortfolioDimensionType.ASSET_GROUP,
                        dimensionKey = groupKey,
                        from = window.startDate.plusDays(1),
                        to = window.endDate,
                    ),
            )
        }
    }

    fun getRecalculationStatus(accountId: UUID): RecalculationStatusDTO {
        val pendingJob = portfolioRecalculationJobRepository.findActiveByAccount(accountId)
        if (pendingJob != null) {
            return RecalculationStatusDTO(
                isCalculating = true,
                jobId = pendingJob.id,
                startedAt = pendingJob.startedAt ?: pendingJob.createdAt,
                errorCode = null,
                message =
                    when (pendingJob.status) {
                        PortfolioRecalculationStatus.PENDING -> "Historical performance recalculation is queued."
                        PortfolioRecalculationStatus.PROCESSING -> "Historical performance recalculation is in progress."
                        else -> null
                    },
            )
        }

        val latestJob = portfolioRecalculationJobRepository.findLatestByAccount(accountId)
        val failed = latestJob?.takeIf { it.status == PortfolioRecalculationStatus.FAILED }
        return RecalculationStatusDTO(
            isCalculating = false,
            jobId = latestJob?.id,
            startedAt = latestJob?.startedAt,
            errorCode = failed?.errorCode,
            message =
                failed?.let {
                    StoredRecalculationFailure.displayMessage(it.errorCode, it.errorDetails)
                },
            errorDetails = failed?.errorDetails,
        )
    }

    private fun resolvePerformanceWindow(
        financialAccountId: UUID?,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        period: PerformancePeriodDTO,
    ): PerformanceWindow? {
        val scopedFinancialAccountId = financialAccountId ?: return null
        val today = LocalDate.now()
        val end =
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = scopedFinancialAccountId,
                dimensionType = dimensionType,
                dimensionKey = dimensionKey,
                date = today,
            ) ?: return null

        val start =
            when (period) {
                PerformancePeriodDTO.ALL -> findFirstQuota(scopedFinancialAccountId, dimensionType, dimensionKey, end.quotaDate)
                PerformancePeriodDTO.YTD -> {
                    val boundary = end.quotaDate.withDayOfYear(1).minusDays(1)
                    findQuotaOnOrBefore(scopedFinancialAccountId, dimensionType, dimensionKey, boundary)
                        ?: findFirstQuotaInRange(scopedFinancialAccountId, dimensionType, dimensionKey, boundary.plusDays(1), end.quotaDate)
                }
                PerformancePeriodDTO.MTD -> {
                    val boundary = end.quotaDate.withDayOfMonth(1).minusDays(1)
                    findQuotaOnOrBefore(scopedFinancialAccountId, dimensionType, dimensionKey, boundary)
                        ?: findFirstQuotaInRange(scopedFinancialAccountId, dimensionType, dimensionKey, boundary.plusDays(1), end.quotaDate)
                }
                PerformancePeriodDTO.ONE_YEAR -> {
                    val boundary = end.quotaDate.minusYears(1)
                    findQuotaOnOrBefore(scopedFinancialAccountId, dimensionType, dimensionKey, boundary)
                        ?: findFirstQuotaInRange(scopedFinancialAccountId, dimensionType, dimensionKey, boundary.plusDays(1), end.quotaDate)
                }
                PerformancePeriodDTO.THREE_YEARS -> {
                    val boundary = end.quotaDate.minusYears(3)
                    findQuotaOnOrBefore(scopedFinancialAccountId, dimensionType, dimensionKey, boundary)
                        ?: findFirstQuotaInRange(scopedFinancialAccountId, dimensionType, dimensionKey, boundary.plusDays(1), end.quotaDate)
                }
            } ?: return null

        if (start.quotaDate == end.quotaDate) return null

        val series =
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = scopedFinancialAccountId,
                dimensionType = dimensionType,
                dimensionKey = dimensionKey,
                from = start.quotaDate,
                to = end.quotaDate,
            )

        if (series.size < 2) return null

        return PerformanceWindow(start = start, end = end, series = series)
    }

    private fun findFirstQuota(
        financialAccountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        endDate: LocalDate,
    ): InvestmentPortfolioQuota? =
        findFirstQuotaInRange(financialAccountId, dimensionType, dimensionKey, LocalDate.of(1970, 1, 1), endDate)

    private fun findFirstQuotaInRange(
        financialAccountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        from: LocalDate,
        to: LocalDate,
    ): InvestmentPortfolioQuota? =
        portfolioQuotaRepository.findByAccountAndDateRange(
            financialAccountId = financialAccountId,
            dimensionType = dimensionType,
            dimensionKey = dimensionKey,
            from = from,
            to = to,
        ).firstOrNull()

    private fun findQuotaOnOrBefore(
        financialAccountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        boundaryDate: LocalDate,
    ): InvestmentPortfolioQuota? =
        portfolioQuotaRepository.findLatestBefore(
            financialAccountId = financialAccountId,
            dimensionType = dimensionType,
            dimensionKey = dimensionKey,
            date = boundaryDate,
        )

    private fun computeTimeWeightedReturn(
        series: List<AggregatedValuePoint>,
        flowAmountsByDate: Map<LocalDate, BigDecimal>,
    ): Double? {
        val indexSeries = buildTimeWeightedIndexSeries(series, flowAmountsByDate) ?: return null
        return indexSeries.last().indexValue
            .subtract(BigDecimal.ONE)
            .setScale(8, RoundingMode.HALF_UP)
            .toDouble()
    }

    private fun buildTimeWeightedIndexSeries(
        series: List<AggregatedValuePoint>,
        flowAmountsByDate: Map<LocalDate, BigDecimal>,
    ): List<TimeWeightedIndexPoint>? {
        if (series.size < 2) return null
        var previousValue = series.first().totalValue
        if (previousValue.compareTo(BigDecimal.ZERO) <= 0) return null

        var factor = BigDecimal.ONE
        val points = mutableListOf(TimeWeightedIndexPoint(series.first().date, factor))
        series.drop(1).forEach { point ->
            if (previousValue.compareTo(BigDecimal.ZERO) <= 0) return null
            val netFlow = flowAmountsByDate[point.date] ?: BigDecimal.ZERO
            val dailyReturn =
                point.totalValue
                    .subtract(previousValue)
                    .subtract(netFlow)
                    .divide(previousValue, 8, RoundingMode.HALF_UP)
            factor = factor.multiply(BigDecimal.ONE.add(dailyReturn), MathContext.DECIMAL64)
            points += TimeWeightedIndexPoint(point.date, factor)
            previousValue = point.totalValue
        }
        return points
    }

    private fun buildScopedDimensionFlowAmounts(
        financialAccountId: UUID,
        accountId: UUID?,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, BigDecimal> {
        if (from.isAfter(to)) return emptyMap()
        val events =
            portfolioFlowEventRepository.findByAccountAndDateRange(financialAccountId, from, to)
            .asSequence()
            .filterNot { it.sourceReferenceType == PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION }
            .toList()
        if (events.isEmpty()) return emptyMap()

        val filteredHoldingIds = dimensionHoldingIds(financialAccountId, accountId, dimensionType, dimensionKey)
        if (filteredHoldingIds != null && filteredHoldingIds.isEmpty()) return emptyMap()

        return events
            .asSequence()
            .filter { event ->
                filteredHoldingIds == null || (event.relatedHoldingId != null && filteredHoldingIds.contains(event.relatedHoldingId))
            }
            .mapNotNull { event -> event.toCanonicalNetFlowAmount()?.let { amount -> event.eventDate to amount } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO, BigDecimal::add) }
    }

    private fun buildConsolidatedDimensionFlowAmounts(
        accountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, BigDecimal> {
        if (from.isAfter(to)) return emptyMap()
        return investmentAccountIds(accountId)
            .flatMap { financialAccountId ->
                buildScopedDimensionFlowAmounts(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    dimensionType = dimensionType,
                    dimensionKey = dimensionKey,
                    from = from,
                    to = to,
                ).entries
            }.groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.fold(BigDecimal.ZERO, BigDecimal::add) }
    }

    private fun dimensionHoldingIds(
        financialAccountId: UUID,
        accountId: UUID?,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
    ): Set<UUID>? {
        if (dimensionType == PortfolioDimensionType.PORTFOLIO) return null
        val scopedAccountId = accountId ?: return null
        if (dimensionKey.isNullOrBlank()) return emptySet()
        val holdings = holdingRepository.findByFinancialAccount(financialAccountId, scopedAccountId)
        if (holdings.isEmpty()) return emptySet()
        val assetsById = assetRepository.findByIds(holdings.map { it.assetId }.toSet()).associateBy { it.id }
        return holdings.asSequence()
            .filter { holding ->
                val assetType = assetsById[holding.assetId]?.assetType
                when (dimensionType) {
                    PortfolioDimensionType.ASSET_GROUP -> AssetGroupMapping.groupKeyFor(assetType) == dimensionKey
                    PortfolioDimensionType.GRID_CLASS -> GridClassMapping.gridClassFor(assetType) == dimensionKey
                    PortfolioDimensionType.PORTFOLIO -> true
                }
            }.mapNotNull { it.id }
            .toSet()
    }

    private fun buildBenchmarkPerformance(window: PerformanceWindow): List<BenchmarkPerformanceDTO> =
        buildBenchmarkPerformance(window.start.quotaDate, window.end.quotaDate)

    private fun buildBenchmarkPerformance(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<BenchmarkPerformanceDTO> =
        MarketBenchmarkCode.entries.map { code ->
            BenchmarkPerformanceDTO(
                code = code.name,
                label = benchmarkLabel(code),
                twr = calculateBenchmarkTwr(code, startDate.plusDays(1), endDate),
            )
        }

    private fun calculateBenchmarkTwr(
        code: MarketBenchmarkCode,
        from: LocalDate,
        to: LocalDate,
    ): Double? {
        if (from.isAfter(to)) return null
        val rows = marketBenchmarkDailyRepository.findByCodeAndDateRange(code, from, to)
        if (rows.isEmpty()) return null

        val factor =
            rows.fold(BigDecimal.ONE) { acc, row ->
                acc.multiply(row.dailyFactor)
            }

        return factor.subtract(BigDecimal.ONE).setScale(8, RoundingMode.HALF_UP).toDouble()
    }

    private fun benchmarkLabel(code: MarketBenchmarkCode): String =
        when (code) {
            MarketBenchmarkCode.CDI -> "CDI"
            MarketBenchmarkCode.IBOV -> "Ibovespa"
            MarketBenchmarkCode.IPCA -> "IPCA"
            MarketBenchmarkCode.IFIX -> "IFIX"
        }

    private fun resolveConsolidatedValueWindow(
        accountId: UUID,
        dimensionType: PortfolioDimensionType,
        dimensionKey: String?,
        period: PerformancePeriodDTO,
    ): ConsolidatedValueWindow? {
        val financialAccountIds = investmentAccountIds(accountId)
        if (financialAccountIds.isEmpty()) return null

        val today = LocalDate.now()
        val latestQuotasByAccount =
            financialAccountIds.mapNotNull { financialAccountId ->
                portfolioQuotaRepository.findLatestBefore(
                    financialAccountId = financialAccountId,
                    dimensionType = dimensionType,
                    dimensionKey = dimensionKey,
                    date = today,
                )
            }
        if (latestQuotasByAccount.isEmpty()) return null

        val endDate = latestQuotasByAccount.minOf { it.quotaDate }
        val boundaryDate = boundaryDateFor(period, endDate)
        val rangeStart = if (period == PerformancePeriodDTO.ALL) LocalDate.of(1970, 1, 1) else requireNotNull(boundaryDate)

        val aggregatedSeries =
            financialAccountIds
                .flatMap { financialAccountId ->
                    portfolioQuotaRepository.findByAccountAndDateRange(
                        financialAccountId = financialAccountId,
                        dimensionType = dimensionType,
                        dimensionKey = dimensionKey,
                        from = rangeStart,
                        to = endDate,
                    )
                }.groupBy { it.quotaDate }
                .map { (date, quotas) ->
                    AggregatedValuePoint(
                        date = date,
                        totalValue = quotas.fold(BigDecimal.ZERO) { acc, quota -> acc + quota.totalPortfolioValue },
                        hasEstimatedValues = quotas.any { it.hasEstimatedValues },
                    )
                }.sortedBy { it.date }

        if (aggregatedSeries.isEmpty()) return null

        val startDate =
            when (period) {
                PerformancePeriodDTO.ALL -> aggregatedSeries.first().date
                else -> {
                    val boundary = requireNotNull(boundaryDate)
                    aggregatedSeries.lastOrNull { !it.date.isAfter(boundary) }?.date
                        ?: aggregatedSeries.firstOrNull { it.date.isAfter(boundary) }?.date
                }
            } ?: return null

        val scopedSeries = aggregatedSeries.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
        if (scopedSeries.size < 2) return null

        return ConsolidatedValueWindow(
            startDate = startDate,
            endDate = endDate,
            series = scopedSeries,
        )
    }

    private fun boundaryDateFor(
        period: PerformancePeriodDTO,
        endDate: LocalDate,
    ): LocalDate? =
        when (period) {
            PerformancePeriodDTO.ALL -> null
            PerformancePeriodDTO.YTD -> endDate.withDayOfYear(1).minusDays(1)
            PerformancePeriodDTO.MTD -> endDate.withDayOfMonth(1).minusDays(1)
            PerformancePeriodDTO.ONE_YEAR -> endDate.minusYears(1)
            PerformancePeriodDTO.THREE_YEARS -> endDate.minusYears(3)
        }

    private fun investmentAccountIds(accountId: UUID): List<UUID> =
        financialAccountRepository.findByAccountId(accountId)
            .filter { it.type == FinancialAccountType.INVESTMENT }
            .mapNotNull { it.id }

    private fun buildConsolidatedGridClassSeries(
        accountId: UUID,
        gridClassKey: String,
        effectiveDimensionType: PortfolioDimensionType,
        from: LocalDate,
        to: LocalDate,
    ): List<GridClassTwrPointDTO> {
        val financialAccountIds = investmentAccountIds(accountId)
        if (financialAccountIds.isEmpty()) return emptyList()

        val summedByDate =
            portfolioQuotaRepository.findByAccountsAndDateRange(
                financialAccountIds = financialAccountIds,
                dimensionType = effectiveDimensionType,
                dimensionKey = gridClassKey,
                from = from,
                to = to,
            )
                .groupBy({ it.quotaDate }, { it.totalPortfolioValue })
                .mapValues { (_, values) -> values.fold(BigDecimal.ZERO, BigDecimal::add) }
                .toSortedMap()

        if (summedByDate.isEmpty()) return emptyList()
        val series =
            summedByDate.entries.map { (date, totalValue) ->
                AggregatedValuePoint(
                    date = date,
                    totalValue = totalValue,
                    hasEstimatedValues = false,
                )
            }
        if (series.size < 2) return emptyList()
        val flowAmountsByDate =
            buildConsolidatedDimensionFlowAmounts(
                accountId = accountId,
                dimensionType = effectiveDimensionType,
                dimensionKey = gridClassKey,
                from = series.first().date.plusDays(1),
                to = series.last().date,
            )
        val twrIndex = buildTimeWeightedIndexSeries(series, flowAmountsByDate) ?: return emptyList()
        return twrIndex.map { point ->
            GridClassTwrPointDTO(
                date = point.date,
                quotaValue = point.indexValue.toDouble(),
            )
        }
    }

    private data class PerformanceWindow(
        val start: InvestmentPortfolioQuota,
        val end: InvestmentPortfolioQuota,
        val series: List<InvestmentPortfolioQuota>,
    )

    private data class AggregatedValuePoint(
        val date: LocalDate,
        val totalValue: BigDecimal,
        val hasEstimatedValues: Boolean,
    )

    private data class ConsolidatedValueWindow(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val series: List<AggregatedValuePoint>,
    )

    private data class TimeWeightedIndexPoint(
        val date: LocalDate,
        val indexValue: BigDecimal,
    )

    private fun InvestmentPortfolioFlowEvent.toCanonicalNetFlowAmount(): BigDecimal? =
        when (eventType) {
            PortfolioFlowEventType.DEPOSIT -> grossAmount
            PortfolioFlowEventType.WITHDRAWAL,
            PortfolioFlowEventType.DIVIDEND,
            PortfolioFlowEventType.INCOME_TAX,
            -> grossAmount.negate()
            PortfolioFlowEventType.CORPORATE_ACTION -> null
        }

    private fun InvestmentPortfolioQuota.toAggregatedPoint(): AggregatedValuePoint =
        AggregatedValuePoint(
            date = quotaDate,
            totalValue = totalPortfolioValue,
            hasEstimatedValues = hasEstimatedValues,
        )

    private companion object {
        const val PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION = "BANKING_TRANSACTION"
    }

    private fun gridClassSeriesStartDate(period: GridClassTwrPeriod, today: LocalDate): LocalDate =
        when (period) {
            GridClassTwrPeriod.SIX_MONTHS -> today.minusMonths(6)
            GridClassTwrPeriod.ONE_YEAR -> today.minusYears(1)
            GridClassTwrPeriod.THREE_YEARS -> today.minusYears(3)
            GridClassTwrPeriod.ALL -> LocalDate.of(1970, 1, 1)
        }
}
