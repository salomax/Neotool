package io.github.salomax.neotool.banking.client.investment.financialdata

import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.service.GraphQLServiceClient
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

@Singleton
class FinancialDataIndicatorRateProvider(
    private val graphQLServiceClient: GraphQLServiceClient,
) : IndicatorRateProvider {
    private val logger = KotlinLogging.logger {}

    private val cacheRef = AtomicReference<CachedRates?>()

    override fun getLatestRates(): Map<Indexer, BigDecimal> = getOrLoad().rates

    override fun getReferenceDate(): LocalDate? = getOrLoad().referenceDate

    override fun getMonthlySeriesRange(
        indexer: Indexer,
        from: LocalDate,
        to: LocalDate,
    ): List<Pair<LocalDate, BigDecimal>> {
        val indicatorLabel = monthlySeriesIndicatorLabel(indexer) ?: return emptyList()
        return try {
            fetchMonthlySeries(indicatorLabel, from, to)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch monthly series for $indexer ($indicatorLabel); returning empty list." }
            emptyList()
        }
    }

    /**
     * Loads rates from cache or fetches fresh. Note: check-then-act is not atomic — two concurrent
     * threads that both see expired cache may both call fetchLatestRates(); the last write wins.
     * With 1h TTL the impact is minimal (at most two fetches at cold start).
     */
    private fun getOrLoad(): CachedRates {
        val cached = cacheRef.get()
        if (cached != null && !cached.isExpired()) {
            return cached
        }

        return try {
            val fresh = fetchLatestRates()
            cacheRef.set(fresh)
            fresh
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch indicator rates from financial-data; returning empty map." }
            val fallback =
                CachedRates(
                    rates = emptyMap(),
                    referenceDate = null,
                    expiresAt = System.currentTimeMillis() + ONE_MINUTE_MS,
                )
            cacheRef.set(fallback)
            fallback
        }
    }

    private fun fetchLatestRates(): CachedRates {
        val query =
            """
            query EconomicIndicatorsForValuation {
              economicIndicators {
                label
                variations {
                  label
                  series(limit: 1) {
                    unit
                    values {
                      referenceDate
                      value
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val response: GraphQLResponse =
            runBlocking {
                graphQLServiceClient.query(
                    query = query,
                    variables = emptyMap(),
                    targetAudience = "apollo-router",
                )
            }

        val indicators =
            response.data
                ?.get("economicIndicators") as? List<*>
                ?: emptyList<Any?>()

        if (indicators.isEmpty()) {
            return CachedRates(emptyMap(), null, System.currentTimeMillis() + DEFAULT_TTL_MS)
        }

        var refDate: LocalDate? = null
        val rates = mutableMapOf<Indexer, BigDecimal>()

        for (indRaw in indicators) {
            val ind = indRaw as? Map<*, *> ?: continue
            val indicatorLabel = (ind["label"] as? String)?.takeIf { it.isNotBlank() } ?: continue
            val indexer = indexerFor(indicatorLabel) ?: continue
            val expectedVariationLabel = preferredVariationLabel(indexer)
            val variations = ind["variations"] as? List<*> ?: continue

            for (varRaw in variations) {
                val vari = varRaw as? Map<*, *> ?: continue
                val variationLabel = (vari["label"] as? String)?.takeIf { it.isNotBlank() } ?: continue
                if (variationLabel != expectedVariationLabel) continue
                val series = vari["series"] as? Map<*, *> ?: continue
                val unit = series["unit"] as? String
                val values = series["values"] as? List<*> ?: continue
                val lastPoint = values.lastOrNull() as? Map<*, *> ?: continue
                val value = (lastPoint["value"] as? Number)?.toDouble() ?: continue
                val referenceDateStr = lastPoint["referenceDate"] as? String

                if (referenceDateStr != null) {
                    runCatching { LocalDate.parse(referenceDateStr, DateTimeFormatter.ISO_DATE) }
                        .onSuccess { parsed ->
                            if (refDate == null || parsed.isAfter(refDate)) {
                                refDate = parsed
                            }
                        }
                }

                rates[indexer] = normalizeRateValue(value, unit)
                break
            }
        }

        // DI and CDI track the same benchmark rate; fall back to CDI when DI_OVER has no ETL data
        if (!rates.containsKey(Indexer.DI)) {
            rates[Indexer.CDI]?.let { rates[Indexer.DI] = it }
        }

        return CachedRates(
            rates = rates,
            referenceDate = refDate,
            expiresAt = System.currentTimeMillis() + DEFAULT_TTL_MS,
        )
    }

    private data class CachedRates(
        val rates: Map<Indexer, BigDecimal>,
        val referenceDate: LocalDate?,
        val expiresAt: Long,
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt
    }

    private fun fetchMonthlySeries(
        indicatorLabel: String,
        from: LocalDate,
        to: LocalDate,
    ): List<Pair<LocalDate, BigDecimal>> {
        val query =
            """
            query InflationMonthlySeries(${'$'}label: String, ${'$'}range: IndicatorRangeInput) {
              economicIndicators(label: ${'$'}label) {
                variations(label: "MONTHLY_CHANGE") {
                  series(range: ${'$'}range) {
                    unit
                    values {
                      referenceDate
                      value
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val variables =
            mapOf(
                "label" to indicatorLabel,
                "range" to
                    mapOf(
                        "from" to from.format(DateTimeFormatter.ISO_DATE),
                        "to" to to.format(DateTimeFormatter.ISO_DATE),
                    ),
            )

        val response: GraphQLResponse =
            runBlocking {
                graphQLServiceClient.query(
                    query = query,
                    variables = variables,
                    targetAudience = "apollo-router",
                )
            }

        val indicators = response.data?.get("economicIndicators") as? List<*> ?: return emptyList()
        val firstIndicator = indicators.firstOrNull() as? Map<*, *> ?: return emptyList()
        val variations = firstIndicator["variations"] as? List<*> ?: return emptyList()
        val firstVariation = variations.firstOrNull() as? Map<*, *> ?: return emptyList()
        val series = firstVariation["series"] as? Map<*, *> ?: return emptyList()
        val unit = series["unit"] as? String
        val values = series["values"] as? List<*> ?: return emptyList()

        return values
            .mapNotNull { pointRaw ->
                val point = pointRaw as? Map<*, *> ?: return@mapNotNull null
                val refDateStr = point["referenceDate"] as? String ?: return@mapNotNull null
                val value = (point["value"] as? Number)?.toDouble() ?: return@mapNotNull null
                val refDate =
                    runCatching { LocalDate.parse(refDateStr, DateTimeFormatter.ISO_DATE) }.getOrNull()
                        ?: return@mapNotNull null
                refDate to normalizeRateValue(value, unit)
            }.sortedBy { it.first }
    }

    private fun monthlySeriesIndicatorLabel(indexer: Indexer): String? =
        when (indexer) {
            Indexer.CDI -> "CDI"
            Indexer.DI -> "DI_OVER"
            Indexer.SELIC -> "SELIC"
            Indexer.IPCA -> "IPCA"
            Indexer.IGP_M -> "IGP-M"
            Indexer.IGP_DI -> "IGP-DI"
            Indexer.INPC -> "INPC"
            Indexer.TR -> "TR"
            else -> null
        }

    private fun indexerFor(indicatorLabel: String): Indexer? =
        when (indicatorLabel.uppercase()) {
            "CDI" -> Indexer.CDI
            "DI", "DI_OVER" -> Indexer.DI
            "SELIC" -> Indexer.SELIC
            "IPCA" -> Indexer.IPCA
            "IGP-M", "IGP_M" -> Indexer.IGP_M
            "IGP-DI", "IGP_DI" -> Indexer.IGP_DI
            "INPC" -> Indexer.INPC
            "TR" -> Indexer.TR
            else -> null
        }

    private fun preferredVariationLabel(indexer: Indexer): String =
        when (indexer) {
            Indexer.SELIC -> "TARGET_RATE"
            Indexer.CDI,
            Indexer.DI,
            Indexer.IPCA,
            Indexer.IGP_M,
            Indexer.IGP_DI,
            Indexer.INPC,
            Indexer.TR,
            -> "ROLLING_12M"
            else -> "ROLLING_12M"
        }

    private fun normalizeRateValue(
        value: Double,
        unit: String?,
    ): BigDecimal {
        val raw = BigDecimal.valueOf(value)
        if (unit?.contains("%") == true) {
            return raw.divide(ONE_HUNDRED, RATE_SCALE, RoundingMode.HALF_UP)
        }
        return raw.setScale(RATE_SCALE, RoundingMode.HALF_UP)
    }

    private companion object {
        private const val DEFAULT_TTL_MS: Long = 60L * 60L * 1000L // 1 hour
        private const val ONE_MINUTE_MS: Long = 60L * 1000L
        private const val RATE_SCALE = 10
        private val ONE_HUNDRED = BigDecimal("100")
    }
}
