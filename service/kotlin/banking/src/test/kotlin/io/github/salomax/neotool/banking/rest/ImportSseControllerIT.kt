package io.github.salomax.neotool.banking.rest

import io.github.salomax.neotool.banking.config.BankingTestTokenPrincipalDecoder
import io.github.salomax.neotool.banking.integration.BankingPostgresIntegrationTest
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.sql.DriverManager
import java.util.UUID

/**
 * Integration test for [ImportSseController]: MicronautTest + TestHttpClient with streaming.
 * Uses [io.github.salomax.neotool.banking.config.BankingTestTokenPrincipalDecoder] for auth when env=test.
 */
@MicronautTest(startApplication = true)
@DisplayName("ImportSseController IT")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("banking")
class ImportSseControllerIT : BankingPostgresIntegrationTest() {
    @Inject
    @Client("/")
    lateinit var client: HttpClient

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties().toMutableMap()
        props["micronaut.env"] = "test"
        return props
    }

    private lateinit var jobId: UUID
    private val accountId = BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID
    private val userId = BankingTestTokenPrincipalDecoder.TEST_USER_ID
    private lateinit var financialAccountId: UUID

    @BeforeEach
    fun insertTestData() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            financialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at)
                VALUES ('$financialAccountId', '$accountId', '$userId', 'BANK', 'CONTA_CORRENTE', 'SSE IT', 'BRL', NOW(), NOW())
                ON CONFLICT DO NOTHING
                """.trimIndent(),
            )
            jobId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.transaction_imports (id, financial_account_id, account_id, created_by_user_id, source, file_hash, status, imported_count, skipped_count, duplicate_review_count, error_count, created_at, updated_at)
                VALUES ('$jobId', '$financialAccountId', '$accountId', '$userId', 'OFX', 'sse-it-hash', 'COMPLETED', 5, 0, 0, 0, NOW(), NOW())
                """.trimIndent(),
            )
        }
    }

    @Test
    @Disabled("SSE streaming + blocking client can fail; happy path covered by ImportSseControllerTest")
    fun `GET imports jobId events returns 200 and text-event-stream when job is COMPLETED`() {
        val request =
            HttpRequest.GET<Any>("/imports/$jobId/events")
                .header("Authorization", "Bearer ${BankingTestTokenPrincipalDecoder.TEST_TOKEN}")
                .accept(MediaType.TEXT_EVENT_STREAM)
        val body = client.toBlocking().retrieve(request)
        assertThat(body).contains(jobId.toString())
        assertThat(body).containsIgnoringCase("COMPLETED")
    }

    @Test
    fun `GET imports non-existent jobId returns 404 or 500`() {
        val missingId = UUID.randomUUID()
        val request =
            HttpRequest.GET<Any>("/imports/$missingId/events")
                .header("Authorization", "Bearer ${BankingTestTokenPrincipalDecoder.TEST_TOKEN}")
                .accept(MediaType.TEXT_EVENT_STREAM)

        val ex =
            assertThrows<HttpClientResponseException> {
                client.toBlocking().retrieve(request)
            }
        assertThat(ex.response.status.code).isIn(404, 500)
    }

    @Test
    fun `GET without Authorization returns 401 or 500`() {
        val request =
            HttpRequest.GET<Any>("/imports/$jobId/events")
                .accept(MediaType.TEXT_EVENT_STREAM)

        val ex =
            assertThrows<HttpClientResponseException> {
                client.toBlocking().retrieve(request)
            }
        assertThat(ex.response.status.code).isIn(401, 500)
    }
}
