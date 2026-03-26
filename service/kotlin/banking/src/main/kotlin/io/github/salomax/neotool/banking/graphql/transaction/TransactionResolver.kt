package io.github.salomax.neotool.banking.graphql.transaction

import io.github.salomax.neotool.banking.service.transaction.BulkCategorizeResult
import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategoryTotal
import io.github.salomax.neotool.banking.domain.transaction.CreateTransactionCommand
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBill
import io.github.salomax.neotool.banking.service.transaction.CreditCardBillService
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.TransactionRule
import io.github.salomax.neotool.banking.repo.transaction.MerchantRepository
import io.github.salomax.neotool.banking.repo.transaction.TransactionCategoryRepository
import io.github.salomax.neotool.banking.service.transaction.MonthSummaryQueryService
import io.github.salomax.neotool.banking.domain.transaction.TagTotal
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.service.transaction.TransactionService
import io.github.salomax.neotool.banking.domain.transaction.TransactionStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.domain.transaction.UpdateTransactionCommand
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
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.slf4j.LoggerFactory

/**
 * GraphQL resolver for [Transaction] and [TransactionCategory] queries and mutations.
 */
@Singleton
class TransactionResolver(
    private val service: TransactionService,
    private val monthSummaryQueryService: MonthSummaryQueryService,
    private val creditCardBillService: CreditCardBillService,
    private val merchantRepository: MerchantRepository,
    private val transactionRuleEngine: TransactionRuleEngine,
    private val transactionCategoryRepository: TransactionCategoryRepository,
    private val accountPreferencesService: AccountPreferencesService,
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

        val categoryIds = connection.edges.mapNotNull { it.node.categoryId }.toSet()
        val categories = service.findAccessibleCategoriesByIds(categoryIds, accountId)
        val categoriesFinishedAt = System.nanoTime()
        val merchantDTOs = buildMerchantDTOMap(connection.edges.mapNotNull { it.node.merchantId }.toSet())
        val merchantsFinishedAt = System.nanoTime()

        val edges =
            connection.edges.map { edge ->
                val tx = edge.node
                val category = categories[tx.categoryId]
                val merchant = merchantDTOs[tx.merchantId]
                val dto = tx.toDTO(category, merchant)
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
                "transactions.resolver timings accountId={} edgeCount={} totalCount={} listMs={} categoriesMs={} merchantsMs={} dtoMapMs={} responseMs={} totalMs={}",
                accountId,
                connection.edges.size,
                connection.totalCount,
                nanosToMillis(listFinishedAt - startedAt),
                nanosToMillis(categoriesFinishedAt - listFinishedAt),
                nanosToMillis(merchantsFinishedAt - categoriesFinishedAt),
                nanosToMillis(mappingFinishedAt - merchantsFinishedAt),
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
        return tx.toDTO(category, merchant)
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
        val date = closingDate?.let { LocalDate.parse(it) }
        return creditCardBillService.findBill(UUID.fromString(financialAccountId), accountId, date)?.toDTO()
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
                    rule.toRuleDTO()
                }
            }

        return CreateTransactionPayloadDTO(
            transaction = saved.toDTO(category, merchant),
            appliedRules = appliedRules,
        )
    }

    fun updateTransaction(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): TransactionDTO {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        val txId = UUID.fromString(id)

        val command = input.toUpdateCommand()
        val updated = service.update(txId, command, accountId)
        val category = service.findAccessibleCategory(updated.categoryId, accountId)
        val merchant = updated.merchantId?.let { loadMerchantDTO(it) }
        return updated.toDTO(category, merchant)
    }

    fun deleteTransaction(
        id: String,
        principal: RequestPrincipal,
    ): Boolean {
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        return service.delete(UUID.fromString(id), accountId)
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

    private fun TransactionRule.toRuleDTO(): TransactionRuleDTO {
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
            isImplicit = isImplicit,
            matchCount = matchCount,
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
    ): TransactionDTO =
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
            categorySource = categorySource,
            merchant = merchant,
            counterpartyName = counterpartyName,
            counterpartyDocument = counterpartyDocument,
            counterpartyBankCode = counterpartyBankCode,
            counterpartyAccountNumber = counterpartyAccountNumber,
            paymentReason = paymentReason,
            transferPeerTransactionId = transferPeerTransactionId,
            installmentNumber = installmentNumber,
            totalInstallments = totalInstallments,
            billId = billId,
            cardIdentifier = cardIdentifier,
            payeeMcc = payeeMcc,
            billPostDate = billPostDate,
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
            logoAsset = logoAssetId?.let { mapOf("__typename" to "Asset", "id" to it) },
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
                    logoAsset = merchant.domain?.let(logoAssetIds::get)?.let { mapOf("__typename" to "Asset", "id" to it) },
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
