package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.mcp.catalog.FinanceWriteMcpTools
import io.github.salomax.neotool.mcp.render.McpToolResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import jakarta.inject.Singleton

@Singleton
class CreateTransactionToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceWriteMcpTools.CREATE_TRANSACTION
    override val graphQLDocuments: List<String> = listOf(CREATE_TRANSACTION_MUTATION)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(CREATE_TRANSACTION_ARGUMENTS)
        val variables =
            mapOf(
                "input" to
                    buildMap<String, Any> {
                        put("financialAccountId", arguments.requiredUuidArgument("financialAccountId"))
                        put("creditDebitType", arguments.requiredStringArgument("creditDebitType").uppercase())
                        put("type", arguments.requiredStringArgument("type").uppercase())
                        put("amount", arguments.requiredDoubleArgument("amount"))
                        put("date", arguments.requiredDateArgument("date"))
                        put("description", arguments.requiredStringArgument("description"))
                        arguments.stringArgument("status")?.uppercase()?.let { put("status", it) }
                        arguments.stringArgument("currencyCode")?.let { put("currencyCode", it) }
                        arguments.stringArgument("notes")?.let { put("notes", it) }
                        arguments.stringListArgument("tags")?.let { put("tags", it) }
                        arguments.uuidArgument("categoryId")?.let { put("categoryId", it) }
                        arguments.uuidArgument("merchantId")?.let { put("merchantId", it) }
                        arguments.uuidArgument("belongsToUserId")?.let { put("belongsToUserId", it) }
                        arguments.stringArgument("belongsToName")?.let { put("belongsToName", it) }
                        arguments.stringArgument("counterpartyName")?.let { put("counterpartyName", it) }
                        arguments.stringArgument("counterpartyDocument")?.let { put("counterpartyDocument", it) }
                        arguments.stringArgument("counterpartyBankCode")?.let { put("counterpartyBankCode", it) }
                        arguments.stringArgument("counterpartyAccountNumber")?.let {
                            put("counterpartyAccountNumber", it)
                        }
                        arguments.stringArgument("paymentReason")?.let { put("paymentReason", it) }
                        arguments.intArgument("installmentNumber")?.let { put("installmentNumber", it) }
                        arguments.intArgument("totalInstallments")?.let { put("totalInstallments", it) }
                        arguments.stringArgument("cardIdentifier")?.let { put("cardIdentifier", it) }
                        arguments.intArgument("payeeMcc")?.let { put("payeeMcc", it) }
                    },
                "idempotencyKey" to arguments.requiredStringArgument("idempotency_key"),
            )

        val payload =
            mutationField(
                fieldName = "createTransaction",
                mutation = CREATE_TRANSACTION_MUTATION,
                variables = variables,
                principal = principal,
            ) ?: emptyMap<String, Any?>()

        return success("createTransaction" to payload)
    }
}

@Singleton
class UpdateTransactionToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceWriteMcpTools.UPDATE_TRANSACTION
    override val graphQLDocuments: List<String> = listOf(UPDATE_TRANSACTION_MUTATION)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(UPDATE_TRANSACTION_ARGUMENTS)
        val variables =
            mapOf(
                "id" to arguments.requiredUuidArgument("id"),
                "input" to
                    buildMap<String, Any> {
                        arguments.stringArgument("creditDebitType")?.uppercase()?.let { put("creditDebitType", it) }
                        arguments.stringArgument("type")?.uppercase()?.let { put("type", it) }
                        arguments.doubleArgument("amount")?.let { put("amount", it) }
                        arguments.dateArgument("date")?.let { put("date", it) }
                        arguments.stringArgument("description")?.let { put("description", it) }
                        arguments.stringArgument("status")?.uppercase()?.let { put("status", it) }
                        arguments.stringArgument("notes")?.let { put("notes", it) }
                        arguments.stringListArgument("tags")?.let { put("tags", it) }
                        arguments.uuidArgument("categoryId")?.let { put("categoryId", it) }
                        arguments.uuidArgument("merchantId")?.let { put("merchantId", it) }
                        arguments.uuidArgument("belongsToUserId")?.let { put("belongsToUserId", it) }
                        arguments.stringArgument("belongsToName")?.let { put("belongsToName", it) }
                        arguments.stringArgument("counterpartyName")?.let { put("counterpartyName", it) }
                        arguments.stringArgument("counterpartyDocument")?.let { put("counterpartyDocument", it) }
                        arguments.stringArgument("counterpartyBankCode")?.let { put("counterpartyBankCode", it) }
                        arguments.stringArgument("counterpartyAccountNumber")?.let {
                            put("counterpartyAccountNumber", it)
                        }
                        arguments.stringArgument("paymentReason")?.let { put("paymentReason", it) }
                        arguments.stringArgument("cardIdentifier")?.let { put("cardIdentifier", it) }
                        arguments.intArgument("payeeMcc")?.let { put("payeeMcc", it) }
                    },
                "idempotencyKey" to arguments.requiredStringArgument("idempotency_key"),
            )

        val payload =
            mutationField(
                fieldName = "updateTransaction",
                mutation = UPDATE_TRANSACTION_MUTATION,
                variables = variables,
                principal = principal,
            ) ?: emptyMap<String, Any?>()

        return success("updateTransaction" to payload)
    }
}

