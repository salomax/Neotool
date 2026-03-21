package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.domain.DateWindowPolicy
import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.domain.transaction.port.AccountMembershipValidationPort
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.graphql.pagination.Connection
import io.github.salomax.neotool.common.graphql.pagination.CursorEncoder
import io.github.salomax.neotool.common.graphql.pagination.Edge
import io.github.salomax.neotool.common.graphql.pagination.PageInfo
import io.github.salomax.neotool.common.graphql.pagination.PaginationConstants
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.slf4j.LoggerFactory

/**
 * Domain service for [Transaction] and [TransactionCategory] operations.
 *
 * [accountId] is the tenant account ID resolved from the JWT.
 * [userId] is the authenticated user ID (audit).
 */
@Singleton
open class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: TransactionCategoryRepository,
    private val transactionRuleEngine: TransactionRuleEngine,
    private val merchantService: MerchantService,
    private val financialAccountRepository: FinancialAccountRepository,
    private val accountMembershipValidationPort: AccountMembershipValidationPort,
    private val snapshotComputationService: SnapshotComputationService,
    private val creditCardBillService: CreditCardBillService,
) {
    private val logger = LoggerFactory.getLogger(TransactionService::class.java)
    // ── Categories ───────────────────────────────────────────────────────────

    fun listCategories(accountId: UUID): List<TransactionCategory> = categoryRepository.findAll(accountId)

    fun findAccessibleCategory(
        categoryId: UUID?,
        accountId: UUID,
    ): TransactionCategory? {
        if (categoryId == null) return null
        val category = categoryRepository.findById(categoryId) ?: return null
        return if (isCategoryAccessible(category, accountId)) category else null
    }

    fun findAccessibleCategoriesByIds(
        categoryIds: Set<UUID>,
        accountId: UUID,
    ): Map<UUID, TransactionCategory> {
        if (categoryIds.isEmpty()) return emptyMap()
        return categoryRepository.findAll(accountId)
            .mapNotNull { category ->
                val id = category.id
                if (id != null && categoryIds.contains(id) && isCategoryAccessible(category, accountId)) {
                    id to category
                } else {
                    null
                }
            }
            .toMap()
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    fun listByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<Transaction> {
        requireAccountOwnsFinancialAccount(financialAccountId, accountId)
        return transactionRepository.findByFinancialAccount(financialAccountId, accountId)
    }

    fun listBelongsToOptions(accountId: UUID): List<TransactionBelongsToOption> =
        transactionRepository.listBelongsToOptions(accountId)

    fun listTags(accountId: UUID): List<String> = transactionRepository.listTags(accountId)

    /**
     * List transactions for the authenticated account with optional financialAccountId filter,
     * optional (defaultable) month/year filters, and Relay-style forward pagination.
     *
     * - When [financialAccountId] is null, returns transactions from all financial accounts
     *   belonging to the given [accountId].
     * - When [month]/[year] are null, the current month/year (server time) are used.
     * - When [categoryId] is set, returns only transactions in that category or its subcategories.
     * - When [uncategorized] is true, returns only transactions with no category.
     * - Pagination uses index-based cursors encoded as base64 strings via [CursorEncoder].
     */
    fun listTransactions(
        accountId: UUID,
        financialAccountId: UUID?,
        belongsToUserId: UUID?,
        belongsToName: String?,
        tags: List<String>?,
        billId: UUID?,
        categoryId: UUID?,
        uncategorized: Boolean?,
        creditDebitType: CreditDebitType?,
        types: List<TransactionType>?,
        month: Int?,
        year: Int?,
        first: Int?,
        after: String?,
    ): Connection<Transaction> {
        val filtered = fetchFilteredTransactions(
            accountId = accountId,
            financialAccountId = financialAccountId,
            belongsToUserId = belongsToUserId,
            belongsToName = belongsToName,
            tags = tags,
            billId = billId,
            categoryId = categoryId,
            uncategorized = uncategorized,
            creditDebitType = creditDebitType,
            types = types,
            month = month,
            year = year,
        ) ?: return Connection(emptyList(), PageInfo(false, false, null, null), 0L)

        val pageSize = PaginationConstants.validatePageSize(first)

        val startIndex =
            after?.let {
                val decoded = CursorEncoder.decodeCursorToInt(it)
                decoded + 1
            } ?: 0

        require(startIndex >= 0 && startIndex <= filtered.size) {
            "Invalid cursor for transactions pagination"
        }

        val endExclusive = minOf(startIndex + pageSize, filtered.size)
        val pageItems = filtered.subList(startIndex, endExclusive)

        val edges: List<Edge<Transaction>> =
            pageItems.withIndex().map { (idx, tx) ->
                val globalIndex = startIndex + idx
                Edge(
                    node = tx,
                    cursor = CursorEncoder.encodeCursor(globalIndex),
                )
            }

        val hasNextPage = endExclusive < filtered.size
        val hasPreviousPage = startIndex > 0

        val pageInfo =
            PageInfo(
                hasNextPage = hasNextPage,
                hasPreviousPage = hasPreviousPage,
                startCursor = edges.firstOrNull()?.cursor,
                endCursor = edges.lastOrNull()?.cursor,
            )

        return Connection(
            edges = edges,
            pageInfo = pageInfo,
            totalCount = filtered.size.toLong(),
        )
    }

    /**
     * Returns category totals for the given filter, mirroring the same filter semantics as
     * [listTransactions]. Transactions without a category are excluded from the breakdown.
     *
     * [applicableTo] narrows to INCOME (CREDIT) or EXPENSE (DEBIT) transactions only.
     */
    fun getCategoryBreakdown(
        accountId: UUID,
        financialAccountId: UUID?,
        belongsToUserId: UUID?,
        belongsToName: String?,
        tags: List<String>?,
        billId: UUID?,
        categoryId: UUID?,
        uncategorized: Boolean?,
        month: Int?,
        year: Int?,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryTotal> {
        val creditDebitFilter =
            when (applicableTo) {
                CategoryApplicableTo.INCOME -> CreditDebitType.CREDIT
                CategoryApplicableTo.EXPENSE -> CreditDebitType.DEBIT
                else -> null
            }

        val transactions =
            fetchFilteredTransactions(
                accountId = accountId,
                financialAccountId = financialAccountId,
                belongsToUserId = belongsToUserId,
                belongsToName = belongsToName,
                tags = tags,
                billId = billId,
                categoryId = categoryId,
                uncategorized = uncategorized,
                creditDebitType = creditDebitFilter,
                types = null,
                month = month,
                year = year,
            ) ?: return emptyList()

        val categorized =
            transactions.filter { tx ->
                tx.categoryId != null &&
                    (creditDebitFilter == null || tx.creditDebitType == creditDebitFilter)
            }

        val allCategories = categoryRepository.findAll(accountId).associateBy { it.id }

        val byCategory = categorized.groupBy { it.categoryId!! }
        val grandTotal = categorized.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }

        return byCategory
            .mapNotNull { (catId, txs) ->
                val cat = allCategories[catId] ?: return@mapNotNull null
                // Skip categories that don't match the applicableTo filter at the category level.
                if (applicableTo != null &&
                    cat.applicableTo != CategoryApplicableTo.ANY &&
                    cat.applicableTo != applicableTo
                ) {
                    return@mapNotNull null
                }
                val total = txs.fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }
                val pct =
                    if (grandTotal > BigDecimal.ZERO) {
                        (total.toDouble() / grandTotal.toDouble() * 100.0)
                            .toBigDecimal()
                            .setScale(2, java.math.RoundingMode.HALF_UP)
                            .toDouble()
                    } else {
                        0.0
                    }
                CategoryTotal(
                    category = cat,
                    totalAmount = total,
                    transactionCount = txs.size,
                    percentage = pct,
                )
            }
            .sortedByDescending { it.totalAmount }
    }

    /**
     * Returns all transactions matching the given filters, without pagination.
     * Returns null when a categoryId filter references an unknown / inaccessible category
     * (callers should treat null as an empty result).
     */
    private fun fetchFilteredTransactions(
        accountId: UUID,
        financialAccountId: UUID?,
        belongsToUserId: UUID?,
        belongsToName: String?,
        tags: List<String>?,
        billId: UUID?,
        categoryId: UUID?,
        uncategorized: Boolean?,
        creditDebitType: CreditDebitType?,
        types: List<TransactionType>?,
        month: Int?,
        year: Int?,
    ): List<Transaction>? {
        val normalizedBelongsToName = belongsToName?.trim()
        require(normalizedBelongsToName?.isNotEmpty() != false) {
            "belongsToName must not be blank"
        }
        require(!(belongsToUserId != null && normalizedBelongsToName != null)) {
            "Only one of belongsToUserId or belongsToName can be provided"
        }
        val normalizedTagFilter = tags?.let(::normalizeTags)
        val normalizedTagFilterSet = normalizedTagFilter?.toSet()

        val base: List<Transaction> =
            if (billId != null) {
                creditCardBillService.requireBillForAccount(billId, accountId)
                transactionRepository.findByBillId(billId, accountId)
            } else {
                val today = LocalDate.now()
                val targetYear = year ?: today.year
                val targetMonth = month ?: today.monthValue
                require(targetMonth in 1..12) {
                    "month must be between 1 and 12, got: $targetMonth"
                }

                when {
                    uncategorized == true -> {
                        if (financialAccountId != null) {
                            requireAccountOwnsFinancialAccount(financialAccountId, accountId)
                        }
                        transactionRepository.findInDateRangeUncategorized(
                            accountId = accountId,
                            financialAccountId = financialAccountId,
                            year = targetYear,
                            month = targetMonth,
                        )
                    }
                    categoryId != null -> {
                        val category = categoryRepository.findById(categoryId) ?: return null
                        if (category.accountId != null && category.accountId != accountId) return null
                        val categoryIds: List<UUID> =
                            if (category.parentId == null) {
                                val allCats = categoryRepository.findAll(accountId)
                                listOf(categoryId) +
                                    allCats.filter { it.parentId == categoryId }.mapNotNull { it.id }
                            } else {
                                listOf(categoryId)
                            }
                        if (financialAccountId != null) {
                            requireAccountOwnsFinancialAccount(financialAccountId, accountId)
                        }
                        transactionRepository.findInDateRangeByCategoryIds(
                            accountId = accountId,
                            financialAccountId = financialAccountId,
                            year = targetYear,
                            month = targetMonth,
                            categoryIds = categoryIds,
                        )
                    }
                    else -> {
                        if (financialAccountId != null) {
                            requireAccountOwnsFinancialAccount(financialAccountId, accountId)
                        }
                        transactionRepository.findInDateRange(
                            accountId = accountId,
                            financialAccountId = financialAccountId,
                            year = targetYear,
                            month = targetMonth,
                        )
                    }
                }
            }

        val normalizedTypes = types?.takeIf { it.isNotEmpty() }

        return base.filter { tx ->
            val belongsToUserMatches = belongsToUserId == null || tx.belongsToUserId == belongsToUserId
            val belongsToNameMatches = normalizedBelongsToName == null || tx.belongsToName == normalizedBelongsToName
            val tagsMatch =
                normalizedTagFilterSet == null ||
                    normalizedTagFilterSet.isEmpty() ||
                    tx.tags.any { normalizedTagFilterSet.contains(it) }
            val creditDebitTypeMatches = creditDebitType == null || tx.creditDebitType == creditDebitType
            val typeMatches = normalizedTypes == null || tx.type in normalizedTypes
            belongsToUserMatches && belongsToNameMatches && tagsMatch && creditDebitTypeMatches && typeMatches
        }
    }

    fun findById(
        id: UUID,
        accountId: UUID,
    ): Transaction =
        transactionRepository.findById(id, accountId)
            ?: throw NoSuchElementException("Transaction $id not found")

    @Transactional
    open fun create(
        command: CreateTransactionCommand,
        accountId: UUID,
        userId: UUID,
    ): Transaction {
        requireAccountOwnsFinancialAccount(command.financialAccountId, accountId)
        validateCreateCommand(command)
        validateDateWithinMovingWindow(command.date)
        val (belongsToUserId, belongsToName) = resolveBelongsToForCreate(command, userId)
        if (command.belongsToUserId != null) {
            requireBelongsToUserIsActiveMember(command.belongsToUserId, accountId)
        }
        val normalizedTags = normalizeTags(command.tags)

        // Resolve bill for credit/charge card accounts (null for all other account types).
        // When command.billId is set (e.g. payBill), use it instead of resolving from date.
        val cardAccount = resolveCardAccount(command.financialAccountId, accountId)
        val resolvedBillId =
            command.billId
                ?: cardAccount?.let {
                    creditCardBillService.resolveBillForTransaction(it, command.date, command.type)
                }

        val ruleSuggestion =
            if (command.categoryId == null || command.merchantId == null) {
                try {
                    transactionRuleEngine.suggest(accountId, command.description, command.creditDebitType)
                } catch (e: Exception) {
                    logger.warn(
                        "Rule engine suggestion failed; continuing without suggestion (accountId={}, txType={}, descriptionLength={})",
                        accountId,
                        command.creditDebitType,
                        command.description?.length,
                        e,
                    )
                    null
                }
            } else {
                null
            }

        val finalCategoryId = command.categoryId ?: ruleSuggestion?.categoryId
        val finalCategorySource =
            when {
                command.categoryId != null -> CategorySource.MANUAL
                ruleSuggestion?.categoryId != null -> CategorySource.RULE
                else -> null
            }
        val finalMerchantId = command.merchantId ?: ruleSuggestion?.merchantId

        val now = Instant.now()
        val transaction =
            Transaction(
                financialAccountId = command.financialAccountId,
                accountId = accountId,
                createdByUserId = userId,
                belongsToUserId = belongsToUserId,
                belongsToName = belongsToName,
                creditDebitType = command.creditDebitType,
                type = command.type,
                status = command.status,
                source = TransactionSource.MANUAL,
                amount = command.amount,
                currencyCode = command.currencyCode,
                date = command.date,
                description = command.description,
                notes = command.notes,
                tags = normalizedTags,
                categoryId = finalCategoryId,
                categorySource = finalCategorySource,
                merchantId = finalMerchantId,
                counterpartyName = command.counterpartyName,
                counterpartyDocument = command.counterpartyDocument,
                counterpartyBankCode = command.counterpartyBankCode,
                counterpartyAccountNumber = command.counterpartyAccountNumber,
                paymentReason = command.paymentReason,
                transferPeerTransactionId = command.transferPeerTransactionId,
                billId = resolvedBillId,
                installmentNumber = command.installmentNumber,
                totalInstallments = command.totalInstallments,
                cardIdentifier = command.cardIdentifier,
                payeeMcc = command.payeeMcc,
                createdAt = now,
                updatedAt = now,
            )

        if (command.type != TransactionType.BALANCE_ADJUSTMENT) {
            val initialBalanceDate = transactionRepository.findInitialBalanceDate(command.financialAccountId)
            if (initialBalanceDate != null && command.date.isBefore(initialBalanceDate)) {
                throw ValidationException(
                    errorCode = BankingErrorCode.TRANSACTION_BEFORE_INITIAL_BALANCE,
                    field = "date",
                    parameters = mapOf("initialBalanceDate" to initialBalanceDate.toString()),
                )
            }
        }

        val saved = transactionRepository.save(transaction)
        creditCardBillService.recomputeBill(saved.billId, accountId)
        snapshotComputationService.recomputeChain(
            financialAccountId = saved.financialAccountId,
            accountId = accountId,
            fromYear = saved.date.year,
            fromMonth = saved.date.monthValue,
        )
        return saved
    }

    @Transactional
    open fun update(
        id: UUID,
        command: UpdateTransactionCommand,
        accountId: UUID,
    ): Transaction {
        val existing =
            transactionRepository.findById(id, accountId)
                ?: throw NoSuchElementException("Transaction $id not found")
        val updatedBelongsTo = resolveBelongsToForUpdate(command)
        if (command.belongsToProvided && command.belongsToUserId != null) {
            requireBelongsToUserIsActiveMember(command.belongsToUserId, accountId)
        }
        val updatedBelongsToUserId =
            updatedBelongsTo?.first ?: if (updatedBelongsTo == null) existing.belongsToUserId else null
        val updatedBelongsToName =
            updatedBelongsTo?.second ?: if (updatedBelongsTo == null) existing.belongsToName else null
        val updatedTags = resolveTagsForUpdate(command, existing.tags)

        // Resolve new bill if the date changed (may move to a different billing cycle).
        val newDate = command.date ?: existing.date
        val newBillId: UUID? =
            if (command.date != null) {
                val cardAccount = resolveCardAccount(existing.financialAccountId, accountId)
                cardAccount?.let {
                    creditCardBillService.resolveBillForTransaction(it, newDate, existing.type)
                } ?: existing.billId
            } else {
                existing.billId
            }

        val categoryChanged = command.categoryId != null && command.categoryId != existing.categoryId
        val merchantChanged = command.merchantIdProvided && command.merchantId != existing.merchantId
        if (categoryChanged || merchantChanged) {
            try {
                transactionRuleEngine.learnFromManualAssignment(
                    accountId = accountId,
                    description = existing.description,
                    categoryId = if (categoryChanged) command.categoryId else existing.categoryId,
                    merchantId = if (merchantChanged) command.merchantId else existing.merchantId,
                )
            } catch (e: Exception) {
                // Learning failure must not fail the update
                logger.warn(
                    "Implicit rule learning failed; continuing update without learning (accountId={}, txId={})",
                    accountId,
                    id,
                    e,
                )
            }
        }

        val updated =
            existing.copy(
                creditDebitType = command.creditDebitType ?: existing.creditDebitType,
                type = command.type ?: existing.type,
                amount = command.amount ?: existing.amount,
                date = newDate,
                description = command.description ?: existing.description,
                status = command.status ?: existing.status,
                notes = command.notes ?: existing.notes,
                tags = updatedTags,
                categoryId = command.categoryId ?: existing.categoryId,
                categorySource =
                    when {
                        command.categoryId != null -> CategorySource.MANUAL
                        else -> existing.categorySource
                    },
                merchantId = if (command.merchantIdProvided) command.merchantId else existing.merchantId,
                belongsToUserId = updatedBelongsToUserId,
                belongsToName = updatedBelongsToName,
                counterpartyName = command.counterpartyName ?: existing.counterpartyName,
                counterpartyDocument = command.counterpartyDocument ?: existing.counterpartyDocument,
                counterpartyBankCode = command.counterpartyBankCode ?: existing.counterpartyBankCode,
                counterpartyAccountNumber = command.counterpartyAccountNumber ?: existing.counterpartyAccountNumber,
                paymentReason = command.paymentReason ?: existing.paymentReason,
                transferPeerTransactionId = command.transferPeerTransactionId ?: existing.transferPeerTransactionId,
                billId = newBillId,
                cardIdentifier = command.cardIdentifier ?: existing.cardIdentifier,
                payeeMcc = command.payeeMcc ?: existing.payeeMcc,
                updatedAt = Instant.now(),
            )

        require(updated.amount > java.math.BigDecimal.ZERO) { "amount must be > 0" }

        if (command.date != null) {
            validateDateWithinMovingWindow(command.date)
        }

        if (existing.type != TransactionType.BALANCE_ADJUSTMENT && command.date != null) {
            val initialBalanceDate = transactionRepository.findInitialBalanceDate(existing.financialAccountId)
            if (initialBalanceDate != null && command.date.isBefore(initialBalanceDate)) {
                throw ValidationException(
                    errorCode = BankingErrorCode.TRANSACTION_BEFORE_INITIAL_BALANCE,
                    field = "date",
                    parameters = mapOf("initialBalanceDate" to initialBalanceDate.toString()),
                )
            }
        }

        val saved = transactionRepository.update(updated)

        // If the transaction moved to a different bill, recompute both the old and new bill.
        if (existing.billId != null && existing.billId != saved.billId) {
            creditCardBillService.recomputeBill(existing.billId, accountId)
        }
        creditCardBillService.recomputeBill(saved.billId, accountId)

        // Recompute from the earliest affected transaction month.
        val fromDate = minOf(existing.date, saved.date)
        snapshotComputationService.recomputeChain(
            financialAccountId = saved.financialAccountId,
            accountId = accountId,
            fromYear = fromDate.year,
            fromMonth = fromDate.monthValue,
        )
        return saved
    }

    @Transactional
    open fun delete(
        id: UUID,
        accountId: UUID,
    ): Boolean {
        val exists =
            transactionRepository.findById(id, accountId)
                ?: throw NoSuchElementException("Transaction $id not found")
        check(exists.source == TransactionSource.MANUAL) {
            "Only MANUAL transactions can be deleted; OFB-synced transactions must be hidden via the provider"
        }
        val deleted = transactionRepository.softDelete(id, accountId)
        if (deleted) {
            creditCardBillService.recomputeBill(exists.billId, accountId)
            snapshotComputationService.recomputeChain(
                financialAccountId = exists.financialAccountId,
                accountId = accountId,
                fromYear = exists.date.year,
                fromMonth = exists.date.monthValue,
            )
        }
        return deleted
    }

    /**
     * Assigns the same category to multiple transactions (e.g. post-import).
     * Only updates transactions belonging to [accountId]. Sets categorySource to MANUAL.
     * Does not trigger implicit rule learning.
     */
    @Transactional
    open fun bulkCategorize(
        transactionIds: List<UUID>,
        categoryId: UUID,
        accountId: UUID,
    ): BulkCategorizeResult {
        val category =
            categoryRepository.findById(categoryId)
                ?: throw NoSuchElementException("Category $categoryId not found")
        val accessible =
            category.accountId == null || category.accountId == accountId
        require(accessible) {
            "Category is not accessible to this account"
        }
        val affectedCount =
            transactionRepository.bulkUpdateCategory(
                transactionIds = transactionIds,
                categoryId = categoryId,
                categorySource = CategorySource.MANUAL,
                accountId = accountId,
            )
        return BulkCategorizeResult(affectedCount = affectedCount, errors = emptyList())
    }

    /**
     * Registers a payment against a credit card bill (BILL-02).
     * Creates a CREDIT TRANSFER on the card account; optionally a DEBIT TRANSFER on the source account.
     * Marks the bill as PAID when the payment amount covers the full total.
     */
    @Transactional
    open fun payBill(
        billId: UUID,
        amount: BigDecimal,
        sourceAccountId: UUID?,
        accountId: UUID,
        userId: UUID,
    ): CreditCardBill {
        val bill = creditCardBillService.requireBillForAccount(billId, accountId)
        require(bill.status != BillStatus.PAID) {
            "Bill ${bill.id} is already paid"
        }
        val totalBeforePayment = bill.totalAmount
        val paymentDate = LocalDate.now()

        // Create the bank debit first so its ID can be set on the card credit in one pass,
        // avoiding a second update() call (and a redundant recomputeChain on the card account).
        val bankTxId: UUID? =
            if (sourceAccountId != null) {
                requireAccountOwnsFinancialAccount(sourceAccountId, accountId)
                val bankCommand =
                    CreateTransactionCommand(
                        financialAccountId = sourceAccountId,
                        creditDebitType = CreditDebitType.DEBIT,
                        type = TransactionType.TRANSFER,
                        amount = amount,
                        date = paymentDate,
                        description = "Pagamento fatura (cartão)",
                    )
                create(bankCommand, accountId, userId).id
            } else {
                null
            }

        val cardCommand =
            CreateTransactionCommand(
                financialAccountId = bill.financialAccountId,
                creditDebitType = CreditDebitType.CREDIT,
                type = TransactionType.TRANSFER,
                amount = amount,
                date = paymentDate,
                description = "Pagamento fatura",
                billId = billId,
                transferPeerTransactionId = bankTxId,
            )
        create(cardCommand, accountId, userId)

        return if (amount >= totalBeforePayment) {
            creditCardBillService.markAsPaid(billId, accountId)
        } else {
            creditCardBillService.requireBillForAccount(billId, accountId)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the [FinancialAccount] if it is a credit/charge card account, null otherwise.
     * Reuses the accounts already fetched by [requireAccountOwnsFinancialAccount].
     */
    private fun resolveCardAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): FinancialAccount? {
        val account =
            financialAccountRepository.findByAccountId(accountId)
                .firstOrNull { it.id == financialAccountId } ?: return null
        return if (account.subtype in CreditCardBillService.CARD_SUBTYPES) account else null
    }

    private fun requireAccountOwnsFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ) {
        val accounts = financialAccountRepository.findByAccountId(accountId)
        require(accounts.any { it.id == financialAccountId }) {
            "Financial account $financialAccountId does not belong to account $accountId"
        }
    }

    private fun requireBelongsToUserIsActiveMember(
        userId: UUID,
        accountId: UUID,
    ) {
        if (!accountMembershipValidationPort.isUserActiveMemberOfAccount(userId, accountId)) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "belongsToUserId",
                parameters =
                    mapOf(
                        "userId" to userId.toString(),
                        "accountId" to accountId.toString(),
                    ),
            )
        }
    }

    private fun validateCreateCommand(command: CreateTransactionCommand) {
        require(command.amount > java.math.BigDecimal.ZERO) { "amount must be > 0" }

        val installNum = command.installmentNumber
        val installTotal = command.totalInstallments
        if (installNum != null || installTotal != null) {
            requireNotNull(installNum) { "installmentNumber is required when totalInstallments is set" }
            requireNotNull(installTotal) { "totalInstallments is required when installmentNumber is set" }
            require(installNum >= 1) { "installmentNumber must be >= 1" }
            require(installTotal >= 1) { "totalInstallments must be >= 1" }
            require(installNum <= installTotal) { "installmentNumber must be <= totalInstallments" }
        }
    }

    private fun resolveBelongsToForCreate(
        command: CreateTransactionCommand,
        authenticatedUserId: UUID,
    ): Pair<UUID?, String?> {
        val normalizedName = command.belongsToName?.trim()
        require(normalizedName?.isNotEmpty() != false) {
            "belongsToName must not be blank"
        }
        require(!(command.belongsToUserId != null && normalizedName != null)) {
            "Only one of belongsToUserId or belongsToName can be provided"
        }

        return when {
            command.belongsToUserId != null -> command.belongsToUserId to null
            normalizedName != null -> null to normalizedName
            else -> authenticatedUserId to null
        }
    }

    private fun resolveBelongsToForUpdate(command: UpdateTransactionCommand): Pair<UUID?, String?>? {
        if (!command.belongsToProvided) return null

        val normalizedName = command.belongsToName?.trim()
        require(normalizedName?.isNotEmpty() != false) {
            "belongsToName must not be blank"
        }
        require(!(command.belongsToUserId != null && normalizedName != null)) {
            "Only one of belongsToUserId or belongsToName can be provided"
        }
        require(command.belongsToUserId != null || normalizedName != null) {
            "When updating attribution, one of belongsToUserId or belongsToName is required"
        }
        return command.belongsToUserId to normalizedName
    }

    private fun resolveTagsForUpdate(
        command: UpdateTransactionCommand,
        existingTags: List<String>,
    ): List<String> {
        if (!command.tagsProvided) return existingTags
        return normalizeTags(command.tags ?: emptyList())
    }

    private fun normalizeTags(tags: List<String>): List<String> {
        val normalized = tags.map { it.trim() }
        require(normalized.none { it.isBlank() }) {
            "tags cannot contain blank values"
        }
        return normalized.distinct()
    }

    private fun isCategoryAccessible(
        category: TransactionCategory,
        accountId: UUID,
    ): Boolean = category.accountId == null || category.accountId == accountId

    private fun validateDateWithinMovingWindow(date: LocalDate) {
        val minAllowedDate = DateWindowPolicy.minAllowedDate()
        if (date.isBefore(minAllowedDate)) {
            throw ValidationException(
                errorCode = BankingErrorCode.TRANSACTION_DATE_OUTSIDE_ALLOWED_WINDOW,
                field = "date",
                parameters =
                    mapOf(
                        "minAllowedDate" to minAllowedDate.toString(),
                        "windowYears" to DateWindowPolicy.MAX_PAST_YEARS,
                    ),
            )
        }
    }
}

/** Result of bulkCategorize mutation. */
data class BulkCategorizeResult(
    val affectedCount: Int,
    val errors: List<String>,
)
