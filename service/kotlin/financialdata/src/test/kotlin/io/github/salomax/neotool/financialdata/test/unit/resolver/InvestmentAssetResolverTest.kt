package io.github.salomax.neotool.financialdata.test.unit.resolver

import io.github.salomax.neotool.common.graphql.pagination.ConnectionBuilder
import io.github.salomax.neotool.financialdata.domain.AssetDailyPrice
import io.github.salomax.neotool.financialdata.domain.ListedAsset
import io.github.salomax.neotool.financialdata.graphql.dto.AssetDailyPriceDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetAutocompleteConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetAutocompleteItemDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetConnectionDTO
import io.github.salomax.neotool.financialdata.graphql.dto.ListedAssetDTO
import io.github.salomax.neotool.financialdata.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.financialdata.graphql.mapper.ListedAssetMapper
import io.github.salomax.neotool.financialdata.graphql.resolver.InvestmentAssetResolver
import io.github.salomax.neotool.financialdata.service.ListedAssetSearchService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class InvestmentAssetResolverTest {
    private val assetSearchService: ListedAssetSearchService = mock()
    private val mapper: ListedAssetMapper = mock()
    private lateinit var resolver: InvestmentAssetResolver

    @BeforeEach
    fun setUp() {
        resolver = InvestmentAssetResolver(assetSearchService, mapper)
    }

    @Test
    fun `searchInvestmentAssetsAutocomplete throws when query is blank`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolver.searchInvestmentAssetsAutocomplete("", null)
        }
    }

    @Test
    fun `searchInvestmentAssetsAutocomplete delegates to service and mapper`() {
        val assets = listOf(listedAsset("PETR4"))
        val expected =
            ListedAssetAutocompleteConnectionDTO(
                suggestions =
                    listOf(
                        ListedAssetAutocompleteItemDTO(
                            id = assets[0].id.toString(),
                            ticker = "PETR4",
                            name = assets[0].name,
                            assetType = assets[0].assetType,
                            isActive = true,
                            logoAssetId = null,
                        ),
                    ),
            )
        whenever(assetSearchService.searchAssetsAutocomplete("petr", "EQUITY", 10)).thenReturn(assets)
        whenever(mapper.toAutocompleteConnectionDTO(assets)).thenReturn(expected)

        val result = resolver.searchInvestmentAssetsAutocomplete("petr", "EQUITY")

        assertThat(result).isEqualTo(expected)
        verify(assetSearchService).searchAssetsAutocomplete(eq("petr"), eq("EQUITY"), eq(10))
    }

    @Test
    fun `investmentAssets delegates all params to service`() {
        val connection =
            ConnectionBuilder.buildConnection<ListedAsset>(
                items = emptyList(),
                hasMore = false,
                encodeCursor = { "" },
            )
        val expected =
            ListedAssetConnectionDTO(
                edges = emptyList(),
                pageInfo = PageInfoDTO(false, false, null, null),
                totalCount = 0,
            )
        whenever(assetSearchService.searchAssets("pet", "EQUITY", true, 5, "cursor")).thenReturn(connection)
        whenever(mapper.toConnectionDTO(connection)).thenReturn(expected)

        val result = resolver.investmentAssets("pet", "EQUITY", true, 5, "cursor")

        assertThat(result).isEqualTo(expected)
        verify(assetSearchService).searchAssets(eq("pet"), eq("EQUITY"), eq(true), eq(5), eq("cursor"))
    }

    @Test
    fun `investmentAsset throws for invalid UUID`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                resolver.investmentAsset("not-a-uuid")
            }

        assertThat(ex.message).contains("Invalid asset id")
    }

    @Test
    fun `investmentAsset returns null when service returns null`() {
        val id = UUID.randomUUID()
        whenever(assetSearchService.findAssetById(id)).thenReturn(null)

        val result = resolver.investmentAsset(id.toString())

        assertThat(result).isNull()
    }

    @Test
    fun `investmentAsset returns mapped DTO when found`() {
        val id = UUID.randomUUID()
        val asset = listedAsset("PETR4", id)
        val expected = assetDto(asset)
        whenever(assetSearchService.findAssetById(id)).thenReturn(asset)
        whenever(mapper.toAssetDTO(asset)).thenReturn(expected)

        val result = resolver.investmentAsset(id.toString())

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `investmentAssetByTicker returns null when not found`() {
        whenever(assetSearchService.findAssetByTicker("PETR4")).thenReturn(null)

        val result = resolver.investmentAssetByTicker("PETR4")

        assertThat(result).isNull()
    }

    @Test
    fun `investmentAssetByTicker returns mapped DTO when found`() {
        val asset = listedAsset("PETR4")
        val expected = assetDto(asset)
        whenever(assetSearchService.findAssetByTicker("PETR4")).thenReturn(asset)
        whenever(mapper.toAssetDTO(asset)).thenReturn(expected)

        val result = resolver.investmentAssetByTicker("PETR4")

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `investmentAssetsByTickers delegates set of tickers to service`() {
        val tickers = listOf("PETR4", "VALE3")
        whenever(assetSearchService.findAssetsByTickers(setOf("PETR4", "VALE3"))).thenReturn(emptyList())

        resolver.investmentAssetsByTickers(tickers)

        verify(assetSearchService).findAssetsByTickers(eq(setOf("PETR4", "VALE3")))
    }

    @Test
    fun `priceHistory delegates UUID and date range to service`() {
        val id = UUID.randomUUID()
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        val history = listOf(assetPrice())
        val expected = listOf(AssetDailyPriceDTO(LocalDate.of(2024, 1, 1), 10.0, 11.0, 9.0, 10.5, 10.2, 1000.0, 10))
        whenever(assetSearchService.getPriceHistory(id, from, to, 30)).thenReturn(history)
        whenever(mapper.toPriceListDTO(history)).thenReturn(expected)

        val result = resolver.priceHistory(id.toString(), from, to, 30)

        assertThat(result).isEqualTo(expected)
        verify(assetSearchService).getPriceHistory(eq(id), eq(from), eq(to), eq(30))
    }

    private fun listedAsset(
        ticker: String,
        id: UUID = UUID.randomUUID(),
    ): ListedAsset =
        ListedAsset(
            id = id,
            ticker = ticker,
            name = "Asset $ticker",
            assetType = "EQUITY",
            exchange = "B3",
            isActive = true,
            logoAssetId = null,
            latestPrice = assetPrice(),
            simScore = 0.5,
        )

    private fun assetPrice(): AssetDailyPrice =
        AssetDailyPrice(
            date = LocalDate.of(2024, 1, 1),
            openPrice = BigDecimal("10.0"),
            highPrice = BigDecimal("11.0"),
            lowPrice = BigDecimal("9.0"),
            closePrice = BigDecimal("10.5"),
            avgPrice = BigDecimal("10.2"),
            volume = BigDecimal("1000.0"),
            trades = 10,
        )

    private fun assetDto(asset: ListedAsset): ListedAssetDTO =
        ListedAssetDTO(
            id = asset.id.toString(),
            ticker = asset.ticker,
            name = asset.name,
            assetType = asset.assetType,
            exchange = asset.exchange,
            isActive = asset.isActive,
            logoAssetId = asset.logoAssetId,
            latestPrice = AssetDailyPriceDTO(LocalDate.of(2024, 1, 1), 10.0, 11.0, 9.0, 10.5, 10.2, 1000.0, 10),
        )
}
