package io.github.salomax.neotool.banking.graphql.transaction

import io.github.salomax.neotool.banking.domain.attachment.TransactionAttachment
import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategoryTotal
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBill
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TagTotal
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.domain.transaction.TransactionRule
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.graphql.merchant.MerchantDTO
import io.github.salomax.neotool.banking.graphql.pendingaction.toDto
import io.github.salomax.neotool.banking.graphql.portfolio.PortfolioMonthlyValueDTO
import io.github.salomax.neotool.banking.model.dto.transaction.BulkCategorizeResult
import io.github.salomax.neotool.banking.model.dto.transaction.BulkOperationOutcome
import io.github.salomax.neotool.banking.model.dto.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.model.dto.transaction.UpdateTransactionCommand
import io.github.salomax.neotool.banking.repo.attachment.TransactionAttachmentRepository
import io.github.salomax.neotool.banking.repo.import.nfce.NfceDocumentRepository
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.service.pendingaction.AgentWriteConfirmationService
import io.github.salomax.neotool.banking.service.preferences.AccountPreferencesService
import io.github.salomax.neotool.banking.service.transaction.BulkWriteCapDecision
import io.github.salomax.neotool.banking.service.transaction.BulkWriteCapGuard
import io.github.salomax.neotool.banking.service.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.service.transaction.DashboardAnalyticsService
import io.github.salomax.neotool.banking.service.transaction.IdempotencyService
import io.github.salomax.neotool.banking.service.transaction.McpChangeLogService
import io.github.salomax.neotool.banking.service.transaction.MonthSummaryQueryService
import io.github.salomax.neotool.banking.service.transaction.TransactionRuleEngine
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.banking.service.transaction.executeIfAgent
import io.github.salomax.neotool.common.security.agent.AgentCallContext
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * GraphQL resolver for [Transaction] and [TransactionCategory] queries and mutations.
 */
