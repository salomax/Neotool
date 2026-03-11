package io.github.salomax.neotool.banking.domain.transaction

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
}

/**
 * Domain repository for [TransactionCategory].
 */
interface TransactionCategoryRepository {
    /** Returns all system categories + user-defined categories for the given account. */
    fun findAll(accountId: UUID): List<TransactionCategory>

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