@Singleton
class DeleteTransactionToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceWriteMcpTools.DELETE_TRANSACTION
    override val graphQLDocuments: List<String> = listOf(DELETE_TRANSACTION_MUTATION)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(DELETE_TRANSACTION_ARGUMENTS)
        val variables =
            mapOf(
                "id" to arguments.requiredUuidArgument("id"),
                "idempotencyKey" to arguments.requiredStringArgument("idempotency_key"),
            )

        val payload =
            mutationField(
                fieldName = "deleteTransaction",
                mutation = DELETE_TRANSACTION_MUTATION,
                variables = variables,
                principal = principal,
            ) ?: emptyMap<String, Any?>()

        return success("deleteTransaction" to payload)
    }
}

@Singleton
class CreateTransactionRuleToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceWriteMcpTools.CREATE_TRANSACTION_RULE
    override val graphQLDocuments: List<String> = listOf(CREATE_TRANSACTION_RULE_MUTATION)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(CREATE_TRANSACTION_RULE_ARGUMENTS)
        val variables =
            mapOf(
                "input" to
                    buildMap<String, Any> {
                        arguments.uuidArgument("categoryId")?.let { put("categoryId", it) }
                        arguments.uuidArgument("merchantId")?.let { put("merchantId", it) }
                        arguments.stringArgument("typeOverride")?.uppercase()?.let { put("typeOverride", it) }
                        arguments.stringArgument("descriptionOverride")?.let { put("descriptionOverride", it) }
                        arguments.stringListArgument("tagsOverride")?.let { put("tagsOverride", it) }
                        put("pattern", arguments.requiredStringArgument("pattern"))
                        put("patternType", arguments.requiredStringArgument("patternType").uppercase())
                        put("applicableTo", arguments.requiredStringArgument("applicableTo").uppercase())
                        arguments.intArgument("priority")?.let { put("priority", it) }
                    },
                "idempotencyKey" to arguments.requiredStringArgument("idempotency_key"),
            )

        val payload =
            mutationField(
                fieldName = "createTransactionRule",
                mutation = CREATE_TRANSACTION_RULE_MUTATION,
                variables = variables,
                principal = principal,
            ) ?: emptyMap<String, Any?>()

        return success("createTransactionRule" to payload)
    }
}

@Singleton
class CategorizeTransactionsToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceWriteMcpTools.CATEGORIZE_TRANSACTIONS
    override val graphQLDocuments: List<String> = listOf(BULK_CATEGORIZE_MUTATION)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(CATEGORIZE_TRANSACTIONS_ARGUMENTS)
        val variables =
            mapOf(
                "transactionIds" to arguments.requiredUuidListArgument("transactionIds"),
                "categoryId" to arguments.requiredUuidArgument("categoryId"),
                "idempotencyKey" to arguments.requiredStringArgument("idempotency_key"),
            )

        val payload =
            mutationField(
                fieldName = "bulkCategorize",
                mutation = BULK_CATEGORIZE_MUTATION,
                variables = variables,
                principal = principal,
            ) ?: emptyMap<String, Any?>()

        return success("bulkCategorize" to payload)
    }
}

@Singleton
class ApplyRuleToHistoricalToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceWriteMcpTools.APPLY_RULE_TO_HISTORICAL
    override val graphQLDocuments: List<String> = listOf(APPLY_RULE_TO_HISTORICAL_MUTATION)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(APPLY_RULE_TO_HISTORICAL_ARGUMENTS)
        val variables =
            mapOf(
                "id" to arguments.requiredUuidArgument("id"),
                "idempotencyKey" to arguments.requiredStringArgument("idempotency_key"),
            )

        val payload =
            mutationField(
                fieldName = "applyTransactionRuleToHistorical",
                mutation = APPLY_RULE_TO_HISTORICAL_MUTATION,
                variables = variables,
                principal = principal,
            ) ?: emptyMap<String, Any?>()

        return success("applyTransactionRuleToHistorical" to payload)
    }
}

private val CREATE_TRANSACTION_ARGUMENTS =
    setOf(
        "idempotency_key",
        "financialAccountId",
        "creditDebitType",
        "type",
        "amount",
        "date",
        "description",
        "status",
        "currencyCode",
        "notes",
        "tags",
        "categoryId",
        "merchantId",
        "belongsToUserId",
        "belongsToName",
        "counterpartyName",
        "counterpartyDocument",
        "counterpartyBankCode",
        "counterpartyAccountNumber",
        "paymentReason",
        "installmentNumber",
        "totalInstallments",
        "cardIdentifier",
        "payeeMcc",
    )

