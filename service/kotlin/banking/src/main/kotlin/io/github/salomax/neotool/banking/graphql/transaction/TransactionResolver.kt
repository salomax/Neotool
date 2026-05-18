package io.github.salomax.neotool.banking.graphql.transaction

import io.github.salomax.neotool.banking.model.dto.transaction.BulkCategorizeResult
import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategoryTotal
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBill
import io.github.salomax.neotool.banking.service.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionRule
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.service.transaction.DashboardAnalyticsService
import io.github.salomax.neotool.banking.service.transaction.MonthSummaryQueryService
import io.github.salomax.neotool.banking.domain.transaction.TagTotal
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.domain.attachment.TransactionAttachment
import io.github.salomax.neotool.banking.repo.attachment.TransactionAttachmentRepository
import io.github.salomax.neotool.banking.repo.import.nfce.NfceDocumentRepository
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.model.dto.transaction.UpdateTransactionCommand
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleEngine
import io.github.salomax.neotool.banking.service.preferences.AccountPreferencesService
import io.github.salomax.neotool.banking.graphql.transaction.CategoryTotalDTO
import io.github.salomax.neotool.banking.graphql.transaction.CreditCardBillDTO
import io.github.salomax.neotool.banking.graphql.merchant.MerchantDTO
import io.github.salomax.neotool.banking.graphql.transaction.MonthSummaryDTO
import io.github.salomax.neotool.banking.graphql.transaction.PageInfoDTO
import io.github.salomax.neotool.banking.graphql.transaction.SimilarTransactionEditPreviewDTO
import io.github.salomax.neotool.banking.graphql.transaction.SimilarTransactionEditResultDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionBelongsToOptionDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionCategoryDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionConnectionDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionDTO
import io.github.salomax.neotool.banking.graphql.transaction.CreateTransactionPayloadDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionRuleDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionEdgeDTO
import io.github.salomax.neotool.banking.graphql.transaction.TagTotalDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionTagOptionDTO
import io.github.salomax.neotool.banking.graphql.portfolio.PortfolioMonthlyValueDTO
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID
import org.slf4j.LoggerFactory

/**
 * GraphQL resolver for [Transaction] and [TransactionCategory] queries and mutations.
 */
