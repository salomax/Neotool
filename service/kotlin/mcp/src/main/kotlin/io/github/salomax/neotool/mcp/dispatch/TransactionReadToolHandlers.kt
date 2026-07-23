package io.github.salomax.neotool.mcp.dispatch

import io.github.salomax.neotool.mcp.catalog.FinanceReadMcpTools
import io.github.salomax.neotool.mcp.render.McpToolResult
import io.github.salomax.neotool.mcp.security.McpPrincipal
import jakarta.inject.Singleton

@Singleton
class ListTransactionsToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.LIST_TRANSACTIONS

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
                "creditDebitType",
                "types",
                "month",
                "year",
                "first",
                "after",
            ),
        )

        val variables =
            buildMap<String, Any> {
                put("first", (arguments.intArgument("first") ?: 50).coerceIn(1, 100))
                arguments.stringArgument("after")?.let { put("after", it) }
                arguments.uuidArgument("financialAccountId")?.let { put("financialAccountId", it) }
                arguments.stringArgument("search")?.let { put("search", it) }
                arguments.uuidArgument("categoryId")?.let { put("categoryId", it) }
                arguments.booleanArgument("uncategorized")?.let { put("uncategorized", it) }
                arguments.stringListArgument("tags")?.let { put("tags", it) }
                arguments.enumArgument("creditDebitType", setOf("CREDIT", "DEBIT"))?.let { put("creditDebitType", it) }
                arguments.stringListArgument("types")?.let { put("types", it) }
                validateMonth("month", arguments.intArgument("month"))?.let { put("month", it) }
                validateYear("year", arguments.intArgument("year"))?.let { put("year", it) }
            }

        val transactions = queryField("transactions", TRANSACTIONS_QUERY, variables, principal) ?: emptyMap<String, Any?>()
        return success("transactions" to transactions)
    }

    private companion object {
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
            linkedInvestmentAsset {
              id
              name
              symbol
            }
            createdAt
            updatedAt
        """

        private const val TRANSACTIONS_QUERY = """
            query McpListTransactions(
              ${'$'}financialAccountId: ID,
              ${'$'}search: String,
              ${'$'}categoryId: ID,
              ${'$'}uncategorized: Boolean,
              ${'$'}tags: [String!],
              ${'$'}creditDebitType: CreditDebitType,
              ${'$'}types: [TransactionType!],
              ${'$'}month: Int,
              ${'$'}year: Int,
              ${'$'}first: Int!,
              ${'$'}after: String
            ) {
              transactions(
                financialAccountId: ${'$'}financialAccountId,
                search: ${'$'}search,
                categoryId: ${'$'}categoryId,
                uncategorized: ${'$'}uncategorized,
                tags: ${'$'}tags,
                creditDebitType: ${'$'}creditDebitType,
                types: ${'$'}types,
                month: ${'$'}month,
                year: ${'$'}year,
                first: ${'$'}first,
                after: ${'$'}after
              ) {
                edges {
                  cursor
                  node {
                    $TRANSACTION_FIELDS
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

@Singleton
class GetTransactionToolHandler(
    bankingClient: McpBankingGraphQLClient,
) : AbstractMcpBankingToolHandler(bankingClient) {
    override val name: String = FinanceReadMcpTools.GET_TRANSACTION

    override fun call(
        arguments: Map<String, Any?>,
        principal: McpPrincipal,
    ): McpToolResult {
        arguments.assertOnlySupportedArguments(setOf("id"))
        val id = arguments.requiredUuidArgument("id")
        val transaction = queryField("transaction", TRANSACTION_QUERY, mapOf("id" to id), principal)
        if (transaction == null) {
            notFound("transaction", id)
        }
        return success("transaction" to transaction)
    }

    private companion object {
        private const val TRANSACTION_QUERY = """
            query McpGetTransaction(${'$'}id: ID!) {
              transaction(id: ${'$'}id) {
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
                linkedInvestmentAsset {
                  id
                  name
                  symbol
                }
                createdAt
                updatedAt
              }
            }
        """
    }
}