private val UPDATE_TRANSACTION_ARGUMENTS =
    setOf(
        "id",
        "idempotency_key",
        "creditDebitType",
        "type",
        "amount",
        "date",
        "description",
        "status",
        "notes",
        "tags",
        "categoryId",
        "merchantId",
        "belongsToUserId",
        "belongsToName",
        "counterpartyName",
        "counterpartyDocument",
        "counterpartyBankCode",
        "counterpartyAccountNumber",
        "paymentReason",
        "cardIdentifier",
        "payeeMcc",
    )

private val DELETE_TRANSACTION_ARGUMENTS =
    setOf(
        "id",
        "idempotency_key",
    )

private val CREATE_TRANSACTION_RULE_ARGUMENTS =
    setOf(
        "idempotency_key",
        "categoryId",
        "merchantId",
        "typeOverride",
        "descriptionOverride",
        "tagsOverride",
        "pattern",
        "patternType",
        "applicableTo",
        "priority",
    )

private val CATEGORIZE_TRANSACTIONS_ARGUMENTS =
    setOf(
        "idempotency_key",
        "transactionIds",
        "categoryId",
    )

private val APPLY_RULE_TO_HISTORICAL_ARGUMENTS =
    setOf(
        "id",
        "idempotency_key",
    )

private const val TRANSACTION_FIELDS = """
    id
    financialAccountId
    creditDebitType
    type
    status
    source
    hasNfceDocument
    amount
    currencyCode
    amountBrl
    date
    transactionDatetime
    description
    notes
    tags
    categorySource
    category {
      id
      parentId
      name
      applicableTo
      icon
      color
      isSystem
      sortOrder
    }
    merchant {
      id
      name
      domain
      sector
      isSystem
      isOwned
    }
    counterpartyName
    paymentReason
    billId
    cardIdentifier
    installmentNumber
    totalInstallments
    investmentTransactionId
    createdAt
    updatedAt
"""

private const val TRANSACTION_RULE_FIELDS = """
    id
    categoryId
    category {
      id
      parentId
      name
      applicableTo
      icon
      color
      isSystem
      sortOrder
    }
    merchantId
    merchant {
      id
      name
      domain
      sector
      isSystem
      isOwned
    }
    typeOverride
    descriptionOverride
    tagsOverride
    pattern
    patternType
    applicableTo
    priority
    isSystem
    isDisabledForAccount
    isImplicit
    matchCount
    position
    createdAt
"""

private const val CREATE_TRANSACTION_MUTATION = """
    mutation McpCreateTransaction(
      ${'$'}input: CreateTransactionInput!,
      ${'$'}idempotencyKey: String
    ) {
      createTransaction(input: ${'$'}input, idempotencyKey: ${'$'}idempotencyKey) {
        transaction {
          $TRANSACTION_FIELDS
        }
        appliedRules {
          $TRANSACTION_RULE_FIELDS
        }
      }
    }
"""

private const val UPDATE_TRANSACTION_MUTATION = """
    mutation McpUpdateTransaction(
      ${'$'}id: ID!,
      ${'$'}input: UpdateTransactionInput!,
      ${'$'}idempotencyKey: String
    ) {
      updateTransaction(id: ${'$'}id, input: ${'$'}input, idempotencyKey: ${'$'}idempotencyKey) {
        $TRANSACTION_FIELDS
      }
    }
"""

private const val DELETE_TRANSACTION_MUTATION = """
    mutation McpDeleteTransaction(
      ${'$'}id: ID!,
      ${'$'}idempotencyKey: String
    ) {
      deleteTransaction(id: ${'$'}id, idempotencyKey: ${'$'}idempotencyKey) {
        deleted
        pendingAction {
          id
          type
          severity
          status
          entityType
          entityId
          payload
        }
      }
    }
"""

private const val CREATE_TRANSACTION_RULE_MUTATION = """
    mutation McpCreateTransactionRule(
      ${'$'}input: CreateTransactionRuleInput!,
      ${'$'}idempotencyKey: String
    ) {
      createTransactionRule(input: ${'$'}input, idempotencyKey: ${'$'}idempotencyKey) {
        $TRANSACTION_RULE_FIELDS
      }
    }
"""

private const val BULK_RESULT_FIELDS = """
    affectedCount
    errors
    outcome
"""

private const val BULK_CATEGORIZE_MUTATION = """
    mutation McpBulkCategorize(
      ${'$'}transactionIds: [ID!]!,
      ${'$'}categoryId: ID!,
      ${'$'}idempotencyKey: String
    ) {
      bulkCategorize(
        transactionIds: ${'$'}transactionIds,
        categoryId: ${'$'}categoryId,
        idempotencyKey: ${'$'}idempotencyKey
      ) {
        $BULK_RESULT_FIELDS
      }
    }
"""

private const val APPLY_RULE_TO_HISTORICAL_MUTATION = """
    mutation McpApplyRuleToHistorical(
      ${'$'}id: ID!,
      ${'$'}idempotencyKey: String
    ) {
      applyTransactionRuleToHistorical(
        id: ${'$'}id,
        idempotencyKey: ${'$'}idempotencyKey
      ) {
        $BULK_RESULT_FIELDS
      }
    }
"""
