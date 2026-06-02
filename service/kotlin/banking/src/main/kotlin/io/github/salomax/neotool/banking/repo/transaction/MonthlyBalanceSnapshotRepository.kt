package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.MonthlyBalanceSnapshot
import java.util.UUID

/**
 * Domain repository for [MonthlyBalanceSnapshot].
 * All queries are scoped by [financialAccountId] (with [accountId] for tenant safety).
 */
interface MonthlyBalanceSnapshotRepository {
    /** Returns the snapshot for the given (account, year, month), or null if none exists. */
    fun findByYearMonth(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ): MonthlyBalanceSnapshot?

    /**
     * Returns the most recent clean (is_dirty = false) snapshot strictly before (year, month).
     * Used to find the nearest valid starting point for a chain recompute.
     */
    fun findLatestCleanBefore(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ): MonthlyBalanceSnapshot?

    /**
     * Returns all snapshots for the given financial account ordered by (year ASC, month ASC).
     * Used by the sweeper to find dirty rows.
     */
    fun findAllDirty(financialAccountId: UUID): List<MonthlyBalanceSnapshot>

    /**
     * Returns all financial account IDs that have at least one dirty snapshot.
     * Used by the sweeper job to iterate accounts.
     */
    fun findFinancialAccountIdsWithDirtySnapshots(): List<UUID>

    /**
     * Creates or updates the snapshot row for (financialAccountId, year, month).
     * Uses INSERT ON CONFLICT DO UPDATE semantics.
     */
    fun upsert(snapshot: MonthlyBalanceSnapshot): MonthlyBalanceSnapshot

    /**
     * Marks all snapshots for the account at or after (year, month) as dirty.
     * Called when a transaction write fails partway through the chain recompute.
     */
    fun markDirtyFromMonth(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    )

    /**
     * Returns all snapshots for [financialAccountId] within the inclusive range
     * [startYear/startMonth, endYear/endMonth], ordered ASC.
     * Used by dashboard analytics to build cash-flow series for a single account.
     */
    fun findByRange(
        financialAccountId: UUID,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): List<MonthlyBalanceSnapshot>

    /**
     * Returns all snapshots for all financial accounts belonging to [accountId]
     * within the inclusive range, ordered ASC.
     * Used by dashboard analytics to aggregate across all accounts.
     */
    fun findByRangeForAccount(
        accountId: UUID,
        startYear: Int,
        startMonth: Int,
        endYear: Int,
        endMonth: Int,
    ): List<MonthlyBalanceSnapshot>
}
