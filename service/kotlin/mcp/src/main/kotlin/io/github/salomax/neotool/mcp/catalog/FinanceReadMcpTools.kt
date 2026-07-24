package io.github.salomax.neotool.mcp.catalog

data class FinanceReadMcpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?>,
    val outputSchema: Map<String, Any?>,
    val requiredScope: String,
)

object FinanceReadMcpTools {
    const val LIST_TRANSACTIONS = "list_transactions"
    const val GET_TRANSACTION = "get_transaction"
    const val GET_MONTH_SUMMARY = "get_month_summary"
    const val GET_PERIOD_SUMMARY = "get_period_summary"
    const val GET_CASH_FLOW_SERIES = "get_cash_flow_series"
    const val GET_SPENDING_BY_CATEGORY = "get_spending_by_category"
    const val GET_SPENDING_BY_TAG = "get_spending_by_tag"
    const val GET_TOP_MERCHANTS = "get_top_merchants"
    const val LIST_FINANCIAL_ACCOUNTS = "list_financial_accounts"
    const val LIST_CREDIT_CARD_BILLS = "list_credit_card_bills"
    const val GET_CREDIT_CARD_BILL = "get_credit_card_bill"
    const val LIST_CATEGORIES = "list_categories"
    const val LIST_TRANSACTION_RULES = "list_transaction_rules"

