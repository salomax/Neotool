package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.mcp.render.McpToolSuccessResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class ListTransactionsToolHandlerTest {
    @Test
    fun `coerces first into banking ceiling and preserves pagination metadata`() {
        val client = CapturingBankingClient()
        val handler = ListTransactionsToolHandler(client)

        val result =
            handler.call(
                arguments = mapOf("first" to 500, "month" to 7, "year" to 2026),
                principal = principal(),
            ) as McpToolSuccessResult

        assertThat(client.variables).containsEntry("first", 100)
        assertThat(client.variables).containsEntry("month", 7)
        assertThat(client.variables).containsEntry("year", 2026)
        assertThat((result.data["transactions"] as Map<*, *>)["totalCount"]).isEqualTo(42)
    }

    @Test
    fun `rejects unsupported filter arguments instead of silently forwarding them`() {
        val handler = ListTransactionsToolHandler(CapturingBankingClient())

        val exception =
            assertThrows<ValidationException> {
                handler.call(arguments = mapOf("billId" to UUID.randomUUID().toString()), principal = principal())
            }

        assertThat(exception.field).isEqualTo("billId")
    }

    @Test
    fun `rejects malformed account identifiers before banking dispatch`() {
        val client = CapturingBankingClient()
        val handler = ListTransactionsToolHandler(client)

        val exception =
            assertThrows<ValidationException> {
                handler.call(arguments = mapOf("financialAccountId" to "not-a-uuid"), principal = principal())
            }

        assertThat(exception.field).isEqualTo("financialAccountId")
        assertThat(client.variables).isNull()
    }

    private class CapturingBankingClient : McpBankingGraphQLClient {
        var variables: Map<String, Any>? = null

        override suspend fun query(
            query: String,
            variables: Map<String, Any>,
            principal: McpPrincipal,
        ): GraphQLResponse {
            this.variables = variables
            return GraphQLResponse(
                data =
                    mapOf(
                        "transactions" to
                            mapOf(
                                "edges" to emptyList<Map<String, Any?>>(),
                                "pageInfo" to mapOf("hasNextPage" to false, "endCursor" to null),
                                "totalCount" to 42,
                            ),
                    ),
            )
        }
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
