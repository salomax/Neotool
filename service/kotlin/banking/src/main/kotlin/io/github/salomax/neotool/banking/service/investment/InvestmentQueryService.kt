package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.FundMarketDataProvider
import io.github.salomax.neotool.banking.client.investment.financialdata.IndicatorRateProvider
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.CouponFrequency
import io.github.salomax.neotool.banking.domain.investment.CouponScheduleService
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentHoldingAssetClass
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketData
import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketPrice
import io.github.salomax.neotool.banking.domain.issuer.Issuer
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.graphql.account.BankAccountDataDTO
import io.github.salomax.neotool.banking.graphql.account.CardDataDTO
import io.github.salomax.neotool.banking.graphql.account.FinancialAccountDTO
import io.github.salomax.neotool.banking.graphql.investment.CouponScheduleEntryDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceCapabilitiesDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformancePointDTO
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceResolution
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceSummaryDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentAssetDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentHoldingDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentHoldingPerformanceDTO
import io.github.salomax.neotool.banking.graphql.investment.InvestmentTransactionDTO
import io.github.salomax.neotool.banking.graphql.issuer.IssuerDTO
import io.github.salomax.neotool.banking.model.domain.investment.performance.FundNavPerformancePoint
import io.github.salomax.neotool.banking.model.domain.investment.performance.applyPerformanceTransaction
import io.github.salomax.neotool.banking.model.domain.investment.performance.asEmptyPerformanceState
import io.github.salomax.neotool.banking.model.domain.investment.performance.buildFundNavPerformancePoints
import io.github.salomax.neotool.banking.model.domain.investment.performance.isCapitalContributionForPerformance
import io.github.salomax.neotool.banking.model.domain.investment.performance.isCapitalWithdrawalForPerformance
import io.github.salomax.neotool.banking.model.dto.investment.InvestmentCashTransactionCursor
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.issuer.IssuerRepository
import io.github.salomax.neotool.banking.repo.portfolio.AssetDailyPriceRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import io.github.salomax.neotool.banking.service.investment.recalculation.HoldingPositionEvolution
import io.github.salomax.neotool.banking.service.investment.recalculation.PositionEvolutionInput
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.TreeMap
import java.util.UUID

