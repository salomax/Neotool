package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceCapabilitiesDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformancePointDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceResolution
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceSummaryDTO
import io.github.salomax.neotool.banking.graphql.investment.PortfolioPerformanceSeriesDTO
import io.github.salomax.neotool.banking.model.dto.investment.AssetGroupMapping
import io.github.salomax.neotool.banking.model.dto.investment.PortfolioPerformanceSubgroupMapping
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

@Singleton
class PortfolioPerformanceSeriesService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val investmentQueryService: InvestmentQueryService,
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    fun getPortfolioPerformance(
        accountId: UUID,
        groupKey: String?,
        subgroupKey: String?,
        assetTypes: Set<AssetType>?,
        resolution: HoldingPerformanceResolution?,
        includeProjection: Boolean,
        financialAccountId: UUID? = null,
    ): PortfolioPerformanceSeriesDTO {
        val resolvedResolution = resolution.takeIf { it == HoldingPerformanceResolution.MONTH }
            ?: HoldingPerformanceResolution.MONTH
        val useOpenScope = shouldUseOpenHoldingsScope(groupKey, subgroupKey, assetTypes)
        val holdings =
            when {
                financialAccountId != null && useOpenScope ->
                    holdingRepository.findOpenByFinancialAccount(financialAccountId, accountId)
                financialAccountId != null && !useOpenScope ->
                    holdingRepository.findByFinancialAccount(financialAccountId, accountId)
                useOpenScope -> holdingRepository.findOpenByAccount(accountId)
                else -> holdingRepository.findByAccount(accountId)
            }
        if (holdings.isEmpty()) {
            return emptySeries(groupKey, resolvedResolution)
        }

        val assetsById = assetRepository.findByIds(holdings.map { it.assetId }.toSet()).associateBy { it.id!! }
        val subgroupAssetTypes = subgroupKey?.let(PortfolioPerformanceSubgroupMapping::assetTypesFor)
        val effectiveAssetTypes = if (!assetTypes.isNullOrEmpty()) assetTypes else subgroupAssetTypes
        val selected = holdings.filter { holding ->
            val asset = assetsById[holding.assetId] ?: return@filter false
            (groupKey == null || AssetGroupMapping.groupKeyFor(asset.assetType) == groupKey) &&
                (effectiveAssetTypes.isNullOrEmpty() || effectiveAssetTypes.contains(asset.assetType))
        }
        if (selected.isEmpty()) return emptySeries(groupKey, resolvedResolution)

        val perHolding = mutableListOf<HoldingPerformanceDTO>()
        var excludedCount = 0
        for (holding in selected) {
            val asset = assetsById[holding.assetId]
            if (asset == null) {
                excludedCount += 1
                continue
            }
            val performance = investmentQueryService.computeHoldingPerformance(
                holding = holding,
                asset = asset,
                accountId = accountId,
                resolution = resolvedResolution,
                fromDate = null,
                toDate = null,
                includeProjection = includeProjection,
            )
            if (performance == null || performance.points.isEmpty()) {
                excludedCount += 1
                continue
            }
            perHolding += performance
        }

        if (perHolding.isEmpty()) {
            return emptySeries(
                groupKey = groupKey,
                resolution = resolvedResolution,
                excludedHoldingsCount = excludedCount,
            )
        }

        val currencyCodes = perHolding.map { it.currencyCode }.toSet()
        if (currencyCodes.size > 1) {
            throw IllegalStateException("Cannot aggregate performance across multiple currencies: ${currencyCodes.sorted()}")
        }
        val pointsByMonth = perHolding
            .flatMap { dto -> dto.points }
            .groupBy { YearMonth.from(it.timestamp.atOffset(ZoneOffset.UTC)) }

        val aggregatedPoints = pointsByMonth
            .toSortedMap()
            .map { (yearMonth, points) -> aggregateMonth(yearMonth, points) }
        val trimmedAggregatedPoints = trimLeadingInactiveMonths(aggregatedPoints)

        val latestHistorical = trimmedAggregatedPoints.lastOrNull { !it.isProjected } ?: trimmedAggregatedPoints.last()
        val grossGainPercent = percent(latestHistorical.grossGain, latestHistorical.currentCostBasis)

        return PortfolioPerformanceSeriesDTO(
            groupKey = groupKey,
            label = groupKey?.let(AssetGroupMapping::labelFor) ?: "Portfolio",
            currencyCode = currencyCodes.first(),
            resolution = resolvedResolution,
            valuationSource = latestHistorical.valuationSource,
            valuationReferenceDate = latestHistorical.valuationReferenceDate,
            capabilities = HoldingPerformanceCapabilitiesDTO(
                supportsProjection = perHolding.any { it.capabilities.supportsProjection },
                projectionDefaultEnabled = false,
                supportedResolutions = listOf(HoldingPerformanceResolution.MONTH),
                defaultResolution = HoldingPerformanceResolution.MONTH,
            ),
            summary = HoldingPerformanceSummaryDTO(
                investedPrincipal = latestHistorical.investedPrincipal,
                netContributions = latestHistorical.netContributions,
                currentCostBasis = latestHistorical.currentCostBasis,
                grossValue = latestHistorical.grossValue,
                netValue = latestHistorical.netValue,
                incomeTax = latestHistorical.incomeTax,
                iof = latestHistorical.iof,
                grossGain = latestHistorical.grossGain,
                netGain = latestHistorical.netGain,
                grossGainPercent = grossGainPercent,
            ),
            points = trimmedAggregatedPoints.map { it.point },
            excludedHoldingsCount = excludedCount,
            includedHoldingsCount = perHolding.size,
        )
    }

    private fun emptySeries(
        groupKey: String?,
        resolution: HoldingPerformanceResolution,
        excludedHoldingsCount: Int = 0,
    ) = PortfolioPerformanceSeriesDTO(
        groupKey = groupKey,
        label = groupKey?.let(AssetGroupMapping::labelFor) ?: "Portfolio",
        currencyCode = "BRL",
        resolution = resolution,
        valuationSource = ValuationSource.UNAVAILABLE,
        valuationReferenceDate = null,
        capabilities = HoldingPerformanceCapabilitiesDTO(
            supportsProjection = false,
            projectionDefaultEnabled = false,
            supportedResolutions = listOf(HoldingPerformanceResolution.MONTH),
            defaultResolution = HoldingPerformanceResolution.MONTH,
        ),
        summary = HoldingPerformanceSummaryDTO(
            investedPrincipal = 0.0,
            netContributions = 0.0,
            currentCostBasis = 0.0,
            grossValue = 0.0,
            netValue = 0.0,
            incomeTax = 0.0,
            iof = 0.0,
            grossGain = 0.0,
            netGain = 0.0,
            grossGainPercent = 0.0,
        ),
        points = emptyList(),
        excludedHoldingsCount = excludedHoldingsCount,
        includedHoldingsCount = 0,
    )

    private data class AggregatedMonth(
        val point: HoldingPerformancePointDTO,
        val investedPrincipal: Double?,
        val netContributions: Double?,
        val currentCostBasis: Double?,
        val grossValue: Double?,
        val netValue: Double?,
        val incomeTax: Double?,
        val iof: Double?,
        val grossGain: Double?,
        val netGain: Double?,
        val valuationSource: ValuationSource,
        val valuationReferenceDate: LocalDate?,
        val isProjected: Boolean,
    )

    private fun aggregateMonth(
        yearMonth: YearMonth,
        points: List<HoldingPerformancePointDTO>,
    ): AggregatedMonth {
        val point = HoldingPerformancePointDTO(
            timestamp = yearMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC),
            isProjected = points.any { it.isProjected },
            investedPrincipal = sumNullable(points.map { it.investedPrincipal }),
            netContributions = sumNullable(points.map { it.netContributions }),
            currentCostBasis = sumNullable(points.map { it.currentCostBasis }),
            grossValue = sumNullable(points.map { it.grossValue }),
            netValue = sumNullable(points.map { it.netValue }),
            incomeTax = sumNullable(points.map { it.incomeTax }),
            iof = sumNullable(points.map { it.iof }),
            grossGain = sumNullable(points.map { it.grossGain }),
            netGain = sumNullable(points.map { it.netGain }),
            quantity = null,
        )
        val latestDate = points.maxByOrNull { it.timestamp }?.timestamp
        return AggregatedMonth(
            point = point,
            investedPrincipal = point.investedPrincipal,
            netContributions = point.netContributions,
            currentCostBasis = point.currentCostBasis,
            grossValue = point.grossValue,
            netValue = point.netValue,
            incomeTax = point.incomeTax,
            iof = point.iof,
            grossGain = point.grossGain,
            netGain = point.netGain,
            valuationSource = if (points.any { it.isProjected }) ValuationSource.ESTIMATED else ValuationSource.CALCULATED,
            valuationReferenceDate = latestDate?.atOffset(ZoneOffset.UTC)?.toLocalDate(),
            isProjected = point.isProjected,
        )
    }

    private fun sumNullable(values: List<Double?>): Double? {
        if (values.none { it != null }) return null
        return values.fold(BigDecimal.ZERO) { acc, value ->
            acc + (value?.let(BigDecimal::valueOf) ?: BigDecimal.ZERO)
        }.round(mathContext).toDouble()
    }

    private fun percent(numerator: Double?, denominator: Double?): Double? {
        if (numerator == null || denominator == null || denominator <= 0.0) return null
        return BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), mathContext)
            .toDouble()
    }

    private fun shouldUseOpenHoldingsScope(
        groupKey: String?,
        subgroupKey: String?,
        assetTypes: Set<AssetType>?,
    ): Boolean = groupKey != null || subgroupKey != null || !assetTypes.isNullOrEmpty()

    private fun trimLeadingInactiveMonths(points: List<AggregatedMonth>): List<AggregatedMonth> {
        if (points.isEmpty()) return points
        val firstActiveIndex = points.indexOfFirst { it.hasExposureOrFlow() }
        if (firstActiveIndex == -1) return points
        if (firstActiveIndex == 0) return points
        return points.drop(firstActiveIndex)
    }

    private fun AggregatedMonth.hasExposureOrFlow(): Boolean =
        (grossValue ?: 0.0) > 0.0 || (netContributions ?: 0.0) != 0.0
}
