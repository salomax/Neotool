package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.mcp.protocol.McpToolExecutionException
import io.github.salomax.neotool.mcp.render.McpToolSuccessResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class RecentTransactionsToolHandlerTest {
    @Test
    fun `dispatch ignores crafted account id argument and preserves principal account context`() {
        val accountId = UUID.randomUUID()
        val maliciousAccountId = UUID.randomUUID().toString()
        val client = CapturingBankingClient()
        val handler = RecentTransactionsToolHandler(client)

        val result =
            handler.call(
                arguments =
                    mapOf(
                        "first" to 100,
                        "month" to 7,
                        "year" to 2026,
                        "accountId" to maliciousAccountId,
                    ),
                principal = principal(accountId),
            ) as McpToolSuccessResult

        assertThat(client.variables).containsEntry("first", 50)
        assertThat(client.variables).containsEntry("month", 7)
        assertThat(client.variables).containsEntry("year", 2026)
        assertThat(client.variables).doesNotContainKey("accountId")
        assertThat(client.principal?.principal?.currentAccountId).isEqualTo(accountId)
        assertThat(result.data).containsKey("transactions")
    }

    @Test
    fun `maps downstream GraphQL domain error extensions to tool execution exception`() {
        val client =
            CapturingBankingClient(
                response =
                    GraphQLResponse(
                        errors =
                            listOf(
                                mapOf(
                                    "message" to "Internal resolver detail",
                                    "extensions" to
                                        mapOf(
                                            "code" to "BANKING_INVALID_MONTH",
                                            "field" to listOf("month"),
                                            "parameters" to mapOf("month" to 13),
                                        ),
                                ),
                            ),
                    ),
            )
        val handler = RecentTransactionsToolHandler(client)

        val exception =
            assertThrows<McpToolExecutionException> {
                handler.call(arguments = mapOf("month" to 13), principal = principal(UUID.randomUUID()))
            }

        assertThat(exception.code).isEqualTo("BANKING_INVALID_MONTH")
        assertThat(exception.service).isEqualTo("banking")
        assertThat(exception.field).containsExactly("month")
        assertThat(exception.parameters).containsEntry("month", 13)
        assertThat(exception.message).doesNotContain("Internal resolver detail")
    }

    private class CapturingBankingClient(
        private val response: GraphQLResponse =
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
            ),
    ) : McpBankingGraphQLClient {
        lateinit var variables: Map<String, Any>
        var principal: McpPrincipal? = null

        override suspend fun query(
            query: String,
            variables: Map<String, Any>,
            principal: McpPrincipal,
        ): GraphQLResponse {
            this.variables = variables
            this.principal = principal
            return response
        }
    }

    private fun principal(accountId: UUID): McpPrincipal =
        McpPrincipal(
            principal =
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = UUID.randomUUID(),
                    serviceId = null,
                    token = "access-token",
                    tokenId = "token-id",
                    permissionsFromToken = emptyList(),
                    currentAccountId = accountId,
                ),
            clientId = "client",
            scopes = setOf("finance:cashflow:read"),
        )
}
