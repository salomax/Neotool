package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.financialdata.FundMarketDataProvider
import io.github.salomax.neotool.banking.client.investment.financialdata.IndicatorRateProvider
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountSubtype
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.FundMarketNav
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.HoldingPricingMode
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentHoldingAssetClass
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketData
import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketDataProvider
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.graphql.investment.HoldingPerformanceResolution
import io.github.salomax.neotool.banking.model.dto.investment.FundValuationResult
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.issuer.IssuerRepository
import io.github.salomax.neotool.banking.repo.portfolio.AssetDailyPriceRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
    private val assetDailyPriceRepository: AssetDailyPriceRepository = mock()
    private val investmentTransactionRepository: InvestmentTransactionRepository = mock()
    private val indicatorRateProvider: IndicatorRateProvider = mock()
    private val transactionRepository: TransactionRepository = mock()

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
            assetDailyPriceRepository = assetDailyPriceRepository,
            investmentTransactionRepository = investmentTransactionRepository,
            indicatorRateProvider = indicatorRateProvider,
            transactionRepository = transactionRepository,
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

        whenever(holdingRepository.findOpenByAccount(accountId)).thenReturn(listOf(holding))
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(financialAccount))
        whenever(issuerRepository.findByIds(emptySet())).thenReturn(emptyList())
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(emptySet())).thenReturn(emptyMap())
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(setOf("PETR4"))).thenReturn(
            mapOf(
                "PETR4" to
                    ListedAssetMarketData(
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

        val result = service.getHoldings(financialAccountId = null, accountId = accountId)

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
    fun `market-data fund holding uses estimated fund valuation in holdings query`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val now = Instant.parse("2025-12-02T12:00:00Z")
        val fundCnpj = "12345678000199"

        val holding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("100"),
                averageCost = BigDecimal("10.00"),
                pricingMode = HoldingPricingMode.MARKET_DATA,
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                name = "Fund Test",
                assetType = AssetType.MUTUAL_FUND,
                fundCnpj = fundCnpj,
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
        val purchase =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("100"),
                unitPrice = BigDecimal("10.00"),
                grossValue = BigDecimal("1000.00"),
                tradeDate = LocalDate.of(2025, 1, 1),
                source = InvestmentSource.MANUAL,
            )
        val snapshot =
            FundMarketSnapshot(
                fundCnpj = fundCnpj,
                assetType = "FUNDO DE RENDA FIXA",
                taxProfile = FundTaxProfile.UNKNOWN,
                latestNav = FundMarketNav(LocalDate.of(2025, 12, 1), BigDecimal("10.50")),
            )

        whenever(holdingRepository.findOpenByAccount(accountId)).thenReturn(listOf(holding))
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(financialAccount))
        whenever(issuerRepository.findByIds(emptySet())).thenReturn(emptyList())
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))).thenReturn(mapOf(fundCnpj to snapshot))
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(emptySet())).thenReturn(emptyMap())
        whenever(
            investmentTransactionRepository.findByHoldingIds(listOf(holdingId), accountId),
        ).thenReturn(listOf(purchase))
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(null)
        whenever(fundValuationService.estimate(eq(holding), eq(snapshot), eq(listOf(purchase)))).thenReturn(
            FundValuationResult(
                grossValue = BigDecimal("1050.00"),
                incomeTax = BigDecimal("0.00"),
                iof = BigDecimal("0.00"),
                netValue = BigDecimal("1050.00"),
                valuationSource = ValuationSource.MARKET_DATA,
                valuationReferenceDate = LocalDate.of(2025, 12, 1),
            ),
        )

        val result = service.getHoldings(financialAccountId = null, accountId = accountId)

        assertThat(result).hasSize(1)
        assertThat(result.first().grossValue).isEqualTo(1050.0)
        assertThat(result.first().netValue).isEqualTo(1050.0)
        assertThat(result.first().incomeTax).isEqualTo(0.0)
        assertThat(result.first().iof).isEqualTo(0.0)
        assertThat(result.first().valuationSource).isEqualTo(ValuationSource.MARKET_DATA)
        assertThat(result.first().valuationReferenceDate).isEqualTo(LocalDate.of(2025, 12, 1))
    }

    @Test
    fun `market-data fund holding derives quantity and valuation from system withholding tax transactions`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val now = Instant.parse("2025-12-02T12:00:00Z")
        val fundCnpj = "12345678000199"

        val staleHolding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("100"),
                averageCost = BigDecimal("10.00"),
                pricingMode = HoldingPricingMode.MARKET_DATA,
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                name = "Fund Test",
                assetType = AssetType.MUTUAL_FUND,
                fundCnpj = fundCnpj,
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
        val purchase =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("100"),
                unitPrice = BigDecimal("10.00"),
                grossValue = BigDecimal("1000.00"),
                tradeDate = LocalDate.of(2025, 1, 1),
                source = InvestmentSource.MANUAL,
                createdAt = now.minusSeconds(60),
                updatedAt = now.minusSeconds(60),
            )
        val withholdingTax =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.OUTFLOW,
                type = InvestmentTransactionType.WITHHOLDING_TAX,
                quantity = BigDecimal("5"),
                unitPrice = BigDecimal("10.50"),
                grossValue = BigDecimal("52.50"),
                netValue = BigDecimal("52.50"),
                tradeDate = LocalDate.of(2025, 5, 30),
                source = InvestmentSource.SYSTEM,
                createdAt = now,
                updatedAt = now,
            )
        val snapshot =
            FundMarketSnapshot(
                fundCnpj = fundCnpj,
                assetType = "FUNDO DE RENDA FIXA",
                taxProfile = FundTaxProfile.COME_COTAS_LONG,
                latestNav = FundMarketNav(LocalDate.of(2025, 12, 1), BigDecimal("10.50")),
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(staleHolding)
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(financialAccount))
        whenever(issuerRepository.findByIds(emptySet())).thenReturn(emptyList())
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(assetRepository.findById(assetId)).thenReturn(asset)
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))).thenReturn(mapOf(fundCnpj to snapshot))
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(emptySet())).thenReturn(emptyMap())
        whenever(investmentTransactionRepository.findByHolding(holdingId, accountId))
            .thenReturn(listOf(purchase, withholdingTax))
        whenever(investmentTransactionRepository.findByHoldingIds(listOf(holdingId), accountId))
            .thenReturn(listOf(purchase, withholdingTax))
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(null)
        whenever(fundValuationService.estimate(any(), eq(snapshot), eq(listOf(purchase, withholdingTax))))
            .thenAnswer { invocation ->
                val effectiveHolding = invocation.arguments[0] as Holding
                val grossValue = effectiveHolding.quantity.multiply(BigDecimal("10.50"))
                FundValuationResult(
                    grossValue = grossValue,
                    incomeTax = BigDecimal("0.00"),
                    iof = BigDecimal("0.00"),
                    netValue = grossValue,
                    valuationSource = ValuationSource.MARKET_DATA,
                    valuationReferenceDate = LocalDate.of(2025, 12, 1),
                )
            }

        val result = service.getHolding(holdingId, accountId)

        assertThat(result).isNotNull
        assertThat(result!!.quantity).isEqualTo(95.0)
        assertThat(result.averageCost).isEqualTo(10.0)
        assertThat(result.grossValue).isEqualTo(997.5)
        assertThat(result.netValue).isEqualTo(997.5)
    }

    @Test
    fun `getHoldings filters by aggregate asset class when provided`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val fixedIncomeAssetId = UUID.randomUUID()
        val variableIncomeAssetId = UUID.randomUUID()
        val now = Instant.parse("2025-12-02T12:00:00Z")

        val fixedIncomeHolding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = fixedIncomeAssetId,
                quantity = BigDecimal("10"),
                averageCost = BigDecimal("100.00"),
                createdAt = now,
                updatedAt = now,
            )
        val variableIncomeHolding =
            Holding(
                id = UUID.randomUUID(),
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = variableIncomeAssetId,
                quantity = BigDecimal("5"),
                averageCost = BigDecimal("20.00"),
                createdAt = now,
                updatedAt = now,
            )
        val fixedIncomeAsset =
            Asset(
                id = fixedIncomeAssetId,
                name = "CDB Banco X",
                assetType = AssetType.CDB,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            )
        val variableIncomeAsset =
            Asset(
                id = variableIncomeAssetId,
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

        whenever(
            holdingRepository.findOpenByAccount(accountId),
        ).thenReturn(listOf(fixedIncomeHolding, variableIncomeHolding))
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(financialAccount))
        whenever(issuerRepository.findByIds(emptySet())).thenReturn(emptyList())
        whenever(assetRepository.findByIds(setOf(fixedIncomeAssetId, variableIncomeAssetId))).thenReturn(
            listOf(fixedIncomeAsset, variableIncomeAsset),
        )
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(emptySet())).thenReturn(emptyMap())
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(emptySet())).thenReturn(emptyMap())
        whenever(
            investmentTransactionRepository.findByHoldingIds(listOf(fixedIncomeHolding.id!!), accountId),
        ).thenReturn(emptyList())
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(null)

        val result =
            service.getHoldings(
                financialAccountId = null,
                accountId = accountId,
                assetClass = InvestmentHoldingAssetClass.FIXED_INCOME,
            )

        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo(fixedIncomeHolding.id)
        assertThat(result.first().asset?.assetType).isEqualTo(AssetType.CDB)
    }

    @Test
    fun `investment holding performance computes xirr total return and dividends for equity holdings`() {
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
                quantity = BigDecimal("100"),
                averageCost = BigDecimal("10.00"),
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
        val purchase =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("100"),
                unitPrice = BigDecimal("10.00"),
                grossValue = BigDecimal("1000.00"),
                tradeDate = LocalDate.of(2025, 1, 1),
                source = InvestmentSource.MANUAL,
            )
        val dividend =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.DIVIDEND,
                grossValue = BigDecimal("50.00"),
                tradeDate = LocalDate.of(2025, 6, 1),
                source = InvestmentSource.MANUAL,
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(financialAccount))
        whenever(issuerRepository.findByIds(emptySet())).thenReturn(emptyList())
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(assetRepository.findById(assetId)).thenReturn(asset)
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(emptySet())).thenReturn(emptyMap())
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(setOf("PETR4"))).thenReturn(
            mapOf(
                "PETR4" to
                    ListedAssetMarketData(
                        ticker = "PETR4",
                        closePrice = BigDecimal("12.00"),
                        referenceDate = LocalDate.of(2025, 12, 1),
                        logoAssetId = null,
                    ),
            ),
        )
        whenever(
            investmentTransactionRepository.findByHolding(holdingId, accountId),
        ).thenReturn(listOf(purchase, dividend))
        whenever(
            investmentTransactionRepository.findByHoldingIds(listOf(holdingId), accountId),
        ).thenReturn(listOf(purchase, dividend))
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(null)

        val result = service.getInvestmentHoldingPerformance(holdingId, accountId)

        assertThat(result).isNotNull
        assertThat(result?.xirrSince).isEqualTo(LocalDate.of(2025, 1, 1))
        assertThat(result?.dividendsReceived).isEqualTo(50.0)
        assertThat(result?.totalReturnAbsolute).isEqualTo(250.0)
        assertThat(result?.totalReturn).isEqualTo(0.25)
        assertThat(result?.xirr).isNotNull
        assertThat(result?.xirr).isCloseTo(0.28, org.assertj.core.data.Offset.offset(0.03))
    }

    @Test
    fun `investment holding performance keeps xirr null when history is shorter than thirty days`() {
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
                quantity = BigDecimal("10"),
                averageCost = BigDecimal("100.00"),
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                ticker = "VALE3",
                name = "Vale ON",
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
        val purchase =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("10"),
                unitPrice = BigDecimal("100.00"),
                grossValue = BigDecimal("1000.00"),
                tradeDate = LocalDate.of(2025, 11, 20),
                source = InvestmentSource.MANUAL,
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(financialAccount))
        whenever(issuerRepository.findByIds(emptySet())).thenReturn(emptyList())
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(assetRepository.findById(assetId)).thenReturn(asset)
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(emptySet())).thenReturn(emptyMap())
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(setOf("VALE3"))).thenReturn(
            mapOf(
                "VALE3" to
                    ListedAssetMarketData(
                        ticker = "VALE3",
                        closePrice = BigDecimal("120.00"),
                        referenceDate = LocalDate.of(2025, 12, 1),
                        logoAssetId = null,
                    ),
            ),
        )
        whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(listOf(purchase))
        whenever(
            investmentTransactionRepository.findByHoldingIds(listOf(holdingId), accountId),
        ).thenReturn(listOf(purchase))
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(null)

        val result = service.getInvestmentHoldingPerformance(holdingId, accountId)

        assertThat(result).isNotNull
        assertThat(result?.xirrSince).isEqualTo(LocalDate.of(2025, 11, 20))
        assertThat(result?.xirr).isNull()
        assertThat(result?.totalReturnAbsolute).isEqualTo(200.0)
        assertThat(result?.totalReturn).isEqualTo(0.2)
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

        whenever(holdingRepository.findOpenByAccount(accountId)).thenReturn(listOf(holding))
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(financialAccount))
        whenever(issuerRepository.findByIds(emptySet())).thenReturn(emptyList())
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(emptySet())).thenReturn(emptyMap())
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(setOf("PETR4"))).thenReturn(
            mapOf(
                "PETR4" to
                    ListedAssetMarketData(
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

        val result = service.getHoldings(financialAccountId = null, accountId = accountId)

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
                assetDailyPriceRepository = assetDailyPriceRepository,
                investmentTransactionRepository = investmentTransactionRepository,
                indicatorRateProvider = indicatorRateProvider,
                transactionRepository = transactionRepository,
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

        whenever(holdingRepository.findOpenByAccount(accountId)).thenReturn(listOf(holding))
        whenever(financialAccountRepository.findByAccountId(accountId)).thenReturn(listOf(financialAccount))
        whenever(issuerRepository.findByIds(emptySet())).thenReturn(emptyList())
        whenever(assetRepository.findByIds(setOf(assetId))).thenReturn(listOf(asset))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(emptySet())).thenReturn(emptyMap())
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(emptySet())).thenReturn(emptyMap())
        whenever(
            investmentTransactionRepository.findByHoldingIds(listOf(holdingId), accountId),
        ).thenReturn(listOf(purchaseTransaction))
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(mapOf(Indexer.IPCA to BigDecimal("0.0701")))
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(LocalDate.of(2026, 2, 1))
        whenever(
            indicatorRateProvider.getMonthlySeriesRange(
                Indexer.IPCA,
                LocalDate.of(2024, 9, 23),
                LocalDate.of(2025, 12, 2),
            ),
        )
            .thenReturn(emptyList())

        val result = serviceWithRealValuation.getHoldings(financialAccountId = null, accountId = accountId)

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
                assetDailyPriceRepository = assetDailyPriceRepository,
                investmentTransactionRepository = investmentTransactionRepository,
                indicatorRateProvider = indicatorRateProvider,
                transactionRepository = transactionRepository,
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
        whenever(
            investmentTransactionRepository.findByHolding(holdingId, accountId),
        ).thenReturn(listOf(purchaseTransaction))
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(LocalDate.of(2025, 12, 2))

        val result =
            performanceService.getHoldingPerformance(
                id = holdingId,
                accountId = accountId,
                resolution = HoldingPerformanceResolution.MONTH,
                fromDate = null,
                toDate = null,
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
                assetDailyPriceRepository = assetDailyPriceRepository,
                investmentTransactionRepository = investmentTransactionRepository,
                indicatorRateProvider = indicatorRateProvider,
                transactionRepository = transactionRepository,
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
        whenever(
            investmentTransactionRepository.findByHolding(holdingId, accountId),
        ).thenReturn(listOf(purchaseTransaction))
        whenever(indicatorRateProvider.getLatestRates()).thenReturn(emptyMap())
        whenever(indicatorRateProvider.getReferenceDate()).thenReturn(LocalDate.of(2026, 3, 27))

        val result =
            performanceService.getHoldingPerformance(
                id = holdingId,
                accountId = accountId,
                resolution = HoldingPerformanceResolution.MONTH,
                fromDate = null,
                toDate = null,
                includeProjection = true,
            )

        val pointDates = result?.points.orEmpty().map { LocalDate.ofInstant(it.timestamp, java.time.ZoneOffset.UTC) }
        val pointMonths = pointDates.map(YearMonth::from)

        assertThat(pointMonths).doesNotHaveDuplicates()
        assertThat(pointDates).contains(LocalDate.of(2026, 3, 27))
        assertThat(pointDates).doesNotContain(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 31))
        assertThat(pointDates).contains(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31), LocalDate.of(2026, 6, 10))
    }

    @Test
    fun `holding performance returns mutual fund series without projection capability`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val now = Instant.parse("2025-02-15T12:00:00Z")
        val fundCnpj = "12345678000199"
        val performanceService =
            InvestmentQueryService(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                financialAccountRepository = financialAccountRepository,
                issuerRepository = issuerRepository,
                fixedIncomeValuationService = fixedIncomeValuationService,
                fundValuationService = FundValuationService(),
                fundMarketDataProvider = fundMarketDataProvider,
                listedAssetMarketDataProvider = listedAssetMarketDataProvider,
                assetDailyPriceRepository = assetDailyPriceRepository,
                investmentTransactionRepository = investmentTransactionRepository,
                indicatorRateProvider = indicatorRateProvider,
                transactionRepository = transactionRepository,
                clock = { LocalDate.of(2025, 2, 28) },
            )

        val holding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("100"),
                averageCost = BigDecimal("1.00"),
                pricingMode = HoldingPricingMode.MARKET_DATA,
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                name = "Fundo Teste",
                assetType = AssetType.MUTUAL_FUND,
                fundCnpj = fundCnpj,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            )
        val purchase =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("100"),
                unitPrice = BigDecimal("1.00"),
                grossValue = BigDecimal("100.00"),
                tradeDate = LocalDate.of(2025, 1, 10),
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
        val snapshot =
            FundMarketSnapshot(
                fundCnpj = fundCnpj,
                assetType = "MUTUAL_FUND",
                taxProfile = FundTaxProfile.UNKNOWN,
                latestNav = FundMarketNav(LocalDate.of(2025, 2, 28), BigDecimal("1.20")),
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(assetRepository.findById(assetId)).thenReturn(asset)
        whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(listOf(purchase))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))).thenReturn(mapOf(fundCnpj to snapshot))
        whenever(fundMarketDataProvider.getNavSeries(eq(fundCnpj), any(), any())).thenReturn(
            listOf(
                LocalDate.of(2025, 1, 10) to BigDecimal("1.00"),
                LocalDate.of(2025, 2, 28) to BigDecimal("1.20"),
            ),
        )

        val result =
            performanceService.getHoldingPerformance(
                id = holdingId,
                accountId = accountId,
                resolution = HoldingPerformanceResolution.MONTH,
                fromDate = null,
                toDate = null,
                includeProjection = true,
            )

        assertThat(result).isNotNull
        assertThat(result!!.assetType).isEqualTo(AssetType.MUTUAL_FUND)
        assertThat(result.capabilities.supportsProjection).isFalse()
        assertThat(result.points).isNotEmpty
        assertThat(result.points.none { it.isProjected }).isTrue()
        assertThat(result.valuationSource).isEqualTo(ValuationSource.MARKET_DATA)
    }

    @Test
    fun `holding performance returns daily mutual fund series clipped to requested window`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val now = Instant.parse("2025-02-15T12:00:00Z")
        val fundCnpj = "12345678000199"
        val performanceService =
            InvestmentQueryService(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                financialAccountRepository = financialAccountRepository,
                issuerRepository = issuerRepository,
                fixedIncomeValuationService = fixedIncomeValuationService,
                fundValuationService = FundValuationService(),
                fundMarketDataProvider = fundMarketDataProvider,
                listedAssetMarketDataProvider = listedAssetMarketDataProvider,
                assetDailyPriceRepository = assetDailyPriceRepository,
                investmentTransactionRepository = investmentTransactionRepository,
                indicatorRateProvider = indicatorRateProvider,
                transactionRepository = transactionRepository,
                clock = { LocalDate.of(2025, 1, 13) },
            )

        val holding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("100"),
                averageCost = BigDecimal("1.00"),
                pricingMode = HoldingPricingMode.MARKET_DATA,
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                name = "Fundo Teste",
                assetType = AssetType.MUTUAL_FUND,
                fundCnpj = fundCnpj,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            )
        val purchase =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("100"),
                unitPrice = BigDecimal("1.00"),
                grossValue = BigDecimal("100.00"),
                tradeDate = LocalDate.of(2025, 1, 10),
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
        val snapshot =
            FundMarketSnapshot(
                fundCnpj = fundCnpj,
                assetType = "MUTUAL_FUND",
                taxProfile = FundTaxProfile.UNKNOWN,
                latestNav = FundMarketNav(LocalDate.of(2025, 1, 13), BigDecimal("1.04")),
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(assetRepository.findById(assetId)).thenReturn(asset)
        whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(listOf(purchase))
        whenever(fundMarketDataProvider.getSnapshotsByCnpj(setOf(fundCnpj))).thenReturn(mapOf(fundCnpj to snapshot))
        whenever(fundMarketDataProvider.getNavSeries(eq(fundCnpj), any(), any())).thenReturn(
            listOf(
                LocalDate.of(2025, 1, 10) to BigDecimal("1.00"),
                LocalDate.of(2025, 1, 11) to BigDecimal("1.02"),
                LocalDate.of(2025, 1, 13) to BigDecimal("1.04"),
            ),
        )

        val result =
            performanceService.getHoldingPerformance(
                id = holdingId,
                accountId = accountId,
                resolution = HoldingPerformanceResolution.DAY,
                fromDate = LocalDate.of(2025, 1, 11),
                toDate = LocalDate.of(2025, 1, 13),
                includeProjection = false,
            )

        assertThat(result).isNotNull
        assertThat(result!!.resolution).isEqualTo(HoldingPerformanceResolution.DAY)
        assertThat(result.capabilities.supportedResolutions)
            .containsExactly(HoldingPerformanceResolution.DAY, HoldingPerformanceResolution.MONTH)
        assertThat(result.points.map { LocalDate.ofInstant(it.timestamp, java.time.ZoneOffset.UTC) })
            .containsExactly(LocalDate.of(2025, 1, 11), LocalDate.of(2025, 1, 13))
        assertThat(result.points.mapNotNull { it.grossValue }).containsExactly(102.0, 104.0)
    }

    @Test
    fun `holding performance returns monthly listed stock series marked to historical market prices`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val now = Instant.parse("2025-03-15T12:00:00Z")
        val performanceService =
            InvestmentQueryService(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                financialAccountRepository = financialAccountRepository,
                issuerRepository = issuerRepository,
                fixedIncomeValuationService = fixedIncomeValuationService,
                fundValuationService = fundValuationService,
                fundMarketDataProvider = fundMarketDataProvider,
                listedAssetMarketDataProvider = listedAssetMarketDataProvider,
                assetDailyPriceRepository = assetDailyPriceRepository,
                investmentTransactionRepository = investmentTransactionRepository,
                indicatorRateProvider = indicatorRateProvider,
                transactionRepository = transactionRepository,
                clock = { LocalDate.of(2025, 3, 31) },
            )

        val holding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("100"),
                averageCost = BigDecimal("28.88"),
                grossValue = BigDecimal("4815"),
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                ticker = "PETR4",
                name = "Listed",
                assetType = AssetType.STOCK,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            )
        val purchase =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("100"),
                unitPrice = BigDecimal("28.88"),
                grossValue = BigDecimal("2888"),
                tradeDate = LocalDate.of(2025, 1, 15),
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(assetRepository.findById(assetId)).thenReturn(asset)
        whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(listOf(purchase))
        whenever(assetDailyPriceRepository.findLatestBefore(assetId, LocalDate.of(2025, 1, 15))).thenReturn(null)
        whenever(
            assetDailyPriceRepository.findByAssetAndDateRange(
                assetId,
                LocalDate.of(2025, 1, 15),
                LocalDate.of(2025, 3, 31),
            ),
        ).thenReturn(
            listOf(
                InvestmentAssetDailyPrice(
                    assetId = assetId,
                    priceDate = LocalDate.of(2025, 1, 31),
                    closePrice = BigDecimal("31.10"),
                    source = "MARKET_DATA",
                ),
                InvestmentAssetDailyPrice(
                    assetId = assetId,
                    priceDate = LocalDate.of(2025, 2, 28),
                    closePrice = BigDecimal("26.75"),
                    source = "MARKET_DATA",
                ),
                InvestmentAssetDailyPrice(
                    assetId = assetId,
                    priceDate = LocalDate.of(2025, 3, 28),
                    closePrice = BigDecimal("48.15"),
                    source = "MARKET_DATA",
                ),
            ),
        )
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(setOf("PETR4"))).thenReturn(
            mapOf(
                "PETR4" to
                    ListedAssetMarketData(
                        ticker = "PETR4",
                        closePrice = BigDecimal("48.15"),
                        referenceDate = LocalDate.of(2025, 3, 28),
                    ),
            ),
        )

        val result =
            performanceService.getHoldingPerformance(
                id = holdingId,
                accountId = accountId,
                resolution = HoldingPerformanceResolution.MONTH,
                fromDate = null,
                toDate = null,
                includeProjection = false,
            )

        assertThat(result).isNotNull
        assertThat(result!!.assetType).isEqualTo(AssetType.STOCK)
        assertThat(result.points.size).isGreaterThanOrEqualTo(2)
        assertThat(result.points.first().grossValue).isEqualTo(2888.0)
        assertThat(result.points[1].grossValue).isEqualTo(2675.0)
        assertThat(result.points.last().grossValue).isEqualTo(4815.0)
        assertThat(result.valuationSource).isEqualTo(ValuationSource.MARKET_DATA)
    }

    @Test
    fun `holding performance returns daily listed stock series clipped to requested window`() {
        val accountId = UUID.randomUUID()
        val financialAccountId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val holdingId = UUID.randomUUID()
        val now = Instant.parse("2025-01-20T12:00:00Z")
        val performanceService =
            InvestmentQueryService(
                holdingRepository = holdingRepository,
                assetRepository = assetRepository,
                financialAccountRepository = financialAccountRepository,
                issuerRepository = issuerRepository,
                fixedIncomeValuationService = fixedIncomeValuationService,
                fundValuationService = fundValuationService,
                fundMarketDataProvider = fundMarketDataProvider,
                listedAssetMarketDataProvider = listedAssetMarketDataProvider,
                assetDailyPriceRepository = assetDailyPriceRepository,
                investmentTransactionRepository = investmentTransactionRepository,
                indicatorRateProvider = indicatorRateProvider,
                transactionRepository = transactionRepository,
                clock = { LocalDate.of(2025, 1, 20) },
            )

        val holding =
            Holding(
                id = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                assetId = assetId,
                quantity = BigDecimal("100"),
                averageCost = BigDecimal("10.00"),
                grossValue = BigDecimal("1180"),
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )
        val asset =
            Asset(
                id = assetId,
                ticker = "PETR4",
                name = "Listed",
                assetType = AssetType.STOCK,
                currencyCode = "BRL",
                createdAt = now,
                updatedAt = now,
            )
        val purchase =
            InvestmentTransaction(
                id = UUID.randomUUID(),
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                createdByUserId = UUID.randomUUID(),
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = BigDecimal("100"),
                unitPrice = BigDecimal("10.00"),
                grossValue = BigDecimal("1000"),
                tradeDate = LocalDate.of(2025, 1, 15),
                source = InvestmentSource.MANUAL,
                createdAt = now,
                updatedAt = now,
            )

        whenever(holdingRepository.findById(holdingId, accountId)).thenReturn(holding)
        whenever(assetRepository.findById(assetId)).thenReturn(asset)
        whenever(investmentTransactionRepository.findByHolding(holdingId, accountId)).thenReturn(listOf(purchase))
        whenever(assetDailyPriceRepository.findLatestBefore(assetId, LocalDate.of(2025, 1, 16))).thenReturn(null)
        whenever(
            assetDailyPriceRepository.findByAssetAndDateRange(
                assetId,
                LocalDate.of(2025, 1, 16),
                LocalDate.of(2025, 1, 20),
            ),
        ).thenReturn(
            listOf(
                InvestmentAssetDailyPrice(
                    assetId = assetId,
                    priceDate = LocalDate.of(2025, 1, 16),
                    closePrice = BigDecimal("10.50"),
                    source = "MARKET_DATA",
                ),
                InvestmentAssetDailyPrice(
                    assetId = assetId,
                    priceDate = LocalDate.of(2025, 1, 17),
                    closePrice = BigDecimal("11.80"),
                    source = "MARKET_DATA",
                ),
            ),
        )
        whenever(listedAssetMarketDataProvider.getAssetsByTicker(setOf("PETR4"))).thenReturn(
            mapOf(
                "PETR4" to
                    ListedAssetMarketData(
                        ticker = "PETR4",
                        closePrice = BigDecimal("11.80"),
                        referenceDate = LocalDate.of(2025, 1, 17),
                    ),
            ),
        )

        val result =
            performanceService.getHoldingPerformance(
                id = holdingId,
                accountId = accountId,
                resolution = HoldingPerformanceResolution.DAY,
                fromDate = LocalDate.of(2025, 1, 16),
                toDate = LocalDate.of(2025, 1, 20),
                includeProjection = false,
            )

        assertThat(result).isNotNull
        assertThat(result!!.resolution).isEqualTo(HoldingPerformanceResolution.DAY)
        assertThat(result.capabilities.supportedResolutions)
            .containsExactly(HoldingPerformanceResolution.DAY, HoldingPerformanceResolution.MONTH)
        assertThat(result.points.map { LocalDate.ofInstant(it.timestamp, java.time.ZoneOffset.UTC) })
            .containsExactly(LocalDate.of(2025, 1, 16), LocalDate.of(2025, 1, 17), LocalDate.of(2025, 1, 20))
        assertThat(result.points.mapNotNull { it.grossValue }).containsExactly(1050.0, 1180.0, 1180.0)
    }
}
