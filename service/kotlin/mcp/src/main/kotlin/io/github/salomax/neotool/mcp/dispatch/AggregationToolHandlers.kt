package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.mcp.catalog.FinanceReadMcpTools
import io.github.salomax.neotool.mcp.render.McpToolResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import jakarta.inject.Singleton

@Singleton
class GetMonthSummaryToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.GET_MONTH_SUMMARY
    override val graphQLDocuments: List<String> = listOf(MONTH_SUMMARY_QUERY)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(setOf("financialAccountId", "year", "month"))
        val variables =
            buildMap<String, Any> {
                arguments.uuidArgument("financialAccountId")?.let { put("financialAccountId", it) }
                validateYear("year", arguments.intArgument("year"))?.let { put("year", it) }
                validateMonth("month", arguments.intArgument("month"))?.let { put("month", it) }
            }

        val summary = queryField("monthSummary", MONTH_SUMMARY_QUERY, variables, principal) ?: emptyMap<String, Any?>()
        return success("monthSummary" to summary)
    }

    private companion object {
        private const val MONTH_SUMMARY_QUERY = """
            query McpMonthSummary(${'$'}financialAccountId: ID, ${'$'}year: Int, ${'$'}month: Int) {
              monthSummary(financialAccountId: ${'$'}financialAccountId, year: ${'$'}year, month: ${'$'}month) {
                year
                month
                totalIncome
                totalExpenses
                closingBalance
                openingBalance
                transfersIn
                transfersOut
                projectedClosingBalance
                isCurrentMonth
                isFutureMonth
                expensesByCategory {
                  uncategorized
                  totalAmount
                  transactionCount
                  percentage
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
                }
                incomeByCategory {
                  uncategorized
                  totalAmount
                  transactionCount
                  percentage
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
                }
                tagsSummary {
                  tag
                  totalAmount
                  transactionCount
                  percentage
                }
              }
            }
        """
    }
}

@Singleton
class GetPeriodSummaryToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.GET_PERIOD_SUMMARY
    override val graphQLDocuments: List<String> = listOf(PERIOD_SUMMARY_QUERY)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(
            setOf("financialAccountId", "startYear", "startMonth", "endYear", "endMonth"),
        )
        val startYear = validateYear("startYear", arguments.intArgument("startYear")) ?: missing("startYear")
        val startMonth = validateMonth("startMonth", arguments.intArgument("startMonth")) ?: missing("startMonth")
        val endYear = validateYear("endYear", arguments.intArgument("endYear")) ?: missing("endYear")
        val endMonth = validateMonth("endMonth", arguments.intArgument("endMonth")) ?: missing("endMonth")
        validateYearMonthRange(startYear, startMonth, endYear, endMonth)

        val variables =
            buildMap<String, Any> {
                arguments.uuidArgument("financialAccountId")?.let { put("financialAccountId", it) }
                put("startYear", startYear)
                put("startMonth", startMonth)
                put("endYear", endYear)
                put("endMonth", endMonth)
            }

        val summary =
            queryField(
                "dashboardPeriodSummary",
                PERIOD_SUMMARY_QUERY,
                variables,
                principal,
            ) ?: emptyMap<String, Any?>()
        return success("periodSummary" to summary)
    }

    private fun missing(field: String): Nothing =
        throw io.github.salomax.neotool.common.error.ValidationException(
            io.github.salomax.neotool.common.error.CommonErrorCode.INVALID_INPUT,
            field = field,
            parameters = mapOf("field" to field),
        )

    private companion object {
        private const val PERIOD_SUMMARY_QUERY = """
            query McpPeriodSummary(${'$'}financialAccountId: ID, ${'$'}startYear: Int!, ${'$'}startMonth: Int!, ${'$'}endYear: Int!, ${'$'}endMonth: Int!) {
              dashboardPeriodSummary(
                financialAccountId: ${'$'}financialAccountId,
                startYear: ${'$'}startYear,
                startMonth: ${'$'}startMonth,
                endYear: ${'$'}endYear,
                endMonth: ${'$'}endMonth
              ) {
                totalIncome
                totalExpenses
                net
                monthCount
                avgMonthlyIncome
                avgMonthlyExpenses
                avgMonthlySavings
                savingsRate
                closingCashBalance
                closingPortfolioValue
                totalNetWorth
                totalInvested
                avgMonthlyInvested
                totalReturn
                avgMonthlyReturn
                openingPortfolioValue
                returnPct
                closingBalance
                openingBalance
              }
            }
        """
    }
}

