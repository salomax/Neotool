package io.github.salomax.neotool.banking.infra.financialdata

import io.github.salomax.neotool.banking.domain.investment.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.FundMarketNav
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.service.GraphQLServiceClient
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

@Singleton
class FinancialDataFundMarketDataProvider(
    private val graphQLServiceClient: GraphQLServiceClient,
) : FundMarketDataProvider {
    private val logger = KotlinLogging.logger {}
    private val cache = ConcurrentHashMap<String, CachedSnapshot>()

    override fun getSnapshotsByCnpj(cnpjs: Set<String>): Map<String, FundMarketSnapshot> {
        if (cnpjs.isEmpty()) return emptyMap()
        val normalized = cnpjs.map { it.filter(Char::isDigit) }.filter { it.isNotBlank() }.toSet()
        val now = System.currentTimeMillis()
        val result = mutableMapOf<String, FundMarketSnapshot>()
        val missing = mutableSetOf<String>()

        normalized.forEach { cnpj ->
            val cached = cache[cnpj]
            if (cached != null && cached.expiresAt > now) {
                cached.snapshot?.let { result[cnpj] = it }
            } else {
                missing += cnpj
            }
        }

        if (missing.isNotEmpty()) {
            val fetched = fetchSnapshots(missing)
            missing.forEach { cnpj ->
                val snapshot = fetched[cnpj]
                cache[cnpj] = CachedSnapshot(snapshot, now + if (snapshot == null) MISS_TTL_MS else DEFAULT_TTL_MS)
                if (snapshot != null) {
                    result[cnpj] = snapshot
                }
            }
        }

        return result
    }

    private fun fetchSnapshots(cnpjs: Set<String>): Map<String, FundMarketSnapshot> {
        val query =
            """
            query InvestmentFundMarketSnapshots(${'$'}cnpjs: [String!]!) {
              investmentFundMarketSnapshots(cnpjs: ${'$'}cnpjs) {
                cnpj
                assetType
                taxProfile
                latestNav {
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
                    variables = mapOf("cnpjs" to cnpjs.toList()),
                    targetAudience = "apollo-router",
                )
            }

        val items = response.data?.get("investmentFundMarketSnapshots") as? List<*> ?: emptyList<Any?>()
        return items.mapNotNull { raw ->
            val item = raw as? Map<*, *> ?: return@mapNotNull null
            val cnpj = item["cnpj"] as? String ?: return@mapNotNull null
            val latestNav = item["latestNav"] as? Map<*, *>
            cnpj to FundMarketSnapshot(
                fundCnpj = cnpj,
                assetType = item["assetType"] as? String ?: "UNKNOWN",
                taxProfile = (item["taxProfile"] as? String)?.let {
                    runCatching { FundTaxProfile.valueOf(it) }.getOrDefault(FundTaxProfile.UNKNOWN)
                } ?: FundTaxProfile.UNKNOWN,
                latestNav = latestNav?.let {
                    val date = it["date"] as? String ?: return@let null
                    val closePrice = it["closePrice"] as? Number ?: return@let null
                    FundMarketNav(
                        date = LocalDate.parse(date, DateTimeFormatter.ISO_DATE),
                        closePrice = BigDecimal(closePrice.toString()),
                    )
                },
            )
        }.toMap().also {
            if (it.isEmpty()) {
                logger.debug { "No fund market snapshots returned for ${cnpjs.size} CNPJs" }
            }
        }
    }

    private data class CachedSnapshot(
        val snapshot: FundMarketSnapshot?,
        val expiresAt: Long,
    )

    private companion object {
        private const val DEFAULT_TTL_MS: Long = 60L * 60L * 1000L
        private const val MISS_TTL_MS: Long = 10L * 60L * 1000L
    }
}
