package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.CategoryApplicableTo
import io.github.salomax.neotool.banking.domain.transaction.CategoryBreakdownRow
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.MonthTransactionTotals
import io.github.salomax.neotool.banking.domain.transaction.MonthTransferTotals
import io.github.salomax.neotool.banking.domain.transaction.TagBreakdownRow
import io.github.salomax.neotool.banking.domain.transaction.Transaction
import io.github.salomax.neotool.banking.domain.transaction.TransactionBelongsToOption
import io.github.salomax.neotool.banking.domain.transaction.TransactionCategory
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Domain repository for [Transaction].
 * All queries are scoped by [accountId] (tenant ID from JWT) for multi-tenant safety.
 * Soft-deleted transactions are never returned.
 */
interface TransactionRepository {
    /** Returns transactions for a financial account, ordered by date DESC, createdAt DESC. */
    fun findByFinancialAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<Transaction>

    /** Returns all transactions for an account across all financial accounts, ordered by date DESC, createdAt DESC. */
    fun findByAccount(accountId: UUID): List<Transaction>

    /** Returns all transactions linked to a specific credit card bill, ordered by date DESC, createdAt DESC. */
    fun findByBillId(
        billId: UUID,
        accountId: UUID,
    ): List<Transaction>

    /** Returns all distinct "belongs to" options used in transactions for the account. */
    fun listBelongsToOptions(accountId: UUID): List<TransactionBelongsToOption>

    /** Returns all distinct tags used in transactions for the account. */
    fun listTags(accountId: UUID): List<String>

    fun findById(
        id: UUID,
        accountId: UUID,
    ): Transaction?

    fun save(transaction: Transaction): Transaction

    fun update(transaction: Transaction): Transaction

    /** Soft-deletes a transaction (sets deleted_at = now). Returns false if not found. */
    fun softDelete(
        id: UUID,
        accountId: UUID,
    ): Boolean

    /**
     * Returns aggregate totals for one (financialAccountId, year, month).
     * Used by [SnapshotComputationService] to recompute monthly balance snapshots.
     * Never returns null — returns zero totals when no transactions exist for the period.
     */
    fun getMonthTotals(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ): MonthTransactionTotals

    /**
     * Returns TRANSFER totals (in/out) for one (financialAccountId, year, month).
     * On-demand; not cached in the snapshot.
     */
    fun getMonthTransferTotals(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ): MonthTransferTotals

    /**
     * Returns all financial account IDs that belong to the given [accountId] (tenant).
     * Used by [MonthSummaryQueryService] for the all-accounts aggregation.
     */
    fun findFinancialAccountIdsByAccount(accountId: UUID): List<UUID>

    /**
     * Returns the date of the earliest BALANCE_ADJUSTMENT transaction for the financial account,
     * or null if none exists. Used to validate that new transactions are not created before
     * the initial balance date.
     */
    fun findInitialBalanceDate(financialAccountId: UUID): LocalDate?

    /**
     * Returns the date of the earliest non-deleted transaction for the financial account,
     * or null when the account has no transactions.
     */
    fun findEarliestTransactionDate(financialAccountId: UUID): LocalDate?

    /**
     * Returns the earliest non-deleted transaction date per financial account.
     * Accounts without transactions are omitted from the returned map.
     */
    fun findEarliestTransactionDates(financialAccountIds: Collection<UUID>): Map<UUID, LocalDate>

    /**
     * Returns transactions in the given month/year for the account (optionally scoped to one financial account)
     * whose category_id is in [categoryIds]. Used for categoryId filter on transactions query.
     */
    fun findInDateRangeByCategoryIds(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
        categoryIds: List<UUID>,
    ): List<Transaction>

    /**
     * Returns transactions in the given month/year for the account (optionally scoped to one financial account).
     * When [financialAccountId] is null and transaction type is CARD with installmentNumber > 1,
     * month matching is based on the bill due date (fallback to transaction date when bill is absent).
     */
    fun findInDateRange(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
    ): List<Transaction>

    /**
     * Returns transactions in the given month/year for the account (optionally scoped to one financial account)
     * with category_id IS NULL. Used for uncategorized filter on transactions query.
     */
    fun findInDateRangeUncategorized(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
    ): List<Transaction>

