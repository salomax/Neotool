package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.domain.AssetDailyPrice
import io.github.salomax.neotool.financialdata.domain.ListedAsset
import io.github.salomax.neotool.financialdata.repo.ListedAssetRepositoryCustom
import io.github.salomax.neotool.financialdata.service.ListedAssetSearchService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class ListedAssetSearchServiceTest {
    private val assetRepository: ListedAssetRepositoryCustom = mock()
    private lateinit var service: ListedAssetSearchService

    @BeforeEach
    fun setUp() {
        service = ListedAssetSearchService(assetRepository)
    }

    @Test
    fun `searchAssetsAutocomplete throws when query is blank`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.searchAssetsAutocomplete("  ", null)
        }
    }

    @Test
    fun `searchAssetsAutocomplete trims query and delegates to repository`() {
        whenever(assetRepository.searchAssetsAutocomplete(any(), any(), any())).thenReturn(emptyList())

        service.searchAssetsAutocomplete(" petr ", "EQUITY")

        verify(assetRepository).searchAssetsAutocomplete(eq("petr"), eq("EQUITY"), eq(10))
    }

    @Test
    fun `searchAssets validates page size`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.searchAssets(
                query = null,
                assetType = null,
                isActive = null,
                first = 0,
                after = null,
            )
        }
    }

    @Test
    fun `searchAssets throws for invalid cursor`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                service.searchAssets(
                    query = null,
                    assetType = null,
                    isActive = null,
                    first = 10,
                    after = "!!!invalid!!!",
                )
            }

        assertThat(ex.message).contains("cursor")
    }

    @Test
    fun `searchAssets returns connection with correct edges`() {
        whenever(
            assetRepository.searchAssets(eq("petr"), eq("EQUITY"), eq(true), eq(6), isNull()),
        ).thenReturn(Pair(listOf(listedAsset("PETR4")), 1L))

        val result =
            service.searchAssets(
                query = "petr",
                assetType = "EQUITY",
                isActive = true,
                first = 5,
                after = null,
            )

        assertThat(result.edges).hasSize(1)
        assertThat(result.totalCount).isEqualTo(1L)
    }

    @Test
    fun `searchAssets sets hasMore correctly`() {
        whenever(
            assetRepository.searchAssets(eq(null), eq(null), eq(null), eq(3), isNull()),
        ).thenReturn(
            Pair(
                listOf(listedAsset("PETR4"), listedAsset("VALE3"), listedAsset("ITUB4")),
                3L,
            ),
        )

        val result = service.searchAssets(null, null, null, 2, null)

        assertThat(result.pageInfo.hasNextPage).isTrue()
        assertThat(result.edges).hasSize(2)
    }

    @Test
    fun `searchAssets normalizes blank query to null`() {
        whenever(
            assetRepository.searchAssets(eq(null), eq(null), eq(null), eq(21), isNull()),
        ).thenReturn(Pair(emptyList(), 0L))

        service.searchAssets("  ", null, null, null, null)

        verify(assetRepository).searchAssets(eq(null), eq(null), eq(null), eq(21), isNull())
    }

    @Test
    fun `findAssetById delegates to repository`() {
        val id = UUID.randomUUID()
        val expected = listedAsset("PETR4", id = id)
        whenever(assetRepository.findAssetById(id)).thenReturn(expected)

        val result = service.findAssetById(id)

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `findAssetById returns null when not found`() {
        val id = UUID.randomUUID()
        whenever(assetRepository.findAssetById(id)).thenReturn(null)

        val result = service.findAssetById(id)

        assertThat(result).isNull()
    }

    @Test
    fun `findAssetByTicker uppercases and trims ticker`() {
        whenever(assetRepository.findAssetByTicker(any())).thenReturn(null)

        service.findAssetByTicker(" petr4 ")

        verify(assetRepository).findAssetByTicker(eq("PETR4"))
    }

    @Test
    fun `findAssetsByTickers normalizes all tickers to uppercase`() {
        whenever(assetRepository.findAssetsByTickers(any())).thenReturn(emptyList())

        service.findAssetsByTickers(setOf("petr4", "vale3"))

        verify(assetRepository).findAssetsByTickers(eq(setOf("PETR4", "VALE3")))
    }

    @Test
    fun `findAssetsByTickers filters out blank tickers`() {
        whenever(assetRepository.findAssetsByTickers(any())).thenReturn(emptyList())

        service.findAssetsByTickers(setOf("petr4", " ", " vale3 "))

        verify(assetRepository).findAssetsByTickers(eq(setOf("PETR4", "VALE3")))
    }

    @Test
    fun `getPriceHistory delegates to repository`() {
        val assetId = UUID.randomUUID()
        val from = LocalDate.of(2024, 1, 1)
        val to = LocalDate.of(2024, 1, 31)
        whenever(assetRepository.getPriceHistory(any(), any(), any(), any())).thenReturn(emptyList())

        service.getPriceHistory(assetId, from, to, 10)

        verify(assetRepository).getPriceHistory(eq(assetId), eq(from), eq(to), eq(10))
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
            latestPrice =
                AssetDailyPrice(
                    date = LocalDate.of(2024, 1, 1),
                    openPrice = BigDecimal("10.00"),
                    highPrice = BigDecimal("11.00"),
                    lowPrice = BigDecimal("9.00"),
                    closePrice = BigDecimal("10.50"),
                    avgPrice = BigDecimal("10.25"),
                    volume = BigDecimal("1000.00"),
                    trades = 100,
                ),
            simScore = 0.5,
        )
}
