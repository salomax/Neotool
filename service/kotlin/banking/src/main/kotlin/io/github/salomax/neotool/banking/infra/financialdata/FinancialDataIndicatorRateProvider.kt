package io.github.salomax.neotool.banking.infra.financialdata

import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.IndicatorRateProvider
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

    override fun getLatestRates(): Map<Indexer, BigDecimal> =
        getOrLoad().rates

    override fun getReferenceDate(): LocalDate? =
        getOrLoad().referenceDate

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
