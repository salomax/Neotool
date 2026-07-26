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
    private val transactionsWriteScope = "finance:transactions:write"
    private val rulesWriteScope = "finance:rules:write"

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var objectMapper: ObjectMapper

    private var catalogTools: List<McpCatalogTool> = emptyList()
    private var bankingResponse: GraphQLResponse = emptyTransactionsResponse()
    private var capturedBankingVariables: Map<String, Any>? = null
    private var capturedBankingPrincipal: McpPrincipal? = null
    private var bankingDispatchCount: Int = 0

    override fun getProperties(): MutableMap<String, String> =
        mutableMapOf(
            "jwt.issuer" to McpJwtTestFixture.ISSUER,
            "jwt.public-key" to McpJwtTestFixture.publicKeyPem,
            "mcp.resource.audience" to McpJwtTestFixture.AUDIENCE,
            "mcp.resource.metadata-url" to "https://mcp.example.test/.well-known/oauth-protected-resource",
            "micronaut.executors.scheduled.enabled" to "false",
            "mcp.rate-limit.cleanup.enabled" to "false",
            "mcp.revocation.eviction.enabled" to "false",
            "datasources.default.enabled" to "false",
            "flyway.datasources.default.enabled" to "false",
        )

    @BeforeEach
    fun resetMocks() {
        catalogTools = emptyList()
        bankingResponse = emptyTransactionsResponse()
        capturedBankingVariables = null
        capturedBankingPrincipal = null
        bankingDispatchCount = 0
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
    fun `write tools are not visible with only read scopes`() {
        catalogTools =
            listOf(
                tool(name = "create_transaction", requiredScope = transactionsWriteScope),
                tool(name = "delete_transaction", requiredScope = transactionsWriteScope),
                tool(name = "create_transaction_rule", requiredScope = rulesWriteScope),
                tool(name = "categorize_transactions", requiredScope = transactionsWriteScope),
                tool(name = "apply_rule_to_historical", requiredScope = rulesWriteScope),
            )

        val response =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "list-write-1",
                        "method" to "tools/list",
                    ),
                token = McpJwtTestFixture.accessToken(scope = transactionsReadScope),
            )

        assertThat(response.toString()).doesNotContain("create_transaction")
        assertThat(response.toString()).doesNotContain("delete_transaction")
        assertThat(response.toString()).doesNotContain("create_transaction_rule")
        assertThat(response.toString()).doesNotContain("categorize_transactions")
        assertThat(response.toString()).doesNotContain("apply_rule_to_historical")
    }

    @Test
    fun `write catalog does not expose pending action confirmation tool`() {
        catalogTools =
            listOf(
                tool(name = "create_transaction", requiredScope = transactionsWriteScope),
                tool(name = "delete_transaction", requiredScope = transactionsWriteScope),
                tool(name = "categorize_transactions", requiredScope = transactionsWriteScope),
            )

        val response =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "list-write-confirmation-guard",
                        "method" to "tools/list",
                    ),
                token = McpJwtTestFixture.accessToken(scope = transactionsWriteScope),
            )

        assertThat(response.toString()).doesNotContain("confirmPendingAction")
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
    fun `read-only token cannot call write tool`() {
        catalogTools = listOf(tool(name = "create_transaction", requiredScope = transactionsWriteScope))

        val response =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-write-readonly",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "create_transaction",
                                "arguments" to
                                    mapOf(
                                        "idempotency_key" to "key-1",
                                        "financialAccountId" to UUID.randomUUID().toString(),
                                        "creditDebitType" to "DEBIT",
                                        "type" to "OTHER_EXPENSE",
                                        "amount" to 10.0,
                                        "date" to "2026-07-25",
                                        "description" to "Coffee",
                                    ),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(scope = transactionsReadScope),
            )

        assertThat((response["error"] as Map<*, *>)["message"]).isEqualTo("Unknown tool")
        assertThat(capturedBankingVariables).isNull()
    }

    @Test
    fun `transactions write scope can call create transaction but not rule tool`() {
        catalogTools =
            listOf(
                tool(name = "create_transaction", requiredScope = transactionsWriteScope),
                tool(name = "create_transaction_rule", requiredScope = rulesWriteScope),
                tool(name = "categorize_transactions", requiredScope = transactionsWriteScope),
                tool(name = "apply_rule_to_historical", requiredScope = rulesWriteScope),
            )
        bankingResponse =
            GraphQLResponse(
                data =
                    mapOf(
                        "createTransaction" to
                            mapOf(
                                "transaction" to
                                    mapOf(
                                        "id" to "tx-1",
                                        "amount" to 42.5,
                                        "currencyCode" to "BRL",
                                        "description" to "Coffee",
                                    ),
                            ),
                    ),
            )

        val allowedResponse =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-write-allowed",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "create_transaction",
                                "arguments" to
                                    mapOf(
                                        "idempotency_key" to "key-1",
                                        "financialAccountId" to UUID.randomUUID().toString(),
                                        "creditDebitType" to "DEBIT",
                                        "type" to "OTHER_EXPENSE",
                                        "amount" to 10.0,
                                        "date" to "2026-07-25",
                                        "description" to "Coffee",
                                    ),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(scope = transactionsWriteScope),
            )

        assertThat((allowedResponse["result"] as Map<*, *>)["isError"]).isEqualTo(false)
        assertThat(capturedBankingVariables).containsEntry("idempotencyKey", "key-1")

        capturedBankingVariables = null
        capturedBankingPrincipal = null
        val forbiddenResponse =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-rule-forbidden",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "create_transaction_rule",
                                "arguments" to
                                    mapOf(
                                        "idempotency_key" to "key-2",
                                        "pattern" to "ifood",
                                        "patternType" to "CONTAINS",
                                        "applicableTo" to "EXPENSE",
                                        "typeOverride" to "TRANSFER",
                                    ),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(scope = transactionsWriteScope),
            )

        assertThat((forbiddenResponse["error"] as Map<*, *>)["message"]).isEqualTo("Unknown tool")
        assertThat(capturedBankingVariables).isNull()
    }

    @Test
    fun `transactions write scope can call categorize transactions but not apply rule to historical`() {
        val accountId = UUID.randomUUID()
        val transactionIds = listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val categoryId = UUID.randomUUID().toString()
        catalogTools =
            listOf(
                tool(name = "categorize_transactions", requiredScope = transactionsWriteScope),
                tool(name = "apply_rule_to_historical", requiredScope = rulesWriteScope),
            )
        bankingResponse =
            GraphQLResponse(
                data =
                    mapOf(
                        "bulkCategorize" to
                            mapOf(
                                "affectedCount" to 3,
                                "errors" to emptyList<String>(),
                                "outcome" to "CONFIRMATION_REQUIRED",
                            ),
                    ),
            )

        val allowedResponse =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-bulk-categorize",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "categorize_transactions",
                                "arguments" to
                                    mapOf(
                                        "idempotency_key" to "bulk-key-1",
                                        "transactionIds" to transactionIds,
                                        "categoryId" to categoryId,
                                    ),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(accountId = accountId, scope = transactionsWriteScope),
            )

        val result = allowedResponse["result"] as Map<*, *>
        val content = (result["content"] as List<*>).first() as Map<*, *>
        val textBody = objectMapper.readValue(content["text"].toString(), Map::class.java)
        val payload = textBody["bulkCategorize"] as Map<*, *>

        assertThat(result["isError"]).isEqualTo(false)
        assertThat(capturedBankingPrincipal?.principal?.currentAccountId).isEqualTo(accountId)
        assertThat(capturedBankingVariables).containsEntry("idempotencyKey", "bulk-key-1")
        assertThat(capturedBankingVariables).containsEntry("transactionIds", transactionIds)
        assertThat(capturedBankingVariables).containsEntry("categoryId", categoryId)
        assertThat(payload["outcome"]).isEqualTo("CONFIRMATION_REQUIRED")

        capturedBankingVariables = null
        val forbiddenResponse =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-apply-rule-forbidden",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "apply_rule_to_historical",
                                "arguments" to
                                    mapOf(
                                        "id" to UUID.randomUUID().toString(),
                                        "idempotency_key" to "rule-key-1",
                                    ),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(scope = transactionsWriteScope),
            )

        assertThat((forbiddenResponse["error"] as Map<*, *>)["message"]).isEqualTo("Unknown tool")
        assertThat(capturedBankingVariables).isNull()
    }

    @Test
    fun `rules write scope can call apply rule to historical but not categorize transactions`() {
        catalogTools =
            listOf(
                tool(name = "categorize_transactions", requiredScope = transactionsWriteScope),
                tool(name = "apply_rule_to_historical", requiredScope = rulesWriteScope),
            )
        bankingResponse =
            GraphQLResponse(
                data =
                    mapOf(
                        "applyTransactionRuleToHistorical" to
                            mapOf(
                                "affectedCount" to 2,
                                "errors" to emptyList<String>(),
                                "outcome" to "EXECUTED",
                            ),
                    ),
            )

        val allowedResponse =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-apply-rule-allowed",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "apply_rule_to_historical",
                                "arguments" to
                                    mapOf(
                                        "id" to UUID.randomUUID().toString(),
                                        "idempotency_key" to "rule-key-2",
                                    ),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(scope = rulesWriteScope),
            )

        val result = allowedResponse["result"] as Map<*, *>
        val content = (result["content"] as List<*>).first() as Map<*, *>
        val textBody = objectMapper.readValue(content["text"].toString(), Map::class.java)
        val payload = textBody["applyTransactionRuleToHistorical"] as Map<*, *>

        assertThat(result["isError"]).isEqualTo(false)
        assertThat(capturedBankingVariables).containsEntry("idempotencyKey", "rule-key-2")
        assertThat(payload["outcome"]).isEqualTo("EXECUTED")

        capturedBankingVariables = null
        val forbiddenResponse =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-bulk-categorize-forbidden",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "categorize_transactions",
                                "arguments" to
                                    mapOf(
                                        "idempotency_key" to "bulk-key-2",
                                        "transactionIds" to listOf(UUID.randomUUID().toString()),
                                        "categoryId" to UUID.randomUUID().toString(),
                                    ),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(scope = rulesWriteScope),
            )

        assertThat((forbiddenResponse["error"] as Map<*, *>)["message"]).isEqualTo("Unknown tool")
        assertThat(capturedBankingVariables).isNull()
    }

    @Test
    fun `create transaction tools call attaches agent call context for banking dispatch`() {
        catalogTools = listOf(tool(name = "create_transaction", requiredScope = transactionsWriteScope))
        bankingResponse = createTransactionBankingResponse(id = "tx-agent-1", description = "Agent context")

        val response =
            retrieve(
                body = createTransactionCallBody(id = "call-agent-context", idempotencyKey = "agent-key"),
                token = McpJwtTestFixture.accessToken(scope = transactionsWriteScope, clientId = "agent-client-a"),
            )

        assertThat((response["result"] as Map<*, *>)["isError"]).isEqualTo(false)
        val principal = requireNotNull(capturedBankingPrincipal)
        assertThat(principal.clientId).isEqualTo("agent-client-a")
        assertThat(principal.agentCallContext).isNotNull()
        assertThat(principal.agentCallContext!!.originClientId).isEqualTo("agent-client-a")
        assertThat(principal.agentCallContext!!.toolCallId).isNotNull()
        assertThat(capturedBankingVariables).containsEntry("idempotencyKey", "agent-key")
    }

    @Test
    fun `create transaction tools call with same idempotency key dispatches key on every call`() {
        catalogTools = listOf(tool(name = "create_transaction", requiredScope = transactionsWriteScope))
        bankingResponse = createTransactionBankingResponse(id = "tx-idempotent-1", description = "Idempotent")

        val first =
            retrieve(
                body = createTransactionCallBody(id = "call-idempotent-1", idempotencyKey = "same-key"),
                token = McpJwtTestFixture.accessToken(scope = transactionsWriteScope),
            )
        val second =
            retrieve(
                body = createTransactionCallBody(id = "call-idempotent-2", idempotencyKey = "same-key"),
                token = McpJwtTestFixture.accessToken(scope = transactionsWriteScope),
            )

        assertThat((first["result"] as Map<*, *>)["isError"]).isEqualTo(false)
        assertThat((second["result"] as Map<*, *>)["isError"]).isEqualTo(false)
        assertThat(bankingDispatchCount).isEqualTo(2)
        assertThat(capturedBankingVariables).containsEntry("idempotencyKey", "same-key")
    }

    @Test
    fun `create transaction tools call rejects unsupported account argument before banking dispatch`() {
        catalogTools = listOf(tool(name = "create_transaction", requiredScope = transactionsWriteScope))

        val response =
            retrieve(
                body =
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to "call-unsupported-account",
                        "method" to "tools/call",
                        "params" to
                            mapOf(
                                "name" to "create_transaction",
                                "arguments" to
                                    mapOf(
                                        "accountId" to UUID.randomUUID().toString(),
                                        "idempotency_key" to "key-1",
                                        "financialAccountId" to UUID.randomUUID().toString(),
                                        "creditDebitType" to "DEBIT",
                                        "type" to "OTHER_EXPENSE",
                                        "amount" to 10.0,
                                        "date" to "2026-07-25",
                                        "description" to "Unsupported",
                                    ),
                            ),
                    ),
                token = McpJwtTestFixture.accessToken(scope = transactionsWriteScope),
            )

        val result = response["result"] as Map<*, *>
        assertThat(result["isError"]).isEqualTo(true)
        assertThat(bankingDispatchCount).isZero()
        assertThat(capturedBankingVariables).isNull()
    }

    @Test
    fun `delete transaction tools call forwards id and idempotency key`() {
        val accountId = UUID.randomUUID()
        val transactionId = UUID.randomUUID().toString()
        catalogTools = listOf(tool(name = "delete_transaction", requiredScope = transactionsWriteScope))
        bankingResponse =
            GraphQLResponse(
                data =
                    mapOf(
                        "deleteTransaction" to
                            mapOf(
                                "deleted" to false,
                                "pendingAction" to
                                    mapOf(
                                        "id" to "pending-1",
                                        "type" to "AGENT_WRITE_CONFIRMATION",
                                        "severity" to "ACTION_REQUIRED",
                                        "status" to "PENDING",
                                        "entityType" to "DeleteTransactionConfirmationPayload",
                                        "entityId" to UUID.randomUUID().toString(),
                                        "payload" to emptyMap<String, Any>(),
                                    ),
                            ),
                    ),
            )

        val response =
            retrieve(
                body =
                    deleteTransactionCallBody(
                        id = "call-delete-1",
                        idempotencyKey = "delete-key-1",
                        transactionId = transactionId,
                    ),
                token = McpJwtTestFixture.accessToken(accountId = accountId, scope = transactionsWriteScope),
            )

        val result = response["result"] as Map<*, *>
        val content = (result["content"] as List<*>).first() as Map<*, *>
        val textBody = objectMapper.readValue(content["text"].toString(), Map::class.java)
        assertThat(result["isError"]).isEqualTo(false)
        val payload = textBody["deleteTransaction"] as Map<*, *>
        assertThat(capturedBankingVariables).containsEntry("id", transactionId)
        assertThat(capturedBankingVariables).containsEntry("idempotencyKey", "delete-key-1")
        assertThat(payload["deleted"]).isEqualTo(false)
        assertThat(payload["pendingAction"]).isInstanceOf(Map::class.java)
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
                bankingDispatchCount += 1
                capturedBankingVariables = variables
                capturedBankingPrincipal = principal
                return bankingResponse
            }

            override suspend fun mutation(
                query: String,
                variables: Map<String, Any>,
                principal: McpPrincipal,
            ): GraphQLResponse {
                bankingDispatchCount += 1
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

    private fun createTransactionCallBody(
        id: String,
        idempotencyKey: String,
    ): Map<String, Any?> =
        mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "method" to "tools/call",
            "params" to
                mapOf(
                    "name" to "create_transaction",
                    "arguments" to
                        mapOf(
                            "idempotency_key" to idempotencyKey,
                            "financialAccountId" to UUID.randomUUID().toString(),
                            "creditDebitType" to "DEBIT",
                            "type" to "OTHER_EXPENSE",
                            "amount" to 10.0,
                            "date" to "2026-07-25",
                            "description" to "Coffee",
                        ),
                ),
        )

    private fun deleteTransactionCallBody(
        id: String,
        idempotencyKey: String,
        transactionId: String,
    ): Map<String, Any?> =
        mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "method" to "tools/call",
            "params" to
                mapOf(
                    "name" to "delete_transaction",
                    "arguments" to
                        mapOf(
                            "id" to transactionId,
                            "idempotency_key" to idempotencyKey,
                        ),
                ),
        )

    private fun createTransactionBankingResponse(
        id: String,
        description: String,
    ): GraphQLResponse =
        GraphQLResponse(
            data =
                mapOf(
                    "createTransaction" to
                        mapOf(
                            "transaction" to
                                mapOf(
                                    "id" to id,
                                    "amount" to 10.0,
                                    "currencyCode" to "BRL",
                                    "description" to description,
                                ),
                        ),
                ),
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