@Singleton
class TransactionResolver(
    private val service: TransactionService,
    private val monthSummaryQueryService: MonthSummaryQueryService,
    private val dashboardAnalyticsService: DashboardAnalyticsService,
    private val creditCardBillService: CreditCardBillService,
    private val merchantRepository: MerchantRepository,
    private val transactionRuleEngine: TransactionRuleEngine,
    private val transactionCategoryRepository: TransactionCategoryRepository,
    private val accountPreferencesService: AccountPreferencesService,
    private val nfceDocumentRepository: NfceDocumentRepository,
    private val transactionAttachmentRepository: TransactionAttachmentRepository,
) {
    private val logger = LoggerFactory.getLogger(TransactionResolver::class.java)

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
        search: String?,
        tags: List<String>?,
        billId: String?,
        categoryId: String?,
        uncategorized: Boolean?,
        creditDebitType: CreditDebitType?,
        types: List<TransactionType>?,
        month: Int?,
        year: Int?,
        first: Int?,
        after: String?,
        principal: RequestPrincipal,
    ): TransactionConnectionDTO {
        val startedAt = System.nanoTime()
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val financialAccountId = financialAccountId?.let { UUID.fromString(it) }
        val belongsToUserUuid = belongsToUserId?.let { UUID.fromString(it) }
        val billUuid = billId?.let { UUID.fromString(it) }
        val categoryUuid = categoryId?.let { UUID.fromString(it) }

        val connection =
            service.listTransactions(
                accountId = accountId,
                financialAccountId = financialAccountId,
                belongsToUserId = belongsToUserUuid,
                belongsToName = belongsToName,
                search = search,
                tags = tags,
                billId = billUuid,
                categoryId = categoryUuid,
                uncategorized = uncategorized,
                creditDebitType = creditDebitType,
                types = types,
                month = month,
                year = year,
                first = first,
                after = after,
            )
        val listFinishedAt = System.nanoTime()

        val transactionIds = connection.edges.mapNotNull { it.node.id }
        val categoryIds = connection.edges.mapNotNull { it.node.categoryId }.toSet()
        val categories = service.findAccessibleCategoriesByIds(categoryIds, accountId)
        val categoriesFinishedAt = System.nanoTime()
        val merchantDTOs = buildMerchantDTOMap(connection.edges.mapNotNull { it.node.merchantId }.toSet())
        val merchantsFinishedAt = System.nanoTime()
        val nfceLinkedTransactionIds = nfceDocumentRepository.findLinkedTransactionIds(accountId, transactionIds)
        val nfceFinishedAt = System.nanoTime()
        val attachmentsByTxId = transactionAttachmentRepository.findByTransactionIds(transactionIds, accountId)
        val attachmentsFinishedAt = System.nanoTime()

        val edges =
            connection.edges.map { edge ->
                val tx = edge.node
                val category = categories[tx.categoryId]
                val merchant = merchantDTOs[tx.merchantId]
                val dto = tx.toDTO(
                    category,
                    merchant,
                    nfceLinkedTransactionIds.contains(tx.id),
                    attachmentsByTxId[tx.id] ?: emptyList(),
                )
                TransactionEdgeDTO(
                    node = dto,
                    cursor = edge.cursor,
                )
            }
        val mappingFinishedAt = System.nanoTime()

        val pageInfoDto =
            PageInfoDTO(
                hasNextPage = connection.pageInfo.hasNextPage,
                hasPreviousPage = connection.pageInfo.hasPreviousPage,
                startCursor = connection.pageInfo.startCursor,
                endCursor = connection.pageInfo.endCursor,
            )
        val responseFinishedAt = System.nanoTime()
        if (logger.isDebugEnabled) {
            logger.debug(
                "transactions.resolver timings accountId={} edgeCount={} totalCount={} listMs={} categoriesMs={} merchantsMs={} nfceMs={} attachmentsMs={} dtoMapMs={} responseMs={} totalMs={}",
                accountId,
                connection.edges.size,
                connection.totalCount,
                nanosToMillis(listFinishedAt - startedAt),
                nanosToMillis(categoriesFinishedAt - listFinishedAt),
                nanosToMillis(merchantsFinishedAt - categoriesFinishedAt),
                nanosToMillis(nfceFinishedAt - merchantsFinishedAt),
                nanosToMillis(attachmentsFinishedAt - nfceFinishedAt),
                nanosToMillis(mappingFinishedAt - attachmentsFinishedAt),
                nanosToMillis(responseFinishedAt - mappingFinishedAt),
                nanosToMillis(responseFinishedAt - startedAt),
            )
        }

        return TransactionConnectionDTO(
            edges = edges,
            pageInfo = pageInfoDto,
            totalCount = connection.totalCount?.toInt(),
        )
    }

    fun transaction(
        id: String,
        principal: RequestPrincipal,
    ): TransactionDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val txId = UUID.fromString(id)

        val tx = service.findById(txId, accountId)
        val category = service.findAccessibleCategory(tx.categoryId, accountId)
        val merchant = tx.merchantId?.let { loadMerchantDTO(it) }
        val hasNfceDocument = nfceDocumentRepository.findByTransactionId(txId, accountId) != null
        return tx.toDTO(category, merchant, hasNfceDocument)
    }

    fun similarTransactionEditPreview(
        id: String,
        principal: RequestPrincipal,
    ): SimilarTransactionEditPreviewDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val preview = service.previewSimilarTransactionEdit(UUID.fromString(id), accountId)
        return SimilarTransactionEditPreviewDTO(
            similarCount = preview.similarCount,
            matchDescription = preview.matchDescription,
            supportsFuturePropagation = preview.supportsFuturePropagation,
        )
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

        val summary =
            if (financialAccountId != null) {
                monthSummaryQueryService.getMonthSummary(
                    UUID.fromString(financialAccountId),
                    accountId,
                    targetYear,
                    targetMonth,
                )
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
            isFutureMonth = summary.isFutureMonth,
            expensesByCategory = summary.expensesByCategory.map { it.toCategoryTotalDTO() },
            incomeByCategory = summary.incomeByCategory.map { it.toCategoryTotalDTO() },
            tagsSummary = summary.tagsSummary.map { it.toTagTotalDTO() },
        )
    }

    fun transactionsByCategory(
        financialAccountId: String?,
        belongsToUserId: String?,
        belongsToName: String?,
        search: String?,
        tags: List<String>?,
        billId: String?,
        categoryId: String?,
        uncategorized: Boolean?,
        year: Int?,
        month: Int?,
        applicableTo: String?,
        principal: RequestPrincipal,
    ): List<CategoryTotalDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val applicableToEnum = applicableTo?.let { CategoryApplicableTo.valueOf(it) }
        val breakdown =
            service.getCategoryBreakdown(
                accountId = accountId,
                financialAccountId = financialAccountId?.let { UUID.fromString(it) },
                belongsToUserId = belongsToUserId?.let { UUID.fromString(it) },
                belongsToName = belongsToName,
                search = search,
                tags = tags,
                billId = billId?.let { UUID.fromString(it) },
                categoryId = categoryId?.let { UUID.fromString(it) },
                uncategorized = uncategorized,
                month = month,
                year = year,
                applicableTo = applicableToEnum,
            )
        return breakdown.map { it.toCategoryTotalDTO() }
    }

    fun cashFlowSeries(
        financialAccountId: String?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
        principal: RequestPrincipal,
    ): List<CashFlowDataPointDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        require(startMonth in 1..12) { "startMonth must be between 1 and 12" }
        require(endMonth in 1..12) { "endMonth must be between 1 and 12" }
        return dashboardAnalyticsService.getCashFlowSeries(
            accountId = accountId,
            financialAccountId = financialAccountId?.let { UUID.fromString(it) },
            startYear = startYear,
            startMonth = startMonth,
            endYear = endYear,
            endMonth = endMonth,
        ).map { point ->
            CashFlowDataPointDTO(
                year = point.year,
                month = point.month,
                totalIncome = point.totalIncome,
                totalExpenses = point.totalExpenses,
                net = point.net,
                closingBalance = point.closingBalance,
            )
        }
    }

    fun dashboardPeriodSummary(
        financialAccountId: String?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
        principal: RequestPrincipal,
    ): DashboardPeriodSummaryDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        require(startMonth in 1..12) { "startMonth must be between 1 and 12" }
        require(endMonth in 1..12) { "endMonth must be between 1 and 12" }
        val summary = dashboardAnalyticsService.getDashboardPeriodSummary(
            accountId = accountId,
            financialAccountId = financialAccountId?.let { UUID.fromString(it) },
            startYear = startYear,
            startMonth = startMonth,
            endYear = endYear,
            endMonth = endMonth,
        )
        return DashboardPeriodSummaryDTO(
            totalIncome = summary.totalIncome,
            totalExpenses = summary.totalExpenses,
            net = summary.net,
            monthCount = summary.monthCount,
            avgMonthlyIncome = summary.avgMonthlyIncome,
            avgMonthlyExpenses = summary.avgMonthlyExpenses,
            avgMonthlySavings = summary.avgMonthlySavings,
            savingsRate = summary.savingsRate,
            closingCashBalance = summary.closingCashBalance,
            closingPortfolioValue = summary.closingPortfolioValue,
            totalNetWorth = summary.totalNetWorth,
            totalInvested = summary.totalInvested,
            avgMonthlyInvested = summary.avgMonthlyInvested,
            totalReturn = summary.totalReturn,
            avgMonthlyReturn = summary.avgMonthlyReturn,
            openingPortfolioValue = summary.openingPortfolioValue,
            returnPct = summary.returnPct,
            closingBalance = summary.closingBalance,
            openingBalance = summary.openingBalance,
        )
    }

    fun categoryBreakdownForRange(
        financialAccountId: String?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
        applicableTo: String?,
        principal: RequestPrincipal,
    ): List<CategoryTotalDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        require(startMonth in 1..12) { "startMonth must be between 1 and 12" }
        require(endMonth in 1..12) { "endMonth must be between 1 and 12" }
        val applicableToEnum = applicableTo?.let { CategoryApplicableTo.valueOf(it) }
            ?: CategoryApplicableTo.EXPENSE
        return dashboardAnalyticsService.getCategoryBreakdownForRange(
            accountId = accountId,
            financialAccountId = financialAccountId?.let { UUID.fromString(it) },
            startYear = startYear,
            startMonth = startMonth,
            endYear = endYear,
            endMonth = endMonth,
            applicableTo = applicableToEnum,
        ).map { it.toCategoryTotalDTO() }
    }

    fun portfolioNetWorthSeries(
        financialAccountId: String?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
        principal: RequestPrincipal,
    ): List<PortfolioMonthlyValueDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        require(startMonth in 1..12) { "startMonth must be between 1 and 12" }
        require(endMonth in 1..12) { "endMonth must be between 1 and 12" }
        return dashboardAnalyticsService.getPortfolioNetWorthSeries(
            accountId = accountId,
            financialAccountId = financialAccountId?.let { UUID.fromString(it) },
            startYear = startYear,
            startMonth = startMonth,
            endYear = endYear,
            endMonth = endMonth,
        ).map { point ->
            PortfolioMonthlyValueDTO(
                year = point.year,
                month = point.month,
                grossValue = point.grossValue,
                isEstimated = point.isEstimated,
            )
        }
    }

    fun transactionsByTag(
        financialAccountId: String?,
        belongsToUserId: String?,
        belongsToName: String?,
        search: String?,
        tags: List<String>?,
        billId: String?,
        categoryId: String?,
        uncategorized: Boolean?,
        year: Int?,
        month: Int?,
        applicableTo: String?,
        principal: RequestPrincipal,
    ): List<TagTotalDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val applicableToEnum = applicableTo?.let { CategoryApplicableTo.valueOf(it) }
        val breakdown =
            service.getTagBreakdown(
                accountId = accountId,
                financialAccountId = financialAccountId?.let { UUID.fromString(it) },
                belongsToUserId = belongsToUserId?.let { UUID.fromString(it) },
                belongsToName = belongsToName,
                search = search,
                tags = tags,
                billId = billId?.let { UUID.fromString(it) },
                categoryId = categoryId?.let { UUID.fromString(it) },
                uncategorized = uncategorized,
                month = month,
                year = year,
                applicableTo = applicableToEnum,
            )
        return breakdown.map { it.toTagTotalDTO() }
    }

    fun creditCardBills(
        financialAccountId: String,
        principal: RequestPrincipal,
    ): List<CreditCardBillDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return creditCardBillService.listBills(UUID.fromString(financialAccountId), accountId).map { it.toDTO() }
    }

    fun creditCardBill(
        financialAccountId: String,
        closingDate: String?,
        principal: RequestPrincipal,
    ): CreditCardBillDTO? {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val date = closingDate?.let { parseGraphQlDateArgument(it) }
        return creditCardBillService.findBill(UUID.fromString(financialAccountId), accountId, date)?.toDTO()
    }

    /**
     * Accepts ISO-8601 calendar dates and date-times (router/clients may send `YYYY-MM-DD` or `...T...Z`).
     */
    private fun parseGraphQlDateArgument(raw: String): LocalDate =
        try {
            LocalDate.parse(raw)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(raw).toLocalDate()
            } catch (_: DateTimeParseException) {
                LocalDate.parse(raw.take(10))
            }
        }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun createTransaction(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): CreateTransactionPayloadDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }

        val command = input.toCreateCommand()
        val statusDefault = accountPreferencesService.get(accountId).defaultTransactionStatus
        val commandWithDefaultStatus =
            if ((input["status"] as? String?) != null) {
                command
            } else {
                command.copy(status = statusDefault)
            }

        val outcome = service.createTransactionOutcome(commandWithDefaultStatus, accountId, userId)
        val saved = outcome.transaction
        val category = service.findAccessibleCategory(saved.categoryId, accountId)
        val merchant = saved.merchantId?.let { loadMerchantDTO(it) }

        val appliedRules =
            outcome.appliedRuleIds.takeIf { it.isNotEmpty() }?.let { ids ->
                val rulesById =
                    transactionRuleEngine.preloadRules(accountId)
                        .filter { it.id != null }
                        .associateBy { requireNotNull(it.id) }
                ids.mapNotNull { id ->
                    val rule = rulesById[id] ?: return@mapNotNull null
                    val position = ids.indexOf(id) + 1
                    rule.toRuleDTO(position)
                }
            }

        return CreateTransactionPayloadDTO(
            transaction =
                saved.toDTO(
                    category,
                    merchant,
                    hasNfceDocument = false,
                ),
            appliedRules = appliedRules,
        )
    }

    fun updateTransaction(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): TransactionDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }
        val txId = UUID.fromString(id)

        val command = input.toUpdateCommand()
        val updated = service.update(txId, command, accountId, userId)
        val category = service.findAccessibleCategory(updated.categoryId, accountId)
        val merchant = updated.merchantId?.let { loadMerchantDTO(it) }
        return updated.toDTO(category, merchant, false)
    }

    fun deleteTransaction(
        id: String,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return service.delete(UUID.fromString(id), accountId)
    }

    /** Maps a persisted [Transaction] to the GraphQL DTO (e.g. after domain service create). */
    fun toTransactionDTO(
        transaction: Transaction,
        accountId: UUID,
    ): TransactionDTO {
        val category = service.findAccessibleCategory(transaction.categoryId, accountId)
        val merchant = transaction.merchantId?.let { loadMerchantDTO(it) }
        return transaction.toDTO(category, merchant, hasNfceDocument = false)
    }

    /** Batch mapping for paginated lists (categories, merchants, NFC-e flags, attachments loaded once). */
    fun toTransactionDTOBatch(
        transactions: List<Transaction>,
        accountId: UUID,
    ): List<TransactionDTO> {
        if (transactions.isEmpty()) return emptyList()
        val transactionIds = transactions.mapNotNull { it.id }
        val categoryIds = transactions.mapNotNull { it.categoryId }.toSet()
        val categories = service.findAccessibleCategoriesByIds(categoryIds, accountId)
        val merchantDTOs = buildMerchantDTOMap(transactions.mapNotNull { it.merchantId }.toSet())
        val nfceLinked = nfceDocumentRepository.findLinkedTransactionIds(accountId, transactionIds)
        val attachmentsByTxId = transactionAttachmentRepository.findByTransactionIds(transactionIds, accountId)
        return transactions.map { tx ->
            tx.toDTO(
                categories[tx.categoryId],
                merchantDTOs[tx.merchantId],
                nfceLinked.contains(tx.id),
                attachmentsByTxId[tx.id] ?: emptyList(),
            )
        }
    }

    fun bulkCategorize(
        transactionIds: List<String>,
        categoryId: String,
        principal: RequestPrincipal,
    ): BulkCategorizeResult {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val ids = transactionIds.map { UUID.fromString(it) }
        val categoryUuid = UUID.fromString(categoryId)
        return service.bulkCategorize(ids, categoryUuid, accountId)
    }

    fun applyTransactionEditToSimilar(
        id: String,
        matchDescription: String,
        input: Map<String, Any?>,
        applyToFuture: Boolean,
        applyToExisting: Boolean,
        principal: RequestPrincipal,
    ): SimilarTransactionEditResultDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val result =
            service.applyEditToSimilarTransactions(
                id = UUID.fromString(id),
                matchDescription = matchDescription,
                command = input.toUpdateCommand(),
                applyToFuture = applyToFuture,
                applyToExisting = applyToExisting,
                accountId = accountId,
            )
        return SimilarTransactionEditResultDTO(
            affectedCount = result.affectedCount,
            futureRuleCreated = result.futureRuleCreated,
        )
    }

    fun payBill(
        billId: String,
        amount: Double,
        sourceAccountId: String?,
        principal: RequestPrincipal,
    ): CreditCardBillDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val userId = requireNotNull(principal.userId) { "No user in token" }
        val billUuid = UUID.fromString(billId)
        val sourceUuid = sourceAccountId?.let { UUID.fromString(it) }
        val bill = service.payBill(billUuid, BigDecimal(amount.toString()), sourceUuid, accountId, userId)
        return bill.toDTO()
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun CreditCardBill.toDTO() =
        CreditCardBillDTO(
            id = id!!,
            financialAccountId = financialAccountId,
            closingDate = closingDate,
            dueDate = dueDate,
            totalAmount = totalAmount.toDouble(),
            minimumPayment = minimumPayment?.toDouble(),
            status = status,
            createdAt = createdAt.atOffset(ZoneOffset.UTC),
            updatedAt = updatedAt.atOffset(ZoneOffset.UTC),
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

    private fun TransactionRule.toRuleDTO(position: Int): TransactionRuleDTO {
        val category = categoryId?.let { transactionCategoryRepository.findById(it) }
        val merchant = merchantId?.let { loadMerchantDTO(it) }
        return TransactionRuleDTO(
            id = requireNotNull(id),
            categoryId = categoryId,
            category = category?.toDTO(),
            merchantId = merchantId,
            merchant = merchant,
            descriptionOverride = descriptionOverride,
            tagsOverride = tagsOverride,
            pattern = pattern,
            patternType = patternType,
            applicableTo = applicableTo,
            priority = priority,
            isSystem = isSystem,
            isDisabledForAccount = false,
            isImplicit = isImplicit,
            matchCount = matchCount,
            position = position,
            createdAt = createdAt,
        )
    }

    private fun CategoryTotal.toCategoryTotalDTO(): CategoryTotalDTO =
        CategoryTotalDTO(
            category = category?.toDTO(),
            uncategorized = uncategorized,
            totalAmount = totalAmount.toDouble(),
            transactionCount = transactionCount,
            percentage = percentage,
        )

    private fun TagTotal.toTagTotalDTO(): TagTotalDTO =
        TagTotalDTO(
            tag = tag,
            totalAmount = totalAmount.toDouble(),
            transactionCount = transactionCount,
            percentage = percentage,
        )

    private fun Transaction.toDTO(
        category: TransactionCategory?,
        merchant: MerchantDTO?,
        hasNfceDocument: Boolean,
        attachments: List<TransactionAttachment>? = null,
    ): TransactionDTO =
        TransactionDTO(
            id = id!!,
            attachments = attachments,
            financialAccountId = financialAccountId,
            belongsToUserId = belongsToUserId,
            belongsToName = belongsToName,
            creditDebitType = creditDebitType,
            type = type,
            status = status,
            source = source,
            hasNfceDocument = hasNfceDocument,
            amount = amount.toDouble(),
            currencyCode = currencyCode,
            amountBrl = amountBrl?.toDouble(),
            date = date,
            transactionDatetime = transactionDatetime,
            description = description,
            notes = notes,
            tags = tags,
            category = category?.toDTO(),
            categorySource = categorySource,
            merchant = merchant,
            counterpartyName = counterpartyName,
            counterpartyDocument = counterpartyDocument,
            counterpartyBankCode = counterpartyBankCode,
            counterpartyAccountNumber = counterpartyAccountNumber,
            paymentReason = paymentReason,
            transferPeerTransactionId = transferPeerTransactionId,
            investmentTransactionId = investmentTransactionId,
            recurringPaymentId = recurringPaymentId,
            installmentNumber = installmentNumber,
            totalInstallments = totalInstallments,
            billId = billId,
            cardIdentifier = cardIdentifier,
            payeeMcc = payeeMcc,
            billPostDate = billPostDate,
            appliedRuleIds = appliedRuleIds,
            appliedRuleHistory = appliedRuleHistory.map { AppliedRuleHistoryEntryDTO(ruleId = it.ruleId, appliedAt = it.appliedAt) },
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun loadMerchantDTO(merchantId: UUID): MerchantDTO? {
        val merchant = merchantRepository.findById(merchantId) ?: return null
        val logoAssetId = merchant.domain?.let { merchantRepository.findLogoAssetIdByDomain(it) }
        return MerchantDTO(
            id = merchant.id!!,
            name = merchant.name,
            domain = merchant.domain,
            sector = merchant.sector,
            isSystem = merchant.isSystem,
            isOwned = merchant.accountId != null,
            logoAsset = logoAssetId?.let { mapOf("__typename" to "Asset", "id" to it.toString()) },
        )
    }

    /**
     * Batch-loads [MerchantDTO]s for a set of merchant IDs, sharing one logo-cache lookup per domain.
     */
    private fun buildMerchantDTOMap(merchantIds: Set<UUID>): Map<UUID, MerchantDTO> {
        if (merchantIds.isEmpty()) return emptyMap()
        val merchants = merchantRepository.findByIds(merchantIds)
        val logoAssetIds =
            merchantRepository.findLogoAssetIdsByDomains(
                merchants.mapNotNull { it.domain }.toSet(),
            )
        return merchants.associate { merchant ->
            merchant.id!! to
                MerchantDTO(
                    id = merchant.id,
                    name = merchant.name,
                    domain = merchant.domain,
                    sector = merchant.sector,
                    isSystem = merchant.isSystem,
                    isOwned = merchant.accountId != null,
                    logoAsset = merchant.domain?.let(logoAssetIds::get)?.let { mapOf("__typename" to "Asset", "id" to it.toString()) },
                )
        }
    }

    private fun nanosToMillis(nanos: Long): Double = nanos / 1_000_000.0

    private fun getDate(
        map: Map<String, Any?>,
        key: String,
    ): LocalDate? {
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
            status =
                (this["status"] as? String)?.let { TransactionStatus.valueOf(it) }
                    ?: TransactionStatus.POSTED,
            currencyCode = (this["currencyCode"] as? String) ?: "BRL",
            notes = this["notes"] as? String,
            tags = (this["tags"] as? List<*>)?.map { it as String } ?: emptyList(),
            categoryId = (this["categoryId"] as? String)?.let { UUID.fromString(it) },
            merchantId = (this["merchantId"] as? String)?.let { UUID.fromString(it) },
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
            categoryIdProvided = this.containsKey("categoryId"),
            merchantId = (this["merchantId"] as? String)?.let { UUID.fromString(it) },
            merchantIdProvided = this.containsKey("merchantId"),
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
