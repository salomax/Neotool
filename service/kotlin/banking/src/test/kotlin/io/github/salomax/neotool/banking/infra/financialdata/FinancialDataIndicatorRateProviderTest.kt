package io.github.salomax.neotool.banking.client.investment.financialdata

import io.github.salomax.neotool.banking.client.investment.financialdata.*
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.service.GraphQLServiceClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("FinancialDataIndicatorRateProvider")
class FinancialDataIndicatorRateProviderTest {

    @Test
    @DisplayName("uses the expected variation and normalizes percentage values into decimals")
    fun usesExpectedVariationAndNormalizesPercentages() {
        runBlocking {
            val client = mockk<GraphQLServiceClient>()
            coEvery { client.query(any(), any(), any(), any()) } returns
                GraphQLResponse(
                    data = mapOf(
                        "economicIndicators" to listOf(
                            indicator(
                                label = "CDI",
                                variations = listOf(
                                    variation("MONTHLY_CHANGE", "Percentage (%)", "2026-03-12", 1.05),
                                    variation("ROLLING_12M", "Percentage (%)", "2026-03-12", 13.67),
                                ),
                            ),
                            indicator(
                                label = "IPCA",
                                variations = listOf(
                                    variation("ROLLING_12M", "Percentage (%)", "2026-03-10", 5.32),
                                ),
                            ),
                            indicator(
                                label = "SELIC",
                                variations = listOf(
                                    variation("TARGET_RATE", "Percentage (% a.a.)", "2026-03-12", 14.25),
                                ),
                            ),
                        ),
                    ),
                )

            val provider = FinancialDataIndicatorRateProvider(client)

            val rates = provider.getLatestRates()

            assertThat(rates[Indexer.CDI]).isEqualByComparingTo(BigDecimal("0.1367000000"))
            assertThat(rates[Indexer.IPCA]).isEqualByComparingTo(BigDecimal("0.0532000000"))
            assertThat(rates[Indexer.SELIC]).isEqualByComparingTo(BigDecimal("0.1425000000"))
            assertThat(provider.getReferenceDate()).hasToString("2026-03-12")
        }
    }

    @Test
    @DisplayName("maps DI_OVER into DI and ignores unsupported indicators")
    fun mapsDiOverIntoDiAndIgnoresUnsupportedIndicators() {
        runBlocking {
            val client = mockk<GraphQLServiceClient>()
            coEvery { client.query(any(), any(), any(), any()) } returns
                GraphQLResponse(
                    data = mapOf(
                        "economicIndicators" to listOf(
                            indicator(
                                label = "DI_OVER",
                                variations = listOf(
                                    variation("ROLLING_12M", "Percentage (%)", "2026-03-12", 13.42),
                                ),
                            ),
                            indicator(
                                label = "UNKNOWN_LABEL",
                                variations = listOf(
                                    variation("ROLLING_12M", "Percentage (%)", "2026-03-12", 99.99),
                                ),
                            ),
                        ),
                    ),
                )

            val provider = FinancialDataIndicatorRateProvider(client)

            val rates = provider.getLatestRates()

            assertThat(rates).containsOnlyKeys(Indexer.DI)
            assertThat(rates[Indexer.DI]).isEqualByComparingTo(BigDecimal("0.1342000000"))
        }
    }

    @Test
    @DisplayName("service unavailable returns empty map and null reference date")
    fun serviceUnavailableReturnsEmptyMap() {
        runBlocking {
            val client = mockk<GraphQLServiceClient>()
            coEvery { client.query(any(), any(), any(), any()) } throws RuntimeException("network error")

            val provider = FinancialDataIndicatorRateProvider(client)

            assertThat(provider.getLatestRates()).isEmpty()
            assertThat(provider.getReferenceDate()).isNull()
        }
    }

    @Test
    @DisplayName("missing preferred variation skips the indicator")
    fun missingPreferredVariationSkipsIndicator() {
        runBlocking {
            val client = mockk<GraphQLServiceClient>()
            coEvery { client.query(any(), any(), any(), any()) } returns
                GraphQLResponse(
                    data = mapOf(
                        "economicIndicators" to listOf(
                            indicator(
                                label = "CDI",
                                variations = listOf(
                                    variation("MONTHLY_CHANGE", "Percentage (%)", "2026-03-12", 1.05),
                                ),
                            ),
                        ),
                    ),
                )

            val provider = FinancialDataIndicatorRateProvider(client)

            assertThat(provider.getLatestRates()).isEmpty()
        }
    }

