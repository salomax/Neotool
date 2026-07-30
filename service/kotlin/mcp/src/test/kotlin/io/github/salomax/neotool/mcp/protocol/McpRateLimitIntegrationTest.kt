package io.github.salomax.neotool.mcp.protocol

import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.mcp.audit.ToolCallAuditService
import io.github.salomax.neotool.mcp.catalog.McpCatalogTool
import io.github.salomax.neotool.mcp.catalog.McpToolCatalog
import io.github.salomax.neotool.mcp.dispatch.McpBankingGraphQLClient
import io.github.salomax.neotool.mcp.security.ConnectionRevocationStore
import io.github.salomax.neotool.mcp.security.McpPrincipal
import io.github.salomax.neotool.mcp.test.McpJwtTestFixture
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = true, transactional = false)
@DisplayName("MCP rate limit integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("mcp")
open class McpRateLimitIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest,
    TestPropertyProvider {
    private val transactionsReadScope = "finance:transactions:read"
    private var catalogTools: List<McpCatalogTool> = emptyList()

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties()
        props["jwt.issuer"] = McpJwtTestFixture.ISSUER
        props["jwt.public-key"] = McpJwtTestFixture.publicKeyPem
        props["mcp.resource.audience"] = McpJwtTestFixture.AUDIENCE
        props["mcp.resource.metadata-url"] = "https://mcp.example.test/.well-known/oauth-protected-resource"
        props["mcp.rate-limit.cleanup.enabled"] = "false"
        props["mcp.revocation.eviction.enabled"] = "false"
        return props
    }

    @BeforeEach
    fun resetState() {
        catalogTools = listOf(tool(name = "list_transactions", requiredScope = transactionsReadScope))
        val container = getPgContainer()
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE TABLE mcp.rate_limit_counters")
            }
        }
    }

    @Test
    fun `tool call over the limit returns 429 with retry after and succeeds again after window rollover`() {
        val token = McpJwtTestFixture.accessToken(scope = transactionsReadScope)
        // One allowed call creates the current-window counter rows; saturating via SQL avoids
        // burning through the default 60-request / 60s window (which flakes across minute boundaries).
        val underLimit = client.toBlocking().exchange(toolCallRequest(token), String::class.java)
        assertThat(underLimit.status).isEqualTo(HttpStatus.OK)
        saturateCounters()

        val exception =
            assertThrows<HttpClientResponseException> {
                client.toBlocking().exchange(toolCallRequest(token), String::class.java)
            }
        val overLimit = exception.response
        val countersAfterLimit = findCounters()

        assertThat(overLimit.status)
            .withFailMessage("expected 429 after saturating counters; counters were %s", countersAfterLimit)
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        val retryAfterSeconds = overLimit.header("Retry-After")!!.toLong()
        assertThat(retryAfterSeconds).isPositive()

        expireCounters()

        val afterRollover = client.toBlocking().exchange(toolCallRequest(token), String::class.java)
        assertThat(afterRollover.status).isEqualTo(HttpStatus.OK)
    }

    @Test
    fun `sse endpoint returns json 429 with retry after once the read limit is exceeded`() {
        val token = McpJwtTestFixture.accessToken(scope = transactionsReadScope)
        val underLimit = client.toBlocking().exchange(eventsRequest(token), String::class.java)
        assertThat(underLimit.status).isEqualTo(HttpStatus.OK)
        saturateCounters()

        val exception =
            assertThrows<HttpClientResponseException> {
                client.toBlocking().exchange(eventsRequest(token), String::class.java)
            }

        assertThat(exception.status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(exception.response.contentType.orElse(null)).isEqualTo(MediaType.APPLICATION_JSON_TYPE)
        assertThat(exception.response.header("Retry-After")).isNotBlank()
    }

    private fun toolCallRequest(token: String): HttpRequest<Map<String, Any>> =
        HttpRequest
            .POST(
                "/mcp",
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to "rate-limit-1",
                    "method" to "tools/call",
                    "params" to mapOf("name" to "list_transactions", "arguments" to mapOf("first" to 1)),
                ),
            ).header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun eventsRequest(token: String): HttpRequest<Map<String, Any>> =
        HttpRequest
            .POST(
                "/mcp/events",
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to "rate-limit-stream-1",
                    "method" to "tools/call",
                    "params" to mapOf("name" to "list_transactions", "arguments" to mapOf("first" to 1)),
                ),
            ).header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun findCounters(): List<String> {
        val container = getPgContainer()
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT scope, scope_id, window_start, window_size_ms, request_count
                FROM mcp.rate_limit_counters
                ORDER BY scope, window_start
                """.trimIndent(),
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val rows = mutableListOf<String>()
                    while (rs.next()) {
                        val scope = rs.getString("scope")
                        val scopeId = rs.getString("scope_id")
                        val windowStart = rs.getTimestamp("window_start").toInstant()
                        val windowSizeMs = rs.getLong("window_size_ms")
                        val requestCount = rs.getLong("request_count")
                        rows += "$scope:$scopeId:$windowStart:$windowSizeMs:$requestCount"
                    }
                    return rows
                }
            }
        }
    }

    private fun saturateCounters() {
        val container = getPgContainer()
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    UPDATE mcp.rate_limit_counters
                    SET request_count = 100000
                    """.trimIndent(),
                )
            }
        }
    }

    private fun expireCounters() {
        val container = getPgContainer()
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    UPDATE mcp.rate_limit_counters
                    SET window_start = window_start - ((window_size_ms + 1000) * INTERVAL '1 millisecond')
                    """.trimIndent(),
                )
            }
        }
    }

    @MockBean(ConnectionRevocationStore::class)
    fun connectionRevocationStore(): ConnectionRevocationStore =
        object : ConnectionRevocationStore {
            override fun isRevoked(
                userId: UUID,
                clientId: String,
                accountId: UUID,
            ): Boolean = false

            override fun deleteExpired(now: Instant): Long = 0
        }

    @MockBean(McpToolCatalog::class)
    fun mcpToolCatalog(): McpToolCatalog =
        object : McpToolCatalog {
            override fun listTools(): List<McpCatalogTool> = catalogTools
        }

    @MockBean(McpBankingGraphQLClient::class)
    fun mcpBankingGraphQLClient(): McpBankingGraphQLClient =
        object : McpBankingGraphQLClient {
            override suspend fun query(
                query: String,
                variables: Map<String, Any>,
                principal: McpPrincipal,
            ): GraphQLResponse = emptyTransactionsResponse()

            override suspend fun mutation(
                query: String,
                variables: Map<String, Any>,
                principal: McpPrincipal,
            ): GraphQLResponse = emptyTransactionsResponse()
        }

    @MockBean(ToolCallAuditService::class)
    fun toolCallAuditService(): ToolCallAuditService = mock()

    private fun tool(
        name: String,
        requiredScope: String,
    ): McpCatalogTool =
        McpCatalogTool(
            name = name,
            description = "$name description",
            inputSchema = mapOf("type" to "object"),
            mcpExposed = true,
            mcpRequiredScope = requiredScope,
        )

    private fun emptyTransactionsResponse(): GraphQLResponse =
        GraphQLResponse(
            data =
                mapOf(
                    "transactions" to
                        mapOf(
                            "edges" to emptyList<Map<String, Any?>>(),
                            "pageInfo" to mapOf("hasNextPage" to false, "endCursor" to null),
                            "totalCount" to 0,
                        ),
                ),
        )
}
