package io.github.salomax.neotool.banking.client.investment.financialdata

import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.service.GraphQLServiceClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("FinancialDataListedAssetMarketDataProvider")
class FinancialDataListedAssetMarketDataProviderTest {
    @Test
    fun `loads multiple tickers in one graphql request`() {
        runBlocking {
            val client = mockk<GraphQLServiceClient>()
            coEvery { client.query(any(), any(), any(), any()) } returns
                GraphQLResponse(
                    data =
                        mapOf(
                            "investmentAssetsByTickers" to
                                listOf(
                                    mapOf(
                                        "ticker" to "PETR4",
                                        "logoAsset" to mapOf("id" to "logo-petr4"),
                                        "latestPrice" to
                                            mapOf(
                                                "date" to "2026-03-24",
                                                "closePrice" to 31.45,
                                            ),
                                    ),
                                    mapOf(
                                        "ticker" to "VALE3",
                                        "logoAsset" to mapOf("id" to "logo-vale3"),
                                        "latestPrice" to
                                            mapOf(
                                                "date" to "2026-03-24",
                                                "closePrice" to 62.10,
                                            ),
                                    ),
                                ),
                        ),
                )

            val provider = FinancialDataListedAssetMarketDataProvider(client)

            val assets = provider.getAssetsByTicker(setOf("PETR4", "VALE3"))

            assertThat(assets).hasSize(2)
            assertThat(assets["PETR4"]?.closePrice).isEqualByComparingTo(BigDecimal("31.45"))
            assertThat(assets["PETR4"]?.referenceDate).isEqualTo(LocalDate.of(2026, 3, 24))
            assertThat(assets["PETR4"]?.logoAssetId).isEqualTo("logo-petr4")
            assertThat(assets["VALE3"]?.closePrice).isEqualByComparingTo(BigDecimal("62.1"))
            assertThat(assets["VALE3"]?.logoAssetId).isEqualTo("logo-vale3")

            coVerify(exactly = 1) { client.query(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `falls back to per ticker lookup when bulk field is unavailable`() {
        runBlocking {
            val client = mockk<GraphQLServiceClient>()
            coEvery {
                client.query(match { it.contains("investmentAssetsByTickers") }, any(), any(), any())
            } returns
                GraphQLResponse(
                    errors =
                        listOf(
                            mapOf("message" to "Cannot query field 'investmentAssetsByTickers' on type 'Query'."),
                        ),
                )
            coEvery {
                client.query(
                    match { it.contains("investmentAssetByTicker") },
                    match { it == mapOf("ticker" to "PETR4") },
                    any(),
                    any(),
                )
            } returns
                GraphQLResponse(
                    data =
                        mapOf(
                            "investmentAssetByTicker" to
                                mapOf(
                                    "ticker" to "PETR4",
                                    "logoAsset" to mapOf("id" to "logo-petr4"),
                                    "latestPrice" to
                                        mapOf(
                                            "date" to "2026-03-24",
                                            "closePrice" to 31.45,
                                        ),
                                ),
                        ),
                )
            coEvery {
                client.query(
                    match { it.contains("investmentAssetByTicker") },
                    match { it == mapOf("ticker" to "VALE3") },
                    any(),
                    any(),
                )
            } returns
                GraphQLResponse(
                    data =
                        mapOf(
                            "investmentAssetByTicker" to
                                mapOf(
                                    "ticker" to "VALE3",
                                    "logoAsset" to mapOf("id" to "logo-vale3"),
                                    "latestPrice" to
                                        mapOf(
                                            "date" to "2026-03-24",
                                            "closePrice" to 62.10,
                                        ),
                                ),
                        ),
                )

            val provider = FinancialDataListedAssetMarketDataProvider(client)

            val assets = provider.getAssetsByTicker(setOf("PETR4", "VALE3"))

            assertThat(assets).hasSize(2)
            assertThat(assets["PETR4"]?.closePrice).isEqualByComparingTo(BigDecimal("31.45"))
            assertThat(assets["PETR4"]?.logoAssetId).isEqualTo("logo-petr4")
            assertThat(assets["VALE3"]?.closePrice).isEqualByComparingTo(BigDecimal("62.1"))
            assertThat(assets["VALE3"]?.logoAssetId).isEqualTo("logo-vale3")

            coVerify(exactly = 1) {
                client.query(match { it.contains("investmentAssetsByTickers") }, any(), any(), any())
            }
            coVerify(exactly = 2) {
                client.query(match { it.contains("investmentAssetByTicker") }, any(), any(), any())
            }
        }
    }
}