    /**
     * Returns one page of transactions plus total count for the same filtered set.
     * The repository applies filtering and pagination in SQL so list latency scales
     * with the requested page rather than the full period size.
     */
    fun findPage(query: TransactionListQuery): TransactionPageResult

    /**
     * Batch-update category_id and category_source for the given transaction IDs.
     * Only updates rows where account_id = accountId and deleted_at IS NULL.
     * Returns the number of rows updated.
     */
    fun bulkUpdateCategory(
        transactionIds: List<UUID>,
        categoryId: UUID,
        categorySource: CategorySource,
        accountId: UUID,
    ): Int

    /**
     * Returns category totals for the given period (CAT-14). Only POSTED/PENDING transactions
     * are included; uncategorized transactions are exposed as a special row with
     * category = null and uncategorized = true, ordered last.
     */
    fun getCategoryBreakdown(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryBreakdownRow>

    /**
     * Returns category totals for the given period restricted to [includedFinancialAccountIds].
     * Used by aggregate month summary to exclude accounts outside net-worth calculations.
     */
    fun getCategoryBreakdownForAccounts(
        accountId: UUID,
        includedFinancialAccountIds: List<UUID>,
        year: Int,
        month: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryBreakdownRow>

    /**
     * Returns category totals for the exact filtered transaction set described by [query].
     * Used by transactions summary/filter UX so breakdown semantics stay aligned with
     * the paginated transactions query, including the explicit uncategorized row.
     */
    fun getFilteredCategoryBreakdown(
        query: TransactionListQuery,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryBreakdownRow>

    /**
     * Returns category totals for a date range (CAT-14 multi-month variant).
     * Aggregates POSTED/PENDING transactions from startYear/startMonth through endYear/endMonth inclusive.
     * When financialAccountId is null, aggregates across all accounts.
     */
    fun getCategoryBreakdownForRange(
        accountId: UUID,
        financialAccountId: UUID?,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<CategoryBreakdownRow>

    /**
     * Returns tag totals for one period. Only POSTED/PENDING tagged transactions are included.
     * Ordered by totalAmount DESC.
     */
    fun getTagBreakdown(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<TagBreakdownRow>

    /**
     * Returns tag totals for one period restricted to [includedFinancialAccountIds].
     * Used by aggregate month summary to exclude accounts outside net-worth calculations.
     */
    fun getTagBreakdownForAccounts(
        accountId: UUID,
        includedFinancialAccountIds: List<UUID>,
        year: Int,
        month: Int,
        applicableTo: CategoryApplicableTo?,
    ): List<TagBreakdownRow>

    /**
     * Returns tag totals for the exact filtered transaction set described by [query].
     * Percentages are computed in the service layer.
     */
    fun getFilteredTagBreakdown(
        query: TransactionListQuery,
        applicableTo: CategoryApplicableTo?,
    ): List<TagBreakdownRow>

    /**
     * Counts non-manual transactions for the account that share the same original/base description
     * and direction, excluding [excludeTransactionId].
     */
    fun countSimilarNonManualTransactions(
        accountId: UUID,
        excludeTransactionId: UUID,
        baseDescription: String,
        creditDebitType: CreditDebitType,
    ): Int

    /**
     * Loads non-manual transactions for the account that share the same original/base description
     * and direction, excluding [excludeTransactionId].
     */
    fun findSimilarNonManualTransactions(
        accountId: UUID,
        excludeTransactionId: UUID,
        baseDescription: String,
        creditDebitType: CreditDebitType,
    ): List<Transaction>

    /**
     * Whether a non-deleted ledger row is already linked to this investment transaction (Open Finance dedup).
     */
    fun existsByInvestmentTransactionId(investmentTransactionId: UUID): Boolean

    /**
     * Whether at least one non-deleted transaction is linked to the given import job.
     */
    fun existsByImportJobId(importJobId: UUID): Boolean

    /**
     * Clears provider_transaction_id on soft-deleted rows for this financial account/provider id pair.
     * Returns number of updated rows.
     */
    fun clearProviderTransactionIdForDeleted(
        financialAccountId: UUID,
        providerTransactionId: String,
    ): Int

    /**
     * Custodian ledger rows linked to any of the given investment transactions (non-deleted).
     */
    fun findByInvestmentTransactionIds(
        investmentTransactionIds: Collection<UUID>,
        accountId: UUID,
    ): List<Transaction>

    /**
     * Loads non-deleted transactions by id for the same account.
     */
    fun findByIds(
        ids: Collection<UUID>,
        accountId: UUID,
    ): List<Transaction>

    /**
     * Net cash position: SUM(CREDIT amount) − SUM(DEBIT amount) for POSTED and PENDING rows across the given accounts.
     */
    fun sumLedgerBalance(
        accountId: UUID,
        financialAccountIds: Collection<UUID>,
    ): BigDecimal

    /**
     * The other leg of a transfer: row where [Transaction.transferPeerTransactionId] equals [transferPeerTransactionId].
     */
    fun findByTransferPeerTransactionId(
        transferPeerTransactionId: UUID,
        accountId: UUID,
    ): Transaction?

    /**
     * Loads non-deleted transactions where [Transaction.transferPeerTransactionId] is in [transferPeerTransactionIds].
     */
    fun findByTransferPeerTransactionIds(
        transferPeerTransactionIds: Collection<UUID>,
        accountId: UUID,
    ): List<Transaction>

    /**
     * Investment custodian cash ledger rows: TRANSFER, INVESTMENT_RETURN, BALANCE_ADJUSTMENT on INVESTMENT/BENEFIT accounts.
     * Ordered by date DESC, id DESC. Keyset: rows strictly before [(afterDate, afterId)] when both are non-null.
     */
    fun findInvestmentCashTransactionsKeyset(
        accountId: UUID,
        financialAccountId: UUID?,
        afterDate: LocalDate?,
        afterId: UUID?,
        limit: Int,
    ): List<Transaction>

    /**
     * Links a posted/pending transaction to a recurring payment template.
     */
    fun setRecurringPaymentId(
        transactionId: UUID,
        recurringPaymentId: UUID,
        accountId: UUID,
    ): Boolean

    /**
     * Clears [Transaction.recurringPaymentId] on all non-deleted transactions linked to a recurring payment.
     */
    fun clearRecurringPaymentId(
        recurringPaymentId: UUID,
        accountId: UUID,
    ): Int

    /**
     * Deletes FUTURE rows that belong to one recurring payment (e.g. paused/deleted template).
     */
    fun deleteFutureByRecurringPaymentId(recurringPaymentId: UUID): Int

    /**
     * Deletes one projected FUTURE row for (recurring_payment_id, date), usually when a real POSTED row is confirmed.
     */
    fun deleteFutureByRecurringAndDate(
        recurringPaymentId: UUID,
        date: LocalDate,
    ): Int
}

/**
 * Domain repository for [TransactionCategory].
 */
interface TransactionCategoryRepository {
    /** Returns all system categories + user-defined categories for the given account. */
    fun findAll(accountId: UUID): List<TransactionCategory>

    /** Returns only the requested accessible categories for the given account. */
    fun findAccessibleByIds(
        ids: Set<UUID>,
        accountId: UUID,
    ): List<TransactionCategory>

    fun findById(id: UUID): TransactionCategory?

    fun save(category: TransactionCategory): TransactionCategory

    fun existsByNameAndParentIdAndAccountIdAndDeletedAtIsNull(
        name: String,
        parentId: UUID?,
        accountId: UUID,
    ): Boolean

    fun maxSortOrder(
        accountId: UUID,
        parentId: UUID?,
    ): Int?

    fun softDelete(
        id: UUID,
        accountId: UUID,
    )

    fun softDeleteChildren(
        parentId: UUID,
        accountId: UUID,
    )
}

data class TransactionListQuery(
    val accountId: UUID,
    val financialAccountId: UUID?,
    val includedFinancialAccountIds: List<UUID>? = null,
    val belongsToUserId: UUID?,
    val belongsToName: String?,
    val search: String?,
    val tags: Set<String>?,
    val billId: UUID?,
    val categoryIds: List<UUID>?,
    val uncategorizedOnly: Boolean,
    val creditDebitType: CreditDebitType?,
    val types: List<TransactionType>?,
    val startDate: LocalDate?,
    val endDateExclusive: LocalDate?,
    val limit: Int,
    val offset: Int,
)

data class TransactionPageResult(
    val items: List<Transaction>,
    val totalCount: Long,
)