@Singleton
class GetCashFlowSeriesToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.GET_CASH_FLOW_SERIES
    override val graphQLDocuments: List<String> = listOf(CASH_FLOW_QUERY)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(
            setOf("financialAccountId", "startYear", "startMonth", "endYear", "endMonth"),
        )
        val startYear = validateYear("startYear", arguments.intArgument("startYear")) ?: missing("startYear")
        val startMonth = validateMonth("startMonth", arguments.intArgument("startMonth")) ?: missing("startMonth")
        val endYear = validateYear("endYear", arguments.intArgument("endYear")) ?: missing("endYear")
        val endMonth = validateMonth("endMonth", arguments.intArgument("endMonth")) ?: missing("endMonth")
        validateYearMonthRange(startYear, startMonth, endYear, endMonth)

        val variables =
            buildMap<String, Any> {
                arguments.uuidArgument("financialAccountId")?.let { put("financialAccountId", it) }
                put("startYear", startYear)
                put("startMonth", startMonth)
                put("endYear", endYear)
                put("endMonth", endMonth)
            }

        val series =
            queryField(
                "cashFlowSeries",
                CASH_FLOW_QUERY,
                variables,
                principal,
            ) ?: emptyList<Map<String, Any?>>()
        return success("cashFlowSeries" to series)
    }

    private fun missing(field: String): Nothing =
        throw io.github.salomax.neotool.common.error.ValidationException(
            io.github.salomax.neotool.common.error.CommonErrorCode.INVALID_INPUT,
            field = field,
            parameters = mapOf("field" to field),
        )

    private companion object {
        private const val CASH_FLOW_QUERY = """
            query McpCashFlowSeries(${'$'}financialAccountId: ID, ${'$'}startYear: Int!, ${'$'}startMonth: Int!, ${'$'}endYear: Int!, ${'$'}endMonth: Int!) {
              cashFlowSeries(
                financialAccountId: ${'$'}financialAccountId,
                startYear: ${'$'}startYear,
                startMonth: ${'$'}startMonth,
                endYear: ${'$'}endYear,
                endMonth: ${'$'}endMonth
              ) {
                year
                month
                totalIncome
                totalExpenses
                net
                closingBalance
              }
            }
        """
    }
}

