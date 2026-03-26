package io.github.salomax.neotool.banking.client.investment.financialdata

import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketData
import io.github.salomax.neotool.banking.domain.investment.ListedAssetMarketDataProvider
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.service.GraphQLServiceClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

@Singleton
class FinancialDataListedAssetMarketDataProvider(
    private val graphQLServiceClient: GraphQLServiceClient,
) : ListedAssetMarketDataProvider {
    private val logger = KotlinLogging.logger {}
    private val cache = ConcurrentHashMap<String, CachedAssetData>()
    @Volatile
    private var bulkQuerySupport = BulkQuerySupport.UNKNOWN

    override fun getAssetsByTicker(tickers: Set<String>): Map<String, ListedAssetMarketData> {
        if (tickers.isEmpty()) return emptyMap()

        val normalized = tickers.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
        val now = System.currentTimeMillis()
        val result = mutableMapOf<String, ListedAssetMarketData>()
        val missing = mutableSetOf<String>()

        normalized.forEach { ticker ->
            val cached = cache[ticker]
            if (cached != null && cached.expiresAt > now) {
                cached.assetData?.let { result[ticker] = it }
            } else {
                missing += ticker
            }
        }

        if (missing.isNotEmpty()) {
            val fetched = fetchAssets(missing)
            missing.forEach { ticker ->
                val assetData = fetched[ticker]
                cache[ticker] = CachedAssetData(assetData, now + if (assetData == null) MISS_TTL_MS else DEFAULT_TTL_MS)
                if (assetData != null) {
                    result[ticker] = assetData
                }
            }
        }

        return result
    }

    private fun fetchAssets(tickers: Set<String>): Map<String, ListedAssetMarketData> {
        if (bulkQuerySupport != BulkQuerySupport.UNSUPPORTED) {
            fetchAssetsBulk(tickers)?.let { return it }
        }
        return fetchAssetsIndividually(tickers)
    }

    private fun fetchAssetsBulk(tickers: Set<String>): Map<String, ListedAssetMarketData>? {
        val query =
            """
            query InvestmentAssetsByTickers(${'$'}tickers: [String!]!) {
              investmentAssetsByTickers(tickers: ${'$'}tickers) {
                ticker
                logoAsset {
                  id
                }
                latestPrice {
                  date
                  closePrice
                }
              }
            }
            """.trimIndent()

        val response: GraphQLResponse =
            runBlocking {
                graphQLServiceClient.query(
                    query = query,
                    variables = mapOf("tickers" to tickers.toList()),
                    targetAudience = "apollo-router",
                )
            }

        val items = response.data?.get("investmentAssetsByTickers") as? List<*>
        if (items != null) {
            if (!response.errors.isNullOrEmpty()) {
                logger.warn { "Bulk listed asset query returned GraphQL errors with partial data; using available listed-asset data" }
            }
            bulkQuerySupport = BulkQuerySupport.SUPPORTED
            return items.mapNotNull(::parseListedAssetData).toMap()
        }

        if (response.isBulkQueryUnsupported()) {
            bulkQuerySupport = BulkQuerySupport.UNSUPPORTED
            logger.warn { "Bulk listed asset query is unavailable in the current GraphQL schema; falling back to per-ticker lookups" }
            return null
        }

        if (!response.errors.isNullOrEmpty()) {
            logger.warn { "Bulk listed asset query failed without data; falling back to per-ticker lookups" }
            return null
        }

        logger.warn { "Bulk listed asset query returned no data; falling back to per-ticker lookups" }
        return null
    }

    private fun fetchAssetsIndividually(tickers: Set<String>): Map<String, ListedAssetMarketData> =
        runBlocking {
            coroutineScope {
                tickers.map { ticker ->
                    async { fetchAssetByTicker(ticker) }
                }.awaitAll()
                    .mapNotNull { it }
                    .toMap()
            }
        }

    private suspend fun fetchAssetByTicker(ticker: String): Pair<String, ListedAssetMarketData>? {
        val query =
            """
            query InvestmentAssetByTicker(${'$'}ticker: String!) {
              investmentAssetByTicker(ticker: ${'$'}ticker) {
                ticker
                logoAsset {
                  id
                }
                latestPrice {
                  date
                  closePrice
                }
              }
            }
            """.trimIndent()

        val response =
            graphQLServiceClient.query(
                query = query,
                variables = mapOf("ticker" to ticker),
                targetAudience = "apollo-router",
            )

        if (!response.errors.isNullOrEmpty()) {
            logger.warn { "Per-ticker listed asset query failed for $ticker" }
            return null
        }

        val item = response.data?.get("investmentAssetByTicker")
        return parseListedAssetData(item)
    }

    private fun parseListedAssetData(raw: Any?): Pair<String, ListedAssetMarketData>? {
        val item = raw as? Map<*, *> ?: return null
        val resolvedTicker = (item["ticker"] as? String)?.trim()?.uppercase().orEmpty()
        if (resolvedTicker.isBlank()) return null
        val logoAssetId = (item["logoAsset"] as? Map<*, *>)?.get("id") as? String
        val latestPrice = item["latestPrice"] as? Map<*, *>
        val priceDate = latestPrice?.get("date") as? String
        val closePrice = latestPrice?.get("closePrice") as? Number

        return runCatching {
            resolvedTicker to ListedAssetMarketData(
                ticker = resolvedTicker,
                closePrice = closePrice?.let { BigDecimal(it.toString()) },
                referenceDate = priceDate?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) },
                logoAssetId = logoAssetId,
            )
        }.onFailure { error ->
            logger.warn(error) { "Failed to parse listed asset data for ticker $resolvedTicker" }
        }.getOrNull()
    }

    private data class CachedAssetData(
        val assetData: ListedAssetMarketData?,
        val expiresAt: Long,
    )

    private fun GraphQLResponse.isBulkQueryUnsupported(): Boolean =
        errors.orEmpty().any { error ->
            val message = error["message"]?.toString().orEmpty()
            message.contains("investmentAssetsByTickers", ignoreCase = true) &&
                (
                    message.contains("Cannot query field", ignoreCase = true) ||
                        message.contains("validation error", ignoreCase = true) ||
                        message.contains("validation failed", ignoreCase = true)
                    )
        }

    private companion object {
        private const val DEFAULT_TTL_MS: Long = 60L * 60L * 1000L
        private const val MISS_TTL_MS: Long = 10L * 60L * 1000L
    }

    private enum class BulkQuerySupport {
        UNKNOWN,
        SUPPORTED,
        UNSUPPORTED,
    }
}
