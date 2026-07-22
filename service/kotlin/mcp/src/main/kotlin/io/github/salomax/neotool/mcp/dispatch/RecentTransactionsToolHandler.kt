package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.common.error.DomainException
import io.github.salomax.neotool.mcp.protocol.McpToolExecutionException
import io.github.salomax.neotool.mcp.render.McpToolResult
import io.github.salomax.neotool.mcp.render.McpToolSuccessResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class RecentTransactionsToolHandler(
    private val bankingClient: McpBankingGraphQLClient,
) : McpToolHandler {
    override val name: String = "finance.transactions.list"

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        val variables = buildVariables(arguments)
        val response =
            try {
                runBlocking {
                    bankingClient.query(
                        query = TRANSACTIONS_QUERY,
                        variables = variables,
                        principal = principal,
                    )
                }
            } catch (e: DomainException) {
                throw e
            } catch (e: McpToolExecutionException) {
                throw e
            }

        response.errors?.firstOrNull()?.let { throw it.toToolException() }
        val transactions = response.data?.get("transactions") as? Map<*, *> ?: emptyMap<Any, Any?>()

        return McpToolSuccessResult(
            data =
                mapOf(
                    "transactions" to transactions.normalize(),
                    "requestedFirst" to variables["first"],
                ),
        )
    }

    private fun buildVariables(arguments: Map<String, Any?>): Map<String, Any> =
        buildMap {
            put("first", arguments.intArgument("first")?.coerceIn(1, 50) ?: 10)
            arguments.intArgument("month")?.let { put("month", it.coerceIn(1, 12)) }
            arguments.intArgument("year")?.let { put("year", it.coerceIn(1900, 2200)) }
            arguments["search"]?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { put("search", it) }
        }

    private fun Map<String, Any?>.intArgument(name: String): Int? =
        when (val value = this[name]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }

    private fun Map<String, Any?>.toToolException(): McpToolExecutionException {
        val extensions = this["extensions"] as? Map<*, *> ?: emptyMap<Any, Any?>()
        val code = extensions["code"]?.toString()?.takeIf { it.isNotBlank() } ?: "BANKING_GRAPHQL_ERROR"
        val field =
            when (val rawField = extensions["field"]) {
                is List<*> -> rawField.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                is String -> rawField.split(".").filter { it.isNotBlank() }
                else -> null
            }
        val parameters =
            (extensions["parameters"] as? Map<*, *>)
                ?.entries
                ?.associate { (key, value) -> key.toString() to value }
                ?: emptyMap()

        return McpToolExecutionException(
            code = code,
            service = "banking",
            field = field?.takeIf { it.isNotEmpty() },
            parameters = parameters,
        )
    }

    private fun Any?.normalize(): Any? =
        when (this) {
            is Map<*, *> -> entries.associate { (key, value) -> key.toString() to value.normalize() }
            is List<*> -> map { it.normalize() }
            else -> this
        }

    companion object {
        private const val TRANSACTIONS_QUERY = """
            query McpTransactions(${'$'}first: Int!, ${'$'}month: Int, ${'$'}year: Int, ${'$'}search: String) {
              transactions(first: ${'$'}first, month: ${'$'}month, year: ${'$'}year, search: ${'$'}search) {
                edges {
                  cursor
                  node {
                    id
                    amount
                    currencyCode
                    amountBrl
                    date
                    description
                    notes
                    tags
                    counterpartyName
                    paymentReason
                    investmentTransactionId
                    linkedInvestmentAsset {
                      id
                      name
                      symbol
                    }
                    merchant {
                      name
                    }
                  }
                }
                pageInfo {
                  hasNextPage
                  endCursor
                }
                totalCount
              }
            }
        """
    }
}