@Singleton
class GetSpendingByCategoryToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.GET_SPENDING_BY_CATEGORY
    override val graphQLDocuments: List<String> = listOf(CATEGORY_MONTH_QUERY, CATEGORY_RANGE_QUERY)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(
            setOf(
                "financialAccountId",
                "search",
                "categoryId",
                "uncategorized",
                "tags",
                "month",
                "year",
                "startYear",
                "startMonth",
                "endYear",
                "endMonth",
                "applicableTo",
            ),
        )

        val hasMonthShape = arguments.intArgument("month") != null || arguments.intArgument("year") != null
        val hasRangeShape =
            arguments.intArgument("startYear") != null ||
                arguments.intArgument("startMonth") != null ||
                arguments.intArgument("endYear") != null ||
                arguments.intArgument("endMonth") != null

        if (hasMonthShape == hasRangeShape) {
            throw io.github.salomax.neotool.common.error.ValidationException(
                io.github.salomax.neotool.common.error.CommonErrorCode.VALIDATION_ERROR,
                field = "mode",
                parameters = mapOf("field" to "mode"),
            )
        }

        val applicableTo = arguments.enumArgument("applicableTo", setOf("EXPENSE", "INCOME"))
        val financialAccountId = arguments.uuidArgument("financialAccountId")

        val result =
            if (hasMonthShape) {
                val month = validateMonth("month", arguments.intArgument("month")) ?: missing("month")
                val year = validateYear("year", arguments.intArgument("year")) ?: missing("year")
                val variables =
                    buildMap<String, Any> {
                        financialAccountId?.let { put("financialAccountId", it) }
                        arguments.stringArgument("search")?.let { put("search", it) }
                        arguments.uuidArgument("categoryId")?.let { put("categoryId", it) }
                        arguments.booleanArgument("uncategorized")?.let { put("uncategorized", it) }
                        arguments.stringListArgument("tags")?.let { put("tags", it) }
                        applicableTo?.let { put("applicableTo", it) }
                        put("month", month)
                        put("year", year)
                    }
                queryField("transactionsByCategory", CATEGORY_MONTH_QUERY, variables, principal)
            } else {
                if (arguments.stringArgument("search") != null ||
                    arguments.uuidArgument("categoryId") != null ||
                    arguments.booleanArgument("uncategorized") != null ||
                    arguments.stringListArgument("tags") != null
                ) {
                    throw io.github.salomax.neotool.common.error.ValidationException(
                        io.github.salomax.neotool.common.error.CommonErrorCode.INVALID_INPUT,
                        field = "search",
                        parameters = mapOf("field" to "search"),
                    )
                }
                val startYear = validateYear("startYear", arguments.intArgument("startYear")) ?: missing("startYear")
                val startMonth =
                    validateMonth(
                        "startMonth",
                        arguments.intArgument("startMonth"),
                    ) ?: missing("startMonth")
                val endYear = validateYear("endYear", arguments.intArgument("endYear")) ?: missing("endYear")
                val endMonth = validateMonth("endMonth", arguments.intArgument("endMonth")) ?: missing("endMonth")
                validateYearMonthRange(startYear, startMonth, endYear, endMonth)
                val variables =
                    buildMap<String, Any> {
                        financialAccountId?.let { put("financialAccountId", it) }
                        applicableTo?.let { put("applicableTo", it) }
                        put("startYear", startYear)
                        put("startMonth", startMonth)
                        put("endYear", endYear)
                        put("endMonth", endMonth)
                    }
                queryField("categoryBreakdownForRange", CATEGORY_RANGE_QUERY, variables, principal)
            } ?: emptyList<Map<String, Any?>>()

        return success("categoryTotals" to result)
    }

    private fun missing(field: String): Nothing =
        throw io.github.salomax.neotool.common.error.ValidationException(
            io.github.salomax.neotool.common.error.CommonErrorCode.INVALID_INPUT,
            field = field,
            parameters = mapOf("field" to field),
        )

    private companion object {
        private const val CATEGORY_FIELDS = """
            uncategorized
            totalAmount
            transactionCount
            percentage
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
        """

        private const val CATEGORY_MONTH_QUERY = """
            query McpTransactionsByCategory(
              ${'$'}financialAccountId: ID,
              ${'$'}search: String,
              ${'$'}categoryId: ID,
              ${'$'}uncategorized: Boolean,
              ${'$'}tags: [String!],
              ${'$'}month: Int!,
              ${'$'}year: Int!,
              ${'$'}applicableTo: CategoryApplicableTo
            ) {
              transactionsByCategory(
                financialAccountId: ${'$'}financialAccountId,
                search: ${'$'}search,
                categoryId: ${'$'}categoryId,
                uncategorized: ${'$'}uncategorized,
                tags: ${'$'}tags,
                month: ${'$'}month,
                year: ${'$'}year,
                applicableTo: ${'$'}applicableTo
              ) {
                $CATEGORY_FIELDS
              }
            }
        """

        private const val CATEGORY_RANGE_QUERY = """
            query McpCategoryBreakdownForRange(
              ${'$'}financialAccountId: ID,
              ${'$'}startYear: Int!,
              ${'$'}startMonth: Int!,
              ${'$'}endYear: Int!,
              ${'$'}endMonth: Int!,
              ${'$'}applicableTo: CategoryApplicableTo
            ) {
              categoryBreakdownForRange(
                financialAccountId: ${'$'}financialAccountId,
                startYear: ${'$'}startYear,
                startMonth: ${'$'}startMonth,
                endYear: ${'$'}endYear,
                endMonth: ${'$'}endMonth,
                applicableTo: ${'$'}applicableTo
              ) {
                $CATEGORY_FIELDS
              }
            }
        """
    }
}

