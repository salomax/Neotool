package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetRepository
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.HoldingRepository
import io.github.salomax.neotool.banking.domain.investment.IndicatorRateProvider
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketData
import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketDataProvider
import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceResolution
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class InvestmentQueryServiceTest {
    private val holdingRepository: HoldingRepository = mock()
    private val assetRepository: AssetRepository = mock()
    private val financialAccountRepository: FinancialAccountRepository = mock()
    private val issuerRepository: IssuerRepository = mock()
    private val fixedIncomeValuationService: FixedIncomeValuationService = mock()
    private val fundValuationService: FundValuationService = mock()
    private val fundMarketDataProvider: FundMarketDataProvider = mock()
    private val listedAssetMarketDataProvider: ListedAssetMarketDataProvider = mock()
    private val investmentTransactionRepository: InvestmentTransactionRepository = mock()
    private val indicatorRateProvider: IndicatorRateProvider = mock()

    private val service =
        InvestmentQueryService(
            holdingRepository = holdingRepository,
            assetRepository = assetRepository,
            financialAccountRepository = financialAccountRepository,
            issuerRepository = issuerRepository,
            fixedIncomeValuationService = fixedIncomeValuationService,
            fundValuationService = fundValuationService,
            fundMarketDataProvider = fundMarketDataProvider,
            listedAssetMarketDataProvider = listedAssetMarketDataProvider,
            investmentTransactionRepository = investmentTransactionRepository,
            indicatorRateProvider = indicatorRateProvider,
            clock = { LocalDate.of(2025, 12, 2) },
        )

    @Test
    fun `listed stock holding uses latest market price`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val now = Instant.parse("2025-12-02T12:00:00Z")

        val holding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("100"),
                averageCost = BigDecimal("12.00"),
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                ticker = "PETR4",
                name = "Petrobras PN",
                assetType = AssetType.STOCK,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            )
        val financialAccount =
            FinancialAccount(
                id = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                type = FinancialAccountType.INVESTMENT,
                subtype = FinancialAccountSubtype.BROKERAGE,
                name = "Broker",
                createdAt = now,
                updatedAt = now,
            )

        whenever(holdingRepository.findByAccount(accountId)).thenReturn(listOf(holding))
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(financialAccount))
        whenever(issuerRepository.findByIds(emptySet())).thenReturn(emptyList())
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(emptySet())).thenReturn(emptyMap())
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(setOf("PETR4"))).thenReturn(
            mapOf(
                "PETR4" to ListedAssetMarketData(
                    ticker = "PETR4",
                    closePrice = BigDecimal("15.00"),
                    referenceDate = LocalDate.of(2025, 12, 1),
                    logoAssetId = "asset-logo-petr4",
                ),
            ),
        )
        whenever(investmentTransactionRepository.findByHoldingIds(emptyList(), accountId)).thenReturn(emptyList())
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(null)

        val result = service.getAllHoldings(accountId)

        assertThat(result).hasSize(1)
        assertThat(result.first().grossValue).isEqualTo(1500.0)
        assertThat(result.first().netValue).isNull()
        assertThat(result.first().incomeTax).isNull()
        assertThat(result.first().iof).isNull()
        assertThat(result.first().valuationSource).isEqualTo(ValuationSource.MARKET_DATA)
        assertThat(result.first().valuationReferenceDate).isEqualTo(LocalDate.of(2025, 12, 1))
        assertThat(result.first().asset?.logoAsset).containsEntry("id", "asset-logo-petr4")
    }

    @Test
    fun `listed stock holding falls back to legacy issuerName ticker`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val now = Instant.parse("2025-12-02T12:00:00Z")

        val holding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("100"),
                averageCost = BigDecimal("12.00"),
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                ticker = null,
                issuerName = "PETR4",
                name = "Petrobras PN",
                assetType = AssetType.STOCK,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            )
        val financialAccount =
            FinancialAccount(
                id = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                type = FinancialAccountType.INVESTMENT,
                subtype = FinancialAccountSubtype.BROKERAGE,
                name = "Broker",
                createdAt = now,
                updatedAt = now,
            )

        whenever(holdingRepository.findByAccount(accountId)).thenReturn(listOf(holding))
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(financialAccount))
        whenever(issuerRepository.findByIds(emptySet())).thenReturn(emptyList())
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(emptySet())).thenReturn(emptyMap())
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(setOf("PETR4"))).thenReturn(
            mapOf(
                "PETR4" to ListedAssetMarketData(
                    ticker = "PETR4",
                    closePrice = BigDecimal("15.00"),
                    referenceDate = LocalDate.of(2025, 12, 1),
                    logoAssetId = "asset-logo-petr4",
                ),
            ),
        )
        whenever(investmentTransactionRepository.findByHoldingIds(emptyList(), accountId)).thenReturn(emptyList())
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(null)

        val result = service.getAllHoldings(accountId)

        assertThat(result).hasSize(1)
        assertThat(result.first().grossValue).isEqualTo(1500.0)
        assertThat(result.first().valuationSource).isEqualTo(ValuationSource.MARKET_DATA)
        assertThat(result.first().valuationReferenceDate).isEqualTo(LocalDate.of(2025, 12, 1))
        assertThat(result.first().asset?.logoAsset).containsEntry("id", "asset-logo-petr4")
    }

    @Test
    fun `fixed income valuation backfills agreed rate from transactions when holding is missing pre fixed rate`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val now = Instant.parse("2025-12-02T12:00:00Z")
        val serviceWithRealValuation =
            InvestmentQueryService(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                financialAccountRepository = financialAccountRepository,
                issuerRepository = issuerRepository,
                fixedIncomeValuationService = FixedIncomeValuationService { LocalDate.of(2025, 12, 2) },
                fundValuationService = fundValuationService,
                fundMarketDataProvider = fundMarketDataProvider,
                listedAssetMarketDataProvider = listedAssetMarketDataProvider,
                investmentTransactionRepository = investmentTransactionRepository,
                indicatorRateProvider = indicatorRateProvider,
                clock = { LocalDate.of(2025, 12, 2) },
            )

        val holding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("10674.30"),
                averageCost = BigDecimal.ONE,
                indexer = Indexer.IPCA,
                fixedAnnualRate = null,
                issueDate = null,
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                name = "IPCA+ Bond",
                assetType = AssetType.CDB,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            )
        val financialAccount =
            FinancialAccount(
                id = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                type = FinancialAccountType.INVESTMENT,
                subtype = FinancialAccountSubtype.BROKERAGE,
                name = "Broker",
                createdAt = now,
                updatedAt = now,
            )
        val purchaseTransaction =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("10674.30"),
                unitPrice = BigDecimal.ONE,
                grossValue = BigDecimal("10674.30"),
                fixedAnnualRate = BigDecimal("0.088"),
                tradeDate = LocalDate.of(2024, 9, 23),
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )

        whenever(holdingRepository.findByAccount(accountId)).thenReturn(listOf(holding))
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(financialAccount))
        whenever(issuerRepository.findByIds(emptySet())).thenReturn(emptyList())
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(emptySet())).thenReturn(emptyMap())
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(emptySet())).thenReturn(emptyMap())
        whenever(investmentTransactionRepository.findByHoldingIds(listOf(holdingId), accountId)).thenReturn(listOf(purchaseTransaction))
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(mapOf(Indexer.IPCA to BigDecimal("0.0701")))
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(LocalDate.of(2026, 2, 1))
        whenever(indicatorRateProvider.getMonthlySeriesRange(Indexer.IPCA, LocalDate.of(2024, 9, 23), LocalDate.of(2025, 12, 2)))
            .thenReturn(emptyList())

        val result = serviceWithRealValuation.getAllHoldings(accountId)

        assertThat(result).hasSize(1)
        assertThat(result.first().grossValue).isGreaterThan(10674.30)
        assertThat(result.first().netValue).isNotNull()
        assertThat(result.first().valuationSource).isEqualTo(ValuationSource.ESTIMATED)
        assertThat(result.first().valuationReferenceDate).isEqualTo(LocalDate.of(2026, 2, 1))
    }

    @Test
    fun `holding performance returns monthly fixed income history with projection`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val now = Instant.parse("2025-12-02T12:00:00Z")
        val performanceService =
            InvestmentQueryService(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                financialAccountRepository = financialAccountRepository,
                issuerRepository = issuerRepository,
                fixedIncomeValuationService = FixedIncomeValuationService { LocalDate.of(2025, 12, 2) },
                fundValuationService = fundValuationService,
                fundMarketDataProvider = fundMarketDataProvider,
                listedAssetMarketDataProvider = listedAssetMarketDataProvider,
                investmentTransactionRepository = investmentTransactionRepository,
                indicatorRateProvider = indicatorRateProvider,
                clock = { LocalDate.of(2025, 12, 2) },
            )

        val holding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("1000"),
                averageCost = BigDecimal.ONE,
                indexer = Indexer.PRE_FIXADO,
                fixedAnnualRate = BigDecimal("0.10"),
                issueDate = LocalDate.of(2025, 1, 10),
                maturityDate = LocalDate.of(2026, 6, 10),
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                name = "CDB Prefixado",
                assetType = AssetType.CDB,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            )
        val purchaseTransaction =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("1000"),
                unitPrice = BigDecimal.ONE,
                grossValue = BigDecimal("1000"),
                tradeDate = LocalDate.of(2025, 1, 10),
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(assetRepository.findById(assetId)).thenReturn(asset)
        whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(listOf(purchaseTransaction))
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(LocalDate.of(2025, 12, 2))

        val result = performanceService.getHoldingPerformance(
            id = holdingId,
            accountId = accountId,
            resolution = HoldingPerformanceResolution.MONTH,
            includeProjection = true,
        )

        assertThat(result).isNotNull
        assertThat(result?.resolution).isEqualTo(HoldingPerformanceResolution.MONTH)
        assertThat(result?.capabilities?.supportedResolutions).containsExactly(HoldingPerformanceResolution.MONTH)
        assertThat(result?.capabilities?.supportsProjection).isTrue()
        assertThat(result?.summary?.investedPrincipal).isEqualTo(1000.0)
        assertThat(result?.summary?.currentCostBasis).isEqualTo(1000.0)
        assertThat(result?.summary?.grossValue).isGreaterThan(1000.0)
        assertThat(result?.points).isNotEmpty
        assertThat(result?.points?.last()?.isProjected).isTrue()
        assertThat(result?.points?.first()?.isProjected).isFalse()
    }

    @Test
    fun `holding performance month resolution keeps a single point per calendar month`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val now = Instant.parse("2026-03-27T12:00:00Z")
        val performanceService =
            InvestmentQueryService(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                financialAccountRepository = financialAccountRepository,
                issuerRepository = issuerRepository,
                fixedIncomeValuationService = FixedIncomeValuationService { LocalDate.of(2026, 3, 27) },
                fundValuationService = fundValuationService,
                fundMarketDataProvider = fundMarketDataProvider,
                listedAssetMarketDataProvider = listedAssetMarketDataProvider,
                investmentTransactionRepository = investmentTransactionRepository,
                indicatorRateProvider = indicatorRateProvider,
                clock = { LocalDate.of(2026, 3, 27) },
            )

        val holding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("1000"),
                averageCost = BigDecimal.ONE,
                indexer = Indexer.PRE_FIXADO,
                fixedAnnualRate = BigDecimal("0.10"),
                issueDate = LocalDate.of(2026, 1, 10),
                maturityDate = LocalDate.of(2026, 6, 10),
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                name = "CDB Prefixado",
                assetType = AssetType.CDB,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            )
        val purchaseTransaction =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("1000"),
                unitPrice = BigDecimal.ONE,
                grossValue = BigDecimal("1000"),
                tradeDate = LocalDate.of(2026, 1, 10),
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(assetRepository.findById(assetId)).thenReturn(asset)
        whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(listOf(purchaseTransaction))
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(LocalDate.of(2026, 3, 27))

        val result = performanceService.getHoldingPerformance(
            id = holdingId,
            accountId = accountId,
            resolution = HoldingPerformanceResolution.MONTH,
            includeProjection = true,
        )

        val pointDates = result?.points.orEmpty().map { LocalDate.ofInstant(it.timestamp, java.time.ZoneOffset.UTC) }
        val pointMonths = pointDates.map(YearMonth::from)

        assertThat(pointMonths).doesNotHaveDuplicates()
        assertThat(pointDates).contains(LocalDate.of(2026, 3, 27))
        assertThat(pointDates).doesNotContain(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 31))
        assertThat(pointDates).contains(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 10))
    }
}
