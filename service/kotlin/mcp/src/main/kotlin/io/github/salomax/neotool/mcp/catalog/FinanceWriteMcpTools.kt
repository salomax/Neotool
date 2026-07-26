package io.github.salomax.neotool.mcp.catalog

data class FinanceWriteMcpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?>,
    val outputSchema: Map<String, Any?>,
    val requiredScope: String,
)

object FinanceWriteMcpTools {
    const val CREATE_TRANSACTION = "create_transaction"
    const val UPDATE_TRANSACTION = "update_transaction"
    const val DELETE_TRANSACTION = "delete_transaction"
    const val CREATE_TRANSACTION_RULE = "create_transaction_rule"
    const val CATEGORIZE_TRANSACTIONS = "categorize_transactions"
    const val APPLY_RULE_TO_HISTORICAL = "apply_rule_to_historical"

    val all =
        listOf(
            FinanceWriteMcpToolDefinition(
                name = CREATE_TRANSACTION,
                description =
                    "Create one manual transaction for the authenticated account. The caller must provide " +
                        "an idempotency_key so repeated agent writes replay safely instead of creating duplicates.",
                inputSchema =
                    objectSchema(
                        "idempotency_key" to stringSchema(),
                        "financialAccountId" to uuidSchema(),
                        "creditDebitType" to enumSchema("CREDIT", "DEBIT"),
                        "type" to stringSchema(),
                        "amount" to numberSchema(),
                        "date" to dateSchema(),
                        "description" to stringSchema(),
                        "status" to stringSchema(),
                        "currencyCode" to stringSchema(),
                        "notes" to stringSchema(),
                        "tags" to stringArraySchema(),
                        "categoryId" to uuidSchema(),
                        "merchantId" to uuidSchema(),
                        "belongsToUserId" to uuidSchema(),
                        "belongsToName" to stringSchema(),
                        "counterpartyName" to stringSchema(),
                        "counterpartyDocument" to stringSchema(),
                        "counterpartyBankCode" to stringSchema(),
                        "counterpartyAccountNumber" to stringSchema(),
                        "paymentReason" to stringSchema(),
                        "installmentNumber" to intSchema(min = 1),
                        "totalInstallments" to intSchema(min = 1),
                        "cardIdentifier" to stringSchema(),
                        "payeeMcc" to intSchema(min = 0),
                        required =
                            setOf(
                                "idempotency_key",
                                "financialAccountId",
                                "creditDebitType",
                                "type",
                                "amount",
                                "date",
                                "description",
                            ),
                    ),
                outputSchema = objectSchema("createTransaction" to mapOf("type" to "object")),
                requiredScope = FINANCE_TRANSACTIONS_WRITE,
            ),
            FinanceWriteMcpToolDefinition(
                name = UPDATE_TRANSACTION,
                description =
                    "Update one manual transaction for the authenticated account. The caller must provide both " +
                        "the transaction id and an idempotency_key.",
                inputSchema =
                    objectSchema(
                        "id" to uuidSchema(),
                        "idempotency_key" to stringSchema(),
                        "creditDebitType" to enumSchema("CREDIT", "DEBIT"),
                        "type" to stringSchema(),
                        "amount" to numberSchema(),
                        "date" to dateSchema(),
                        "description" to stringSchema(),
                        "status" to stringSchema(),
                        "notes" to stringSchema(),
                        "tags" to stringArraySchema(),
                        "categoryId" to uuidSchema(),
                        "merchantId" to uuidSchema(),
                        "belongsToUserId" to uuidSchema(),
                        "belongsToName" to stringSchema(),
                        "counterpartyName" to stringSchema(),
                        "counterpartyDocument" to stringSchema(),
                        "counterpartyBankCode" to stringSchema(),
                        "counterpartyAccountNumber" to stringSchema(),
                        "paymentReason" to stringSchema(),
                        "cardIdentifier" to stringSchema(),
                        "payeeMcc" to intSchema(min = 0),
                        required = setOf("id", "idempotency_key"),
                    ),
                outputSchema = objectSchema("updateTransaction" to mapOf("type" to "object")),
                requiredScope = FINANCE_TRANSACTIONS_WRITE,
            ),
            FinanceWriteMcpToolDefinition(
                name = DELETE_TRANSACTION,
                description =
                    "Delete one transaction for the authenticated account. The caller must provide an " +
                        "idempotency_key so deferred agent writes can replay safely without duplicating " +
                        "pending confirmation requests.",
                inputSchema =
                    objectSchema(
                        "id" to uuidSchema(),
                        "idempotency_key" to stringSchema(),
                        required = setOf("id", "idempotency_key"),
                    ),
                outputSchema = objectSchema("deleteTransaction" to mapOf("type" to "object")),
                requiredScope = FINANCE_TRANSACTIONS_WRITE,
            ),
            FinanceWriteMcpToolDefinition(
                name = CREATE_TRANSACTION_RULE,
                description =
                    "Create one transaction rule for the authenticated account. The caller must provide an " +
                        "idempotency_key so retries replay instead of creating duplicate rules.",
                inputSchema =
                    objectSchema(
                        "idempotency_key" to stringSchema(),
                        "categoryId" to uuidSchema(),
                        "merchantId" to uuidSchema(),
                        "typeOverride" to stringSchema(),
                        "descriptionOverride" to stringSchema(),
                        "tagsOverride" to stringArraySchema(),
                        "pattern" to stringSchema(),
                        "patternType" to enumSchema("CONTAINS", "EXACT", "REGEX"),
                        "applicableTo" to stringSchema(),
                        "priority" to intSchema(min = 1),
                        required = setOf("idempotency_key", "pattern", "patternType", "applicableTo"),
                    ),
                outputSchema = objectSchema("createTransactionRule" to mapOf("type" to "object")),
                requiredScope = FINANCE_RULES_WRITE,
            ),
            FinanceWriteMcpToolDefinition(
                name = CATEGORIZE_TRANSACTIONS,
                description =
                    "Apply one category to multiple transactions in the authenticated account. The caller " +
                        "must provide an idempotency_key. Banking computes the affected count server-side and " +
                        "returns CONFIRMATION_REQUIRED instead of mutating when the bulk cap is exceeded.",
                inputSchema =
                    objectSchema(
                        "idempotency_key" to stringSchema(),
                        "transactionIds" to uuidArraySchema(),
                        "categoryId" to uuidSchema(),
                        required = setOf("idempotency_key", "transactionIds", "categoryId"),
                    ),
                outputSchema = objectSchema("bulkCategorize" to mapOf("type" to "object")),
                requiredScope = FINANCE_TRANSACTIONS_WRITE,
            ),
            FinanceWriteMcpToolDefinition(
                name = APPLY_RULE_TO_HISTORICAL,
                description =
                    "Apply one transaction rule to historical transactions in the authenticated account. " +
                        "The caller must provide the rule id and an idempotency_key. Banking computes the " +
                        "affected count server-side and returns CONFIRMATION_REQUIRED instead of mutating " +
                        "when the bulk cap is exceeded.",
                inputSchema =
                    objectSchema(
                        "id" to uuidSchema(),
                        "idempotency_key" to stringSchema(),
                        required = setOf("id", "idempotency_key"),
                    ),
                outputSchema = objectSchema("applyTransactionRuleToHistorical" to mapOf("type" to "object")),
                requiredScope = FINANCE_RULES_WRITE,
            ),
        )

