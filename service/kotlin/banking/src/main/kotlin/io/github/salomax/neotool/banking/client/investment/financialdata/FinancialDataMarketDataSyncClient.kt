package io.github.salomax.neotool.banking.client.investment.financialdata

import io.github.salomax.neotool.common.service.GraphQLServiceClient
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ListedAssetDailyPriceSyncNode(
    val ticker: String,
    val isinCode: String?,
    val date: LocalDate,
    val closePrice: BigDecimal,
)

data class FundDailyNavSyncNode(
    val cnpj: String,
    val date: LocalDate,
    val closePrice: BigDecimal,
)

data class EconomicIndicatorPointSyncNode(
    val indicator: String,
    val variation: String,
    val referenceDate: LocalDate,
    val publicationDate: LocalDate?,
    val value: BigDecimal,
)

data class AssetCorporateEventSyncNode(
    val ticker: String?,
    val isinCode: String,
    val eventType: String,
    val factor: BigDecimal?,
    val priceUnit: BigDecimal?,
    val tradingStart: LocalDate?,
    val tradingEnd: LocalDate?,
    val subscriptionDate: LocalDate?,
    val lastDatePrior: LocalDate,
    val approvedOn: LocalDate,
    val remarks: String?,
)

@Singleton
class FinancialDataMarketDataSyncClient(
    private val graphQLServiceClient: GraphQLServiceClient,
) {
    fun forEachListedAssetDailyPrice(
        from: LocalDate,
        to: LocalDate,
        updatedSince: Instant? = null,
        tickers: Collection<String>? = null,
        pageSize: Int = 250,
        consumer: (List<ListedAssetDailyPriceSyncNode>) -> Unit,
    ) {
        paginate(
            query =
                """
                query ListedAssetDailyPrices(${'$'}from: Date!, ${'$'}to: Date!, ${'$'}first: Int!, ${'$'}after: String, ${'$'}updatedSince: DateTime, ${'$'}tickers: [String!]) {
                  listedAssetDailyPrices(from: ${'$'}from, to: ${'$'}to, first: ${'$'}first, after: ${'$'}after, updatedSince: ${'$'}updatedSince, tickers: ${'$'}tickers) {
                    edges {
                      cursor
                      node {
                        ticker
                        isinCode
                        date
                        closePrice
                      }
                    }
                    pageInfo {
                      hasNextPage
                      endCursor
                    }
                  }
                }
                """.trimIndent(),
            rootField = "listedAssetDailyPrices",
            variables =
                mapOf(
                    "from" to formatDate(from),
                    "to" to formatDate(to),
                    "updatedSince" to updatedSince?.toString(),
                    "tickers" to tickers?.toList(),
                ),
            pageSize = pageSize,
            consumer = { edges ->
                consumer(
                    edges.mapNotNull { edge ->
                        val node = edge["node"] as? Map<*, *> ?: return@mapNotNull null
                        val date = parseDate(node["date"]) ?: return@mapNotNull null
                        val closePrice = parseDecimal(node["closePrice"]) ?: return@mapNotNull null
                        ListedAssetDailyPriceSyncNode(
                            ticker = node["ticker"] as? String ?: return@mapNotNull null,
                            isinCode = node["isinCode"] as? String,
                            date = date,
                            closePrice = closePrice,
                        )
                    },
                )
            },
        )
    }

    fun forEachFundDailyNav(
        from: LocalDate,
        to: LocalDate,
        updatedSince: Instant? = null,
        cnpjs: Collection<String>? = null,
        pageSize: Int = 250,
        consumer: (List<FundDailyNavSyncNode>) -> Unit,
    ) {
        paginate(
            query =
                """
                query FundDailyNavs(${'$'}from: Date!, ${'$'}to: Date!, ${'$'}first: Int!, ${'$'}after: String, ${'$'}updatedSince: DateTime, ${'$'}cnpjs: [String!]) {
                  fundDailyNavs(from: ${'$'}from, to: ${'$'}to, first: ${'$'}first, after: ${'$'}after, updatedSince: ${'$'}updatedSince, cnpjs: ${'$'}cnpjs) {
                    edges {
                      cursor
                      node {
                        cnpj
                        date
                        closePrice
                      }
                    }
                    pageInfo {
                      hasNextPage
                      endCursor
                    }
                  }
                }
                """.trimIndent(),
            rootField = "fundDailyNavs",
            variables =
                mapOf(
                    "from" to formatDate(from),
                    "to" to formatDate(to),
                    "updatedSince" to updatedSince?.toString(),
                    "cnpjs" to cnpjs?.toList(),
                ),
            pageSize = pageSize,
            consumer = { edges ->
                consumer(
                    edges.mapNotNull { edge ->
                        val node = edge["node"] as? Map<*, *> ?: return@mapNotNull null
                        val date = parseDate(node["date"]) ?: return@mapNotNull null
                        val closePrice = parseDecimal(node["closePrice"]) ?: return@mapNotNull null
                        FundDailyNavSyncNode(
                            cnpj = node["cnpj"] as? String ?: return@mapNotNull null,
                            date = date,
                            closePrice = closePrice,
                        )
                    },
                )
            },
        )
    }

    fun forEachEconomicIndicatorPoint(
        from: LocalDate,
        to: LocalDate,
        updatedSince: Instant? = null,
        indicators: Collection<String>? = null,
        variations: Collection<String>? = null,
        pageSize: Int = 250,
        consumer: (List<EconomicIndicatorPointSyncNode>) -> Unit,
    ) {
        paginate(
            query =
                """
                query EconomicIndicatorVariationPoints(${'$'}from: Date!, ${'$'}to: Date!, ${'$'}first: Int!, ${'$'}after: String, ${'$'}updatedSince: DateTime, ${'$'}indicators: [String!], ${'$'}variations: [String!]) {
                  economicIndicatorVariationPoints(from: ${'$'}from, to: ${'$'}to, first: ${'$'}first, after: ${'$'}after, updatedSince: ${'$'}updatedSince, indicators: ${'$'}indicators, variations: ${'$'}variations) {
                    edges {
                      cursor
                      node {
                        indicator
                        variation
                        referenceDate
                        publicationDate
                        value
                      }
                    }
                    pageInfo {
                      hasNextPage
                      endCursor
                    }
                  }
                }
                """.trimIndent(),
            rootField = "economicIndicatorVariationPoints",
            variables =
                mapOf(
                    "from" to formatDate(from),
                    "to" to formatDate(to),
                    "updatedSince" to updatedSince?.toString(),
                    "indicators" to indicators?.toList(),
                    "variations" to variations?.toList(),
                ),
            pageSize = pageSize,
            consumer = { edges ->
                consumer(
                    edges.mapNotNull { edge ->
                        val node = edge["node"] as? Map<*, *> ?: return@mapNotNull null
                        val referenceDate = parseDate(node["referenceDate"]) ?: return@mapNotNull null
                        val value = parseDecimal(node["value"]) ?: return@mapNotNull null
                        EconomicIndicatorPointSyncNode(
                            indicator = node["indicator"] as? String ?: return@mapNotNull null,
                            variation = node["variation"] as? String ?: return@mapNotNull null,
                            referenceDate = referenceDate,
                            publicationDate = parseDate(node["publicationDate"]),
                            value = value,
                        )
                    },
                )
            },
        )
    }

    fun forEachAssetCorporateEvent(
        from: LocalDate,
        to: LocalDate,
        updatedSince: Instant? = null,
        tickers: Collection<String>? = null,
        isins: Collection<String>? = null,
        pageSize: Int = 250,
        consumer: (List<AssetCorporateEventSyncNode>) -> Unit,
    ) {
        paginate(
            query =
                """
                query AssetCorporateEvents(${'$'}from: Date!, ${'$'}to: Date!, ${'$'}first: Int!, ${'$'}after: String, ${'$'}updatedSince: DateTime, ${'$'}tickers: [String!], ${'$'}isins: [String!]) {
                  assetCorporateEvents(from: ${'$'}from, to: ${'$'}to, first: ${'$'}first, after: ${'$'}after, updatedSince: ${'$'}updatedSince, tickers: ${'$'}tickers, isins: ${'$'}isins) {
                    edges {
                      cursor
                      node {
                        ticker
                        isinCode
                        eventType
                        factor
                        priceUnit
                        tradingStart
                        tradingEnd
                        subscriptionDate
                        lastDatePrior
                        approvedOn
                        remarks
                      }
                    }
                    pageInfo {
                      hasNextPage
                      endCursor
                    }
                  }
                }
                """.trimIndent(),
            rootField = "assetCorporateEvents",
            variables =
                mapOf(
                    "from" to formatDate(from),
                    "to" to formatDate(to),
                    "updatedSince" to updatedSince?.toString(),
                    "tickers" to tickers?.toList(),
                    "isins" to isins?.toList(),
                ),
            pageSize = pageSize,
            consumer = { edges ->
                consumer(
                    edges.mapNotNull { edge ->
                        val node = edge["node"] as? Map<*, *> ?: return@mapNotNull null
                        AssetCorporateEventSyncNode(
                            ticker = node["ticker"] as? String,
                            isinCode = node["isinCode"] as? String ?: return@mapNotNull null,
                            eventType = node["eventType"] as? String ?: return@mapNotNull null,
                            factor = parseDecimal(node["factor"]),
                            priceUnit = parseDecimal(node["priceUnit"]),
                            tradingStart = parseDate(node["tradingStart"]),
                            tradingEnd = parseDate(node["tradingEnd"]),
                            subscriptionDate = parseDate(node["subscriptionDate"]),
                            lastDatePrior = parseDate(node["lastDatePrior"]) ?: return@mapNotNull null,
                            approvedOn = parseDate(node["approvedOn"]) ?: return@mapNotNull null,
                            remarks = node["remarks"] as? String,
                        )
                    },
                )
            },
        )
    }

    private fun paginate(
        query: String,
        rootField: String,
        variables: Map<String, Any?>,
        pageSize: Int,
        consumer: (List<Map<*, *>>) -> Unit,
    ) {
        var after: String? = null
        do {
            val response =
                runBlocking {
                    graphQLServiceClient.query(
                        query = query,
                        variables =
                            (variables + mapOf("first" to pageSize, "after" to after))
                                .filterValues { it != null }
                                .mapValues { it.value as Any },
                        targetAudience = "apollo-router",
                    )
                }
            if (!response.errors.isNullOrEmpty()) {
                throw IllegalStateException("financialdata sync query failed for $rootField: ${response.errors}")
            }
            val connection =
                response.data?.get(rootField) as? Map<*, *>
                    ?: throw IllegalStateException("financialdata sync query returned no data for $rootField")
            val edges = (connection["edges"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
            if (edges.isNotEmpty()) {
                consumer(edges)
            }
            val pageInfo = connection["pageInfo"] as? Map<*, *> ?: emptyMap<String, Any?>()
            val hasNextPage = pageInfo["hasNextPage"] as? Boolean ?: false
            after = pageInfo["endCursor"] as? String
            if (!hasNextPage) {
                break
            }
        } while (after != null)
    }

    private fun parseDate(raw: Any?): LocalDate? =
        (raw as? String)?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }.getOrNull() }

    private fun parseDecimal(raw: Any?): BigDecimal? =
        when (raw) {
            null -> null
            is Number -> BigDecimal(raw.toString())
            is String -> runCatching { BigDecimal(raw) }.getOrNull()
            else -> null
        }

    private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ISO_DATE)
}
