package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.FinancialDataMarketDataSyncClient
import io.github.salomax.neotool.banking.client.investment.financialdata.EconomicIndicatorPointSyncNode
import io.github.salomax.neotool.banking.client.investment.messaging.MarketDataJobSucceededEvent
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetCorporateEvent
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkCode
import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkDaily
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncControl
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncJobName
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncStatus
import io.github.salomax.neotool.banking.domain.portfolio.MarketIndicatorPoint
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.portfolio.AssetDailyPriceRepository
import io.github.salomax.neotool.banking.repo.portfolio.InvestmentAssetCorporateEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.MarketBenchmarkDailyRepository
import io.github.salomax.neotool.banking.repo.portfolio.MarketDataSyncControlRepository
import io.github.salomax.neotool.banking.repo.portfolio.MarketIndicatorPointRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Singleton
class MarketDataSyncService(
    private val syncControlRepository: MarketDataSyncControlRepository,
    private val financialDataSyncClient: FinancialDataMarketDataSyncClient,
    private val assetRepository: AssetRepository,
    private val assetDailyPriceRepository: AssetDailyPriceRepository,
    private val marketIndicatorPointRepository: MarketIndicatorPointRepository,
    private val marketBenchmarkDailyRepository: MarketBenchmarkDailyRepository,
    private val investmentAssetCorporateEventRepository: InvestmentAssetCorporateEventRepository,
    private val marketDataReadinessService: MarketDataReadinessService,
    private val financialAccountRepository: FinancialAccountRepository,
    private val portfolioQuotaRepository: PortfolioQuotaRepository,
    private val holdingRepository: HoldingRepository,
    private val investmentTransactionRepository: InvestmentTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val portfolioFlowEventService: PortfolioFlowEventService,
) {
    private val logger = KotlinLogging.logger {}
    private val mathContext = MathContext(12)

    fun handleJobSucceeded(event: MarketDataJobSucceededEvent) {
        require(event.status == "SUCCESS") { "Only SUCCESS events can trigger sync" }

        val jobName = MarketDataSyncJobName.from(event.jobName)
        val existing =
            syncControlRepository.findByJobName(jobName)
                ?: MarketDataSyncControl(jobName = jobName)
        val now = Instant.now()
        val sourceAwareControl =
            existing.copy(
                sourceLastDataDate = maxDate(existing.sourceLastDataDate, event.lastDataDate),
                sourceLastRunAt = maxInstant(existing.sourceLastRunAt, event.lastRunAt),
                lastEventId = event.eventId,
                status = if (existing.status == MarketDataSyncStatus.FAILED) MarketDataSyncStatus.IDLE else existing.status,
                updatedAt = now,
            )

        syncControlRepository.upsert(sourceAwareControl)

        if (!shouldSync(existing, event)) {
            return
        }

        val syncStartedAt = Instant.now()
        syncControlRepository.upsert(
            sourceAwareControl.copy(
                status = MarketDataSyncStatus.SYNCING,
                lastSyncStartedAt = syncStartedAt,
                lastError = null,
                updatedAt = syncStartedAt,
            ),
        )

        try {
            val effectiveSyncedDate = sync(jobName, event, existing)
            val completedAt = Instant.now()
            val synced =
                syncControlRepository.upsert(
                    sourceAwareControl.copy(
                        lastSyncedDate = maxDate(existing.lastSyncedDate, effectiveSyncedDate),
                        status = MarketDataSyncStatus.SUCCESS,
                        lastSyncStartedAt = syncStartedAt,
                        lastSyncCompletedAt = completedAt,
                        lastError = null,
                        updatedAt = completedAt,
                    ),
                )
            maybeRequestRecalculation(synced.jobName)
        } catch (e: Exception) {
            logger.error(e) { "Market data sync failed for ${event.jobName}" }
            syncControlRepository.upsert(
                sourceAwareControl.copy(
                    status = MarketDataSyncStatus.FAILED,
                    lastSyncStartedAt = syncStartedAt,
                    lastSyncCompletedAt = Instant.now(),
                    lastError = e.message ?: e::class.simpleName,
                    updatedAt = Instant.now(),
                ),
            )
            throw e
        }
    }

    private fun sync(
        jobName: MarketDataSyncJobName,
        event: MarketDataJobSucceededEvent,
        existing: MarketDataSyncControl,
    ): LocalDate? =
        when (jobName) {
            MarketDataSyncJobName.B3_COTAHIST -> syncListedAssetPrices(event)
            MarketDataSyncJobName.CVM_NAV -> syncFundDailyNavs(event)
            MarketDataSyncJobName.INDICATORS -> syncIndicators(event)
            MarketDataSyncJobName.B3_CORPORATE_EVENTS -> syncCorporateEvents(existing)
        }

    private fun syncListedAssetPrices(event: MarketDataJobSucceededEvent): LocalDate? {
        var maxSyncedDate: LocalDate? = null
        financialDataSyncClient.forEachListedAssetDailyPrice(
            from = event.fromDate,
            to = event.lastDataDate,
        ) { items ->
            items.forEach { item ->
                val asset = assetRepository.findByTicker(item.ticker) ?: return@forEach
                val assetId = asset.id ?: return@forEach
                assetDailyPriceRepository.upsert(
                    InvestmentAssetDailyPrice(
                        assetId = assetId,
                        priceDate = item.date,
                        closePrice = item.closePrice,
                        source = MarketDataSyncJobName.B3_COTAHIST.jobName,
                    ),
                )
                maxSyncedDate = maxDate(maxSyncedDate, item.date)
            }
        }
        return maxSyncedDate
    }

    private fun syncFundDailyNavs(event: MarketDataJobSucceededEvent): LocalDate? {
        var maxSyncedDate: LocalDate? = null
        financialDataSyncClient.forEachFundDailyNav(
            from = event.fromDate,
            to = event.lastDataDate,
        ) { items ->
            items.forEach { item ->
                val asset = assetRepository.findByFundCnpj(item.cnpj) ?: return@forEach
                val assetId = asset.id ?: return@forEach
                assetDailyPriceRepository.upsert(
                    InvestmentAssetDailyPrice(
                        assetId = assetId,
                        priceDate = item.date,
                        closePrice = item.closePrice,
                        source = MarketDataSyncJobName.CVM_NAV.jobName,
                    ),
                )
                maxSyncedDate = maxDate(maxSyncedDate, item.date)
            }
        }
        return maxSyncedDate
    }

    private fun syncIndicators(event: MarketDataJobSucceededEvent): LocalDate? {
        var maxSyncedDate: LocalDate? = null
        financialDataSyncClient.forEachEconomicIndicatorPoint(
            from = event.fromDate,
            to = event.lastDataDate,
        ) { items ->
            items.forEach { item ->
                marketIndicatorPointRepository.upsert(
                    MarketIndicatorPoint(
                        indicatorLabel = item.indicator,
                        variationLabel = item.variation,
                        referenceDate = item.referenceDate,
                        publicationDate = item.publicationDate,
                        value = item.value,
                        source = MarketDataSyncJobName.INDICATORS.jobName,
                    ),
                )
                toBenchmarkDaily(item)?.let(marketBenchmarkDailyRepository::upsert)
                maxSyncedDate = maxDate(maxSyncedDate, item.referenceDate)
            }
        }
        return maxSyncedDate
    }

    private fun syncCorporateEvents(existing: MarketDataSyncControl): LocalDate? {
        var maxSyncedDate: LocalDate? = null
        financialDataSyncClient.forEachAssetCorporateEvent(
            from = LocalDate.of(1900, 1, 1),
            to = LocalDate.of(9999, 12, 31),
            updatedSince = existing.lastSyncCompletedAt?.minusSeconds(1),
        ) { items ->
            items.forEach { item ->
                val assetId =
                    item.ticker?.let(assetRepository::findByTicker)?.id
                        ?: assetRepository.findByIsinCode(item.isinCode)?.id
                investmentAssetCorporateEventRepository.upsert(
                    InvestmentAssetCorporateEvent(
                        assetId = assetId,
                        ticker = item.ticker,
                        isinCode = item.isinCode,
                        eventType = item.eventType,
                        factor = item.factor,
                        priceUnit = item.priceUnit,
                        tradingStart = item.tradingStart,
                        tradingEnd = item.tradingEnd,
                        subscriptionDate = item.subscriptionDate,
                        lastDatePrior = item.lastDatePrior,
                        approvedOn = item.approvedOn,
                        remarks = item.remarks,
                        source = MarketDataSyncJobName.B3_CORPORATE_EVENTS.jobName,
                    ),
                )
                maxSyncedDate = maxDate(maxSyncedDate, item.lastDatePrior)
            }
        }
        return maxSyncedDate
    }

    private fun maybeRequestRecalculation(jobName: MarketDataSyncJobName) {
        if (jobName == MarketDataSyncJobName.INDICATORS || jobName == MarketDataSyncJobName.B3_COTAHIST || jobName == MarketDataSyncJobName.CVM_NAV || jobName == MarketDataSyncJobName.B3_CORPORATE_EVENTS) {
            val readiness = marketDataReadinessService.valuationReadiness()
            val safeDate = readiness.safeValuationDate ?: return
            if (!readiness.isReady) return

            financialAccountRepository.findAllInvestmentAccounts().forEach { account ->
                val financialAccountId = account.id ?: return@forEach
                val latestQuotaDate =
                    portfolioQuotaRepository.findLatestBefore(
                        financialAccountId = financialAccountId,
                        dimensionType = PortfolioDimensionType.PORTFOLIO,
                        dimensionKey = null,
                        date = LocalDate.of(9999, 12, 31),
                    )?.quotaDate
                if (latestQuotaDate == null || safeDate.isAfter(latestQuotaDate)) {
                    val recalculateFrom =
                        latestQuotaDate?.plusDays(1)
                            ?: findEarliestRelevantDate(financialAccountId, account.accountId)?.coerceAtMost(safeDate)
                            ?: safeDate
                    portfolioFlowEventService.requestRecalculation(
                        financialAccountId = financialAccountId,
                        accountId = account.accountId,
                        recalculateFrom = recalculateFrom,
                        triggeredBy = "market_data_sync:${jobName.jobName}:$safeDate",
                    )
                }
            }
        }
    }

    private fun toBenchmarkDaily(item: EconomicIndicatorPointSyncNode): MarketBenchmarkDaily? {
        val benchmarkCode =
            when {
                item.indicator.equals("CDI", ignoreCase = true) && item.variation.equals("MONTHLY_CHANGE", ignoreCase = true) ->
                    MarketBenchmarkCode.CDI
                item.indicator.equals("IPCA", ignoreCase = true) && item.variation.equals("MONTHLY_CHANGE", ignoreCase = true) ->
                    MarketBenchmarkCode.IPCA
                else -> null
            } ?: return null

        val dailyFactor = BigDecimal.ONE.add(item.value.divide(BigDecimal(100), mathContext), mathContext)
        return MarketBenchmarkDaily(
            benchmarkCode = benchmarkCode,
            priceDate = item.referenceDate,
            dailyFactor = dailyFactor,
            source = MarketDataSyncJobName.INDICATORS.jobName,
        )
    }

    private fun shouldSync(
        existing: MarketDataSyncControl,
        event: MarketDataJobSucceededEvent,
    ): Boolean =
        when (MarketDataSyncJobName.from(event.jobName)) {
            MarketDataSyncJobName.B3_CORPORATE_EVENTS -> existing.lastEventId != event.eventId
            else -> shouldSyncByDate(existing, event)
        }

    private fun shouldSyncByDate(
        existing: MarketDataSyncControl,
        event: MarketDataJobSucceededEvent,
    ): Boolean {
        val lastSyncedDate = existing.lastSyncedDate ?: return true
        return event.fromDate <= lastSyncedDate || event.lastDataDate.isAfter(lastSyncedDate)
    }

    private fun maxInstant(
        a: Instant?,
        b: Instant?,
    ): Instant? =
        when {
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }

    private fun maxDate(
        a: LocalDate?,
        b: LocalDate?,
    ): LocalDate? =
        when {
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }

    private fun findEarliestRelevantDate(
        financialAccountId: java.util.UUID,
        accountId: java.util.UUID,
    ): LocalDate? {
        val holdingDates =
            holdingRepository.findByFinancialAccount(financialAccountId, accountId)
                .flatMap { holding ->
                    listOfNotNull(
                        holding.issueDate,
                        holding.manualValuedAt,
                        holding.lastValuedAt?.atZone(ZoneOffset.UTC)?.toLocalDate(),
                        holding.createdAt.atZone(ZoneOffset.UTC).toLocalDate(),
                    )
                }
        val earliestInvestmentTradeDate =
            investmentTransactionRepository.findByFinancialAccount(financialAccountId, accountId)
                .minOfOrNull { it.tradeDate }
        val earliestLedgerDate = transactionRepository.findEarliestTransactionDate(financialAccountId)

        return (holdingDates + listOfNotNull(earliestInvestmentTradeDate, earliestLedgerDate)).minOrNull()
    }
}
