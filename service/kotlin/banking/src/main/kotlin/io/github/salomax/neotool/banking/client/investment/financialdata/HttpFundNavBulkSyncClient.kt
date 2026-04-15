package io.github.salomax.neotool.banking.client.investment.financialdata

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.common.service.ServiceTokenClient
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

@Singleton
open class HttpFundNavBulkSyncClient(
    @param:Property(name = "banking.financialdata-service.url")
    private val financialDataServiceUrl: String,
    @param:Property(name = "banking.financialdata-service.fund-navs-export-path")
    private val exportPath: String,
    @param:Property(name = "banking.financialdata-service.target-audience", defaultValue = "neotool-financial-data-service")
    private val targetAudience: String,
    @param:Property(name = "banking.market-data-sync.fund-navs.connect-timeout-seconds", defaultValue = "10")
    private val connectTimeoutSeconds: Long,
    @param:Property(name = "banking.market-data-sync.fund-navs.read-timeout-seconds", defaultValue = "600")
    private val readTimeoutSeconds: Long,
    private val serviceTokenClient: ServiceTokenClient,
    private val objectMapper: ObjectMapper,
) : FundNavBulkSyncClient {
    private val logger = KotlinLogging.logger {}
    private val httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
            .build()

    override fun streamFundDailyNavs(
        from: LocalDate,
        to: LocalDate,
        updatedSince: Instant?,
        cnpjs: Collection<String>?,
        after: String?,
        consumer: (FundNavBulkSyncNode) -> Unit,
    ) {
        val token = runBlocking { serviceTokenClient.getServiceToken(targetAudience) }
        val uri =
            buildUri(
                from = from,
                to = to,
                updatedSince = updatedSince,
                cnpjs = cnpjs,
                after = after,
            )
        val request =
            HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/x-ndjson")
                .header("Accept-Encoding", "gzip")
                .GET()
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "Fund NAV bulk sync failed with status ${response.statusCode()} for uri=$uri",
            )
        }

        val bodyStream = unwrapBody(response.body(), response.headers().firstValue("Content-Encoding").orElse(null))
        bodyStream.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).useLines { lines ->
                lines.filter { it.isNotBlank() }.forEach { line ->
                    consumer(objectMapper.readValue(line, FundNavBulkSyncNode::class.java))
                }
            }
        }

        logger.info {
            "Fund NAV bulk sync stream consumed successfully: from=$from to=$to cnpjs=${cnpjs?.size ?: 0} after=${after != null}"
        }
    }

    private fun buildUri(
        from: LocalDate,
        to: LocalDate,
        updatedSince: Instant?,
        cnpjs: Collection<String>?,
        after: String?,
    ): URI {
        val normalizedBase = financialDataServiceUrl.trimEnd('/')
        val normalizedPath = if (exportPath.startsWith('/')) exportPath else "/$exportPath"
        val query =
            buildList {
                add("from=${encode(from.toString())}")
                add("to=${encode(to.toString())}")
                updatedSince?.let { add("updatedSince=${encode(it.toString())}") }
                after?.takeIf { it.isNotBlank() }?.let { add("after=${encode(it)}") }
                cnpjs
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    ?.forEach { add("cnpjs=${encode(it)}") }
            }.joinToString("&")
        return URI.create("$normalizedBase$normalizedPath?$query")
    }

    private fun unwrapBody(
        body: InputStream,
        contentEncoding: String?,
    ): InputStream =
        if (contentEncoding?.contains("gzip", ignoreCase = true) == true) {
            GZIPInputStream(body)
        } else {
            body
        }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
