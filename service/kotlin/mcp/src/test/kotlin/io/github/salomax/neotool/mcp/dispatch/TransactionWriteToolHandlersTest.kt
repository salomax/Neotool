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

class TransactionWriteToolHandlersTest {
    @Test
    fun `create transaction handler requires idempotency key`() {
        val handler = CreateTransactionToolHandler(CapturingBankingClient())

        val error =
            assertThrows<ValidationException> {
                handler.call(
                    arguments =
                        mapOf(
                            "financialAccountId" to UUID.randomUUID().toString(),
                            "creditDebitType" to "DEBIT",
                            "type" to "OTHER_EXPENSE",
                            "amount" to 10.0,
                            "date" to "2026-07-25",
                            "description" to "Coffee",
                        ),
                    principal = principal(),
                )
            }

        assertThat(error.field).isEqualTo("idempotency_key")
    }

    @Test
    fun `create transaction handler rejects unsupported arguments`() {
        val handler = CreateTransactionToolHandler(CapturingBankingClient())

        val error =
            assertThrows<ValidationException> {
                handler.call(
                    arguments =
                        mapOf(
                            "idempotency_key" to "key-1",
                            "accountId" to UUID.randomUUID().toString(),
                        ),
                    principal = principal(),
                )
            }

        assertThat(error.field).isEqualTo("accountId")
    }

    @Test
    fun `create transaction handler builds mutation variables and uses mutation dispatch`() {
        val client = CapturingBankingClient()
        val handler = CreateTransactionToolHandler(client)

        handler.call(
            arguments =
                mapOf(
                    "idempotency_key" to "key-1",
                    "financialAccountId" to UUID.randomUUID().toString(),
                    "creditDebitType" to "debit",
                    "type" to "other_expense",
                    "amount" to "10.5",
                    "date" to "2026-07-25",
                    "description" to "Coffee",
                    "tags" to listOf("Food"),
                ),
            principal = principal(),
        )

        assertThat(client.usedMutation).isTrue()
        assertThat(client.usedQuery).isFalse()
        assertThat(client.variables).containsEntry("idempotencyKey", "key-1")
        assertThat(client.variables["input"]).isEqualTo(
            mapOf(
                "financialAccountId" to (client.variables["input"] as Map<*, *>)["financialAccountId"],
                "creditDebitType" to "DEBIT",
                "type" to "OTHER_EXPENSE",
                "amount" to 10.5,
                "date" to "2026-07-25",
                "description" to "Coffee",
                "tags" to listOf("Food"),
            ),
        )
    }

    @Test
    fun `update transaction handler requires id and idempotency key`() {
        val handler = UpdateTransactionToolHandler(CapturingBankingClient())

        val error =
            assertThrows<ValidationException> {
                handler.call(arguments = mapOf("description" to "Updated"), principal = principal())
            }

        assertThat(error.field).isEqualTo("id")
    }

    @Test
    fun `update transaction handler builds mutation variables`() {
        val client = CapturingBankingClient()
        val handler = UpdateTransactionToolHandler(client)
        val txId = UUID.randomUUID().toString()

        handler.call(
            arguments =
                mapOf(
                    "id" to txId,
                    "idempotency_key" to "key-2",
                    "description" to "Updated",
                    "tags" to listOf("Weekend"),
                ),
            principal = principal(),
        )

        assertThat(client.usedMutation).isTrue()
        assertThat(client.variables).containsEntry("id", txId)
        assertThat(client.variables).containsEntry("idempotencyKey", "key-2")
        assertThat(client.variables["input"]).isEqualTo(
            mapOf(
                "description" to "Updated",
                "tags" to listOf("Weekend"),
            ),
        )
    }

    @Test
    fun `delete transaction handler requires idempotency key and forwards id`() {
        val client = CapturingBankingClient()
        val handler = DeleteTransactionToolHandler(client)
        val txId = UUID.randomUUID().toString()

        val error =
            assertThrows<ValidationException> {
                handler.call(
                    arguments = mapOf("id" to txId),
                    principal = principal(),
                )
            }

        assertThat(error.field).isEqualTo("idempotency_key")

        handler.call(
            arguments =
                mapOf(
                    "id" to txId,
                    "idempotency_key" to "delete-key-1",
                ),
            principal = principal(),
        )

        assertThat(client.usedMutation).isTrue()
        assertThat(client.variables).containsEntry("id", txId)
        assertThat(client.variables).containsEntry("idempotencyKey", "delete-key-1")
    }

