package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.mcp.security.McpPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ListTransactionRulesToolHandlerTest {
    @Test
    fun `defaults and clamps first to banking pagination contract`() {
        val client = CapturingBankingClient()
        val handler = ListTransactionRulesToolHandler(client)

        handler.call(arguments = emptyMap(), principal = principal())
        assertThat(client.variables).containsEntry("first", 50)

        handler.call(arguments = mapOf("first" to 999), principal = principal())
        assertThat(client.variables).containsEntry("first", 100)
    }

    private class CapturingBankingClient : McpBankingGraphQLClient {
        var variables: Map<String, Any> = emptyMap()

        override suspend fun query(
            query: String,
            variables: Map<String, Any>,
            principal: McpPrincipal,
        ): GraphQLResponse {
            this.variables = variables
            return GraphQLResponse(
                data =
                    mapOf(
                        "transactionRules" to
                            mapOf(
                                "edges" to emptyList<Map<String, Any?>>(),
                                "pageInfo" to mapOf("hasNextPage" to false, "endCursor" to null),
                                "totalCount" to 0,
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
            scopes = setOf("finance:rules:read"),
        )
}
