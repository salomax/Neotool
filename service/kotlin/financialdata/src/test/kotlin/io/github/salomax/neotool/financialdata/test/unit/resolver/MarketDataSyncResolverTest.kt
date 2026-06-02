package io.github.salomax.neotool.financialdata.test.unit.resolver

import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.financialdata.domain.AssetCorporateEventSyncItem
import io.github.salomax.neotool.financialdata.domain.EconomicIndicatorPointSyncItem
import io.github.salomax.neotool.financialdata.domain.FundDailyNavSyncItem
import io.github.salomax.neotool.financialdata.domain.ListedAssetDailyPriceSyncItem
import io.github.salomax.neotool.financialdata.graphql.resolver.MarketDataSyncResolver
import io.github.salomax.neotool.financialdata.service.MarketDataSyncService
import org.assertj.core.api.Assertions.assertThat
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

class MarketDataSyncResolverTest {
    private val marketDataSyncService: MarketDataSyncService = mock()
    private lateinit var resolver: MarketDataSyncResolver

    @BeforeEach
    fun setUp() {
        resolver = MarketDataSyncResolver(marketDataSyncService)
    }

    @Test
    fun `listedAssetDailyPrices maps closePrice BigDecimal to Double`() {
        val item = listedAssetSyncItem(closePrice = BigDecimal("12.34"))
        whenever(
            marketDataSyncService.listedAssetDailyPrices(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(
            connectionWith(item, hasMore = false),
        )

        val result = resolver.listedAssetDailyPrices(date(), date(), 10, null, null, null)

        assertThat(result.edges[0].node.closePrice).isEqualTo(12.34)
    }

    @Test
    fun `listedAssetDailyPrices propagates pageInfo hasNextPage`() {
        val item = listedAssetSyncItem()
        whenever(
            marketDataSyncService.listedAssetDailyPrices(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(
            connectionWith(item, hasMore = true),
        )

        val result = resolver.listedAssetDailyPrices(date(), date(), 10, null, null, null)

        assertThat(result.pageInfo.hasNextPage).isTrue()
    }

    @Test
    fun `listedAssetDailyPrices maps nullable openPrice to null`() {
        val item = listedAssetSyncItem(openPrice = null)
        whenever(
            marketDataSyncService.listedAssetDailyPrices(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(
            connectionWith(item, hasMore = false),
        )

        val result = resolver.listedAssetDailyPrices(date(), date(), 10, null, null, null)

        assertThat(result.edges[0].node.openPrice).isNull()
    }

    @Test
    fun `fundDailyNavs maps closePrice BigDecimal to Double`() {
        val item = fundSyncItem(closePrice = BigDecimal("1.23"))
        whenever(
            marketDataSyncService.fundDailyNavs(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
        ).thenReturn(
            connectionWith(item, hasMore = false),
        )

        val result = resolver.fundDailyNavs(date(), date(), 10, null, null, null)

        assertThat(result.edges[0].node.closePrice).isEqualTo(1.23)
    }

    @Test
    fun `fundDailyNavs maps nullable totalNetAssets`() {
        val item = fundSyncItem(totalNetAssets = null)
        whenever(
            marketDataSyncService.fundDailyNavs(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
        ).thenReturn(
            connectionWith(item, hasMore = false),
        )

        val result = resolver.fundDailyNavs(date(), date(), 10, null, null, null)

        assertThat(result.edges[0].node.totalNetAssets).isNull()
    }

    @Test
    fun `economicIndicatorVariationPoints maps value BigDecimal to Double`() {
        val item = indicatorSyncItem(value = BigDecimal("3.21"))
        whenever(
            marketDataSyncService.economicIndicatorVariationPoints(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(connectionWith(item, hasMore = false))

        val result = resolver.economicIndicatorVariationPoints(date(), date(), 10, null, null, null, null)

        assertThat(result.edges[0].node.value).isEqualTo(3.21)
    }

    @Test
    fun `assetCorporateEvents maps nullable factor to Double`() {
        val item = eventSyncItem(factor = BigDecimal("2.0"))
        whenever(
            marketDataSyncService.assetCorporateEvents(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        )
            .thenReturn(connectionWith(item, hasMore = false))

        val result = resolver.assetCorporateEvents(date(), date(), 10, null, null, null, null)

        assertThat(result.edges[0].node.factor).isEqualTo(2.0)
    }

    @Test
    fun `assetCorporateEvents maps null priceUnit to null`() {
        val item = eventSyncItem(priceUnit = null)
        whenever(
            marketDataSyncService.assetCorporateEvents(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        )
            .thenReturn(connectionWith(item, hasMore = false))

        val result = resolver.assetCorporateEvents(date(), date(), 10, null, null, null, null)

        assertThat(result.edges[0].node.priceUnit).isNull()
    }

    @Test
    fun `listedAssetDailyPrices delegates all parameters to service`() {
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        val tickers = listOf("PETR4")
        val updatedSince = Instant.parse("2024-01-05T00:00:00Z")
        whenever(
            marketDataSyncService.listedAssetDailyPrices(
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
            ),
        ).thenReturn(
            connectionWith(listedAssetSyncItem(), hasMore = false),
        )

        resolver.listedAssetDailyPrices(from, to, 20, "cursor", updatedSince, tickers)

        verify(marketDataSyncService).listedAssetDailyPrices(
            eq(from),
            eq(to),
            eq(tickers),
            eq(updatedSince),
            eq(20),
            eq("cursor"),
        )
    }

    @Test
    fun `fundDailyNavs delegates all parameters to service`() {
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        val cnpjs = listOf("11222333000181")
        val updatedSince = Instant.parse("2024-01-05T00:00:00Z")
        whenever(
            marketDataSyncService.fundDailyNavs(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
        ).thenReturn(
            connectionWith(fundSyncItem(), hasMore = false),
        )

        resolver.fundDailyNavs(from, to, 20, "cursor", updatedSince, cnpjs)

        verify(marketDataSyncService).fundDailyNavs(
            eq(from),
            eq(to),
            eq(cnpjs),
            eq(updatedSince),
            eq(20),
            eq("cursor"),
        )
    }

    private fun <T> connectionWith(
        item: T,
        hasMore: Boolean,
    ) = ConnectionBuilder.buildConnection(
        items = listOf(item),
        hasMore = hasMore,
        encodeCursor = { "cursor" },
    )

    private fun date(): LocalDate = LocalDate.of(2024, 1, 1)

    private fun listedAssetSyncItem(
        openPrice: BigDecimal? = BigDecimal("10.0"),
        closePrice: BigDecimal = BigDecimal("10.5"),
    ): ListedAssetDailyPriceSyncItem =
        ListedAssetDailyPriceSyncItem(
            assetId = UUID.randomUUID(),
            ticker = "PETR4",
            isinCode = "BRPETRACNPR6",
            date = date(),
            openPrice = openPrice,
            highPrice = BigDecimal("11.0"),
            lowPrice = BigDecimal("9.0"),
            closePrice = closePrice,
            avgPrice = BigDecimal("10.2"),
            volume = BigDecimal("1000.0"),
            trades = 10,
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

    private fun fundSyncItem(
        closePrice: BigDecimal = BigDecimal("1.20"),
        totalNetAssets: BigDecimal? = BigDecimal("1000000"),
    ): FundDailyNavSyncItem =
        FundDailyNavSyncItem(
            assetId = UUID.randomUUID(),
            cnpj = "11222333000181",
            date = date(),
            closePrice = closePrice,
            totalNetAssets = totalNetAssets,
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

    private fun indicatorSyncItem(value: BigDecimal): EconomicIndicatorPointSyncItem =
        EconomicIndicatorPointSyncItem(
            variationId = UUID.randomUUID(),
            indicator = "IPCA",
            variation = "MONTHLY",
            unit = "%",
            referenceDate = date(),
            publicationDate = date(),
            value = value,
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

    private fun eventSyncItem(
        factor: BigDecimal? = null,
        priceUnit: BigDecimal? = BigDecimal("100.0"),
    ): AssetCorporateEventSyncItem =
        AssetCorporateEventSyncItem(
            id = UUID.randomUUID(),
            assetId = UUID.randomUUID(),
            ticker = "PETR4",
            isinCode = "BRPETRACNPR6",
            eventType = "SPLIT",
            factor = factor,
            priceUnit = priceUnit,
            tradingStart = date(),
            tradingEnd = date(),
            subscriptionDate = date(),
            lastDatePrior = date(),
            approvedOn = date(),
            remarks = null,
            updatedAt = Instant.parse("2024-01-01T00:00:00Z"),
        )
}
