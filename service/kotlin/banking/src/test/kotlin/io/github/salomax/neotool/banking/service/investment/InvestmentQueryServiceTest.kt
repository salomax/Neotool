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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
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

        val holding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("10674.30"),
                averageCost = BigDecimal.ONE,
                indexer = Indexer.IPCA,
                preFixedRate = null,
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
                agreedRate = BigDecimal("0.088"),
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
        whenever(fixedIncomeValuationService.estimate(any(), any(), anyOrNull(), any())).thenReturn(
            HoldingValuationResult(
                grossValue = BigDecimal("12963.04"),
                incomeTax = BigDecimal("400.53"),
                iof = BigDecimal.ZERO,
                netValue = BigDecimal("12562.51"),
                valuationSource = ValuationSource.ESTIMATED,
                valuationReferenceDate = LocalDate.of(2026, 2, 1),
            ),
        )

        val result = service.getAllHoldings(accountId)

        val holdingCaptor = argumentCaptor<Holding>()
        verify(fixedIncomeValuationService).estimate(holdingCaptor.capture(), any(), anyOrNull(), any())
        assertThat(holdingCaptor.firstValue.preFixedRate).isEqualByComparingTo("0.088")
        assertThat(holdingCaptor.firstValue.issueDate).isEqualTo(LocalDate.of(2024, 9, 23))
        assertThat(result).hasSize(1)
        assertThat(result.first().grossValue).isEqualTo(12963.04)
        assertThat(result.first().netValue).isEqualTo(12562.51)
    }
}
