package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.model.transaction.MonthlyBalanceSnapshotEntity
import io.github.salomax.neotool.banking.model.transaction.MonthlyBalanceSnapshotId
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JPA repository for banking.monthly_balance_snapshots (entity layer).
 * All writes use native UPSERT to guarantee INSERT ON CONFLICT DO UPDATE semantics.
 */
@Repository
interface MonthlyBalanceSnapshotEntityRepository :
    JpaRepository<MonthlyBalanceSnapshotEntity, MonthlyBalanceSnapshotId> {
    @Query(
        """
        SELECT s FROM MonthlyBalanceSnapshotEntity s
        WHERE s.id.financialAccountId = :financialAccountId
          AND s.id.year = :year
          AND s.id.month = :month
        """,
    )
    fun findByKey(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ): MonthlyBalanceSnapshotEntity?

    /**
     * Latest clean snapshot strictly before (year, month).
     * Ordered desc to get the most recent one.
     */
    @Query(
        """
        SELECT s FROM MonthlyBalanceSnapshotEntity s
        WHERE s.id.financialAccountId = :financialAccountId
          AND s.isDirty = false
          AND (s.id.year < :year OR (s.id.year = :year AND s.id.month < :month))
        ORDER BY s.id.year DESC, s.id.month DESC
        """,
    )
    fun findLatestCleanBefore(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    ): List<MonthlyBalanceSnapshotEntity>

    @Query(
        """
        SELECT s FROM MonthlyBalanceSnapshotEntity s
        WHERE s.id.financialAccountId = :financialAccountId
          AND s.isDirty = true
        ORDER BY s.id.year ASC, s.id.month ASC
        """,
    )
    fun findAllDirty(financialAccountId: UUID): List<MonthlyBalanceSnapshotEntity>

    @Query(
        """
        SELECT DISTINCT s.id.financialAccountId
        FROM MonthlyBalanceSnapshotEntity s
        WHERE s.isDirty = true
        """,
    )
    fun findFinancialAccountIdsWithDirtySnapshots(): List<UUID>

    @Query(
        """
        INSERT INTO banking.monthly_balance_snapshots
            (financial_account_id, account_id, year, month,
             opening_balance, closing_balance, total_income, total_expenses,
             is_dirty, computed_at, created_at, updated_at)
        VALUES
            (:financialAccountId, :accountId, :year, :month,
             :openingBalance, :closingBalance, :totalIncome, :totalExpenses,
             :isDirty, :computedAt, NOW(), NOW())
        ON CONFLICT (financial_account_id, year, month)
        DO UPDATE SET
            opening_balance = EXCLUDED.opening_balance,
            closing_balance = EXCLUDED.closing_balance,
            total_income    = EXCLUDED.total_income,
            total_expenses  = EXCLUDED.total_expenses,
            is_dirty        = EXCLUDED.is_dirty,
            computed_at     = EXCLUDED.computed_at,
            updated_at      = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        financialAccountId: UUID,
        accountId: UUID,
        year: Int,
        month: Int,
        openingBalance: BigDecimal,
        closingBalance: BigDecimal,
        totalIncome: BigDecimal,
        totalExpenses: BigDecimal,
        isDirty: Boolean,
        computedAt: Instant,
    )

    @Query(
        """
        UPDATE banking.monthly_balance_snapshots
           SET is_dirty   = true,
               updated_at = NOW()
         WHERE financial_account_id = :financialAccountId
           AND (year > :year OR (year = :year AND month >= :month))
        """,
        nativeQuery = true,
    )
    fun markDirtyFromMonth(
        financialAccountId: UUID,
        year: Int,
        month: Int,
    )
}
