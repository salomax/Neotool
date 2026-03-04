package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBill
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.MonthSummaryQueryService
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.domain.transaction.TransactionService
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.domain.transaction.UpdateTransactionCommand
import io.github.salomax.neotool.banking.graphql.dto.CreditCardBillDTO
import io.github.salomax.neotool.banking.graphql.dto.MonthSummaryDTO
import io.github.salomax.neotool.banking.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.banking.graphql.dto.TransactionBelongsToOptionDTO
import io.github.salomax.neotool.banking.graphql.dto.TransactionCategoryDTO
import io.github.salomax.neotool.banking.graphql.dto.TransactionConnectionDTO
import io.github.salomax.neotool.banking.graphql.dto.TransactionDTO
import io.github.salomax.neotool.banking.graphql.dto.TransactionEdgeDTO
import io.github.salomax.neotool.banking.graphql.dto.TransactionTagOptionDTO
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * GraphQL resolver for [Transaction] and [TransactionCategory] queries and mutations.
 */
@Singleton
class TransactionResolver(
    private val service: TransactionService,
    private val categoryRepository: TransactionCategoryRepository,
    private val monthSummaryQueryService: MonthSummaryQueryService,
    private val creditCardBillService: CreditCardBillService,
) {

    // ── Queries ──────────────────────────────────────────────────────────────

    fun transactionCategories(principal: RequestPrincipal): List<TransactionCategoryDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return service.listCategories(accountId).map { it.toDTO() }
    }

    fun transactionBelongsToOptions(principal: RequestPrincipal): List<TransactionBelongsToOptionDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return service.listBelongsToOptions(accountId)
            .map {
                TransactionBelongsToOptionDTO(
                    belongsToUserId = it.userId,
                    belongsToName = it.name,
                )
            }
    }

    fun transactionTags(principal: RequestPrincipal): List<TransactionTagOptionDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return service.listTags(accountId).map { TransactionTagOptionDTO(tag = it) }
    }

    fun transactions(
        financialAccountId: String?,
        belongsToUserId: String?,
        belongsToName: String?,
        tags: List<String>?,
        billId: String?,
        month: Int?,
        year: Int?,
        first: Int?,
        after: String?,
        principal: RequestPrincipal,
    ): TransactionConnectionDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val faId = financialAccountId?.let { UUID.fromString(it) }
        val belongsToUserUuid = belongsToUserId?.let { UUID.fromString(it) }
        val billUuid = billId?.let { UUID.fromString(it) }

        val categories = categoryRepository.findAll(accountId).associateBy { it.id!! }

        val connection = service.listTransactions(
            accountId = accountId,
            financialAccountId = faId,
            belongsToUserId = belongsToUserUuid,
            belongsToName = belongsToName,
            tags = tags,
            billId = billUuid,
            month = month,
            year = year,
            first = first,
            after = after,
        )

        val edges =
            connection.edges.map { edge ->
                val tx = edge.node
                val category = categories[tx.categoryId]
                val dto = tx.toDTO(category)
                TransactionEdgeDTO(
                    node = dto,
                    cursor = edge.cursor,
                )
            }

        val pageInfoDto =
            PageInfoDTO(
                hasNextPage = connection.pageInfo.hasNextPage,
                hasPreviousPage = connection.pageInfo.hasPreviousPage,
                startCursor = connection.pageInfo.startCursor,
                endCursor = connection.pageInfo.endCursor,
            )

        return TransactionConnectionDTO(
            edges = edges,
            pageInfo = pageInfoDto,
            totalCount = connection.totalCount?.toInt(),
        )
    }

    fun transaction(id: String, principal: RequestPrincipal): TransactionDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val txId = UUID.fromString(id)

        val tx = service.findById(txId, accountId)
        val category = tx.categoryId?.let { categoryRepository.findById(it) }
        return tx.toDTO(category)
    }

    fun monthSummary(
        financialAccountId: String?,
        year: Int?,
        month: Int?,
        principal: RequestPrincipal,
    ): MonthSummaryDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val today = LocalDate.now()
        val targetYear = year ?: today.year
        val targetMonth = month ?: today.monthValue
        require(targetMonth in 1..12) { "month must be between 1 and 12, got: $targetMonth" }

        val summary = if (financialAccountId != null) {
            monthSummaryQueryService.getMonthSummary(UUID.fromString(financialAccountId), accountId, targetYear, targetMonth)
        } else {
            monthSummaryQueryService.getAggregateMonthlySummary(accountId, targetYear, targetMonth)
        }
        return MonthSummaryDTO(
            year = summary.year,
            month = summary.month,
            totalIncome = summary.totalIncome.toDouble(),
            totalExpenses = summary.totalExpenses.toDouble(),
            closingBalance = summary.closingBalance.toDouble(),
            openingBalance = summary.openingBalance.toDouble(),
            transfersIn = summary.transfersIn.toDouble(),
            transfersOut = summary.transfersOut.toDouble(),
            projectedClosingBalance = summary.projectedClosingBalance?.toDouble(),
            isCurrentMonth = summary.isCurrentMonth,
        )
    }

    fun creditCardBills(financialAccountId: String, principal: RequestPrincipal): List<CreditCardBillDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return creditCardBillService.listBills(UUID.fromString(financialAccountId), accountId).map { it.toDTO() }
    }

    fun creditCardBill(
        financialAccountId: String,
        closingDate: String?,
        principal: RequestPrincipal,
    ): CreditCardBillDTO? {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val date = closingDate?.let { LocalDate.parse(it) }
        return creditCardBillService.findBill(UUID.fromString(financialAccountId), accountId, date)?.toDTO()
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun createTransaction(input: Map<String, Any?>, principal: RequestPrincipal): TransactionDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }

        val command = input.toCreateCommand()
        val saved = service.create(command, accountId, userId)
        val category = saved.categoryId?.let { categoryRepository.findById(it) }
        return saved.toDTO(category)
    }

    fun updateTransaction(id: String, input: Map<String, Any?>, principal: RequestPrincipal): TransactionDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val txId = UUID.fromString(id)

        val command = input.toUpdateCommand()
        val updated = service.update(txId, command, accountId)
        val category = updated.categoryId?.let { categoryRepository.findById(it) }
        return updated.toDTO(category)
    }

    fun deleteTransaction(id: String, principal: RequestPrincipal): Boolean {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return service.delete(UUID.fromString(id), accountId)
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun CreditCardBill.toDTO() = CreditCardBillDTO(
        id = id!!,
        financialAccountId = financialAccountId,
        closingDate = closingDate,
        dueDate = dueDate,
        totalAmount = totalAmount.toDouble(),
        minimumPayment = minimumPayment?.toDouble(),
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun TransactionCategory.toDTO(): TransactionCategoryDTO =
        TransactionCategoryDTO(
            id = id!!,
            parentId = parentId,
            name = name,
            applicableTo = applicableTo,
            icon = icon,
            color = color,
            isSystem = isSystem,
            sortOrder = sortOrder,
        )

    private fun Transaction.toDTO(category: TransactionCategory?): TransactionDTO =
        TransactionDTO(
            id = id!!,
            financialAccountId = financialAccountId,
            belongsToUserId = belongsToUserId,
            belongsToName = belongsToName,
            creditDebitType = creditDebitType,
            type = type,
            status = status,
            source = source,
            amount = amount.toDouble(),
            currencyCode = currencyCode,
            amountBrl = amountBrl?.toDouble(),
            date = date,
            transactionDatetime = transactionDatetime,
            description = description,
            notes = notes,
            tags = tags,
            category = category?.toDTO(),
            counterpartyName = counterpartyName,
            counterpartyDocument = counterpartyDocument,
            counterpartyBankCode = counterpartyBankCode,
            counterpartyAccountNumber = counterpartyAccountNumber,
            paymentReason = paymentReason,
            transferPeerTransactionId = transferPeerTransactionId,
            installmentNumber = installmentNumber,
            totalInstallments = totalInstallments,
            cardIdentifier = cardIdentifier,
            payeeMcc = payeeMcc,
            billPostDate = billPostDate,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun getDate(map: Map<String, Any?>, key: String): LocalDate? {
        val v = map[key] ?: return null
        return when (v) {
            is String -> LocalDate.parse(v)
            is LocalDate -> v
            else -> v.toString().let { s -> runCatching { LocalDate.parse(s) }.getOrNull() }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toCreateCommand(): CreateTransactionCommand =
        CreateTransactionCommand(
            financialAccountId = UUID.fromString(this["financialAccountId"] as String),
            creditDebitType = CreditDebitType.valueOf(this["creditDebitType"] as String),
            type = TransactionType.valueOf(this["type"] as String),
            amount = BigDecimal((this["amount"] as Number).toString()),
            date = getDate(this, "date") ?: throw IllegalArgumentException("date is required"),
            description = this["description"] as String,
            status = (this["status"] as? String)?.let { TransactionStatus.valueOf(it) }
                ?: TransactionStatus.POSTED,
            currencyCode = (this["currencyCode"] as? String) ?: "BRL",
            notes = this["notes"] as? String,
            tags = (this["tags"] as? List<*>)?.map { it as String } ?: emptyList(),
            categoryId = (this["categoryId"] as? String)?.let { UUID.fromString(it) },
            belongsToUserId = (this["belongsToUserId"] as? String)?.let { UUID.fromString(it) },
            belongsToName = this["belongsToName"] as? String,
            counterpartyName = this["counterpartyName"] as? String,
            counterpartyDocument = this["counterpartyDocument"] as? String,
            counterpartyBankCode = this["counterpartyBankCode"] as? String,
            counterpartyAccountNumber = this["counterpartyAccountNumber"] as? String,
            paymentReason = this["paymentReason"] as? String,
            installmentNumber = this["installmentNumber"] as? Int,
            totalInstallments = this["totalInstallments"] as? Int,
            cardIdentifier = this["cardIdentifier"] as? String,
            payeeMcc = this["payeeMcc"] as? Int,
        )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.toUpdateCommand(): UpdateTransactionCommand =
        UpdateTransactionCommand(
            creditDebitType = (this["creditDebitType"] as? String)?.let { CreditDebitType.valueOf(it) },
            type = (this["type"] as? String)?.let { TransactionType.valueOf(it) },
            amount = (this["amount"] as? Number)?.let { BigDecimal(it.toString()) },
            date = getDate(this, "date"),
            description = this["description"] as? String,
            status = (this["status"] as? String)?.let { TransactionStatus.valueOf(it) },
            notes = this["notes"] as? String,
            tags = (this["tags"] as? List<*>)?.map { it as String },
            tagsProvided = this.containsKey("tags"),
            categoryId = (this["categoryId"] as? String)?.let { UUID.fromString(it) },
            belongsToUserId = (this["belongsToUserId"] as? String)?.let { UUID.fromString(it) },
            belongsToName = this["belongsToName"] as? String,
            belongsToProvided = this.containsKey("belongsToUserId") || this.containsKey("belongsToName"),
            counterpartyName = this["counterpartyName"] as? String,
            counterpartyDocument = this["counterpartyDocument"] as? String,
            counterpartyBankCode = this["counterpartyBankCode"] as? String,
            counterpartyAccountNumber = this["counterpartyAccountNumber"] as? String,
            paymentReason = this["paymentReason"] as? String,
            cardIdentifier = this["cardIdentifier"] as? String,
            payeeMcc = this["payeeMcc"] as? Int,
        )
}
