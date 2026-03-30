package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.*
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.service.investment.FixedIncomeValuationService
import io.github.salomax.neotool.banking.client.investment.financialdata.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.service.investment.FundValuationService
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.client.investment.financialdata.IndicatorRateProvider
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketData
import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketPrice
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.service.investment.ValuationSource
import io.github.salomax.neotool.banking.domain.issuer.Issuer
import io.github.salomax.neotool.banking.repo.issuer.IssuerRepository
import io.github.salomax.neotool.banking.graphql.account.BankAccountDataDTO
import io.github.salomax.neotool.banking.graphql.account.CardDataDTO
import io.github.salomax.neotool.banking.graphql.account.FinancialAccountDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceCapabilitiesDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformancePointDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceResolution
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceSummaryDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentAssetDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.issuer.IssuerDTO
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

/**
 * Query service that orchestrates all holding read operations:
 * batch-loading related entities, computing valuations, and mapping to DTOs.
 *
 * Resolvers must not call repositories directly — all holding queries go through here.
 */
@Singleton
class InvestmentQueryService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
    private val financialAccountRepository: FinancialAccountRepository,
    private val issuerRepository: IssuerRepository,
    private val fixedIncomeValuationService: FixedIncomeValuationService,
    private val fundValuationService: FundValuationService,
    private val fundMarketDataProvider: FundMarketDataProvider,
    private val listedAssetMarketDataProvider: ListedAssetMarketDataProvider,
    private val investmentTransactionRepository: InvestmentTransactionRepository,
    private val indicatorRateProvider: IndicatorRateProvider,
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    private val logger = KotlinLogging.logger {}
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    fun getHoldingsByFinancialAccount(financialAccountId: UUID, accountId: UUID): List<InvestmentHoldingDTO> {
        val holdings = holdingRepository.findByFinancialAccount(financialAccountId, accountId)
            .filter { it.quantity > BigDecimal.ZERO }
        return mapHoldingsToDTO(holdings, accountId)
    }

    fun getAllHoldings(accountId: UUID): List<InvestmentHoldingDTO> {
        val holdings = holdingRepository.findByAccount(accountId)
            .filter { it.quantity > BigDecimal.ZERO }
        return mapHoldingsToDTO(holdings, accountId)
    }

    fun getHolding(id: UUID, accountId: UUID): InvestmentHoldingDTO? {
        val holding = holdingRepository.findById(id, accountId) ?: return null
        return mapHoldingsToDTO(listOf(holding), accountId).first()
    }

    fun getHoldingPerformance(
        id: UUID,
        accountId: UUID,
        resolution: HoldingPerformanceResolution?,
        includeProjection: Boolean,
    ): HoldingPerformanceDTO? {
        val holding = holdingRepository.findById(id, accountId) ?: return null
        val asset = assetRepository.findById(holding.assetId) ?: return null
        if (!asset.assetType.isFixedIncomePerformanceSupported()) return null

        val today = clock()
        val transactions = investmentTransactionRepository.findByHolding(id, accountId).sortedBy { it.tradeDate }
        val valuationHolding = holding.withBackfilledFixedIncomeTerms(transactions)
        val resolvedResolution = resolution.takeIf { it == HoldingPerformanceResolution.MONTH }
            ?: HoldingPerformanceResolution.MONTH
        val rateMap = safeGetIndicatorRates()
        val valuationReferenceDate = indicatorRateProvider.getReferenceDate()
        val monthlySeries = safeGetMonthlySeries(listOf(valuationHolding), today)

        val points = buildFixedIncomePerformancePoints(
            holding = valuationHolding,
            transactions = transactions,
            rateMap = rateMap,
            valuationReferenceDate = valuationReferenceDate,
            monthlySeries = monthlySeries,
            includeProjection = includeProjection,
            today = today,
        )

        val latestHistoricalPoint = points.lastOrNull { !it.isProjected }
        val supportsProjection = valuationHolding.maturityDate?.isAfter(today) == true

        return HoldingPerformanceDTO(
            holdingId = id,
            assetType = asset.assetType,
            currencyCode = asset.currencyCode,
            resolution = resolvedResolution,
            valuationSource = latestHistoricalPoint?.valuationSource ?: ValuationSource.UNAVAILABLE,
            valuationReferenceDate = latestHistoricalPoint?.valuationReferenceDate,
            capabilities = HoldingPerformanceCapabilitiesDTO(
                supportsProjection = supportsProjection,
                projectionDefaultEnabled = false,
                supportedResolutions = listOf(HoldingPerformanceResolution.MONTH),
                defaultResolution = HoldingPerformanceResolution.MONTH,
            ),
            summary = HoldingPerformanceSummaryDTO(
                investedPrincipal = latestHistoricalPoint?.investedPrincipal?.toDouble(),
                netContributions = latestHistoricalPoint?.netContributions?.toDouble(),
                currentCostBasis = latestHistoricalPoint?.currentCostBasis?.toDouble(),
                grossValue = latestHistoricalPoint?.grossValue?.toDouble(),
                netValue = latestHistoricalPoint?.netValue?.toDouble(),
                incomeTax = latestHistoricalPoint?.incomeTax?.toDouble(),
                iof = latestHistoricalPoint?.iof?.toDouble(),
                grossGain = latestHistoricalPoint?.grossGain?.toDouble(),
                netGain = latestHistoricalPoint?.netGain?.toDouble(),
                grossGainPercent = latestHistoricalPoint?.grossGainPercent?.toDouble(),
            ),
            points = points.map { point ->
                HoldingPerformancePointDTO(
                    timestamp = point.timestamp,
                    isProjected = point.isProjected,
                    investedPrincipal = point.investedPrincipal?.toDouble(),
                    netContributions = point.netContributions?.toDouble(),
                    currentCostBasis = point.currentCostBasis?.toDouble(),
                    grossValue = point.grossValue?.toDouble(),
                    netValue = point.netValue?.toDouble(),
                    incomeTax = point.incomeTax?.toDouble(),
                    iof = point.iof?.toDouble(),
                    grossGain = point.grossGain?.toDouble(),
                    netGain = point.netGain?.toDouble(),
                    quantity = point.quantity?.toDouble(),
                )
            },
        )
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun mapHoldingsToDTO(holdings: List<Holding>, accountId: UUID): List<InvestmentHoldingDTO> {
        if (holdings.isEmpty()) return emptyList()

        val accountsById = financialAccountRepository.findByAccountId(accountId).associateBy { it.id!! }
        val issuersById = issuerRepository
            .findByIds(accountsById.values.mapNotNull { it.issuerId }.toSet())
            .associateBy { it.id!! }
        val assetsById = assetRepository
            .findByIds(holdings.map { it.assetId }.toSet())
            .associateBy { it.id!! }
        val listedAssetDataByTicker = safeGetListedAssetData(
            holdings.mapNotNull { holding ->
                assetsById[holding.assetId]?.listedAssetTicker()
            }.toSet(),
        )
        val listedAssetPricesByTicker = listedAssetDataByTicker
            .mapNotNull { (ticker, assetData) ->
                val closePrice = assetData.closePrice ?: return@mapNotNull null
                val referenceDate = assetData.referenceDate ?: return@mapNotNull null
                ticker to ListedAssetMarketPrice(
                    ticker = assetData.ticker,
                    closePrice = closePrice,
                    referenceDate = referenceDate,
                )
            }
            .toMap()
        val marketDataCnpjs = holdings
            .mapNotNull { holding ->
                val asset = assetsById[holding.assetId]
                if (holding.pricingMode == HoldingPricingMode.MARKET_DATA && asset?.assetType.isFundAsset()) {
                    asset?.fundCnpj?.normalizedFundCnpj()
                } else {
                    null
                }
            }
            .toSet()
        val marketSnapshotsByCnpj = safeGetFundMarketSnapshots(marketDataCnpjs)
        val holdingTransactionsById = loadHoldingTransactionsById(holdings, assetsById, marketSnapshotsByCnpj, accountId)
        val rateMap = safeGetIndicatorRates()
        val refDate = indicatorRateProvider.getReferenceDate()
        val today = clock()
        val monthlySeries = safeGetMonthlySeries(holdings, today)

        return holdings.map {
            it.toDTO(
                accountsById = accountsById,
                assetsById = assetsById,
                issuersById = issuersById,
                rateMap = rateMap,
                fixedIncomeRefDate = refDate,
                today = today,
                listedAssetDataByTicker = listedAssetDataByTicker,
                listedAssetPricesByTicker = listedAssetPricesByTicker,
                marketSnapshotsByCnpj = marketSnapshotsByCnpj,
                holdingTransactionsById = holdingTransactionsById,
                monthlySeries = monthlySeries,
            )
        }
    }

    private fun safeGetIndicatorRates(): Map<Indexer, BigDecimal> =
        try {
            indicatorRateProvider.getLatestRates()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch indicator rates; valuations will fall back to UNAVAILABLE" }
            emptyMap()
        }

    private fun safeGetMonthlySeries(
        holdings: List<Holding>,
        today: LocalDate,
    ): Map<Indexer, List<Pair<LocalDate, BigDecimal>>> {
        val monthlySeriesHoldings = holdings.filter { it.indexer in MONTHLY_SERIES_INDEXERS && it.issueDate != null }
        if (monthlySeriesHoldings.isEmpty()) return emptyMap()

        val result = mutableMapOf<Indexer, List<Pair<LocalDate, BigDecimal>>>()
        for (indexer in monthlySeriesHoldings.mapNotNull { it.indexer }.toSet()) {
            val from =
                monthlySeriesHoldings
                    .filter { it.indexer == indexer }
                    .mapNotNull { it.issueDate }
                    .minOrNull() ?: continue
            try {
                val series = indicatorRateProvider.getMonthlySeriesRange(indexer, from, today)
                if (series.isNotEmpty()) result[indexer] = series
            } catch (e: Exception) {
                logger.warn(e) { "Failed to fetch monthly series for $indexer; valuation will use annual rate fallback" }
            }
        }
        return result
    }

    private fun safeGetFundMarketSnapshots(cnpjs: Set<String>): Map<String, FundMarketSnapshot> =
        try {
            fundMarketDataProvider.getSnapshotsByCnpj(cnpjs)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch fund market snapshots; fund valuations will fall back" }
            emptyMap()
        }

    private fun safeGetListedAssetData(tickers: Set<String>): Map<String, ListedAssetMarketData> =
        try {
            listedAssetMarketDataProvider.getAssetsByTicker(tickers)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch listed asset market data; listed-asset enrichments will fall back" }
            emptyMap()
        }

    private fun loadHoldingTransactionsById(
        holdings: List<Holding>,
        assetsById: Map<UUID, Asset>,
        marketSnapshotsByCnpj: Map<String, FundMarketSnapshot>,
        accountId: UUID,
    ): Map<UUID, List<InvestmentTransaction>> {
        val marketDataHoldingIds = holdings
            .mapNotNull { holding ->
                val asset = assetsById[holding.assetId] ?: return@mapNotNull null
                val normalizedCnpj = asset.fundCnpj?.normalizedFundCnpj() ?: return@mapNotNull null
                if (
                    holding.pricingMode == HoldingPricingMode.MARKET_DATA &&
                    asset.assetType.isFundAsset() &&
                    marketSnapshotsByCnpj.containsKey(normalizedCnpj)
                ) {
                    holding.id
                } else {
                    null
                }
            }
        val fixedIncomeBackfillHoldingIds = holdings
            .mapNotNull { holding ->
                val holdingId = holding.id ?: return@mapNotNull null
                holdingId.takeIf { holding.requiresFixedIncomeTransactionBackfill() }
            }
        val holdingIds = (marketDataHoldingIds + fixedIncomeBackfillHoldingIds).distinct()
        if (holdingIds.isEmpty()) return emptyMap()
        return investmentTransactionRepository.findByHoldingIds(holdingIds, accountId)
            .groupBy { it.holdingId }
    }

    private fun Holding.toDTO(
        accountsById: Map<UUID, FinancialAccount>,
        assetsById: Map<UUID, Asset>,
        issuersById: Map<UUID, Issuer>,
        rateMap: Map<Indexer, BigDecimal>,
        fixedIncomeRefDate: LocalDate?,
        today: LocalDate,
        listedAssetDataByTicker: Map<String, ListedAssetMarketData>,
        listedAssetPricesByTicker: Map<String, ListedAssetMarketPrice>,
        marketSnapshotsByCnpj: Map<String, FundMarketSnapshot>,
        holdingTransactionsById: Map<UUID, List<InvestmentTransaction>>,
        monthlySeries: Map<Indexer, List<Pair<LocalDate, BigDecimal>>>,
    ): InvestmentHoldingDTO {
        val financialAccount = accountsById[financialAccountId]
        val asset = assetsById[assetId]
        val isExpired = maturityDate != null && !maturityDate!!.isAfter(today)
        val isClosed = closeReason != null
        val isExpiredPending =
            isExpired &&
                quantity > BigDecimal.ZERO &&
                closeReason == null

        val valuation =
            manualValuation()
                ?: marketDataValuation(asset, marketSnapshotsByCnpj, holdingTransactionsById)
                ?: listedAssetMarketValuation(asset, listedAssetPricesByTicker)
                ?: providerValuation()
                ?: estimatedFixedIncomeValuation(rateMap, fixedIncomeRefDate, monthlySeries, holdingTransactionsById)
                ?: Valuation(null, null, null, null, ValuationSource.UNAVAILABLE, null)

        return InvestmentHoldingDTO(
            id = id!!,
            financialAccountId = financialAccountId,
            accountId = accountId,
            assetId = assetId,
            asset = asset?.toDTO(asset.listedAssetLogoAssetId(listedAssetDataByTicker)),
            financialAccount = financialAccount?.toDTO(issuersById[financialAccount.issuerId]),
            quantity = quantity.toDouble(),
            averageCost = averageCost.toDouble(),
            indexer = indexer,
            indexerPercentage = indexerPercentage?.toDouble(),
            fixedAnnualRate = fixedAnnualRate?.toDouble(),
            issueDate = issueDate,
            maturityDate = maturityDate,
            gracePeriodDate = gracePeriodDate,
            isTaxExempt = isTaxExempt,
            grossValue = valuation.grossValue,
            netValue = valuation.netValue,
            incomeTax = valuation.incomeTax,
            iof = valuation.iof,
            lastValuedAt = lastValuedAt,
            pricingMode = pricingMode,
            valuationSource = valuation.source,
            valuationReferenceDate = valuation.refDate,
            isExpired = isExpired,
            isClosed = isClosed,
            isExpiredPending = isExpiredPending,
            closedAt = closedAt,
            closeReason = closeReason,
            source = source,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun Holding.manualValuation(): Valuation? {
        if (pricingMode != HoldingPricingMode.MANUAL || manualGrossValue == null) return null
        return Valuation(
            grossValue = manualGrossValue.toDouble(),
            netValue = null,
            incomeTax = null,
            iof = null,
            source = ValuationSource.MANUAL,
            refDate = manualValuedAt,
        )
    }

    private fun Holding.marketDataValuation(
        asset: Asset?,
        marketSnapshotsByCnpj: Map<String, FundMarketSnapshot>,
        holdingTransactionsById: Map<UUID, List<InvestmentTransaction>>,
    ): Valuation? {
        if (pricingMode != HoldingPricingMode.MARKET_DATA) return null
        if (asset == null || !asset.assetType.isFundAsset()) return null
        val fundCnpj = asset.fundCnpj?.normalizedFundCnpj() ?: return null
        val snapshot = marketSnapshotsByCnpj[fundCnpj] ?: return null
        val holdingId = id ?: return null
        return try {
            val result = fundValuationService.estimate(this, snapshot, holdingTransactionsById[holdingId].orEmpty())
            Valuation(
                grossValue = result.grossValue?.toDouble(),
                netValue = result.netValue?.toDouble(),
                incomeTax = result.incomeTax?.toDouble(),
                iof = result.iof?.toDouble(),
                source = result.valuationSource,
                refDate = result.valuationReferenceDate,
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to estimate fund valuation for holding $holdingId" }
            null
        }
    }

    private fun Holding.providerValuation(): Valuation? {
        if (grossValue == null) return null
        return Valuation(
            grossValue = grossValue.toDouble(),
            netValue = netValue?.toDouble(),
            incomeTax = incomeTax?.toDouble(),
            iof = iof?.toDouble(),
            source = ValuationSource.PROVIDER,
            refDate = null,
        )
    }

    private fun Holding.listedAssetMarketValuation(
        asset: Asset?,
        listedAssetPricesByTicker: Map<String, ListedAssetMarketPrice>,
    ): Valuation? {
        val ticker = asset?.listedAssetTicker() ?: return null
        val price = listedAssetPricesByTicker[ticker] ?: return null
        val grossValue = quantity.multiply(price.closePrice, mathContext).multiply(asset.priceFactor, mathContext)
        return Valuation(
            grossValue = grossValue.toDouble(),
            netValue = null,
            incomeTax = null,
            iof = null,
            source = ValuationSource.MARKET_DATA,
            refDate = price.referenceDate,
        )
    }

    private fun Holding.estimatedFixedIncomeValuation(
        rateMap: Map<Indexer, BigDecimal>,
        refDate: LocalDate?,
        monthlySeries: Map<Indexer, List<Pair<LocalDate, BigDecimal>>>,
        holdingTransactionsById: Map<UUID, List<InvestmentTransaction>>,
    ): Valuation? =
        try {
            val valuationHolding =
                id?.let { holdingId ->
                    withBackfilledFixedIncomeTerms(holdingTransactionsById[holdingId].orEmpty())
                } ?: this
            val result = fixedIncomeValuationService.estimate(valuationHolding, rateMap, refDate, monthlySeries)
            Valuation(
                result.grossValue?.toDouble(),
                result.netValue?.toDouble(),
                result.incomeTax?.toDouble(),
                result.iof?.toDouble(),
                result.valuationSource,
                result.valuationReferenceDate,
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to estimate valuation for holding $id" }
            null
        }

    private fun buildFixedIncomePerformancePoints(
        holding: Holding,
        transactions: List<InvestmentTransaction>,
        rateMap: Map<Indexer, BigDecimal>,
        valuationReferenceDate: LocalDate?,
        monthlySeries: Map<Indexer, List<Pair<LocalDate, BigDecimal>>>,
        includeProjection: Boolean,
        today: LocalDate,
    ): List<PerformancePoint> {
        val startDate = holding.issueDate ?: transactions.minOfOrNull { it.tradeDate } ?: return emptyList()
        val endDate = minOf(holding.maturityDate ?: today, today)
        val pointDates = buildMonthlyPerformanceDates(
            startDate = startDate,
            historicalEndDate = endDate,
            projectionEndDate = if (includeProjection) holding.maturityDate else null,
        )
        if (pointDates.isEmpty()) return emptyList()

        var txIndex = 0
        var state = holding.asEmptyPerformanceState()
        var investedPrincipal = BigDecimal.ZERO
        var netContributions = BigDecimal.ZERO

        return pointDates.map { pointDate ->
            while (txIndex < transactions.size && !transactions[txIndex].tradeDate.isAfter(pointDate.date)) {
                val transaction = transactions[txIndex]
                state = state.applyPerformanceTransaction(transaction)
                if (transaction.isCapitalContribution()) {
                    investedPrincipal += transaction.grossValue
                    netContributions += transaction.grossValue
                } else if (transaction.isCapitalWithdrawal()) {
                    netContributions -= transaction.grossValue
                }
                txIndex += 1
            }

            createPerformancePoint(
                holding = state,
                investedPrincipal = investedPrincipal,
                netContributions = netContributions,
                pointDate = pointDate,
                rateMap = rateMap,
                valuationReferenceDate = valuationReferenceDate,
                monthlySeries = monthlySeries,
            )
        }
    }

    private fun createPerformancePoint(
        holding: Holding,
        investedPrincipal: BigDecimal,
        netContributions: BigDecimal,
        pointDate: DatedPerformancePoint,
        rateMap: Map<Indexer, BigDecimal>,
        valuationReferenceDate: LocalDate?,
        monthlySeries: Map<Indexer, List<Pair<LocalDate, BigDecimal>>>,
    ): PerformancePoint {
        val currentCostBasis = holding.quantity.multiply(holding.averageCost, mathContext)

        if (holding.quantity <= BigDecimal.ZERO || currentCostBasis <= BigDecimal.ZERO) {
            return PerformancePoint(
                timestamp = pointDate.date.atStartOfDay().toInstant(ZoneOffset.UTC),
                isProjected = pointDate.isProjected,
                investedPrincipal = investedPrincipal,
                netContributions = netContributions,
                currentCostBasis = BigDecimal.ZERO,
                grossValue = BigDecimal.ZERO,
                netValue = BigDecimal.ZERO,
                incomeTax = BigDecimal.ZERO,
                iof = BigDecimal.ZERO,
                grossGain = BigDecimal.ZERO,
                netGain = BigDecimal.ZERO,
                grossGainPercent = BigDecimal.ZERO,
                quantity = BigDecimal.ZERO,
                valuationSource = ValuationSource.CALCULATED,
                valuationReferenceDate = valuationReferenceDate,
            )
        }

        val valuation = fixedIncomeValuationService.estimate(
            holding = holding,
            indicatorRates = rateMap,
            valuationReferenceDate = valuationReferenceDate,
            monthlySeries = monthlySeries,
            asOfDate = pointDate.date,
            allowRateProjectionPastSeries = pointDate.isProjected,
        )
        val grossValue = valuation.grossValue
        val netValue = valuation.netValue
        val grossGain = grossValue?.subtract(currentCostBasis)
        val netGain = netValue?.subtract(currentCostBasis)
        val grossGainPercent =
            if (grossGain != null && currentCostBasis > BigDecimal.ZERO) {
                grossGain.divide(currentCostBasis, mathContext)
            } else {
                null
            }

        return PerformancePoint(
            timestamp = pointDate.date.atStartOfDay().toInstant(ZoneOffset.UTC),
            isProjected = pointDate.isProjected,
            investedPrincipal = investedPrincipal,
            netContributions = netContributions,
            currentCostBasis = currentCostBasis,
            grossValue = grossValue,
            netValue = netValue,
            incomeTax = valuation.incomeTax,
            iof = valuation.iof,
            grossGain = grossGain,
            netGain = netGain,
            grossGainPercent = grossGainPercent,
            quantity = holding.quantity,
            valuationSource = valuation.valuationSource,
            valuationReferenceDate = valuation.valuationReferenceDate,
        )
    }

    private fun buildMonthlyPerformanceDates(
        startDate: LocalDate,
        historicalEndDate: LocalDate,
        projectionEndDate: LocalDate?,
    ): List<DatedPerformancePoint> {
        if (historicalEndDate.isBefore(startDate)) return emptyList()

        val dates = mutableListOf<DatedPerformancePoint>()
        fun appendMonthlyRange(
            rangeStart: LocalDate,
            rangeEnd: LocalDate,
            isProjected: Boolean,
            useRangeStartForFirstMonth: Boolean,
        ) {
            if (rangeEnd.isBefore(rangeStart)) return

            val startMonth = YearMonth.from(rangeStart)
            val endMonth = YearMonth.from(rangeEnd)
            var monthCursor = startMonth
            while (!monthCursor.isAfter(endMonth)) {
                val pointDate =
                    when {
                        startMonth == endMonth -> rangeEnd
                        monthCursor == startMonth && useRangeStartForFirstMonth -> rangeStart
                        monthCursor == startMonth -> monthCursor.atEndOfMonth()
                        monthCursor == endMonth -> rangeEnd
                        else -> monthCursor.atEndOfMonth()
                    }
                if (dates.none { it.date == pointDate }) {
                    dates += DatedPerformancePoint(pointDate, isProjected)
                }
                monthCursor = monthCursor.plusMonths(1)
            }
        }

        appendMonthlyRange(startDate, historicalEndDate, false, true)

        if (projectionEndDate != null && projectionEndDate.isAfter(historicalEndDate)) {
            val projectionStart =
                if (YearMonth.from(historicalEndDate) == YearMonth.from(historicalEndDate.plusDays(1))) {
                    historicalEndDate.withDayOfMonth(1).plusMonths(1)
                } else {
                    historicalEndDate.plusDays(1)
                }
            appendMonthlyRange(projectionStart, projectionEndDate, true, false)
        }

        return dates.sortedBy { it.date }
    }

    private fun Holding.asEmptyPerformanceState(): Holding =
        copy(
            quantity = BigDecimal.ZERO,
            averageCost = BigDecimal.ZERO,
            grossValue = null,
            netValue = null,
            incomeTax = null,
            iof = null,
            lastValuedAt = null,
        )

    private fun Holding.applyPerformanceTransaction(transaction: InvestmentTransaction): Holding {
        val quantity = transaction.quantity
        val unitPrice = transaction.unitPrice ?: quantity?.takeIf { it > BigDecimal.ZERO }?.let {
            transaction.grossValue.divide(it, COST_SCALE, RoundingMode.HALF_UP)
        }

        return when (transaction.type) {
            InvestmentTransactionType.PURCHASE,
            InvestmentTransactionType.SUBSCRIPTION,
            -> {
                if (quantity == null || unitPrice == null || quantity <= BigDecimal.ZERO) return this
                val newQuantity = this.quantity + quantity
                val newAverageCost =
                    if (this.quantity.signum() == 0) {
                        unitPrice
                    } else {
                        (this.quantity * this.averageCost + quantity * unitPrice)
                            .divide(newQuantity, COST_SCALE, RoundingMode.HALF_UP)
                    }
                copy(quantity = newQuantity, averageCost = newAverageCost)
            }

            InvestmentTransactionType.SALE -> {
                if (quantity == null || quantity <= BigDecimal.ZERO) return this
                copy(quantity = (this.quantity - quantity).coerceAtLeast(BigDecimal.ZERO))
            }

            InvestmentTransactionType.MATURITY -> {
                val closingQuantity = quantity ?: this.quantity
                copy(quantity = (this.quantity - closingQuantity).coerceAtLeast(BigDecimal.ZERO))
            }

            InvestmentTransactionType.CUSTODY_TRANSFER,
            InvestmentTransactionType.OWNERSHIP_TRANSFER,
            InvestmentTransactionType.QUOTA_TRANSFER,
            -> {
                if (transaction.direction == InvestmentTransactionDirection.INFLOW) {
                    if (quantity == null || unitPrice == null || quantity <= BigDecimal.ZERO) return this
                    val newQuantity = this.quantity + quantity
                    val newAverageCost =
                        if (this.quantity.signum() == 0) {
                            unitPrice
                        } else {
                            (this.quantity * this.averageCost + quantity * unitPrice)
                                .divide(newQuantity, COST_SCALE, RoundingMode.HALF_UP)
                        }
                    copy(quantity = newQuantity, averageCost = newAverageCost)
                } else {
                    if (quantity == null || quantity <= BigDecimal.ZERO) return this
                    copy(quantity = (this.quantity - quantity).coerceAtLeast(BigDecimal.ZERO))
                }
            }

            else -> this
        }
    }

    private fun InvestmentTransaction.isCapitalContribution(): Boolean =
        direction == InvestmentTransactionDirection.INFLOW && type in CAPITAL_CONTRIBUTION_TYPES

    private fun InvestmentTransaction.isCapitalWithdrawal(): Boolean =
        direction == InvestmentTransactionDirection.OUTFLOW && type in CAPITAL_WITHDRAWAL_TYPES

    private data class Valuation(
        val grossValue: Double?,
        val netValue: Double?,
        val incomeTax: Double?,
        val iof: Double?,
        val source: ValuationSource,
        val refDate: LocalDate?,
    )

    private data class DatedPerformancePoint(
        val date: LocalDate,
        val isProjected: Boolean,
    )

    private data class PerformancePoint(
        val timestamp: Instant,
        val isProjected: Boolean,
        val investedPrincipal: BigDecimal?,
        val netContributions: BigDecimal?,
        val currentCostBasis: BigDecimal?,
        val grossValue: BigDecimal?,
        val netValue: BigDecimal?,
        val incomeTax: BigDecimal?,
        val iof: BigDecimal?,
        val grossGain: BigDecimal?,
        val netGain: BigDecimal?,
        val grossGainPercent: BigDecimal?,
        val quantity: BigDecimal?,
        val valuationSource: ValuationSource,
        val valuationReferenceDate: LocalDate?,
    )

    private fun Holding.requiresFixedIncomeTransactionBackfill(): Boolean =
        indexer != null && (
            issueDate == null ||
                indexerPercentage == null ||
                ((indexer in INFLATION_LINKED_INDEXERS || indexer == Indexer.PRE_FIXADO) && fixedAnnualRate == null)
        )

    private fun Holding.withBackfilledFixedIncomeTerms(
        transactions: List<InvestmentTransaction>,
    ): Holding {
        if (transactions.isEmpty()) return this
        val chronologicalTransactions = transactions.sortedBy { it.tradeDate }
        return copy(
            indexerPercentage = indexerPercentage ?: chronologicalTransactions.mapNotNull { it.indexerPercentage }.firstOrNull(),
            fixedAnnualRate = fixedAnnualRate ?: chronologicalTransactions.mapNotNull { it.fixedAnnualRate }.firstOrNull(),
            issueDate = issueDate ?: chronologicalTransactions.firstOrNull()?.tradeDate,
        )
    }

    private fun Asset.listedAssetTicker(): String? {
        ticker?.takeIf { it.isNotBlank() }?.trim()?.uppercase()?.let { return it }
        if (!assetType.supportsListedAssetTickerFallback()) return null
        val legacyTicker = issuerName?.takeIf { it.isNotBlank() }?.trim()?.uppercase() ?: return null
        return legacyTicker.takeIf { LISTED_ASSET_TICKER_REGEX.matches(it) }
    }

    private fun Asset.listedAssetLogoAssetId(
        listedAssetDataByTicker: Map<String, ListedAssetMarketData>,
    ): String? =
        listedAssetTicker()?.let { ticker ->
            listedAssetDataByTicker[ticker]?.logoAssetId
        }

    private fun Asset.toDTO(logoAssetId: String?) = InvestmentAssetDTO(
        id = id!!,
        ticker = ticker,
        isinCode = isinCode,
        name = name,
        assetType = assetType,
        exchange = exchange,
        currencyCode = currencyCode,
        issuerCnpj = issuerCnpj,
        issuerName = issuerName,
        fundCnpj = fundCnpj,
        anbimaCategory = anbimaCategory,
        anbimaClass = anbimaClass,
        anbimaSubclass = anbimaSubclass,
        logoAsset = logoAssetId?.let { mapOf("__typename" to "Asset", "id" to it) },
        priceFactor = priceFactor.toDouble(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun AssetType?.isFundAsset(): Boolean =
        this == AssetType.MUTUAL_FUND || this == AssetType.PGBL || this == AssetType.VGBL

    private fun AssetType?.supportsListedAssetTickerFallback(): Boolean =
        this in setOf(
            AssetType.STOCK,
            AssetType.STOCK_RIGHTS,
            AssetType.BDR,
            AssetType.REAL_ESTATE_FUND,
            AssetType.AGRIBUSINESS_FUND,
            AssetType.ETF,
            AssetType.ETF_FIXED_INCOME,
            AssetType.ETF_INTERNATIONAL,
            AssetType.OPTION,
        )

    private fun AssetType?.isFixedIncomePerformanceSupported(): Boolean =
        this in FIXED_INCOME_PERFORMANCE_ASSET_TYPES

    private fun String.normalizedFundCnpj(): String = filter(Char::isDigit)

    private fun FinancialAccount.toDTO(issuer: Issuer?) = FinancialAccountDTO(
        id = id!!,
        type = type,
        subtype = subtype,
        name = name,
        currencyCode = currencyCode,
        includeInNetWorth = includeInNetWorth,
        includeInDashboard = includeInDashboard,
        themeColor = themeColor,
        issuer = issuer?.let {
            IssuerDTO(
                id = it.id!!,
                name = it.name,
                domain = it.domain,
                compeCode = it.compeCode,
                ispb = it.ispb,
                countryCode = it.countryCode,
                logoKey = it.logoKey,
                category = it.category,
                logoAsset = it.logoKey?.let { key -> mapOf("__typename" to "Asset", "id" to key) },
            )
        },
        issuerName = issuerName,
        bankData = bankData?.let {
            BankAccountDataDTO(
                branchCode = it.branchCode,
                number = it.number,
                checkDigit = it.checkDigit,
                maskedNumber = it.maskedNumber,
            )
        },
        cardData = cardData?.let {
            CardDataDTO(
                maskedNumber = it.maskedNumber,
                bin = it.bin,
                cardNetwork = it.cardNetwork,
                cardLevel = it.cardLevel,
                creditLimit = it.creditLimit?.toDouble(),
                availableCreditLimit = it.availableCreditLimit?.toDouble(),
                minimumPaymentPercentage = it.minimumPaymentPercentage?.toDouble(),
                closingDay = it.closingDay,
                dueDay = it.dueDay,
                linkedBankAccountId = it.linkedBankAccountId,
            )
        },
        connectionStatus = connectionStatus,
        consentExpiresAt = consentExpiresAt?.atOffset(ZoneOffset.UTC),
        lastSyncAt = lastSyncAt?.atOffset(ZoneOffset.UTC),
        createdAt = createdAt.atOffset(ZoneOffset.UTC),
        updatedAt = updatedAt.atOffset(ZoneOffset.UTC),
    )

    private companion object {
        private const val COST_SCALE = 8
        private val LISTED_ASSET_TICKER_REGEX = Regex("^[A-Z]{4,6}\\d{1,2}$")
        private val INFLATION_LINKED_INDEXERS =
            setOf(Indexer.IPCA, Indexer.IGP_M, Indexer.IGP_DI, Indexer.INPC, Indexer.TR)
        private val MONTHLY_SERIES_INDEXERS =
            INFLATION_LINKED_INDEXERS + setOf(Indexer.CDI, Indexer.DI, Indexer.SELIC)
        private val FIXED_INCOME_PERFORMANCE_ASSET_TYPES =
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
                AssetType.COE,
            )
        private val CAPITAL_CONTRIBUTION_TYPES =
            setOf(
                InvestmentTransactionType.PURCHASE,
                InvestmentTransactionType.SUBSCRIPTION,
                InvestmentTransactionType.CUSTODY_TRANSFER,
                InvestmentTransactionType.OWNERSHIP_TRANSFER,
                InvestmentTransactionType.QUOTA_TRANSFER,
            )
        private val CAPITAL_WITHDRAWAL_TYPES =
            setOf(
                InvestmentTransactionType.SALE,
                InvestmentTransactionType.MATURITY,
                InvestmentTransactionType.AMORTIZATION,
                InvestmentTransactionType.CANCELLATION,
                InvestmentTransactionType.CUSTODY_TRANSFER,
                InvestmentTransactionType.OWNERSHIP_TRANSFER,
                InvestmentTransactionType.QUOTA_TRANSFER,
            )
    }
}
