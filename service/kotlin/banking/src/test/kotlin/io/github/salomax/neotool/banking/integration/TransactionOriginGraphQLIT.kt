package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.config.BankingTestTokenPrincipalDecoder
import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Transaction origin GraphQL IT")
@Tag("integration")
@Tag("banking")
class TransactionOriginGraphQLIT : BankingPostgresIntegrationTest() {
    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    private lateinit var financialAccountId: UUID

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties().toMutableMap()
        props["micronaut.env"] = "test"
        return props
    }

    @BeforeEach
    fun seedFinancialAccount() {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            financialAccountId = UUID.randomUUID()
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (
                    id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at
                )
                VALUES (
                    '$financialAccountId',
                    '${BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID}',
                    '${BankingTestTokenPrincipalDecoder.TEST_USER_ID}',
                    'BANK',
                    'CONTA_CORRENTE',
                    'GraphQL Origin IT',
                    'BRL',
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `forged MCP headers on user requests are ignored and transaction remains UI-originated`() {
        val toolCallId = UUID.randomUUID()
        val description = "Forged UI ${UUID.randomUUID()}"

        val response =
            client.toBlocking().exchange(
                createTransactionRequest(
                    token = BankingTestTokenPrincipalDecoder.TEST_TOKEN,
                    description = description,
                    toolCallId = toolCallId,
                    clientId = "forged-client",
                    tokenId = "forged-token-id",
                ),
                Map::class.java,
            )

        assertThat(response.status).isEqualTo(HttpStatus.OK)
        val storedOrigin = loadOriginByDescription(description)

        assertThat(storedOrigin.originChannel).isEqualTo("UI")
        assertThat(storedOrigin.originClientId).isNull()
        assertThat(storedOrigin.originToolCallId).isNull()
    }

    @Test
    fun `trusted delegated MCP requests stamp MCP origin metadata`() {
        val toolCallId = UUID.randomUUID()
        val description = "Trusted MCP ${UUID.randomUUID()}"
        val idempotencyKey = "origin-key-${UUID.randomUUID()}"

        val response =
            client.toBlocking().exchange(
                createTransactionRequest(
                    token = BankingTestTokenPrincipalDecoder.MCP_SERVICE_TOKEN,
                    description = description,
                    toolCallId = toolCallId,
                    clientId = "mcp-client-a",
                    tokenId = "origin-token-id-a",
                    idempotencyKey = idempotencyKey,
                ),
                Map::class.java,
            )

        assertThat(response.status).isEqualTo(HttpStatus.OK)
        val storedOrigin = loadOriginByDescription(description)

        assertThat(storedOrigin.originChannel).isEqualTo("MCP")
        assertThat(storedOrigin.originClientId).isEqualTo("mcp-client-a")
        assertThat(storedOrigin.originToolCallId).isEqualTo(toolCallId)
    }

    private fun createTransactionRequest(
        token: String,
        description: String,
        toolCallId: UUID,
        clientId: String,
        tokenId: String,
        idempotencyKey: String? = null,
    ): HttpRequest<Map<String, Any>> {
        val query =
            """
            mutation CreateTransaction(${'$'}input: CreateTransactionInput!, ${'$'}idempotencyKey: String) {
              createTransaction(input: ${'$'}input, idempotencyKey: ${'$'}idempotencyKey) {
                transaction {
                  id
                }
              }
            }
            """.trimIndent()
        val body =
            mapOf(
                "query" to query,
                "variables" to
                    mapOf(
                        "input" to
                            mapOf(
                                "financialAccountId" to financialAccountId.toString(),
                                "creditDebitType" to "DEBIT",
                                "type" to "OTHER_EXPENSE",
                                "amount" to 10.0,
                                "date" to LocalDate.now().toString(),
                                "description" to description,
                            ),
                        "idempotencyKey" to idempotencyKey,
                    ),
            )

        return HttpRequest.POST("/graphql", body)
            .header("Authorization", "Bearer $token")
            .header(AgentCallContext.TOOL_CALL_ID_HEADER, toolCallId.toString())
            .header(AgentCallContext.CLIENT_ID_HEADER, clientId)
            .header(AgentCallContext.TOKEN_ID_HEADER, tokenId)
    }

    private fun loadOriginByDescription(description: String): StoredOrigin {
        val container = getPgContainer()
        val jdbcUrl = buildJdbcUrlWithSslDisabled(container.jdbcUrl)
        DriverManager.getConnection(jdbcUrl, container.username, container.password).use { conn ->
            conn.prepareStatement(
                """
                SELECT origin_channel, origin_client_id, origin_tool_call_id
                FROM banking.transactions
                WHERE description = ? AND account_id = ? AND deleted_at IS NULL
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, description)
                stmt.setObject(2, BankingTestTokenPrincipalDecoder.TEST_ACCOUNT_ID)
                stmt.executeQuery().use { rs ->
                    require(rs.next()) { "Transaction not found for description=$description" }
                    val originToolCallId = rs.getObject("origin_tool_call_id", UUID::class.java)
                    return StoredOrigin(
                        originChannel = rs.getString("origin_channel"),
                        originClientId = rs.getString("origin_client_id"),
                        originToolCallId = originToolCallId,
                    )
                }
            }
        }
    }

    private data class StoredOrigin(
        val originChannel: String,
        val originClientId: String?,
        val originToolCallId: UUID?,
    )
}
