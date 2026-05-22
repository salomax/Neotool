package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.domain.AssetCorporateEventSyncItem
import io.github.salomax.neotool.financialdata.domain.EconomicIndicatorPointSyncItem
import io.github.salomax.neotool.financialdata.domain.FundDailyNavSyncItem
import io.github.salomax.neotool.financialdata.domain.ListedAssetDailyPriceSyncItem
import io.github.salomax.neotool.financialdata.repo.AssetCorporateEventRepository
import io.github.salomax.neotool.financialdata.repo.IndicatorValueRepositoryCustom
import io.github.salomax.neotool.financialdata.repo.InvestmentFundRepositoryCustom
import io.github.salomax.neotool.financialdata.repo.ListedAssetRepositoryCustom
import io.github.salomax.neotool.financialdata.service.MarketDataSyncService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class MarketDataSyncServiceTest {
    private val listedAssetRepository: ListedAssetRepositoryCustom = mock()
    private val investmentFundRepository: InvestmentFundRepositoryCustom = mock()
    private val indicatorValueRepository: IndicatorValueRepositoryCustom = mock()
    private val assetCorporateEventRepository: AssetCorporateEventRepository = mock()
    private lateinit var service: MarketDataSyncService

    @BeforeEach
    fun setUp() {
        service =
            MarketDataSyncService(
                listedAssetRepository,
                investmentFundRepository,
                indicatorValueRepository,
                assetCorporateEventRepository,
            )
    }

    @Test
    fun `validateSyncPageSize uses default 250 when first is null`() {
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        whenever(
            listedAssetRepository.getDailyPricesForSync(
                eq(from),
                eq(to),
                eq(null),
                eq(null),
                eq(251),
                eq(null),
            ),
        ).thenReturn(emptyList())

        service.listedAssetDailyPrices(from, to, null, null, null, null)

        verify(listedAssetRepository).getDailyPricesForSync(
            eq(from),
            eq(to),
            eq(null),
            eq(null),
            eq(251),
            eq(null),
        )
    }

    @Test
    fun `validateSyncPageSize throws when first is 0`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                service.listedAssetDailyPrices(
                    from = LocalDate.of(2024, 1, 1),
                    to = LocalDate.of(2024, 1, 31),
                    tickers = null,
                    updatedSince = null,
                    first = 0,
                    after = null,
                )
            }

        assertThat(ex.message).contains("between 1 and 1000")
    }

    @Test
    fun `validateSyncPageSize throws when first is 1001`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                service.listedAssetDailyPrices(
                    from = LocalDate.of(2024, 1, 1),
                    to = LocalDate.of(2024, 1, 31),
                    tickers = null,
                    updatedSince = null,
                    first = 1001,
                    after = null,
                )
            }

        assertThat(ex.message).contains("between 1 and 1000")
    }

    @Test
    fun `validateSyncPageSize accepts boundary 1`() {
        whenever(
            listedAssetRepository.getDailyPricesForSync(any(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull()),
        ).thenReturn(
            emptyList(),
        )

        val result =
            service.listedAssetDailyPrices(
                from = LocalDate.of(2024, 1, 1),
                to = LocalDate.of(2024, 1, 31),
                tickers = null,
                updatedSince = null,
                first = 1,
                after = null,
            )

        assertThat(result.edges).isEmpty()
    }

    @Test
    fun `validateSyncPageSize accepts boundary 1000`() {
        whenever(
            listedAssetRepository.getDailyPricesForSync(any(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull()),
        ).thenReturn(
            emptyList(),
        )

        val result =
            service.listedAssetDailyPrices(
                from = LocalDate.of(2024, 1, 1),
                to = LocalDate.of(2024, 1, 31),
                tickers = null,
                updatedSince = null,
                first = 1000,
                after = null,
            )

        assertThat(result.edges).isEmpty()
    }

    @Test
    fun `listedAssetDailyPrices returns connection with hasMore=false when results fit page`() {
        whenever(
            listedAssetRepository.getDailyPricesForSync(any(), any(), anyOrNull(), anyOrNull(), eq(3), anyOrNull()),
        ).thenReturn(
            listOf(listedAssetSyncItem("AAA3"), listedAssetSyncItem("BBB4")),
        )

        val result =
            service.listedAssetDailyPrices(
                from = LocalDate.of(2024, 1, 1),
                to = LocalDate.of(2024, 1, 31),
                tickers = null,
                updatedSince = null,
                first = 2,
                after = null,
            )

        assertThat(result.pageInfo.hasNextPage).isFalse()
        assertThat(result.edges).hasSize(2)
    }

    @Test
    fun `listedAssetDailyPrices returns connection with hasMore=true when extra item returned`() {
        whenever(
            listedAssetRepository.getDailyPricesForSync(any(), any(), anyOrNull(), anyOrNull(), eq(3), anyOrNull()),
        ).thenReturn(
            listOf(
                listedAssetSyncItem("AAA3"),
                listedAssetSyncItem("BBB4"),
                listedAssetSyncItem("CCC5"),
            ),
        )

        val result =
            service.listedAssetDailyPrices(
                from = LocalDate.of(2024, 1, 1),
                to = LocalDate.of(2024, 1, 31),
                tickers = null,
                updatedSince = null,
                first = 2,
                after = null,
            )

        assertThat(result.pageInfo.hasNextPage).isTrue()
        assertThat(result.edges).hasSize(2)
    }

    @Test
    fun `fundDailyNavs delegates to investmentFundRepository`() {
        whenever(
            investmentFundRepository.getDailyNavsForSync(any(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull()),
        ).thenReturn(
            emptyList(),
        )
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        val cnpjs = listOf("11222333000181")
        val updatedSince = Instant.parse("2024-01-01T00:00:00Z")

        service.fundDailyNavs(from, to, cnpjs, updatedSince, 10, null)

        verify(investmentFundRepository).getDailyNavsForSync(
            eq(from),
            eq(to),
            eq(cnpjs),
            eq(updatedSince),
            eq(11),
            eq(null),
        )
    }

    @Test
    fun `economicIndicatorVariationPoints delegates to indicatorValueRepository`() {
        whenever(
            indicatorValueRepository.findPointsForSync(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                anyOrNull(),
            ),
        )
            .thenReturn(emptyList())
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        val indicators = listOf("IPCA")
        val variations = listOf("MONTHLY")
        val updatedSince = Instant.parse("2024-01-01T00:00:00Z")

        service.economicIndicatorVariationPoints(from, to, indicators, variations, updatedSince, 10, null)

        verify(indicatorValueRepository).findPointsForSync(
            eq(from),
            eq(to),
            eq(indicators),
            eq(variations),
            eq(updatedSince),
            eq(11),
            eq(null),
        )
    }

    @Test
    fun `assetCorporateEvents delegates to assetCorporateEventRepository`() {
        whenever(
            assetCorporateEventRepository.findStockEventsForSync(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any(),
                anyOrNull(),
            ),
        )
            .thenReturn(emptyList())
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        val tickers = listOf("PETR4")
        val isins = listOf("BRPETRACNPR6")
        val updatedSince = Instant.parse("2024-01-01T00:00:00Z")

        service.assetCorporateEvents(from, to, tickers, isins, updatedSince, 10, null)

        verify(assetCorporateEventRepository).findStockEventsForSync(
            eq(from),
            eq(to),
            eq(tickers),
            eq(isins),
            eq(updatedSince),
            eq(11),
            eq(null),
        )
    }

    private fun listedAssetSyncItem(ticker: String): ListedAssetDailyPriceSyncItem =
        ListedAssetDailyPriceSyncItem(
            assetId = UUID.randomUUID(),
            ticker = ticker,
            isinCode = "BR${ticker}ACNOR0",
            date = LocalDate.of(2024, 1, 1),
            openPrice = BigDecimal("10.00"),
            highPrice = BigDecimal("11.00"),
            lowPrice = BigDecimal("9.00"),
            closePrice = BigDecimal("10.50"),
            avgPrice = BigDecimal("10.25"),
            volume = BigDecimal("1000.00"),
            trades = 12,
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

    @Suppress("unused")
    private fun fundSyncItem(cnpj: String): FundDailyNavSyncItem =
        FundDailyNavSyncItem(
            assetId = UUID.randomUUID(),
            cnpj = cnpj,
            date = LocalDate.of(2024, 1, 1),
            closePrice = BigDecimal("1.02"),
            totalNetAssets = BigDecimal("1000000"),
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

    @Suppress("unused")
    private fun indicatorSyncItem(): EconomicIndicatorPointSyncItem =
        EconomicIndicatorPointSyncItem(
            variationId = UUID.randomUUID(),
            indicator = "IPCA",
            variation = "MONTHLY",
            unit = "%",
            referenceDate = LocalDate.of(2024, 1, 1),
            publicationDate = LocalDate.of(2024, 1, 15),
            value = BigDecimal("0.55"),
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

    @Suppress("unused")
    private fun corporateEventSyncItem(): AssetCorporateEventSyncItem =
        AssetCorporateEventSyncItem(
            id = UUID.randomUUID(),
            assetId = UUID.randomUUID(),
            ticker = "PETR4",
            isinCode = "BRPETRACNPR6",
            eventType = "SPLIT",
            factor = BigDecimal("2.0"),
            priceUnit = null,
            tradingStart = null,
            tradingEnd = null,
            subscriptionDate = null,
            lastDatePrior = LocalDate.of(2024, 1, 1),
            approvedOn = LocalDate.of(2024, 1, 2),
            remarks = null,
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )
}