@Singleton
class TransactionResolver(
    private val service: TransactionService,
    private val monthSummaryQueryService: MonthSummaryQueryService,
    private val dashboardAnalyticsService: DashboardAnalyticsService,
    private val creditCardBillService: CreditCardBillService,
    private val mcpChangeLogService: McpChangeLogService,
    private val merchantRepository: MerchantRepository,
    private val transactionRuleEngine: TransactionRuleEngine,
    private val transactionCategoryRepository: TransactionCategoryRepository,
    private val accountPreferencesService: AccountPreferencesService,
    private val nfceDocumentRepository: NfceDocumentRepository,
    private val transactionAttachmentRepository: TransactionAttachmentRepository,
    private val idempotencyService: IdempotencyService,
    private val bulkWriteCapGuard: BulkWriteCapGuard,
    private val agentWriteConfirmationService: AgentWriteConfirmationService,
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
        originClientId: String? = null,
        originToolCallId: String? = null,
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
        val originToolCallUuid = originToolCallId?.let { UUID.fromString(it) }

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
                originClientId = originClientId,
                originToolCallId = originToolCallUuid,
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
                val dto =
                    tx.toDTO(
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
                "transactions.resolver timings accountId={} edgeCount={} totalCount={} listMs={} " +
                    "categoriesMs={} merchantsMs={} nfceMs={} attachmentsMs={} dtoMapMs={} " +
                    "responseMs={} totalMs={}",
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

    fun mcpChangeLog(
        clientId: String?,
        tokenId: String?,
        toolCallId: String?,
        limit: Int?,
        offset: Int?,
        principal: RequestPrincipal,
    ): List<McpChangeLogEntryDTO> {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val toolCallUuid = toolCallId?.let(UUID::fromString)
        return mcpChangeLogService.listForAccount(
            accountId = accountId,
            clientId = clientId,
            tokenId = tokenId,
            toolCallId = toolCallUuid,
            limit = limit ?: 50,
            offset = offset ?: 0,
        ).map {
            McpChangeLogEntryDTO(
                id = it.id,
                toolCallId = it.toolCallId,
                clientId = it.clientId,
                tokenId = it.tokenId,
                resourceType = it.resourceType.name,
                resourceId = it.resourceId,
                action = it.action.name,
                before = it.before,
                after = it.after,
                createdAt = it.createdAt,
            )
        }
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
        val summary =
            dashboardAnalyticsService.getDashboardPeriodSummary(
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
        val applicableToEnum =
            applicableTo?.let { CategoryApplicableTo.valueOf(it) }
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
        referenceMonth: LocalDate?,
        principal: RequestPrincipal,
    ): CreditCardBillDTO? {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return creditCardBillService.findBill(UUID.fromString(financialAccountId), accountId, referenceMonth)?.toDTO()
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun createTransaction(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
        idempotencyKey: String? = null,
        agentCallContext: AgentCallContext? = null,
    ): CreateTransactionPayloadDTO =
        idempotencyService.executeIfAgent(
            accountId = requireNotNull(principal.currentAccountId) { "No active account in token" },
            key = idempotencyKey,
            operation = CREATE_TRANSACTION_OPERATION,
            agentCallContext = agentCallContext,
        ) {
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

            val outcome =
                service.createTransactionOutcome(
                    commandWithDefaultStatus,
                    accountId,
                    userId,
                    agentCallContext,
                )
            val saved = outcome.transaction
            val category = service.findAccessibleCategory(saved.categoryId, accountId)
            val merchant = saved.merchantId?.let { loadMerchantDTO(it) }

            val appliedRules =
                outcome.appliedRuleIds.takeIf { it.isNotEmpty() }?.let { ids ->
                    val rulesById =
                        transactionRuleEngine.preloadRules(accountId)
                            .filter { it.id != null }
                            .associateBy { requireNotNull(it.id) }
                    ids.mapNotNull { ruleId ->
                        val rule = rulesById[ruleId] ?: return@mapNotNull null
                        val position = ids.indexOf(ruleId) + 1
                        rule.toRuleDTO(position)
                    }
                }

            CreateTransactionPayloadDTO(
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
        idempotencyKey: String? = null,
        agentCallContext: AgentCallContext? = null,
    ): TransactionDTO =
        idempotencyService.executeIfAgent(
            accountId = requireNotNull(principal.currentAccountId) { "No active account in token" },
            key = idempotencyKey,
            operation = UPDATE_TRANSACTION_OPERATION,
            agentCallContext = agentCallContext,
        ) {
            val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
            val userId = requireNotNull(principal.userId) { "No user in token" }
            val txId = UUID.fromString(id)

            val command = input.toUpdateCommand()
            val updated = service.update(txId, command, accountId, userId, agentCallContext)
            val category = service.findAccessibleCategory(updated.categoryId, accountId)
            val merchant = updated.merchantId?.let { loadMerchantDTO(it) }
            updated.toDTO(category, merchant, false)
        }

    fun deleteTransaction(
        id: String,
        principal: RequestPrincipal,
        idempotencyKey: String? = null,
        agentCallContext: AgentCallContext? = null,
    ): DeleteTransactionResultDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val txId = UUID.fromString(id)
        return idempotencyService.executeIfAgent(
            accountId = accountId,
            key = idempotencyKey,
            operation = DELETE_TRANSACTION_OPERATION,
            agentCallContext = agentCallContext,
        ) {
            if (agentCallContext == null) {
                DeleteTransactionResultDTO(
                    deleted = service.delete(txId, accountId, null),
                )
            } else {
                val pending =
                    agentWriteConfirmationService.requestDeleteTransaction(
                        accountId = accountId,
                        transactionId = txId,
                        agentCallContext = agentCallContext,
                    )
                DeleteTransactionResultDTO(
                    deleted = false,
                    pendingAction = pending.toDto(),
                )
            }
        }
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
        idempotencyKey: String? = null,
        agentCallContext: AgentCallContext? = null,
    ): BulkCategorizeResult {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val ids = transactionIds.map { UUID.fromString(it) }
        val categoryUuid = UUID.fromString(categoryId)
        return idempotencyService.executeIfAgent(
            accountId = accountId,
            key = idempotencyKey,
            operation = BULK_CATEGORIZE_OPERATION,
            agentCallContext = agentCallContext,
        ) {
            if (agentCallContext == null) {
                service.bulkCategorize(ids, categoryUuid, accountId, null)
            } else {
                when (
                    val decision =
                        bulkWriteCapGuard.evaluate(
                            service.countCategorizableTransactions(ids, accountId),
                        )
                ) {
                    BulkWriteCapDecision.Proceed ->
                        service.bulkCategorize(ids, categoryUuid, accountId, agentCallContext)

                    is BulkWriteCapDecision.ConfirmationRequired ->
                        BulkCategorizeResult(
                            affectedCount = decision.affectedCount,
                            errors = emptyList(),
                            outcome = BulkOperationOutcome.CONFIRMATION_REQUIRED,
                            pendingAction =
                                agentWriteConfirmationService.requestBulkCategorize(
                                    accountId = accountId,
                                    transactionIds = ids,
                                    categoryId = categoryUuid,
                                    agentCallContext = agentCallContext,
                                ).toDto(),
                        )
                }
            }
        }
    }

    fun revertToolCallChanges(
        toolCallId: String,
        principal: RequestPrincipal,
    ): BulkCategorizeResult {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return mcpChangeLogService.revertToolCallChanges(accountId, UUID.fromString(toolCallId))
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
                userId = requireNotNull(principal.userId) { "No user ID in token" },
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
            referenceMonth = referenceMonth,
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
            typeOverride = typeOverride,
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
            appliedRuleHistory =
                appliedRuleHistory.map {
                    AppliedRuleHistoryEntryDTO(ruleId = it.ruleId, appliedAt = it.appliedAt)
                },
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
                    logoAsset =
                        merchant.domain?.let(
                            logoAssetIds::get,
                        )?.let { mapOf("__typename" to "Asset", "id" to it.toString()) },
                )
        }
    }

    private fun nanosToMillis(nanos: Long): Double = nanos / 1_000_000.0

    private fun Map<String, Any?>.toCreateCommand(): CreateTransactionCommand =
        TransactionInputParsing.parseCreateInput(this)

    private fun Map<String, Any?>.toUpdateCommand(): UpdateTransactionCommand =
        TransactionInputParsing.parseUpdateInput(this)

    private companion object {
        private const val CREATE_TRANSACTION_OPERATION = "create_transaction"
        private const val UPDATE_TRANSACTION_OPERATION = "update_transaction"
        private const val DELETE_TRANSACTION_OPERATION = "delete_transaction"
        private const val BULK_CATEGORIZE_OPERATION = "bulk_categorize"
    }
}
