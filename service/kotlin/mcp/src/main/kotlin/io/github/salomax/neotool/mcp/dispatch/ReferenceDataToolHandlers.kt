package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.mcp.catalog.FinanceReadMcpTools
import io.github.salomax.neotool.mcp.render.McpToolResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import jakarta.inject.Singleton

@Singleton
class ListFinancialAccountsToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.LIST_FINANCIAL_ACCOUNTS

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(emptySet())
        val accounts = queryField("financialAccounts", FINANCIAL_ACCOUNTS_QUERY, emptyMap(), principal) ?: emptyList<Map<String, Any?>>()
        return success("financialAccounts" to accounts)
    }

    private companion object {
        private const val FINANCIAL_ACCOUNTS_QUERY = """
            query McpFinancialAccounts {
              financialAccounts {
                id
                type
                subtype
                name
                currencyCode
                includeInNetWorth
                includeInDashboard
                includeCashInAllocation
                themeColor
                issuerName
                issuer {
                  id
                  name
                  domain
                }
                bankData {
                  branchCode
                  number
                  checkDigit
                  maskedNumber
                }
                cardData {
                  maskedNumber
                  bin
                  cardNetwork
                  cardLevel
                  creditLimit
                  availableCreditLimit
                  minimumPaymentPercentage
                  closingDay
                  dueDay
                  linkedBankAccountId
                  isMultiCard
                }
                connectionStatus
                consentExpiresAt
                lastSyncAt
                dataSince {
                  year
                  month
                }
                initialBalance
                initialBalanceDate
                createdAt
                updatedAt
              }
            }
        """
    }
}

@Singleton
class ListCreditCardBillsToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.LIST_CREDIT_CARD_BILLS

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(setOf("financialAccountId"))
        val financialAccountId = arguments.requiredUuidArgument("financialAccountId")
        val bills = queryField("creditCardBills", CREDIT_CARD_BILLS_QUERY, mapOf("financialAccountId" to financialAccountId), principal)
            ?: emptyList<Map<String, Any?>>()
        return success("creditCardBills" to bills)
    }

    private companion object {
        private const val CREDIT_CARD_BILL_FIELDS = """
            id
            financialAccountId
            closingDate
            dueDate
            referenceMonth
            totalAmount
            minimumPayment
            status
            createdAt
            updatedAt
        """

        private const val CREDIT_CARD_BILLS_QUERY = """
            query McpCreditCardBills(${'$'}financialAccountId: ID!) {
              creditCardBills(financialAccountId: ${'$'}financialAccountId) {
                $CREDIT_CARD_BILL_FIELDS
              }
            }
        """
    }
}

@Singleton
class GetCreditCardBillToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.GET_CREDIT_CARD_BILL

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(setOf("financialAccountId", "referenceMonth"))
        val financialAccountId = arguments.requiredUuidArgument("financialAccountId")
        val variables =
            buildMap<String, Any> {
                put("financialAccountId", financialAccountId)
                arguments.dateArgument("referenceMonth")?.let { put("referenceMonth", it) }
            }
        val bill = queryField("creditCardBill", CREDIT_CARD_BILL_QUERY, variables, principal)
        if (bill == null) {
            notFound("creditCardBill", financialAccountId)
        }
        return success("creditCardBill" to bill)
    }

    private companion object {
        private const val CREDIT_CARD_BILL_QUERY = """
            query McpCreditCardBill(${'$'}financialAccountId: ID!, ${'$'}referenceMonth: Date) {
              creditCardBill(financialAccountId: ${'$'}financialAccountId, referenceMonth: ${'$'}referenceMonth) {
                id
                financialAccountId
                closingDate
                dueDate
                referenceMonth
                totalAmount
                minimumPayment
                status
                createdAt
                updatedAt
              }
            }
        """
    }
}

@Singleton
class ListCategoriesToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.LIST_CATEGORIES

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(emptySet())
        val categories = queryField("transactionCategories", CATEGORIES_QUERY, emptyMap(), principal) ?: emptyList<Map<String, Any?>>()
        return success("categories" to categories)
    }

    private companion object {
        private const val CATEGORIES_QUERY = """
            query McpTransactionCategories {
              transactionCategories {
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
        """
    }
}

@Singleton
class ListTransactionRulesToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.LIST_TRANSACTION_RULES

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(setOf("isSystem", "search", "first", "after"))
        val variables =
            buildMap<String, Any> {
                put("first", (arguments.intArgument("first") ?: 50).coerceIn(1, 100))
                arguments.booleanArgument("isSystem")?.let { put("isSystem", it) }
                arguments.stringArgument("search")?.let { put("search", it) }
                arguments.stringArgument("after")?.let { put("after", it) }
            }

        val rules = queryField("transactionRules", TRANSACTION_RULES_QUERY, variables, principal) ?: emptyMap<String, Any?>()
        return success("transactionRules" to rules)
    }

    private companion object {
        private const val TRANSACTION_RULES_QUERY = """
            query McpTransactionRules(${'$'}isSystem: Boolean, ${'$'}search: String, ${'$'}first: Int!, ${'$'}after: String) {
              transactionRules(isSystem: ${'$'}isSystem, search: ${'$'}search, first: ${'$'}first, after: ${'$'}after) {
                edges {
                  cursor
                  node {
                    id
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
                  }
                }
                pageInfo {
                  hasNextPage
                  hasPreviousPage
                  startCursor
                  endCursor
                }
                totalCount
              }
            }
        """
    }
}
