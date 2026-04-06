package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkCode
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.graphql.investment.BenchmarkPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.PerformancePeriodDTO
import io.github.salomax.neotool.banking.graphql.investment.PortfolioPerformanceDTO
import io.github.salomax.neotool.banking.error.StoredRecalculationFailure
import io.github.salomax.neotool.banking.graphql.investment.RecalculationStatusDTO
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
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
    private val investmentTransactionRepository: InvestmentTransactionRepository,
) {
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
            twr = computeTwr(window.start, window.end),
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
        val window =
            resolveConsolidatedPortfolioWindow(
                accountId = accountId,
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
            twr = computeAggregatedPortfolioTwr(window.series, window.flowAmountsByDate),
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
    ): Double? =
        if (financialAccountId != null) {
            val window =
                resolvePerformanceWindow(
                    financialAccountId = financialAccountId,
                    dimensionType = PortfolioDimensionType.ASSET_GROUP,
                    dimensionKey = groupKey,
                    period = period,
                ) ?: return null

            computeGroupTwr(
                financialAccountId = financialAccountId,
                accountId = accountId,
                groupKey = groupKey,
                series = window.series,
            )
        } else {
            val window =
                resolveConsolidatedValueWindow(
                accountId = accountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = groupKey,
                period = period,
            ) ?: return null

            computeConsolidatedGroupTwr(
                accountId = accountId,
                groupKey = groupKey,
                series = window.series,
            )
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

    private fun computeTwr(
        start: InvestmentPortfolioQuota,
        end: InvestmentPortfolioQuota,
    ): Double? {
        if (start.quotaValue.compareTo(BigDecimal.ZERO) == 0) return null
        return end.quotaValue
            .divide(start.quotaValue, 8, RoundingMode.HALF_UP)
            .subtract(BigDecimal.ONE)
            .toDouble()
    }

    private fun computeAggregatedPortfolioTwr(
        series: List<AggregatedValuePoint>,
        flowAmountsByDate: Map<LocalDate, BigDecimal>,
    ): Double? {
        if (series.size < 2) return null

        var previousValue = series.first().totalValue
        if (previousValue.compareTo(BigDecimal.ZERO) <= 0) return null

        var factor = BigDecimal.ONE
        series.drop(1).forEach { point ->
            val netFlow = flowAmountsByDate[point.date] ?: BigDecimal.ZERO
            if (previousValue.compareTo(BigDecimal.ZERO) <= 0) {
                previousValue = point.totalValue
                if (previousValue.compareTo(BigDecimal.ZERO) <= 0) return null
                return@forEach
            }
            val dailyReturn =
                point.totalValue
                    .subtract(previousValue)
                    .subtract(netFlow)
                    .divide(previousValue, 8, RoundingMode.HALF_UP)
            factor = factor.multiply(BigDecimal.ONE.add(dailyReturn), MathContext.DECIMAL64)
            previousValue = point.totalValue
        }

        if (previousValue.compareTo(BigDecimal.ZERO) <= 0) return null
        return factor.subtract(BigDecimal.ONE).setScale(8, RoundingMode.HALF_UP).toDouble()
    }

    private fun computeAggregatedValueTwr(series: List<AggregatedValuePoint>): Double? {
        if (series.size < 2) return null
        val startValue = series.first().totalValue
        val endValue = series.last().totalValue
        if (startValue.compareTo(BigDecimal.ZERO) <= 0) return null
        return endValue.divide(startValue, 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).toDouble()
    }

    private fun computeGroupTwr(
        financialAccountId: UUID,
        accountId: UUID,
        groupKey: String,
        series: List<InvestmentPortfolioQuota>,
    ): Double? {
        val aggregatedSeries =
            series.map {
                AggregatedValuePoint(
                    date = it.quotaDate,
                    totalValue = it.totalPortfolioValue,
                    hasEstimatedValues = it.hasEstimatedValues,
                )
            }
        val flowAmountsByDate =
            buildScopedGroupFlowAmounts(
                financialAccountId = financialAccountId,
                accountId = accountId,
                groupKey = groupKey,
                from = series.first().quotaDate.plusDays(1),
                to = series.last().quotaDate,
            )
        return computeAggregatedPortfolioTwr(aggregatedSeries, flowAmountsByDate)
    }

    private fun computeConsolidatedGroupTwr(
        accountId: UUID,
        groupKey: String,
        series: List<AggregatedValuePoint>,
    ): Double? {
        val flowAmountsByDate =
            investmentAccountIds(accountId)
                .flatMap { financialAccountId ->
                    buildScopedGroupFlowAmounts(
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        groupKey = groupKey,
                        from = series.first().date.plusDays(1),
                        to = series.last().date,
                    ).entries
                }.groupBy({ it.key }, { it.value })
                .mapValues { (_, values) -> values.fold(BigDecimal.ZERO, BigDecimal::add) }

        return computeAggregatedPortfolioTwr(series, flowAmountsByDate)
    }

    private fun buildScopedGroupFlowAmounts(
        financialAccountId: UUID,
        accountId: UUID,
        groupKey: String,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, BigDecimal> {
        if (from.isAfter(to)) return emptyMap()

        val groupHoldingIds = groupHoldingIds(financialAccountId, accountId, groupKey)
        if (groupHoldingIds.isEmpty()) return emptyMap()

        val transactionFlows =
            investmentTransactionRepository.findByHoldingIds(groupHoldingIds, accountId)
                .asSequence()
                .filter { !it.tradeDate.isBefore(from) && !it.tradeDate.isAfter(to) }
                .mapNotNull { transaction ->
                    transaction.toGroupNetFlowAmount()?.let { amount ->
                        transaction.tradeDate to amount
                    }
                }

        val eventFlows =
            portfolioFlowEventRepository.findByAccountAndDateRange(financialAccountId, from, to)
                .asSequence()
                .filter { event -> event.relatedHoldingId != null && groupHoldingIds.contains(event.relatedHoldingId) }
                .mapNotNull { event ->
                    event.toGroupNetFlowAmount()?.let { amount ->
                        event.eventDate to amount
                    }
                }

        return (transactionFlows + eventFlows)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO, BigDecimal::add) }
    }

    private fun groupHoldingIds(
        financialAccountId: UUID,
        accountId: UUID,
        groupKey: String,
    ): Set<UUID> {
        val holdings = holdingRepository.findByFinancialAccount(financialAccountId, accountId)
        if (holdings.isEmpty()) return emptySet()
        val assetsById = assetRepository.findByIds(holdings.map { it.assetId }.toSet()).associateBy { it.id }
        return holdings.asSequence()
            .filter { holding ->
                val assetType = assetsById[holding.assetId]?.assetType
                AssetGroupMapping.groupKeyFor(assetType) == groupKey
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

    private fun resolveConsolidatedPortfolioWindow(
        accountId: UUID,
        period: PerformancePeriodDTO,
    ): ConsolidatedPortfolioWindow? {
        val seriesWindow =
            resolveConsolidatedValueWindow(
                accountId = accountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                period = period,
            ) ?: return null

        val flowAmountsByDate =
            investmentAccountIds(accountId)
                .flatMap { financialAccountId ->
                    portfolioFlowEventRepository.findByAccountAndDateRange(
                        financialAccountId = financialAccountId,
                        from = seriesWindow.startDate.plusDays(1),
                        to = seriesWindow.endDate,
                    )
                }.groupBy { it.eventDate }
                .mapValues { (_, events) ->
                    events.fold(BigDecimal.ZERO) { acc, event ->
                        acc + signedFlowAmount(event.eventType, event.grossAmount)
                    }
                }

        return ConsolidatedPortfolioWindow(
            startDate = seriesWindow.startDate,
            endDate = seriesWindow.endDate,
            series = seriesWindow.series,
            flowAmountsByDate = flowAmountsByDate,
        )
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

    private fun signedFlowAmount(
        eventType: PortfolioFlowEventType,
        grossAmount: BigDecimal,
    ): BigDecimal =
        when (eventType) {
            PortfolioFlowEventType.DEPOSIT,
            PortfolioFlowEventType.DIVIDEND,
            PortfolioFlowEventType.CORPORATE_ACTION,
            -> grossAmount
            PortfolioFlowEventType.WITHDRAWAL,
            PortfolioFlowEventType.INCOME_TAX,
            -> grossAmount.negate()
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

    private data class ConsolidatedPortfolioWindow(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val series: List<AggregatedValuePoint>,
        val flowAmountsByDate: Map<LocalDate, BigDecimal>,
    )

    private fun InvestmentTransaction.toGroupNetFlowAmount(): BigDecimal? {
        if (type !in GROUP_TRANSACTION_FLOW_TYPES) return null
        val amount = netValue ?: grossValue
        return when (direction) {
            InvestmentTransactionDirection.INFLOW -> amount
            InvestmentTransactionDirection.OUTFLOW -> amount.negate()
        }
    }

    private fun io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent.toGroupNetFlowAmount(): BigDecimal? =
        when (eventType) {
            PortfolioFlowEventType.DEPOSIT -> grossAmount
            PortfolioFlowEventType.WITHDRAWAL,
            PortfolioFlowEventType.DIVIDEND,
            PortfolioFlowEventType.INCOME_TAX,
            PortfolioFlowEventType.CORPORATE_ACTION,
            -> grossAmount.negate()
        }

    private companion object {
        val GROUP_TRANSACTION_FLOW_TYPES =
            setOf(
                InvestmentTransactionType.PURCHASE,
                InvestmentTransactionType.SALE,
                InvestmentTransactionType.CANCELLATION,
                InvestmentTransactionType.MATURITY,
                InvestmentTransactionType.SUBSCRIPTION,
                InvestmentTransactionType.CUSTODY_TRANSFER,
                InvestmentTransactionType.OWNERSHIP_TRANSFER,
                InvestmentTransactionType.QUOTA_TRANSFER,
            )
    }
}