    val all =
        listOf(
            FinanceReadMcpToolDefinition(
                name = LIST_TRANSACTIONS,
                description = "Inspect individual transactions for the authenticated account. Use this when you need transaction-level detail; do not use it to compute totals when a summary tool can answer directly.",
                inputSchema =
                    objectSchema(
                        "financialAccountId" to uuidSchema(),
                        "search" to stringSchema(),
                        "categoryId" to uuidSchema(),
                        "uncategorized" to booleanSchema(),
                        "tags" to stringArraySchema(),
                        "creditDebitType" to enumSchema("CREDIT", "DEBIT"),
                        "types" to stringArraySchema(),
                        "month" to intSchema(min = 1, max = 12),
                        "year" to intSchema(min = 1900, max = 2200),
                        "first" to intSchema(min = 1, max = 100),
                        "after" to stringSchema(),
                    ),
                outputSchema = objectSchema("transactions" to mapOf("type" to "object")),
                requiredScope = FINANCE_TRANSACTIONS_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = GET_TRANSACTION,
                description = "Fetch one transaction by id for the authenticated account, including categorization, merchant, notes, and timeline fields.",
                inputSchema =
                    objectSchema(
                        "id" to uuidSchema(),
                        required = setOf("id"),
                    ),
                outputSchema = objectSchema("transaction" to mapOf("type" to "object")),
                requiredScope = FINANCE_TRANSACTIONS_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = GET_MONTH_SUMMARY,
                description = "Answer cash-flow questions for one calendar month. Use this for monthly income, expenses, closing balance, transfers, and top category or tag breakdowns.",
                inputSchema =
                    objectSchema(
                        "financialAccountId" to uuidSchema(),
                        "year" to intSchema(min = 1900, max = 2200),
                        "month" to intSchema(min = 1, max = 12),
                    ),
                outputSchema = objectSchema("monthSummary" to mapOf("type" to "object")),
                requiredScope = FINANCE_CASHFLOW_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = GET_PERIOD_SUMMARY,
                description = "Answer arbitrary date-range summary questions. Prefer this over get_month_summary when the user asks about a span of months rather than one calendar month.",
                inputSchema =
                    objectSchema(
                        "financialAccountId" to uuidSchema(),
                        "startYear" to intSchema(min = 1900, max = 2200),
                        "startMonth" to intSchema(min = 1, max = 12),
                        "endYear" to intSchema(min = 1900, max = 2200),
                        "endMonth" to intSchema(min = 1, max = 12),
                        required = setOf("startYear", "startMonth", "endYear", "endMonth"),
                    ),
                outputSchema = objectSchema("periodSummary" to mapOf("type" to "object")),
                requiredScope = FINANCE_CASHFLOW_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = GET_CASH_FLOW_SERIES,
                description = "Return one row per month for a date range, with income, expenses, net cash flow, and closing balance for each month.",
                inputSchema =
                    objectSchema(
                        "financialAccountId" to uuidSchema(),
                        "startYear" to intSchema(min = 1900, max = 2200),
                        "startMonth" to intSchema(min = 1, max = 12),
                        "endYear" to intSchema(min = 1900, max = 2200),
                        "endMonth" to intSchema(min = 1, max = 12),
                        required = setOf("startYear", "startMonth", "endYear", "endMonth"),
                    ),
                outputSchema = objectSchema("cashFlowSeries" to mapOf("type" to "array")),
                requiredScope = FINANCE_CASHFLOW_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = GET_SPENDING_BY_CATEGORY,
                description = "Break spending or income down by category. Use month and year for one calendar month, or use startYear/startMonth/endYear/endMonth for a date range. Do not send both shapes together.",
                inputSchema =
                    objectSchema(
                        "financialAccountId" to uuidSchema(),
                        "search" to stringSchema(),
                        "categoryId" to uuidSchema(),
                        "uncategorized" to booleanSchema(),
                        "tags" to stringArraySchema(),
                        "month" to intSchema(min = 1, max = 12),
                        "year" to intSchema(min = 1900, max = 2200),
                        "startYear" to intSchema(min = 1900, max = 2200),
                        "startMonth" to intSchema(min = 1, max = 12),
                        "endYear" to intSchema(min = 1900, max = 2200),
                        "endMonth" to intSchema(min = 1, max = 12),
                        "applicableTo" to enumSchema("EXPENSE", "INCOME"),
                    ),
                outputSchema = objectSchema("categoryTotals" to mapOf("type" to "array")),
                requiredScope = FINANCE_CASHFLOW_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = GET_SPENDING_BY_TAG,
                description = "Break transactions down by tag for a month and optional filters. Prefer this when the user asks about tagged spending rather than category totals.",
                inputSchema =
                    objectSchema(
                        "financialAccountId" to uuidSchema(),
                        "search" to stringSchema(),
                        "categoryId" to uuidSchema(),
                        "uncategorized" to booleanSchema(),
                        "tags" to stringArraySchema(),
                        "month" to intSchema(min = 1, max = 12),
                        "year" to intSchema(min = 1900, max = 2200),
                        "applicableTo" to enumSchema("EXPENSE", "INCOME"),
                    ),
                outputSchema = objectSchema("tagTotals" to mapOf("type" to "array")),
                requiredScope = FINANCE_CASHFLOW_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = GET_TOP_MERCHANTS,
                description = "Return the top merchants for a month, with count, total spend, and last-used date. Use this instead of listing transactions when the question is merchant-focused.",
                inputSchema =
                    objectSchema(
                        "financialAccountId" to uuidSchema(),
                        "year" to intSchema(min = 1900, max = 2200),
                        "month" to intSchema(min = 1, max = 12),
                        "limit" to intSchema(min = 1, max = 50),
                    ),
                outputSchema = objectSchema("topMerchants" to mapOf("type" to "array")),
                requiredScope = FINANCE_CASHFLOW_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = LIST_FINANCIAL_ACCOUNTS,
                description = "List the authenticated account's financial accounts and cards, including account metadata, issuer information, and display flags.",
                inputSchema = objectSchema(),
                outputSchema = objectSchema("financialAccounts" to mapOf("type" to "array")),
                requiredScope = FINANCE_ACCOUNTS_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = LIST_CREDIT_CARD_BILLS,
                description = "List credit card bills for one financial account. financialAccountId is required and must point to the card account whose bills you need.",
                inputSchema =
                    objectSchema(
                        "financialAccountId" to uuidSchema(),
                        required = setOf("financialAccountId"),
                    ),
                outputSchema = objectSchema("creditCardBills" to mapOf("type" to "array")),
                requiredScope = FINANCE_BILLS_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = GET_CREDIT_CARD_BILL,
                description = "Fetch one credit card bill for a financial account. financialAccountId is required; referenceMonth is optional and uses YYYY-MM-DD.",
                inputSchema =
                    objectSchema(
                        "financialAccountId" to uuidSchema(),
                        "referenceMonth" to mapOf("type" to "string", "format" to "date"),
                        required = setOf("financialAccountId"),
                    ),
                outputSchema = objectSchema("creditCardBill" to mapOf("type" to "object")),
                requiredScope = FINANCE_BILLS_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = LIST_CATEGORIES,
                description = "List transaction categories available to the authenticated account, including system and user-defined categories.",
                inputSchema = objectSchema(),
                outputSchema = objectSchema("categories" to mapOf("type" to "array")),
                requiredScope = FINANCE_CATEGORIES_READ,
            ),
            FinanceReadMcpToolDefinition(
                name = LIST_TRANSACTION_RULES,
                description = "List transaction auto-categorization rules. Use search to filter by pattern text and isSystem to focus on system or user rules.",
                inputSchema =
                    objectSchema(
                        "isSystem" to booleanSchema(),
                        "search" to stringSchema(),
                        "first" to intSchema(min = 1, max = 100),
                        "after" to stringSchema(),
                    ),
                outputSchema = objectSchema("transactionRules" to mapOf("type" to "object")),
                requiredScope = FINANCE_RULES_READ,
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

    private fun booleanSchema(): Map<String, Any?> = mapOf("type" to "boolean")

    private fun stringArraySchema(): Map<String, Any?> =
        mapOf(
            "type" to "array",
            "items" to mapOf("type" to "string"),
        )

    private fun intSchema(
        min: Int,
        max: Int,
    ): Map<String, Any?> =
        mapOf(
            "type" to "integer",
            "minimum" to min,
            "maximum" to max,
        )

    private fun enumSchema(vararg values: String): Map<String, Any?> =
        mapOf(
            "type" to "string",
            "enum" to values.toList(),
        )

    private const val FINANCE_TRANSACTIONS_READ = "finance:transactions:read"
    private const val FINANCE_CASHFLOW_READ = "finance:cashflow:read"
    private const val FINANCE_ACCOUNTS_READ = "finance:accounts:read"
    private const val FINANCE_BILLS_READ = "finance:bills:read"
    private const val FINANCE_CATEGORIES_READ = "finance:categories:read"
    private const val FINANCE_RULES_READ = "finance:rules:read"
}