@Singleton
class GetSpendingByTagToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.GET_SPENDING_BY_TAG
    override val graphQLDocuments: List<String> = listOf(TAG_QUERY)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(
            setOf(
                "financialAccountId",
                "search",
                "categoryId",
                "uncategorized",
                "tags",
                "month",
                "year",
                "applicableTo",
            ),
        )

        val variables =
            buildMap<String, Any> {
                arguments.uuidArgument("financialAccountId")?.let { put("financialAccountId", it) }
                arguments.stringArgument("search")?.let { put("search", it) }
                arguments.uuidArgument("categoryId")?.let { put("categoryId", it) }
                arguments.booleanArgument("uncategorized")?.let { put("uncategorized", it) }
                arguments.stringListArgument("tags")?.let { put("tags", it) }
                validateMonth("month", arguments.intArgument("month"))?.let { put("month", it) }
                validateYear("year", arguments.intArgument("year"))?.let { put("year", it) }
                arguments.enumArgument("applicableTo", setOf("EXPENSE", "INCOME"))?.let { put("applicableTo", it) }
            }

        val result = queryField("transactionsByTag", TAG_QUERY, variables, principal) ?: emptyList<Map<String, Any?>>()
        return success("tagTotals" to result)
    }

    private companion object {
        private const val TAG_QUERY = """
            query McpTransactionsByTag(
              ${'$'}financialAccountId: ID,
              ${'$'}search: String,
              ${'$'}categoryId: ID,
              ${'$'}uncategorized: Boolean,
              ${'$'}tags: [String!],
              ${'$'}month: Int,
              ${'$'}year: Int,
              ${'$'}applicableTo: CategoryApplicableTo
            ) {
              transactionsByTag(
                financialAccountId: ${'$'}financialAccountId,
                search: ${'$'}search,
                categoryId: ${'$'}categoryId,
                uncategorized: ${'$'}uncategorized,
                tags: ${'$'}tags,
                month: ${'$'}month,
                year: ${'$'}year,
                applicableTo: ${'$'}applicableTo
              ) {
                tag
                totalAmount
                transactionCount
                percentage
              }
            }
        """
    }
}

@Singleton
class GetTopMerchantsToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.GET_TOP_MERCHANTS
    override val graphQLDocuments: List<String> = listOf(TOP_MERCHANTS_QUERY)

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(setOf("financialAccountId", "year", "month", "limit"))
        val variables =
            buildMap<String, Any> {
                arguments.uuidArgument("financialAccountId")?.let { put("financialAccountId", it) }
                validateYear("year", arguments.intArgument("year"))?.let { put("year", it) }
                validateMonth("month", arguments.intArgument("month"))?.let { put("month", it) }
                arguments.intArgument("limit")?.let { put("limit", it) }
            }

        val merchants =
            queryField(
                "topMerchants",
                TOP_MERCHANTS_QUERY,
                variables,
                principal,
            ) ?: emptyList<Map<String, Any?>>()
        return success("topMerchants" to merchants)
    }

    private companion object {
        private const val TOP_MERCHANTS_QUERY = """
            query McpTopMerchants(${'$'}financialAccountId: ID, ${'$'}year: Int, ${'$'}month: Int, ${'$'}limit: Int) {
              topMerchants(financialAccountId: ${'$'}financialAccountId, year: ${'$'}year, month: ${'$'}month, limit: ${'$'}limit) {
                transactionCount
                totalAmount
                lastUsedAt
                merchant {
                  id
                  name
                  domain
                  sector
                  isSystem
                  isOwned
                }
              }
            }
        """
    }
}
