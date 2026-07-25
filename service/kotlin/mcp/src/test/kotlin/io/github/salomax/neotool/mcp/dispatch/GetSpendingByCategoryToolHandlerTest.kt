package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.service.GraphQLResponse
import io.github.salomax.neotool.mcp.security.McpPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class GetSpendingByCategoryToolHandlerTest {
    @Test
    fun `routes month shape to transactions by category`() {
        val client = CapturingBankingClient()
        val handler = GetSpendingByCategoryToolHandler(client)

        handler.call(arguments = mapOf("month" to 7, "year" to 2026), principal = principal())

        assertThat(client.lastQuery).contains("transactionsByCategory")
        assertThat(client.variables).containsEntry("month", 7).containsEntry("year", 2026)
    }

    @Test
    fun `routes range shape to category breakdown for range`() {
        val client = CapturingBankingClient()
        val handler = GetSpendingByCategoryToolHandler(client)

        handler.call(
            arguments = mapOf("startYear" to 2026, "startMonth" to 1, "endYear" to 2026, "endMonth" to 6),
            principal = principal(),
        )

        assertThat(client.lastQuery).contains("categoryBreakdownForRange")
        assertThat(client.variables).containsEntry("startMonth", 1).containsEntry("endMonth", 6)
    }

    @Test
    fun `rejects calls with both month and range shapes`() {
        val handler = GetSpendingByCategoryToolHandler(CapturingBankingClient())

        val exception =
            assertThrows<ValidationException> {
                handler.call(
                    arguments =
                        mapOf(
                            "month" to 7,
                            "year" to 2026,
                            "startYear" to 2026,
                            "startMonth" to 1,
                            "endYear" to 2026,
                            "endMonth" to 6,
                        ),
                    principal = principal(),
                )
            }

        assertThat(exception.field).isEqualTo("mode")
    }

    private class CapturingBankingClient : McpBankingGraphQLClient {
        var lastQuery: String? = null
        var variables: Map<String, Any>? = null

        override suspend fun query(
            query: String,
            variables: Map<String, Any>,
            principal: McpPrincipal,
        ): GraphQLResponse {
            lastQuery = query
            this.variables = variables
            return GraphQLResponse(data = mapOf("transactionsByCategory" to emptyList<Map<String, Any?>>()))
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
            scopes = setOf("finance:cashflow:read"),
        )
}