    @Test
    @DisplayName("DI falls back to CDI rate when DI_OVER has no ETL data")
    fun diFallsBackToCdiWhenDiOverIsAbsent() {
        runBlocking {
            val client = mockk<GraphQLServiceClient>()
            coEvery { client.query(any(), any(), any(), any()) } returns
                GraphQLResponse(
                    data = mapOf(
                        "economicIndicators" to listOf(
                            indicator(
                                label = "CDI",
                                variations = listOf(
                                    variation("ROLLING_12M", "Percentage (%)", "2026-03-12", 13.67),
                                ),
                            ),
                        ),
                    ),
                )

            val provider = FinancialDataIndicatorRateProvider(client)

            val rates = provider.getLatestRates()

            assertThat(rates[Indexer.DI]).isEqualByComparingTo(rates[Indexer.CDI])
        }
    }

    @Test
    @DisplayName("DI keeps its own rate when DI_OVER data is present alongside CDI")
    fun diKeepsOwnRateWhenDiOverIsPresent() {
        runBlocking {
            val client = mockk<GraphQLServiceClient>()
            coEvery { client.query(any(), any(), any(), any()) } returns
                GraphQLResponse(
                    data = mapOf(
                        "economicIndicators" to listOf(
                            indicator(
                                label = "CDI",
                                variations = listOf(
                                    variation("ROLLING_12M", "Percentage (%)", "2026-03-12", 13.67),
                                ),
                            ),
                            indicator(
                                label = "DI_OVER",
                                variations = listOf(
                                    variation("ROLLING_12M", "Percentage (%)", "2026-03-12", 13.42),
                                ),
                            ),
                        ),
                    ),
                )

            val provider = FinancialDataIndicatorRateProvider(client)

            val rates = provider.getLatestRates()

            assertThat(rates[Indexer.DI]).isEqualByComparingTo(BigDecimal("0.1342000000"))
            assertThat(rates[Indexer.CDI]).isEqualByComparingTo(BigDecimal("0.1367000000"))
        }
    }

    @Test
    @DisplayName("monthly series supports CDI")
    fun monthlySeriesSupportsCdi() {
        runBlocking {
            val client = mockk<GraphQLServiceClient>()
            coEvery { client.query(any(), any(), any(), any()) } returns
                GraphQLResponse(
                    data = mapOf(
                        "economicIndicators" to listOf(
                            mapOf(
                                "label" to "CDI",
                                "variations" to listOf(
                                    mapOf(
                                        "label" to "MONTHLY_CHANGE",
                                        "series" to
                                            mapOf(
                                                "unit" to "Percentage (%)",
                                                "values" to
                                                    listOf(
                                                        mapOf(
                                                            "referenceDate" to "2024-02-01",
                                                            "value" to 0.8,
                                                        ),
                                                        mapOf(
                                                            "referenceDate" to "2024-03-01",
                                                            "value" to 0.83,
                                                        ),
                                                    ),
                                            ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )

            val provider = FinancialDataIndicatorRateProvider(client)

            val result =
                provider.getMonthlySeriesRange(
                    indexer = Indexer.CDI,
                    from = LocalDate.of(2024, 2, 1),
                    to = LocalDate.of(2024, 3, 31),
                )

            assertThat(result).containsExactly(
                LocalDate.of(2024, 2, 1) to BigDecimal("0.0080000000"),
                LocalDate.of(2024, 3, 1) to BigDecimal("0.0083000000"),
            )
        }
    }

    private fun indicator(
        label: String,
        variations: List<Map<String, Any?>>,
    ): Map<String, Any?> =
        mapOf(
            "label" to label,
            "variations" to variations,
        )

    private fun variation(
        label: String,
        unit: String,
        referenceDate: String,
        value: Double,
    ): Map<String, Any?> =
        mapOf(
            "label" to label,
            "series" to
                mapOf(
                    "unit" to unit,
                    "values" to
                        listOf(
                            mapOf(
                                "referenceDate" to referenceDate,
                                "value" to value,
                            ),
                        ),
                ),
        )
}
