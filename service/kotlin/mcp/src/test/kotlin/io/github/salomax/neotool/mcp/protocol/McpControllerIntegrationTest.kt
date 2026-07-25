package io.github.salomax.neotool.mcp.protocol

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.mcp.audit.ToolCallAuditService
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
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("MCP Streamable HTTP protocol endpoint")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("mcp")
open class McpControllerIntegrationTest : TestPropertyProvider {
    private val transactionsReadScope = "finance:transactions:read"

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var objectMapper: ObjectMapper

    private var catalogTools: List<McpCatalogTool> = emptyList()
    private var bankingResponse: GraphQLResponse = emptyTransactionsResponse()
    private var capturedBankingVariables: Map<String, Any>? = null
    private var capturedBankingPrincipal: McpPrincipal? = null

    override fun getProperties(): MutableMap<String, String> =
        mutableMapOf(
            "jwt.issuer" to McpJwtTestFixture.ISSUER,
            "jwt.public-key" to McpJwtTestFixture.publicKeyPem,
            "mcp.resource.audience" to McpJwtTestFixture.AUDIENCE,
            "mcp.resource.metadata-url" to "https://mcp.example.test/.well-known/oauth-protected-resource",
            "datasources.default.enabled" to "false",
            "flyway.datasources.default.enabled" to "false",
        )

    @BeforeEach
    fun resetMocks() {
        catalogTools = emptyList()
        bankingResponse = emptyTransactionsResponse()
        capturedBankingVariables = null
        capturedBankingPrincipal = null
    }

    @Test
    fun `initialize negotiates protocol version and advertises tool capability`() {
        val response =
            retrieve(
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to 1,
                    "method" to "initialize",
                    "params" to
                        mapOf(
                            "protocolVersion" to "2025-06-18",
                            "capabilities" to emptyMap<String, Any>(),
                            "clientInfo" to mapOf("name" to "test-client", "version" to "1.0.0"),
                        ),
                ),
            )