data class InvestmentCashTransactionsSlice(
    val items: List<Transaction>,
    val hasNextPage: Boolean,
    val startCursor: String?,
    val endCursor: String?,
)

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
    private val assetDailyPriceRepository: AssetDailyPriceRepository,
    private val investmentTransactionRepository: InvestmentTransactionRepository,
    private val indicatorRateProvider: IndicatorRateProvider,
    private val transactionRepository: TransactionRepository,
    private val couponScheduleService: CouponScheduleService = CouponScheduleService(),
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    private val logger = KotlinLogging.logger {}
    private val mathContext = MathContext(12, RoundingMode.HALF_UP)

    fun getHoldings(
        financialAccountId: UUID?,
        accountId: UUID,
        assetClass: InvestmentHoldingAssetClass? = null,
    ): List<InvestmentHoldingDTO> {
        val rawHoldings =
            if (financialAccountId != null) {
                holdingRepository.findOpenByFinancialAccount(financialAccountId, accountId)
            } else {
                holdingRepository.findOpenByAccount(accountId)
            }
        if (rawHoldings.isEmpty()) return emptyList()

        val assetsById =
            if (assetClass != null) {
                assetRepository.findByIds(rawHoldings.map { it.assetId }.toSet()).associateBy { it.id!! }
            } else {
                null
            }
        val holdings =
            if (assetClass != null) {
                rawHoldings.filter { holding ->
                    assetsById?.get(holding.assetId)?.assetType?.let(assetClass::matches) == true
                }
            } else {
                rawHoldings
            }
        return mapHoldingsToDTO(holdings, accountId, assetsById, includeCouponSchedule = false)
    }

    /**
     * Idle cash on custodian account(s): SUM(CREDIT) − SUM(DEBIT) on `banking.transactions` (POSTED/PENDING).
     */
    fun getInvestmentCashBalance(
        financialAccountId: UUID?,
        accountId: UUID,
    ): BigDecimal {
        val accounts = financialAccountRepository.findByAccountId(accountId)
        val scopeIds: List<UUID> =
            if (financialAccountId != null) {
                val fa = accounts.find { it.id == financialAccountId } ?: return BigDecimal.ZERO
                require(fa.type == FinancialAccountType.INVESTMENT || fa.type == FinancialAccountType.BENEFIT) {
                    "financialAccountId must be an investment or benefit custodian account"
                }
                listOf(financialAccountId)
            } else {
                accounts
                    .filter { it.type == FinancialAccountType.INVESTMENT || it.type == FinancialAccountType.BENEFIT }
                    .mapNotNull { it.id }
            }
        return transactionRepository.sumLedgerBalance(accountId, scopeIds)
    }

    fun listInvestmentCashTransactions(
        financialAccountId: UUID?,
        accountId: UUID,
        first: Int?,
        after: String?,
    ): InvestmentCashTransactionsSlice {
        val pageSize = (first ?: 20).coerceIn(1, 50)
        val scopedFaId =
            financialAccountId?.let { id ->
                val fa =
                    financialAccountRepository.findById(id, accountId)
                        ?: throw ValidationException(
                            BankingErrorCode.INVESTMENT_MANUAL_CASH_INVALID_FINANCIAL_ACCOUNT,
                            field = "financialAccountId",
                            parameters = mapOf("reason" to "Financial account not found"),
                        )
                if (fa.type != FinancialAccountType.INVESTMENT && fa.type != FinancialAccountType.BENEFIT) {
                    throw ValidationException(
                        BankingErrorCode.INVESTMENT_MANUAL_CASH_INVALID_FINANCIAL_ACCOUNT,
                        field = "financialAccountId",
                    )
                }
                id
            }
        val (afterDate, afterId) =
            when {
                after.isNullOrBlank() -> Pair<LocalDate?, UUID?>(null, null)
                else ->
                    try {
                        val p = InvestmentCashTransactionCursor.decode(after)
                        p.first to p.second
                    } catch (_: Exception) {
                        throw ValidationException(
                            CommonErrorCode.INVALID_INPUT,
                            field = "after",
                            parameters = mapOf("reason" to "Invalid cursor"),
                        )
                    }
            }
        val rows =
            transactionRepository.findInvestmentCashTransactionsKeyset(
                accountId = accountId,
                financialAccountId = scopedFaId,
                afterDate = afterDate,
                afterId = afterId,
                limit = pageSize + 1,
            )
        val hasNext = rows.size > pageSize
        val page = rows.take(pageSize)
        val startC =
            page.firstOrNull()?.let { tx ->
                InvestmentCashTransactionCursor.encode(tx.date, requireNotNull(tx.id))
            }
        val endC =
            page.lastOrNull()?.let { tx ->
                InvestmentCashTransactionCursor.encode(tx.date, requireNotNull(tx.id))
            }
        return InvestmentCashTransactionsSlice(
            items = page,
            hasNextPage = hasNext,
            startCursor = startC,
            endCursor = endC,
        )
    }

    /**
     * Asset for a ledger row linked to an investment event ([investmentTransactionId]).
     * Matches [mapHoldingsToDTO] asset mapping (listed-ticker logo via market data).
     */
    fun getLinkedInvestmentAssetForCashLeg(
        investmentTransactionId: UUID,
        accountId: UUID,
    ): InvestmentAssetDTO? {
        val invTx = investmentTransactionRepository.findById(investmentTransactionId, accountId) ?: return null
        val holding = holdingRepository.findById(invTx.holdingId, accountId) ?: return null
        val asset = assetRepository.findById(holding.assetId) ?: return null
        val tickers = asset.listedAssetTicker()?.let { setOf(it) } ?: emptySet()
        val listedAssetDataByTicker = safeGetListedAssetData(tickers)
        return asset.toDTO(asset.listedAssetLogoAssetId(listedAssetDataByTicker))
    }

    fun getHolding(
        id: UUID,
        accountId: UUID,
    ): InvestmentHoldingDTO? {
        val holding = holdingRepository.findById(id, accountId) ?: return null
        return mapHoldingsToDTO(listOf(holding), accountId, includeCouponSchedule = true).first()
    }

    fun getHoldingPerformance(
        id: UUID,
        accountId: UUID,
        resolution: HoldingPerformanceResolution?,
        fromDate: LocalDate?,
        toDate: LocalDate?,
        includeProjection: Boolean,
    ): HoldingPerformanceDTO? {
        val holding = holdingRepository.findById(id, accountId) ?: return null
        val asset = assetRepository.findById(holding.assetId) ?: return null
        return computeHoldingPerformance(
            holding = holding,
            asset = asset,
            accountId = accountId,
            resolution = resolution,
            fromDate = fromDate,
            toDate = toDate,
            includeProjection = includeProjection,
        )
    }

    internal fun computeHoldingPerformance(
        holding: Holding,
        asset: Asset,
        accountId: UUID,
        resolution: HoldingPerformanceResolution?,
        fromDate: LocalDate? = null,
        toDate: LocalDate? = null,
        includeProjection: Boolean,
    ): HoldingPerformanceDTO? {
        val id = holding.id ?: return null

        val today = clock()
        val transactions = investmentTransactionRepository.findByHolding(id, accountId).sortedBy { it.tradeDate }

        if (asset.assetType.isFixedIncomePerformanceSupported()) {
            val resolvedResolution = HoldingPerformanceResolution.MONTH
            val valuationHolding = holding.withBackfilledFixedIncomeTerms(transactions)
            val rateMap = safeGetIndicatorRates()
            val valuationReferenceDate = indicatorRateProvider.getReferenceDate()
            val monthlySeries = safeGetMonthlySeries(listOf(valuationHolding), today)

            val points =
                buildFixedIncomePerformancePoints(
                    holding = valuationHolding,
                    transactions = transactions,
                    rateMap = rateMap,
                    valuationReferenceDate = valuationReferenceDate,
                    monthlySeries = monthlySeries,
                    includeProjection = includeProjection,
                    fromDate = fromDate,
                    toDate = toDate,
                    today = today,
                )

            val latestHistoricalPoint = points.lastOrNull { !it.isProjected }
            val supportsProjection = valuationHolding.maturityDate?.isAfter(today) == true

            return mapToHoldingPerformanceDTO(
                holdingId = id,
                asset = asset,
                resolution = resolvedResolution,
                supportedResolutions = listOf(HoldingPerformanceResolution.MONTH),
                latestHistoricalPoint = latestHistoricalPoint,
                supportsProjection = supportsProjection,
                points = points,
            )
        }

        if (asset.assetType.isFundAsset()) {
            val resolvedResolution = resolveHistoricalPerformanceResolution(resolution)
            val cnpj = asset.fundCnpj?.normalizedFundCnpj()?.takeIf { it.length == 14 }
            if (cnpj != null && transactions.isNotEmpty()) {
                val snapshot =
                    try {
                        fundMarketDataProvider.getSnapshotsByCnpj(setOf(cnpj))[cnpj]
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to fetch fund snapshot for performance holding=$id" }
                        null
                    }
                if (snapshot != null) {
                    val firstTxDate = transactions.minOf { it.tradeDate }
                    val navSeries =
                        try {
                            fundMarketDataProvider.getNavSeries(cnpj, firstTxDate, today)
                        } catch (e: Exception) {
                            logger.warn(e) { "Failed to fetch fund NAV series for performance holding=$id" }
                            emptyList()
                        }
                    if (navSeries.isNotEmpty()) {
                        val fundPoints =
                            buildFundNavPerformancePoints(
                                holding = holding,
                                transactions = transactions,
                                navSeries = navSeries,
                                taxSnapshot = snapshot,
                                fundValuationService = fundValuationService,
                                resolution = resolvedResolution,
                                fromDate = fromDate,
                                toDate = toDate,
                                today = today,
                                mathContext = mathContext,
                            )
                        if (fundPoints.isNotEmpty()) {
                            val latestHistoricalPoint = fundPoints.lastOrNull()
                            return mapToHoldingPerformanceDTO(
                                holdingId = id,
                                asset = asset,
                                resolution = resolvedResolution,
                                supportedResolutions =
                                    listOf(
                                        HoldingPerformanceResolution.DAY,
                                        HoldingPerformanceResolution.MONTH,
                                    ),
                                latestHistoricalPoint = latestHistoricalPoint?.toInternalPerformancePoint(),
                                supportsProjection = false,
                                points = fundPoints.map { it.toInternalPerformancePoint() },
                            )
                        }
                    }
                }
            }
        }

        if (asset.assetType.isVariableIncomePortfolioPerformanceSupported()) {
            val resolvedResolution = resolveHistoricalPerformanceResolution(resolution)
            val listedPoints =
                buildListedVariableIncomePerformancePoints(
                    holding = holding,
                    asset = asset,
                    transactions = transactions,
                    resolution = resolvedResolution,
                    fromDate = fromDate,
                    toDate = toDate,
                    today = today,
                )
            if (listedPoints.isNotEmpty()) {
                val latestHistoricalPoint = listedPoints.lastOrNull()
                return mapToHoldingPerformanceDTO(
                    holdingId = id,
                    asset = asset,
                    resolution = resolvedResolution,
                    supportedResolutions = listOf(HoldingPerformanceResolution.DAY, HoldingPerformanceResolution.MONTH),
                    latestHistoricalPoint = latestHistoricalPoint,
                    supportsProjection = false,
                    points = listedPoints,
                )
            }
        }

        return null
    }

    private fun mapToHoldingPerformanceDTO(
        holdingId: UUID,
        asset: Asset,
        resolution: HoldingPerformanceResolution,
        supportedResolutions: List<HoldingPerformanceResolution>,
        latestHistoricalPoint: PerformancePoint?,
        supportsProjection: Boolean,
        points: List<PerformancePoint>,
    ): HoldingPerformanceDTO =
        HoldingPerformanceDTO(
            holdingId = holdingId,
            assetType = asset.assetType,
            currencyCode = asset.currencyCode,
            resolution = resolution,
            valuationSource = latestHistoricalPoint?.valuationSource ?: ValuationSource.UNAVAILABLE,
            valuationReferenceDate = latestHistoricalPoint?.valuationReferenceDate,
            capabilities =
                HoldingPerformanceCapabilitiesDTO(
                    supportsProjection = supportsProjection,
                    projectionDefaultEnabled = false,
                    supportedResolutions = supportedResolutions,
                    defaultResolution = HoldingPerformanceResolution.MONTH,
                ),
            summary =
                HoldingPerformanceSummaryDTO(
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
            points =
                points.map { point ->
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

    private fun FundNavPerformancePoint.toInternalPerformancePoint(): PerformancePoint =
        PerformancePoint(
            timestamp = timestamp,
            isProjected = isProjected,
            investedPrincipal = investedPrincipal,
            netContributions = netContributions,
            currentCostBasis = currentCostBasis,
            grossValue = grossValue,
            netValue = netValue,
            incomeTax = incomeTax,
            iof = iof,
            grossGain = grossGain,
            netGain = netGain,
            grossGainPercent = grossGainPercent,
            quantity = quantity,
            valuationSource = valuationSource,
            valuationReferenceDate = valuationReferenceDate,
        )

    fun getInvestmentHoldingPerformance(
        id: UUID,
        accountId: UUID,
    ): InvestmentHoldingPerformanceDTO? {
        val holding = holdingRepository.findById(id, accountId) ?: return null
        val holdingSnapshot = getHolding(id, accountId) ?: return null
        val transactions = investmentTransactionRepository.findByHolding(id, accountId).sortedBy { it.tradeDate }
        val currentCostBasis = holding.quantity.multiply(holding.averageCost, mathContext).toDouble()
        val currentValue = holdingSnapshot.grossValue ?: holdingSnapshot.netValue ?: currentCostBasis
        val cashFlows = transactions.mapNotNull { it.toHoldingCashFlow() }
        val totalInvested =
            transactions.sumOf { transaction ->
                if (transaction.isCapitalContributionForPerformance()) transaction.cashFlowAmountMagnitude() else 0.0
            }
        val totalReturnAbsolute = cashFlows.sumOf(HoldingCashFlow::amount) + currentValue
        val totalReturnBase = totalInvested.takeIf { it > 0.0 } ?: currentCostBasis
        val totalReturn = if (totalReturnBase > 0.0) totalReturnAbsolute / totalReturnBase else 0.0
        val dividendsReceived =
            if (holding.couponFrequency != null && holding.couponFrequency != CouponFrequency.AT_MATURITY) {
                transactions.sumOf { tx ->
                    if (tx.type == InvestmentTransactionType.INTEREST_PAYMENT) {
                        tx.cashFlowAmountMagnitude()
                    } else {
                        0.0
                    }
                }
            } else {
                transactions.sumOf { it.dividendLikeAmount() }
            }
        val xirrSince = cashFlows.minOfOrNull(HoldingCashFlow::date)
        val terminalCashFlows = cashFlows + HoldingCashFlow(clock(), currentValue)
        val xirr =
            if (
                xirrSince != null &&
                ChronoUnit.DAYS.between(xirrSince, clock()) >= MIN_XIRR_HISTORY_DAYS
            ) {
                calculateXirr(terminalCashFlows)
            } else {
                null
            }

        return InvestmentHoldingPerformanceDTO(
            holdingId = id,
            xirr = xirr,
            xirrSince = xirrSince,
            totalReturn = totalReturn,
            totalReturnAbsolute = totalReturnAbsolute,
            dividendsReceived = dividendsReceived,
        )
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun mapHoldingsToDTO(
        holdings: List<Holding>,
        accountId: UUID,
        preloadedAssetsById: Map<UUID, Asset>? = null,
        includeCouponSchedule: Boolean = false,
    ): List<InvestmentHoldingDTO> {
        if (holdings.isEmpty()) return emptyList()

        val accountsById = financialAccountRepository.findByAccountId(accountId).associateBy { it.id!! }
        val issuersById =
            issuerRepository
                .findByIds(accountsById.values.mapNotNull { it.issuerId }.toSet())
                .associateBy { it.id!! }
        val assetsById =
            preloadedAssetsById
                ?.filterKeys { assetId -> holdings.any { it.assetId == assetId } }
                ?: assetRepository
                    .findByIds(holdings.map { it.assetId }.toSet())
                    .associateBy { it.id!! }
        val listedAssetDataByTicker =
            safeGetListedAssetData(
                holdings.mapNotNull { holding ->
                    assetsById[holding.assetId]?.listedAssetTicker()
                }.toSet(),
            )
        val listedAssetPricesByTicker =
            listedAssetDataByTicker
                .mapNotNull { (ticker, assetData) ->
                    val closePrice = assetData.closePrice ?: return@mapNotNull null
                    val referenceDate = assetData.referenceDate ?: return@mapNotNull null
                    ticker to
                        ListedAssetMarketPrice(
                            ticker = assetData.ticker,
                            closePrice = closePrice,
                            referenceDate = referenceDate,
                        )
                }
                .toMap()
        val marketDataCnpjs =
            holdings
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
        val holdingTransactionsById =
            loadHoldingTransactionsById(
                holdings = holdings,
                assetsById = assetsById,
                marketSnapshotsByCnpj = marketSnapshotsByCnpj,
                accountId = accountId,
                includeCouponSchedule = includeCouponSchedule,
            )
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
                includeCouponSchedule = includeCouponSchedule,
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
                logger.warn(
                    e,
                ) { "Failed to fetch monthly series for $indexer; valuation will use annual rate fallback" }
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
        includeCouponSchedule: Boolean,
    ): Map<UUID, List<InvestmentTransaction>> {
        val marketDataHoldingIds =
            holdings
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
        val fixedIncomeBackfillHoldingIds =
            holdings
                .mapNotNull { holding ->
                    val holdingId = holding.id ?: return@mapNotNull null
                    holdingId.takeIf { holding.requiresFixedIncomeTransactionBackfill() }
                }
        val couponValuationHoldingIds =
            holdings
                .mapNotNull { holding ->
                    val holdingId = holding.id ?: return@mapNotNull null
                    if (holding.couponFrequency != null && holding.couponFrequency != CouponFrequency.AT_MATURITY) {
                        holdingId
                    } else {
                        null
                    }
                }
        val couponScheduleHoldingIds =
            if (!includeCouponSchedule) {
                emptyList()
            } else {
                holdings
                    .mapNotNull { holding ->
                        val holdingId = holding.id ?: return@mapNotNull null
                        holdingId.takeIf {
                            holding.couponFrequency != null &&
                                holding.couponFrequency != CouponFrequency.AT_MATURITY
                        }
                    }
            }
        val holdingIds =
            (
                marketDataHoldingIds + fixedIncomeBackfillHoldingIds +
                    couponValuationHoldingIds + couponScheduleHoldingIds
            ).distinct()
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
        includeCouponSchedule: Boolean,
    ): InvestmentHoldingDTO {
        val effectiveHolding =
            id
                ?.let { holdingId ->
                    reconcileDerivedHoldingState(holdingTransactionsById[holdingId].orEmpty())
                } ?: this
        val financialAccount = accountsById[financialAccountId]
        val asset = assetsById[assetId]
        val isExpired = maturityDate != null && !maturityDate!!.isAfter(today)
        val isClosed = closeReason != null
        val isExpiredPending =
            isExpired &&
                effectiveHolding.quantity > BigDecimal.ZERO &&
                closeReason == null

        val valuation =
            effectiveHolding.manualValuation()
                ?: effectiveHolding.marketDataValuation(asset, marketSnapshotsByCnpj, holdingTransactionsById)
                ?: effectiveHolding.listedAssetMarketValuation(asset, listedAssetPricesByTicker)
                ?: effectiveHolding.providerValuation()
                ?: effectiveHolding.estimatedFixedIncomeValuation(
                    rateMap,
                    fixedIncomeRefDate,
                    monthlySeries,
                    holdingTransactionsById,
                )
                ?: Valuation(null, null, null, null, ValuationSource.UNAVAILABLE, null)
        val scheduleTransactions = id?.let { holdingId -> holdingTransactionsById[holdingId].orEmpty() }.orEmpty()
        val couponSchedule =
            if (includeCouponSchedule && asset != null) {
                couponScheduleService
                    .generateSchedule(this, asset.assetType, scheduleTransactions, today)
                    .map { entry ->
                        CouponScheduleEntryDTO(
                            seriesNumber = entry.seriesNumber,
                            scheduledDate = entry.scheduledDate,
                            periodStart = entry.periodStart,
                            periodEnd = entry.periodEnd,
                            isPartialPeriod = entry.isPartialPeriod,
                            isPast = entry.isPast,
                            matchedTransaction = entry.matchedTransaction?.toDTO(),
                        )
                    }
            } else {
                emptyList()
            }

        return InvestmentHoldingDTO(
            id = id!!,
            financialAccountId = financialAccountId,
            accountId = accountId,
            assetId = assetId,
            asset = asset?.toDTO(asset.listedAssetLogoAssetId(listedAssetDataByTicker)),
            financialAccount = financialAccount?.toDTO(issuersById[financialAccount.issuerId]),
            quantity = effectiveHolding.quantity.toDouble(),
            averageCost = effectiveHolding.averageCost.toDouble(),
            indexer = effectiveHolding.indexer,
            indexerPercentage = effectiveHolding.indexerPercentage?.toDouble(),
            fixedAnnualRate = effectiveHolding.fixedAnnualRate?.toDouble(),
            issueDate = effectiveHolding.issueDate,
            maturityDate = effectiveHolding.maturityDate,
            gracePeriodDate = effectiveHolding.gracePeriodDate,
            isTaxExempt = effectiveHolding.isTaxExempt,
            couponFrequency = effectiveHolding.couponFrequency,
            couponDayOfMonth = effectiveHolding.couponDayOfMonth,
            couponSchedule = couponSchedule,
            grossValue = valuation.grossValue,
            netValue = valuation.netValue,
            incomeTax = valuation.incomeTax,
            iof = valuation.iof,
            accruedInterest =
                run {
                    val gv = valuation.grossValue ?: return@run null
                    val principal =
                        effectiveHolding.quantity
                            .multiply(effectiveHolding.averageCost, mathContext)
                            .toDouble()
                    if (principal <= 0.0) return@run null
                    val interest = gv - principal
                    if (interest <= 0.0) null else interest
                },
            lastValuedAt = effectiveHolding.lastValuedAt,
            pricingMode = effectiveHolding.pricingMode,
            comeCotasEnabled = effectiveHolding.comeCotasEnabled,
            valuationSource = valuation.source,
            valuationReferenceDate = valuation.refDate,
            isExpired = isExpired,
            isClosed = isClosed,
            isExpiredPending = isExpiredPending,
            closedAt = effectiveHolding.closedAt,
            closeReason = effectiveHolding.closeReason,
            source = effectiveHolding.source,
            createdAt = effectiveHolding.createdAt,
            updatedAt = effectiveHolding.updatedAt,
        )
    }

    private fun Holding.reconcileDerivedHoldingState(transactions: List<InvestmentTransaction>): Holding {
        if (transactions.isEmpty()) return this
        val replayed =
            transactions
                .sortedWith(compareBy<InvestmentTransaction>({ it.tradeDate }, { it.createdAt }, { it.id.toString() }))
                .fold(copy(quantity = BigDecimal.ZERO, averageCost = BigDecimal.ZERO)) { state, transaction ->
                    HoldingPositionEvolution.evolve(state, PositionEvolutionInput.from(transaction))
                }
        return copy(quantity = replayed.quantity, averageCost = replayed.averageCost)
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
            val holdingTransactions = id?.let { holdingId -> holdingTransactionsById[holdingId].orEmpty() }.orEmpty()
            val result =
                fixedIncomeValuationService.estimate(
                    holding = valuationHolding,
                    indicatorRates = rateMap,
                    valuationReferenceDate = refDate,
                    monthlySeries = monthlySeries,
                    transactions = holdingTransactions,
                )
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
        fromDate: LocalDate?,
        toDate: LocalDate?,
        today: LocalDate,
    ): List<PerformancePoint> {
        val startDate = holding.issueDate ?: transactions.minOfOrNull { it.tradeDate } ?: return emptyList()
        val historicalEndDate = minOf(holding.maturityDate ?: today, today)
        val window =
            resolvePerformanceWindow(
                startDate = startDate,
                historicalEndDate = historicalEndDate,
                projectionEndDate = if (includeProjection) holding.maturityDate else null,
                fromDate = fromDate,
                toDate = toDate,
            ) ?: return emptyList()
        val pointDates =
            buildMonthlyPerformanceDates(
                visibleFrom = window.fromDate,
                visibleTo = window.toDate,
                historicalEndDate = window.historicalEndDate,
            )
        if (pointDates.isEmpty()) return emptyList()

        var txIndex = 0
        val appliedTransactions = mutableListOf<InvestmentTransaction>()
        var state = holding.asEmptyPerformanceState()
        var investedPrincipal = BigDecimal.ZERO
        var netContributions = BigDecimal.ZERO

        return pointDates.map { pointDate ->
            while (txIndex < transactions.size && !transactions[txIndex].tradeDate.isAfter(pointDate.date)) {
                val transaction = transactions[txIndex]
                appliedTransactions += transaction
                state = state.applyPerformanceTransaction(transaction)
                if (transaction.isCapitalContributionForPerformance()) {
                    investedPrincipal += transaction.grossValue
                    netContributions += transaction.grossValue
                } else if (transaction.isCapitalWithdrawalForPerformance()) {
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
                transactions = appliedTransactions,
            )
        }
    }

    /**
     * Monthly series for listed variable income holdings, marked to market with historical prices when available.
     */
    private fun buildListedVariableIncomePerformancePoints(
        holding: Holding,
        asset: Asset,
        transactions: List<InvestmentTransaction>,
        resolution: HoldingPerformanceResolution,
        fromDate: LocalDate?,
        toDate: LocalDate?,
        today: LocalDate,
    ): List<PerformancePoint> {
        if (transactions.isEmpty()) return emptyList()

        val startDate =
            listOfNotNull(
                transactions.minOfOrNull { it.tradeDate },
                holding.issueDate,
            ).minOrNull() ?: return emptyList()

        val window =
            resolvePerformanceWindow(
                startDate = startDate,
                historicalEndDate = today,
                projectionEndDate = null,
                fromDate = fromDate,
                toDate = toDate,
            ) ?: return emptyList()
        val historicalPrices = loadListedAssetHistoricalPrices(asset, window.fromDate, window.toDate)
        val pointDates =
            buildListedVariableIncomePointDates(
                resolution = resolution,
                historicalPrices = historicalPrices,
                fromDate = window.fromDate,
                toDate = window.toDate,
            )
        if (pointDates.isEmpty()) return emptyList()

        val ticker = asset.listedAssetTicker()
        val latestMarketPrice: ListedAssetMarketPrice? =
            ticker?.let { t ->
                safeGetListedAssetData(setOf(t))[t]?.let { d ->
                    val close = d.closePrice ?: return@let null
                    val ref = d.referenceDate ?: return@let null
                    ListedAssetMarketPrice(
                        ticker = d.ticker,
                        closePrice = close,
                        referenceDate = ref,
                    )
                }
            }
        val lastHistoricalDate = pointDates.lastOrNull { !it.isProjected }?.date

        var txIndex = 0
        var state = holding.asEmptyPerformanceState()
        var investedPrincipal = BigDecimal.ZERO
        var netContributions = BigDecimal.ZERO

        val rawPoints =
            pointDates.map { pointDate ->
                while (txIndex < transactions.size && !transactions[txIndex].tradeDate.isAfter(pointDate.date)) {
                    val transaction = transactions[txIndex]
                    state = state.applyPerformanceTransaction(transaction)
                    if (transaction.isCapitalContributionForPerformance()) {
                        investedPrincipal += transaction.grossValue
                        netContributions += transaction.grossValue
                    } else if (transaction.isCapitalWithdrawalForPerformance()) {
                        netContributions -= transaction.grossValue
                    }
                    txIndex += 1
                }

                val isTerminal = !pointDate.isProjected && pointDate.date == lastHistoricalDate
                val resolvedMarketPrice =
                    resolveListedAssetMarketPrice(
                        historicalPrices = historicalPrices,
                        pointDate = pointDate.date,
                        latestMarketPrice = latestMarketPrice,
                    )
                createListedVariableIncomePerformancePoint(
                    state = state,
                    asset = asset,
                    investedPrincipal = investedPrincipal,
                    netContributions = netContributions,
                    pointDate = pointDate,
                    isTerminalHistoricalPoint = isTerminal,
                    marketPrice = resolvedMarketPrice,
                    providerGrossValue = holding.grossValue,
                )
            }

        return ensureAtLeastTwoListedPerformancePoints(rawPoints, window.toDate)
    }

    private fun createListedVariableIncomePerformancePoint(
        state: Holding,
        asset: Asset,
        investedPrincipal: BigDecimal,
        netContributions: BigDecimal,
        pointDate: DatedPerformancePoint,
        isTerminalHistoricalPoint: Boolean,
        marketPrice: ListedAssetMarketPrice?,
        providerGrossValue: BigDecimal?,
    ): PerformancePoint {
        val currentCostBasis = state.quantity.multiply(state.averageCost, mathContext)

        if (state.quantity <= BigDecimal.ZERO || currentCostBasis <= BigDecimal.ZERO) {
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
                valuationReferenceDate = null,
            )
        }

        val priceFactor = asset.priceFactor
        val grossValue: BigDecimal?
        val valuationSource: ValuationSource
        val valuationReferenceDate: LocalDate?

        when {
            marketPrice != null -> {
                grossValue =
                    state.quantity
                        .multiply(marketPrice.closePrice, mathContext)
                        .multiply(priceFactor, mathContext)
                valuationSource = ValuationSource.MARKET_DATA
                valuationReferenceDate = marketPrice.referenceDate
            }
            isTerminalHistoricalPoint && providerGrossValue != null -> {
                grossValue = providerGrossValue
                valuationSource = ValuationSource.PROVIDER
                valuationReferenceDate = null
            }
            else -> {
                grossValue = currentCostBasis
                valuationSource = ValuationSource.CALCULATED
                valuationReferenceDate = null
            }
        }

        val netValue = grossValue
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
            incomeTax = null,
            iof = null,
            grossGain = grossGain,
            netGain = netGain,
            grossGainPercent = grossGainPercent,
            quantity = state.quantity,
            valuationSource = valuationSource,
            valuationReferenceDate = valuationReferenceDate,
        )
    }

    private fun ensureAtLeastTwoListedPerformancePoints(
        points: List<PerformancePoint>,
        today: LocalDate,
    ): List<PerformancePoint> {
        if (points.size >= 2) return points
        if (points.isEmpty()) return points
        val last = points.last()
        val lastDay = last.timestamp.atZone(ZoneOffset.UTC).toLocalDate()
        val candidate =
            if (lastDay.isBefore(today)) {
                today.atStartOfDay(ZoneOffset.UTC).toInstant()
            } else {
                last.timestamp.plusSeconds(1)
            }
        val second =
            if (candidate == last.timestamp) {
                last.copy(timestamp = last.timestamp.plusNanos(1))
            } else {
                last.copy(timestamp = candidate)
            }
        return listOf(last, second)
    }

    private fun loadListedAssetHistoricalPrices(
        asset: Asset,
        from: LocalDate,
        to: LocalDate,
    ): TreeMap<LocalDate, ListedAssetMarketPrice> {
        val assetId = asset.id ?: return TreeMap()
        val result = TreeMap<LocalDate, ListedAssetMarketPrice>()

        assetDailyPriceRepository.findLatestBefore(assetId, from)?.let { price ->
            result[price.priceDate] =
                ListedAssetMarketPrice(
                    ticker = asset.listedAssetTicker().orEmpty(),
                    closePrice = price.closePrice,
                    referenceDate = price.priceDate,
                )
        }

        assetDailyPriceRepository.findByAssetAndDateRange(assetId, from, to).forEach { price ->
            result[price.priceDate] =
                ListedAssetMarketPrice(
                    ticker = asset.listedAssetTicker().orEmpty(),
                    closePrice = price.closePrice,
                    referenceDate = price.priceDate,
                )
        }

        return result
    }

    private fun resolveListedAssetMarketPrice(
        historicalPrices: TreeMap<LocalDate, ListedAssetMarketPrice>,
        pointDate: LocalDate,
        latestMarketPrice: ListedAssetMarketPrice?,
    ): ListedAssetMarketPrice? =
        historicalPrices.floorEntry(pointDate)?.value
            ?: latestMarketPrice?.takeIf { !it.referenceDate.isAfter(pointDate) }

    private fun createPerformancePoint(
        holding: Holding,
        investedPrincipal: BigDecimal,
        netContributions: BigDecimal,
        pointDate: DatedPerformancePoint,
        rateMap: Map<Indexer, BigDecimal>,
        valuationReferenceDate: LocalDate?,
        monthlySeries: Map<Indexer, List<Pair<LocalDate, BigDecimal>>>,
        transactions: List<InvestmentTransaction>,
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

        val valuation =
            fixedIncomeValuationService.estimate(
                holding = holding,
                indicatorRates = rateMap,
                valuationReferenceDate = valuationReferenceDate,
                monthlySeries = monthlySeries,
                asOfDate = pointDate.date,
                allowRateProjectionPastSeries = pointDate.isProjected,
                transactions = transactions,
            )
        val grossValue = valuation.grossValue
        val netValue = valuation.netValue
        val couponIncomeNet =
            if (holding.couponFrequency != null && holding.couponFrequency != CouponFrequency.AT_MATURITY) {
                transactions
                    .asSequence()
                    .filter { tx -> tx.type == InvestmentTransactionType.INTEREST_PAYMENT }
                    .filter { tx -> tx.direction == InvestmentTransactionDirection.INFLOW }
                    .filter { tx -> !tx.tradeDate.isAfter(pointDate.date) }
                    .fold(BigDecimal.ZERO) { acc, tx -> acc + (tx.netValue ?: tx.grossValue) }
            } else {
                BigDecimal.ZERO
            }
        val grossGain = grossValue?.subtract(currentCostBasis)?.add(couponIncomeNet)
        val netGain = netValue?.subtract(currentCostBasis)?.add(couponIncomeNet)
        val gainPercentBase =
            if (
                holding.couponFrequency != null &&
                holding.couponFrequency != CouponFrequency.AT_MATURITY &&
                investedPrincipal > BigDecimal.ZERO
            ) {
                investedPrincipal
            } else {
                currentCostBasis
            }
        val grossGainPercent =
            if (grossGain != null && gainPercentBase > BigDecimal.ZERO) {
                grossGain.divide(gainPercentBase, mathContext)
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
        visibleFrom: LocalDate,
        visibleTo: LocalDate,
        historicalEndDate: LocalDate,
    ): List<DatedPerformancePoint> {
        if (visibleTo.isBefore(visibleFrom)) return emptyList()
        val actualUpperBound = minOf(visibleTo, historicalEndDate)
        val points = mutableListOf<DatedPerformancePoint>()

        if (!actualUpperBound.isBefore(visibleFrom)) {
            points +=
                buildMonthlySegmentDates(visibleFrom, actualUpperBound).map { date ->
                    DatedPerformancePoint(date = date, isProjected = false)
                }
        }

        if (visibleTo.isAfter(historicalEndDate)) {
            val startMonth = maxOf(YearMonth.from(visibleFrom), YearMonth.from(historicalEndDate).plusMonths(1))
            val endMonth = YearMonth.from(visibleTo)
            points +=
                generateSequence(startMonth) { it.plusMonths(1) }
                    .takeWhile { !it.isAfter(endMonth) }
                    .map { month ->
                        val pointDate = if (month == endMonth) visibleTo else month.atEndOfMonth()
                        DatedPerformancePoint(date = pointDate, isProjected = true)
                    }
                    .toList()
        }

        return points.distinctBy { it.date }
    }

    private fun buildMonthlySegmentDates(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<LocalDate> {
        if (endDate.isBefore(startDate)) return emptyList()
        val startMonth = YearMonth.from(startDate)
        val endMonth = YearMonth.from(endDate)
        return generateSequence(startMonth) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(endMonth) }
            .map { month ->
                when {
                    startMonth == endMonth -> endDate
                    month == startMonth -> startDate
                    month == endMonth -> endDate
                    else -> month.atEndOfMonth()
                }
            }
            .distinct()
            .toList()
    }

    private fun buildListedVariableIncomePointDates(
        resolution: HoldingPerformanceResolution,
        historicalPrices: TreeMap<LocalDate, ListedAssetMarketPrice>,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<DatedPerformancePoint> =
        when (resolution) {
            HoldingPerformanceResolution.DAY ->
                (
                    historicalPrices.keys.filter { !it.isBefore(fromDate) && !it.isAfter(toDate) } + toDate
                ).distinct()
                    .sorted()
                    .map { DatedPerformancePoint(date = it, isProjected = false) }
            else -> buildMonthlyPerformanceDates(visibleFrom = fromDate, visibleTo = toDate, historicalEndDate = toDate)
        }

    private fun resolveHistoricalPerformanceResolution(
        requested: HoldingPerformanceResolution?,
    ): HoldingPerformanceResolution =
        requested.takeIf { it == HoldingPerformanceResolution.DAY || it == HoldingPerformanceResolution.MONTH }
            ?: HoldingPerformanceResolution.MONTH

    private fun resolvePerformanceWindow(
        startDate: LocalDate,
        historicalEndDate: LocalDate,
        projectionEndDate: LocalDate?,
        fromDate: LocalDate?,
        toDate: LocalDate?,
    ): PerformanceWindow? {
        val upperBound = projectionEndDate ?: historicalEndDate
        val resolvedFrom = maxOf(fromDate ?: startDate, startDate)
        val resolvedTo = minOf(toDate ?: upperBound, upperBound)
        if (resolvedTo.isBefore(resolvedFrom)) return null
        return PerformanceWindow(
            fromDate = resolvedFrom,
            toDate = resolvedTo,
            historicalEndDate = minOf(resolvedTo, historicalEndDate),
        )
    }

    private fun InvestmentTransaction.cashFlowAmountMagnitude(): Double = (netValue ?: grossValue).abs().toDouble()

    private fun InvestmentTransaction.dividendLikeAmount(): Double =
        if (type in DIVIDEND_LIKE_TYPES) cashFlowAmountMagnitude() else 0.0

    private fun InvestmentTransaction.toHoldingCashFlow(): HoldingCashFlow? {
        val amount =
            when {
                isCapitalContributionForPerformance() -> -cashFlowAmountMagnitude()
                isCapitalWithdrawalForPerformance() -> cashFlowAmountMagnitude()
                type in DIVIDEND_LIKE_TYPES -> cashFlowAmountMagnitude()
                type in EXPENSE_LIKE_TYPES -> -cashFlowAmountMagnitude()
                else -> null
            } ?: return null
        if (amount == 0.0) return null
        return HoldingCashFlow(tradeDate, amount)
    }

    private fun calculateXirr(cashFlows: List<HoldingCashFlow>): Double? {
        if (cashFlows.size < 2) return null
        if (cashFlows.none { it.amount < 0.0 } || cashFlows.none { it.amount > 0.0 }) return null

        val startDate = cashFlows.minOf(HoldingCashFlow::date)

        fun npv(rate: Double): Double =
            cashFlows.sumOf { flow ->
                val years = ChronoUnit.DAYS.between(startDate, flow.date).toDouble() / DAYS_PER_YEAR
                flow.amount / Math.pow(1.0 + rate, years)
            }

        var lower = MIN_XIRR_RATE
        var upper = INITIAL_XIRR_UPPER_BOUND
        var lowerValue = npv(lower)
        var upperValue = npv(upper)
        var expansions = 0

        while (lowerValue * upperValue > 0.0 && expansions < MAX_XIRR_BOUND_EXPANSIONS) {
            upper *= 2.0
            upperValue = npv(upper)
            expansions += 1
        }

        if (!lowerValue.isFinite() || !upperValue.isFinite() || lowerValue * upperValue > 0.0) {
            return null
        }

        repeat(MAX_XIRR_ITERATIONS) {
            val mid = (lower + upper) / 2.0
            val midValue = npv(mid)
            if (!midValue.isFinite()) return null
            if (kotlin.math.abs(midValue) < XIRR_TOLERANCE) return mid

            if (lowerValue * midValue <= 0.0) {
                upper = mid
            } else {
                lower = mid
                lowerValue = midValue
            }
        }

        return (lower + upper) / 2.0
    }

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

    private data class PerformanceWindow(
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val historicalEndDate: LocalDate,
    )

    private data class HoldingCashFlow(
        val date: LocalDate,
        val amount: Double,
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

    private fun Holding.withBackfilledFixedIncomeTerms(transactions: List<InvestmentTransaction>): Holding {
        if (transactions.isEmpty()) return this
        val chronologicalTransactions = transactions.sortedBy { it.tradeDate }
        return copy(
            indexerPercentage =
                indexerPercentage ?: chronologicalTransactions.mapNotNull { it.indexerPercentage }.firstOrNull(),
            fixedAnnualRate =
                fixedAnnualRate ?: chronologicalTransactions.mapNotNull { it.fixedAnnualRate }.firstOrNull(),
            issueDate = issueDate ?: chronologicalTransactions.firstOrNull()?.tradeDate,
        )
    }

    private fun Asset.listedAssetTicker(): String? {
        ticker?.takeIf { it.isNotBlank() }?.trim()?.uppercase()?.let { return it }
        if (!assetType.supportsListedAssetTickerFallback()) return null
        val legacyTicker = issuerName?.takeIf { it.isNotBlank() }?.trim()?.uppercase() ?: return null
        return legacyTicker.takeIf { LISTED_ASSET_TICKER_REGEX.matches(it) }
    }

    private fun Asset.listedAssetLogoAssetId(listedAssetDataByTicker: Map<String, ListedAssetMarketData>): String? =
        listedAssetTicker()?.let { ticker ->
            listedAssetDataByTicker[ticker]?.logoAssetId
        }

    private fun Asset.toDTO(logoAssetId: String?) =
        InvestmentAssetDTO(
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

    private fun InvestmentTransaction.toDTO(): InvestmentTransactionDTO =
        InvestmentTransactionDTO(
            id = id!!,
            holdingId = holdingId,
            financialAccountId = financialAccountId,
            direction = direction,
            type = type,
            typeAdditionalInfo = typeAdditionalInfo,
            quantity = quantity?.toDouble(),
            unitPrice = unitPrice?.toDouble(),
            grossValue = grossValue.toDouble(),
            netValue = netValue?.toDouble(),
            incomeTax = incomeTax?.toDouble(),
            iof = iof?.toDouble(),
            indexerPercentage = indexerPercentage?.toDouble(),
            fixedAnnualRate = fixedAnnualRate?.toDouble(),
            priceFactor = priceFactor?.toDouble(),
            tradeDate = tradeDate,
            settlementDate = settlementDate,
            source = source,
            brokerNote = null,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun AssetType?.isFundAsset(): Boolean = this in FUND_ASSET_TYPES

    private fun AssetType?.isVariableIncomePortfolioPerformanceSupported(): Boolean =
        this in VARIABLE_INCOME_PORTFOLIO_PERFORMANCE_ASSET_TYPES

    private fun AssetType?.supportsListedAssetTickerFallback(): Boolean =
        this in
            setOf(
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

    private fun AssetType?.isFixedIncomePerformanceSupported(): Boolean = this in FIXED_INCOME_PERFORMANCE_ASSET_TYPES

    private fun String.normalizedFundCnpj(): String = filter(Char::isDigit)

    private fun FinancialAccount.toDTO(issuer: Issuer?) =
        FinancialAccountDTO(
            id = id!!,
            type = type,
            subtype = subtype,
            name = name,
            currencyCode = currencyCode,
            includeInNetWorth = includeInNetWorth,
            includeInDashboard = includeInDashboard,
            includeCashInAllocation = includeCashInAllocation,
            themeColor = themeColor,
            issuer =
                issuer?.let {
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
            bankData =
                bankData?.let {
                    BankAccountDataDTO(
                        branchCode = it.branchCode,
                        number = it.number,
                        checkDigit = it.checkDigit,
                        maskedNumber = it.maskedNumber,
                    )
                },
            cardData =
                cardData?.let {
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
                        isMultiCard = it.isMultiCard,
                    )
                },
            connectionStatus = connectionStatus,
            consentExpiresAt = consentExpiresAt?.atOffset(ZoneOffset.UTC),
            lastSyncAt = lastSyncAt?.atOffset(ZoneOffset.UTC),
            dataSince = null,
            initialBalance = null,
            initialBalanceDate = null,
            createdAt = createdAt.atOffset(ZoneOffset.UTC),
            updatedAt = updatedAt.atOffset(ZoneOffset.UTC),
        )

    private companion object {
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
        private val FUND_ASSET_TYPES =
            setOf(
                AssetType.MUTUAL_FUND,
                AssetType.PGBL,
                AssetType.VGBL,
                AssetType.REAL_ESTATE_FUND,
                AssetType.AGRIBUSINESS_FUND,
                AssetType.ETF,
                AssetType.ETF_FIXED_INCOME,
                AssetType.ETF_INTERNATIONAL,
            )

        /** Asset types used by portfolio performance subgroup filters when NAV/RF paths do not apply. */
        private val VARIABLE_INCOME_PORTFOLIO_PERFORMANCE_ASSET_TYPES =
            setOf(
                AssetType.STOCK,
                AssetType.STOCK_RIGHTS,
                AssetType.BDR,
                AssetType.ETF,
                AssetType.ETF_FIXED_INCOME,
                AssetType.ETF_INTERNATIONAL,
                AssetType.REAL_ESTATE_FUND,
                AssetType.AGRIBUSINESS_FUND,
                AssetType.CRYPTO,
                AssetType.OPTION,
                AssetType.DERIVATIVES,
            )
        private val DIVIDEND_LIKE_TYPES =
            setOf(
                InvestmentTransactionType.DIVIDEND,
                InvestmentTransactionType.INTEREST_ON_EQUITY,
                InvestmentTransactionType.INTEREST_PAYMENT,
                InvestmentTransactionType.PRIZE,
                InvestmentTransactionType.STOCK_LENDING,
            )
        private val EXPENSE_LIKE_TYPES =
            setOf(
                InvestmentTransactionType.WITHHOLDING_TAX,
                InvestmentTransactionType.FINE,
                InvestmentTransactionType.LATE_PAYMENT_INTEREST,
            )
        private const val DAYS_PER_YEAR = 365.0
        private const val MIN_XIRR_HISTORY_DAYS = 30L
        private const val MIN_XIRR_RATE = -0.9999
        private const val INITIAL_XIRR_UPPER_BOUND = 1.0
        private const val MAX_XIRR_BOUND_EXPANSIONS = 20
        private const val MAX_XIRR_ITERATIONS = 100
        private const val XIRR_TOLERANCE = 1e-7
    }
}
