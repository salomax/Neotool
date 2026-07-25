package io.github.salomax.neotool.mcp.audit

import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.mcp.catalog.McpCatalogTool
import io.github.salomax.neotool.mcp.catalog.McpToolCatalog
import io.github.salomax.neotool.mcp.dispatch.McpBankingGraphQLClient
import io.github.salomax.neotool.mcp.security.ConnectionRevocationStore
import io.github.salomax.neotool.mcp.security.McpPrincipal
import io.github.salomax.neotool.mcp.security.OAuthError
import io.github.salomax.neotool.mcp.test.McpJwtTestFixture
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = true, transactional = false)
@DisplayName("MCP tool-call audit integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("mcp")
open class ToolCallAuditIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest,
    TestPropertyProvider {
    @Inject
    lateinit var repository: ToolCallAuditLogRepository

    private var catalogTools: List<McpCatalogTool> = emptyList()
    private var bankingResponse: GraphQLResponse = emptyTransactionsResponse()

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties()
        props["jwt.issuer"] = McpJwtTestFixture.ISSUER
        props["jwt.public-key"] = McpJwtTestFixture.publicKeyPem
        props["mcp.resource.audience"] = McpJwtTestFixture.AUDIENCE
        props["mcp.resource.metadata-url"] = "https://mcp.example.test/.well-known/oauth-protected-resource"
        return props
    }

    @BeforeEach
    override fun setUp() {
        super.setUp()
        truncateAuditLog()
        catalogTools = emptyList()
        bankingResponse = emptyTransactionsResponse()
    }

    @AfterEach
    fun cleanUp() {
        truncateAuditLog()
    }

    @Test
    fun `successful tool call persists redacted audit row`() {
        catalogTools =
            listOf(
                tool(
                    name = "list_transactions",
                    requiredScope = "finance:transactions:read",
                    sensitiveInputFields = listOf("first"),
                ),
            )

        val response =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-success",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "list_transactions",
                                "arguments" to mapOf("first" to 5),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(scope = "finance:transactions:read"),
            )

        assertThat(response["error"]).isNull()
        val saved = repository.findAll().single()
        assertThat(saved.outcome).isEqualTo(ToolCallAuditOutcome.SUCCESS)
        assertThat(saved.toolName).isEqualTo("list_transactions")
        assertThat(saved.clientId).isEqualTo("test-agent")
        assertThat(saved.tokenId).isNotBlank()
        assertThat(saved.arguments["first"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
        assertThat(saved.latencyMs).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `unknown tool call persists error audit row`() {
        val response =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-unknown",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "missing_tool",
                                "arguments" to
                                    mapOf(
                                        "secret" to "top-secret",
                                        "nested" to mapOf("token" to "nested-token"),
                                    ),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(scope = "finance:transactions:read"),
            )

        val error = response["error"] as Map<*, *>
        assertThat(error["message"]).isEqualTo("Unknown tool")
        val saved = repository.findAll().single()
        assertThat(saved.outcome).isEqualTo(ToolCallAuditOutcome.ERROR)
        assertThat(saved.errorCode).isEqualTo("tool_not_found")
        assertThat(saved.toolName).isEqualTo("missing_tool")
        assertThat(saved.arguments["secret"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
        assertThat((saved.arguments["nested"] as Map<*, *>)["token"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
    }

    @Test
    fun `scope rejected tool call persists rejected audit row`() {
        catalogTools =
            listOf(
                tool(
                    name = "list_transactions",
                    requiredScope = "finance:transactions:read",
                ),
            )

        val response =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-rejected",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "list_transactions",
                                "arguments" to mapOf("first" to 5),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(scope = "finance:cashflow:read"),
            )

        val error = response["error"] as Map<*, *>
        assertThat(error["message"]).isEqualTo("Unknown tool")
        val saved = repository.findAll().single()
        assertThat(saved.outcome).isEqualTo(ToolCallAuditOutcome.REJECTED)
        assertThat(saved.errorCode).isEqualTo("insufficient_scope")
        assertThat(saved.toolName).isEqualTo("list_transactions")
    }

    @Test
    fun `invalid token tools call is audited as rejected`() {
        catalogTools =
            listOf(
                tool(
                    name = "list_transactions",
                    requiredScope = "finance:transactions:read",
                    sensitiveInputFields = listOf("first"),
                ),
            )

        val exception =
            assertThrows<HttpClientResponseException> {
                httpClient
                    .toBlocking()
                    .exchange(
                        HttpRequest
                            .POST(
                                "/mcp",
                                mapOf(
                                    "jsonrpc" to "2.0",
                                    "id" to "call-invalid-token",
                                    "method" to "tools/call",
                                    "params" to
                                        mapOf(
                                            "name" to "list_transactions",
                                            "arguments" to mapOf("first" to 5),
                                        ),
                                ),
                            ).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"),
                        OAuthError::class.java,
                    )
            }

        assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        val saved = repository.findAll().single()
        assertThat(saved.outcome).isEqualTo(ToolCallAuditOutcome.REJECTED)
        assertThat(saved.errorCode).isEqualTo("invalid_token")
        assertThat(saved.toolName).isEqualTo("list_transactions")
        assertThat(saved.arguments["first"]).isEqualTo(ToolInputRedactor.REDACTED_VALUE)
    }

    @Test
    fun `tool call audit log rejects update and delete at database level`() {
        val entry =
            repository.save(
                ToolCallAuditLogEntity(
                    id = UUID.randomUUID(),
                    occurredAt = Instant.now(),
                    userId = UUID.randomUUID(),
                    clientId = "agent-a",
                    tokenId = "token-a",
                    toolName = "list_transactions",
                    arguments = mapOf("first" to 5),
                    outcome = ToolCallAuditOutcome.SUCCESS,
                    latencyMs = 12,
                    createdAt = Instant.now(),
                ),
            )

        val container = getPgContainer()
        assertThrows<Exception> {
            DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
                conn.prepareStatement("UPDATE mcp.tool_call_audit_log SET outcome = ? WHERE id = ?").use { stmt ->
                    stmt.setString(1, ToolCallAuditOutcome.ERROR.name)
                    stmt.setObject(2, entry.id)
                    stmt.executeUpdate()
                }
            }
        }
        assertThrows<Exception> {
            DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
                conn.prepareStatement("DELETE FROM mcp.tool_call_audit_log WHERE id = ?").use { stmt ->
                    stmt.setObject(1, entry.id)
                    stmt.executeUpdate()
                }
            }
        }
    }

    private fun truncateAuditLog() {
        val container = getPgContainer()
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("TRUNCATE TABLE mcp.tool_call_audit_log")
            }
        }
    }

    private fun retrieve(
        body: Map<String, Any?>,
        token: String,
    ): Map<*, *> =
        try {
            httpClient
                .toBlocking()
                .retrieve(
                    HttpRequest.POST("/mcp", body).header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                    Map::class.java,
                )
        } catch (e: HttpClientResponseException) {
            throw AssertionError("body=${e.response.getBody(String::class.java).orElse("<empty>")}", e)
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
            ): GraphQLResponse = bankingResponse
        }

    private fun tool(
        name: String,
        requiredScope: String,
        sensitiveInputFields: List<String> = emptyList(),
    ): McpCatalogTool =
        McpCatalogTool(
            name = name,
            description = "$name description",
            inputSchema = mapOf("type" to "object"),
            mcpExposed = true,
            mcpRequiredScope = requiredScope,
            sensitiveInputFields = sensitiveInputFields,
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