        assertThat(response["jsonrpc"]).isEqualTo("2.0")
        assertThat(response["id"]).isEqualTo(1)
        val result = response["result"] as Map<*, *>
        assertThat(result["protocolVersion"]).isEqualTo("2025-06-18")
        assertThat((result["capabilities"] as Map<*, *>)["tools"]).isNotNull()
        assertThat((result["serverInfo"] as Map<*, *>)["name"]).isEqualTo("neotool-mcp")
    }

    @Test
    fun `tools call for unknown tool returns structured JSON-RPC error`() {
        val response =
            retrieve(
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to "call-1",
                    "method" to "tools/call",
                    "params" to mapOf("name" to "missing_tool", "arguments" to emptyMap<String, Any>()),
                ),
            )

        assertThat(response["result"]).isNull()
        val error = response["error"] as Map<*, *>
        assertThat(error["code"]).isEqualTo(-32602)
        assertThat(error["message"]).isEqualTo("Unknown tool")
        assertThat((error["data"] as Map<*, *>)["code"]).isEqualTo("tool_not_found")
    }

    @Test
    fun `initialized notification returns accepted without body`() {
        val token = McpJwtTestFixture.accessToken(scope = "finance:cashflow:read")

        val response =
            client
                .toBlocking()
                .exchange(
                    HttpRequest
                        .POST(
                            "/mcp",
                            mapOf(
                                "jsonrpc" to "2.0",
                                "method" to "notifications/initialized",
                            ),
                        ).header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                    String::class.java,
                )

        assertThat(response.status).isEqualTo(HttpStatus.ACCEPTED)
        assertThat(response.body()).isNull()
    }

    @Test
    fun `tools call dispatches with user context and renders MCP result`() {
        val accountId = UUID.randomUUID()
        catalogTools = listOf(tool(name = "list_transactions", requiredScope = transactionsReadScope))
        bankingResponse =
            GraphQLResponse(
                data =
                    mapOf(
                        "transactions" to
                            mapOf(
                                "edges" to
                                    listOf(
                                        mapOf(
                                            "cursor" to "cursor-1",
                                            "node" to
                                                mapOf(
                                                    "id" to "tx-1",
                                                    "amount" to 42.50,
                                                    "currencyCode" to "BRL",
                                                    "description" to "Coffee",
                                                ),
                                        ),
                                    ),
                                "pageInfo" to mapOf("hasNextPage" to false, "endCursor" to "cursor-1"),
                                "totalCount" to 1,
                            ),
                    ),
            )

        val response =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-1",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "list_transactions",
                                "arguments" to mapOf("first" to 5),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(accountId = accountId, scope = transactionsReadScope),
            )
        val result = response["result"] as Map<*, *>
        val content = (result["content"] as List<*>).first() as Map<*, *>
        val textBody = objectMapper.readValue(content["text"].toString(), Map::class.java)

        assertThat(result["isError"]).isEqualTo(false)
        assertThat(capturedBankingPrincipal?.principal?.currentAccountId).isEqualTo(accountId)
        assertThat(capturedBankingVariables).containsEntry("first", 5)
        assertThat(textBody.toString()).contains("<untrusted-data>Coffee</untrusted-data>")
    }

    @Test
    fun `unsupported crafted account id argument is rejected before banking dispatch`() {
        val accountId = UUID.randomUUID()
        catalogTools = listOf(tool(name = "list_transactions", requiredScope = transactionsReadScope))

        val response =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-2",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "list_transactions",
                                "arguments" to mapOf("first" to 5, "accountId" to UUID.randomUUID().toString()),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(accountId = accountId, scope = transactionsReadScope),
            )

        val result = response["result"] as Map<*, *>
        assertThat(result["isError"]).isEqualTo(true)
        assertThat(capturedBankingVariables).isNull()
        assertThat(capturedBankingPrincipal).isNull()
    }

    @Test
    fun `transactions tool is not visible with only cashflow read scope`() {
        catalogTools = listOf(tool(name = "list_transactions", requiredScope = transactionsReadScope))

        val response =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "list-1",
                        "method" to "tools/list",
                    ),
                token = McpJwtTestFixture.accessToken(scope = "finance:cashflow:read"),
            )
        assertThat(response.toString()).doesNotContain("list_transactions")
    }

    @Test
    fun `tools call does not dispatch when token lacks tool required scope`() {
        catalogTools = listOf(tool(name = "list_transactions", requiredScope = transactionsReadScope))

        val response =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-3",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "list_transactions",
                                "arguments" to mapOf("first" to 5),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(scope = "finance:cashflow:read"),
            )

        assertThat(response["result"]).isNull()
        assertThat((response["error"] as Map<*, *>)["message"]).isEqualTo("Unknown tool")
        assertThat(capturedBankingVariables).isNull()
    }

    @Test
    fun `sse endpoint streams tool call result`() {
        catalogTools = listOf(tool(name = "list_transactions", requiredScope = transactionsReadScope))
        val token = McpJwtTestFixture.accessToken(scope = transactionsReadScope)

        val response =
            client
                .toBlocking()
                .exchange(
                    HttpRequest
                        .POST(
                            "/mcp/events",
                            mapOf(
                                "jsonrpc" to "2.0",
                                "id" to "stream-1",
                                "method" to "tools/call",
                                "params" to
                                    mapOf(
                                        "name" to "list_transactions",
                                        "arguments" to mapOf("first" to 1),
                                    ),
                            ),
                        ).header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                    String::class.java,
                )

        assertThat(response.status).isEqualTo(HttpStatus.OK)
        assertThat(response.body()).contains("stream-1").contains("transactions")
    }

    @Test
    fun `protocol endpoint requires bearer token`() {
        val exception =
            assertThrows<HttpClientResponseException> {
                client
                    .toBlocking()
                    .exchange(
                        HttpRequest.POST("/mcp", mapOf("jsonrpc" to "2.0", "id" to 1, "method" to "initialize")),
                        OAuthError::class.java,
                    )
            }

        assertThat(exception.status).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `invalid token tools call does not dispatch to banking`() {
        catalogTools = listOf(tool(name = "list_transactions", requiredScope = transactionsReadScope))

        val exception =
            assertThrows<HttpClientResponseException> {
                client
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
        assertThat(capturedBankingVariables).isNull()
        assertThat(capturedBankingPrincipal).isNull()
    }

    private fun retrieve(
        body: Map<String, Any?>,
        token: String = McpJwtTestFixture.accessToken(scope = "finance:cashflow:read"),
    ): Map<*, *> {
        return try {
            client
                .toBlocking()
                .retrieve(
                    HttpRequest
                        .POST("/mcp", body)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token"),
                    Map::class.java,
                )
        } catch (e: HttpClientResponseException) {
            throw AssertionError("body=${e.response.getBody(String::class.java).orElse("<empty>")}", e)
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
            ): GraphQLResponse {
                capturedBankingVariables = variables
                capturedBankingPrincipal = principal
                return bankingResponse
            }
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