    private fun objectSchema(
        vararg properties: Pair<String, Any?>,
        required: Set<String> = emptySet(),
    ): Map<String, Any?> =
        buildMap {
            put("type", "object")
            put("properties", properties.toMap())
            put("additionalProperties", false)
            if (required.isNotEmpty()) {
                put("required", required.sorted())
            }
        }

    private fun uuidSchema(): Map<String, Any?> = mapOf("type" to "string", "format" to "uuid")

    private fun stringSchema(): Map<String, Any?> = mapOf("type" to "string")

    private fun stringArraySchema(): Map<String, Any?> =
        mapOf(
            "type" to "array",
            "items" to mapOf("type" to "string"),
        )

    private fun uuidArraySchema(): Map<String, Any?> =
        mapOf(
            "type" to "array",
            "items" to uuidSchema(),
        )

    private fun numberSchema(): Map<String, Any?> = mapOf("type" to "number")

    private fun dateSchema(): Map<String, Any?> = mapOf("type" to "string", "format" to "date")

    private fun intSchema(min: Int): Map<String, Any?> =
        mapOf(
            "type" to "integer",
            "minimum" to min,
        )

    private fun enumSchema(vararg values: String): Map<String, Any?> =
        mapOf(
            "type" to "string",
            "enum" to values.toList(),
        )

    private const val FINANCE_TRANSACTIONS_WRITE = "finance:transactions:write"
    private const val FINANCE_RULES_WRITE = "finance:rules:write"
}
