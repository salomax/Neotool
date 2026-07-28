package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.mcp.protocol.McpToolExecutionException
import io.github.salomax.neotool.mcp.render.McpToolResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class AbstractMcpBankingToolHandlerTest {
    @Test
    fun `retains upstream detail and marks graphql spec errors as internal defects`() {
        val handler =
            TestQueryToolHandler(
                response =
                    GraphQLResponse(
                        errors =
                            listOf(
                                mapOf(
                                    "message" to
                                        "Validation error: Field 'symbol' is not defined by type " +
                                        "'InvestmentAsset'",
                                    "extensions" to mapOf("classification" to "ValidationError"),
                                ),
                            ),
                    ),
            )

        val exception = assertThrows<McpToolExecutionException> { handler.call(emptyMap(), principal()) }

        assertThat(exception.code).isEqualTo("MCP_INTERNAL_ERROR")
        assertThat(exception.service).isEqualTo("mcp")
        assertThat(exception.detail).contains("Field 'symbol'")
        assertThat(exception.internal).isTrue()
    }

    @Test
    fun `keeps coded banking domain errors on the existing non-internal path`() {
        val handler =
            TestQueryToolHandler(
                response =
                    GraphQLResponse(
                        errors =
                            listOf(
                                mapOf(
                                    "message" to "Month must be between 1 and 12",
                                    "path" to listOf("transactions"),
                                    "extensions" to
                                        mapOf(
                                            "code" to "BANKING_VALIDATION_FAILED",
                                            "field" to "month",
                                            "parameters" to mapOf("month" to 13),
                                        ),
                                ),
                            ),
                    ),
            )

        val exception = assertThrows<McpToolExecutionException> { handler.call(emptyMap(), principal()) }

        assertThat(exception.code).isEqualTo("BANKING_VALIDATION_FAILED")
        assertThat(exception.service).isEqualTo("banking")
        assertThat(exception.field).containsExactly("month")
        assertThat(exception.parameters).containsEntry("month", 13)
        assertThat(exception.detail).isEqualTo("Month must be between 1 and 12")
        assertThat(exception.internal).isFalse()
    }

    @Test
    fun `leaves detail null when upstream message is absent`() {
        val handler =
            TestQueryToolHandler(
                response =
                    GraphQLResponse(
                        errors =
                            listOf(
                                mapOf(
                                    "extensions" to mapOf("code" to "BANKING_GRAPHQL_ERROR"),
                                    "path" to listOf("transactions"),
                                ),
                            ),
                    ),
            )

        val exception = assertThrows<McpToolExecutionException> { handler.call(emptyMap(), principal()) }

        assertThat(exception.detail).isNull()
        assertThat(exception.internal).isFalse()
    }

    @Test
    fun `falls back to internal defect when code classification and path are all absent`() {
        val handler =
            TestQueryToolHandler(
                response =
                    GraphQLResponse(
                        errors =
                            listOf(
                                mapOf(
                                    "message" to "Unexpected GraphQL validation failure",
                                    "extensions" to emptyMap<String, Any?>(),
                                ),
                            ),
                    ),
            )

        val exception = assertThrows<McpToolExecutionException> { handler.call(emptyMap(), principal()) }

        assertThat(exception.code).isEqualTo("MCP_INTERNAL_ERROR")
        assertThat(exception.service).isEqualTo("mcp")
        assertThat(exception.detail).isEqualTo("Unexpected GraphQL validation failure")
        assertThat(exception.internal).isTrue()
    }

    private class TestQueryToolHandler(
        private val response: GraphQLResponse,
    ) : AbstractMcpBankingToolHandler(FixedResponseBankingClient(response)) {
        override val name: String = "test_query"

        override fun call(
            arguments: Map<String, Any?>,
            principal: McpPrincipal,
        ): McpToolResult {
            queryField("transactions", "query Test { transactions { totalCount } }", emptyMap(), principal)
            error("unreachable")
        }
    }

    private class FixedResponseBankingClient(
        private val response: GraphQLResponse,
    ) : McpBankingGraphQLClient {
        override suspend fun query(
            query: String,
            variables: Map<String, Any>,
            principal: McpPrincipal,
        ): GraphQLResponse = response
    }

    private fun principal(): McpPrincipal =
        McpPrincipal(
            principal =
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = UUID.randomUUID(),
                    serviceId = null,
                    token = "token",
                    tokenId = "token-id",
                    permissionsFromToken = emptyList(),
                    currentAccountId = UUID.randomUUID(),
                ),
            clientId = "client",
            scopes = setOf("finance:transactions:read"),
        )
}
