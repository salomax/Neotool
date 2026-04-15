package io.github.salomax.neotool.banking.client.investment.financialdata

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.common.service.ServiceTokenClient
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HttpFundNavBulkSyncClientTest {
    private val serviceTokenClient: ServiceTokenClient = mock()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    @Test
    fun `streams gzip ndjson lines and forwards authorization header`() {
        val mockServer = MockWebServer()
        mockServer.start(18094)
        try {
            runBlocking {
                whenever(serviceTokenClient.getServiceToken("neotool-financial-data-service")).thenReturn("service-token")
            }
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/x-ndjson")
                    .addHeader("Content-Encoding", "gzip")
                    .setBody(
                        gzipBody(
                            """
                            {"cursor":"cursor-1","cnpj":"12345678000199","date":"2026-01-31","closePrice":100.10,"updatedAt":"2026-04-08T10:00:00Z"}
                            {"cursor":"cursor-2","cnpj":"12345678000199","date":"2026-02-28","closePrice":101.20,"updatedAt":"2026-04-08T10:00:01Z"}
                            """.trimIndent(),
                        ),
                    ),
            )

            val client =
                HttpFundNavBulkSyncClient(
                    financialDataServiceUrl = mockServer.url("/").toString().removeSuffix("/"),
                    exportPath = "/internal/market-data/fund-navs/export",
                    targetAudience = "neotool-financial-data-service",
                    connectTimeoutSeconds = 10,
                    readTimeoutSeconds = 30,
                    serviceTokenClient = serviceTokenClient,
                    objectMapper = objectMapper,
                )

            val items = mutableListOf<FundNavBulkSyncNode>()
            client.streamFundDailyNavs(
                from = LocalDate.of(2026, 1, 1),
                to = LocalDate.of(2026, 2, 28),
                updatedSince = null,
                cnpjs = listOf("12345678000199"),
                after = "cursor-prev",
            ) { items += it }

            assertThat(items).hasSize(2)
            assertThat(items.map { it.cursor }).containsExactly("cursor-1", "cursor-2")
            assertThat(items.map { it.closePrice }).containsExactly(BigDecimal("100.10"), BigDecimal("101.20"))

            val request = mockServer.takeRequest()
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer service-token")
            assertThat(request.getHeader("Accept")).isEqualTo("application/x-ndjson")
            assertThat(request.getHeader("Accept-Encoding")).contains("gzip")
            assertThat(request.path).contains("from=2026-01-01")
            assertThat(request.path).contains("to=2026-02-28")
            assertThat(request.path).contains("cnpjs=12345678000199")
            assertThat(request.path).contains("after=cursor-prev")
        } finally {
            mockServer.shutdown()
        }
    }

    private fun gzipBody(raw: String): okio.Buffer {
        val bytes =
            ByteArrayOutputStream().use { output ->
                GZIPOutputStream(output).use { gzip ->
                    gzip.write(raw.toByteArray())
                }
                output.toByteArray()
            }
        return okio.Buffer().write(bytes)
    }
}