    @Test
    fun `create transaction rule handler requires idempotency key and forwards rule fields`() {
        val client = CapturingBankingClient()
        val handler = CreateTransactionRuleToolHandler(client)

        val error =
            assertThrows<ValidationException> {
                handler.call(
                    arguments =
                        mapOf(
                            "pattern" to "ifood",
                            "patternType" to "contains",
                            "applicableTo" to "expense",
                        ),
                    principal = principal(),
                )
            }

        assertThat(error.field).isEqualTo("idempotency_key")

        handler.call(
            arguments =
                mapOf(
                    "idempotency_key" to "key-3",
                    "pattern" to "ifood",
                    "patternType" to "contains",
                    "applicableTo" to "expense",
                    "typeOverride" to "transfer",
                    "tagsOverride" to listOf("Delivery"),
                ),
            principal = principal(),
        )

        assertThat(client.usedMutation).isTrue()
        assertThat(client.variables).containsEntry("idempotencyKey", "key-3")
        assertThat(client.variables["input"]).isEqualTo(
            mapOf(
                "typeOverride" to "TRANSFER",
                "tagsOverride" to listOf("Delivery"),
                "pattern" to "ifood",
                "patternType" to "CONTAINS",
                "applicableTo" to "EXPENSE",
            ),
        )
    }

    @Test
    fun `categorize transactions handler requires idempotency key and builds mutation variables`() {
        val client = CapturingBankingClient()
        val handler = CategorizeTransactionsToolHandler(client)
        val categoryId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()

        val error =
            assertThrows<ValidationException> {
                handler.call(
                    arguments =
                        mapOf(
                            "transactionIds" to listOf(transactionId),
                            "categoryId" to categoryId,
                        ),
                    principal = principal(),
                )
            }

        assertThat(error.field).isEqualTo("idempotency_key")

        handler.call(
            arguments =
                mapOf(
                    "idempotency_key" to "bulk-key-1",
                    "transactionIds" to listOf(transactionId),
                    "categoryId" to categoryId,
                ),
            principal = principal(),
        )

        assertThat(client.usedMutation).isTrue()
        assertThat(client.variables).isEqualTo(
            mapOf(
                "transactionIds" to listOf(transactionId),
                "categoryId" to categoryId,
                "idempotencyKey" to "bulk-key-1",
            ),
        )
    }

    @Test
    fun `apply rule to historical handler rejects unsupported argument and requires idempotency key`() {
        val client = CapturingBankingClient()
        val handler = ApplyRuleToHistoricalToolHandler(client)
        val ruleId = UUID.randomUUID().toString()

        val missingKey =
            assertThrows<ValidationException> {
                handler.call(arguments = mapOf("id" to ruleId), principal = principal())
            }
        assertThat(missingKey.field).isEqualTo("idempotency_key")

        val unsupported =
            assertThrows<ValidationException> {
                handler.call(
                    arguments =
                        mapOf(
                            "id" to ruleId,
                            "idempotency_key" to "bulk-key-2",
                            "accountId" to UUID.randomUUID().toString(),
                        ),
                    principal = principal(),
                )
            }
        assertThat(unsupported.field).isEqualTo("accountId")

        handler.call(
            arguments =
                mapOf(
                    "id" to ruleId,
                    "idempotency_key" to "bulk-key-2",
                ),
            principal = principal(),
        )

        assertThat(client.usedMutation).isTrue()
        assertThat(client.variables).isEqualTo(
            mapOf(
                "id" to ruleId,
                "idempotencyKey" to "bulk-key-2",
            ),
        )
    }

    private class CapturingBankingClient : McpBankingGraphQLClient {
        var variables: Map<String, Any> = emptyMap()
        var usedQuery: Boolean = false
        var usedMutation: Boolean = false

        override suspend fun query(
            query: String,
            variables: Map<String, Any>,
            principal: McpPrincipal,
        ): GraphQLResponse {
            usedQuery = true
            this.variables = variables
            return GraphQLResponse(data = mapOf("query" to true))
        }

        override suspend fun mutation(
            query: String,
            variables: Map<String, Any>,
            principal: McpPrincipal,
        ): GraphQLResponse {
            usedMutation = true
            this.variables = variables
            return GraphQLResponse(
                data =
                    mapOf(
                        "createTransaction" to mapOf("transaction" to mapOf("id" to "tx-1")),
                        "updateTransaction" to mapOf("id" to "tx-1"),
                        "deleteTransaction" to mapOf("deleted" to true, "pendingAction" to null),
                        "createTransactionRule" to mapOf("id" to "rule-1"),
                        "bulkCategorize" to
                            mapOf(
                                "affectedCount" to 2,
                                "errors" to emptyList<String>(),
                                "outcome" to "EXECUTED",
                            ),
                        "applyTransactionRuleToHistorical" to
                            mapOf("affectedCount" to 3, "errors" to emptyList<String>(), "outcome" to "EXECUTED"),
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
            scopes = setOf("finance:transactions:write", "finance:rules:write"),
        )
}
