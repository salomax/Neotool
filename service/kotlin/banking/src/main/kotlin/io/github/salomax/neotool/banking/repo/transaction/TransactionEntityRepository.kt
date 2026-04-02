package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.TransactionSource
import io.github.salomax.neotool.banking.model.transaction.TransactionEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA repository for banking.transactions (entity layer).
 */
@Repository
interface TransactionEntityRepository : JpaRepository<TransactionEntity, UUID> {
    fun findByFinancialAccountIdAndAccountIdAndDeletedAtIsNullOrderByDateDescAndCreatedAtDesc(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<TransactionEntity>

    fun findByBillIdAndAccountIdAndDeletedAtIsNullOrderByDateDescAndCreatedAtDesc(
        billId: UUID,
        accountId: UUID,
    ): List<TransactionEntity>

    fun findByAccountIdAndDeletedAtIsNullOrderByDateDescAndCreatedAtDesc(accountId: UUID): List<TransactionEntity>

    @Query(
        """
        SELECT DISTINCT belongs_to_user_id
          FROM banking.transactions
         WHERE account_id = :accountId
           AND deleted_at IS NULL
           AND belongs_to_user_id IS NOT NULL
         ORDER BY belongs_to_user_id
        """,
        nativeQuery = true,
    )
    fun findDistinctBelongsToUserIds(accountId: UUID): List<UUID>

    @Query(
        """
        SELECT DISTINCT belongs_to_name
          FROM banking.transactions
         WHERE account_id = :accountId
           AND deleted_at IS NULL
           AND belongs_to_name IS NOT NULL
         ORDER BY belongs_to_name
        """,
        nativeQuery = true,
    )
    fun findDistinctBelongsToNames(accountId: UUID): List<String>

    fun findByIdAndAccountIdAndDeletedAtIsNull(
        id: UUID,
        accountId: UUID,
    ): TransactionEntity?

    fun existsByFinancialAccountIdAndProviderTransactionIdAndSourceAndDeletedAtIsNull(
        financialAccountId: UUID,
        providerTransactionId: String,
        source: TransactionSource,
    ): Boolean

    fun existsByFinancialAccountIdAndImportFingerprintAndDeletedAtIsNull(
        financialAccountId: UUID,
        importFingerprint: String,
    ): Boolean

    fun existsByFinancialAccountIdAndBillIdAndImportFingerprintAndDeletedAtIsNull(
        financialAccountId: UUID,
        billId: UUID,
        importFingerprint: String,
    ): Boolean

    @Query(
        """
        SELECT * FROM banking.transactions
        WHERE financial_account_id = :financialAccountId
          AND date = :date
          AND amount = :amount
          AND source != :incomingSource
          AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findCrossSourceCandidates(
        financialAccountId: UUID,
        date: LocalDate,
        amount: BigDecimal,
        incomingSource: String,
    ): List<TransactionEntity>

    @Query(
        """
        SELECT t.*
          FROM banking.transactions t
          LEFT JOIN banking.purchase_documents nd
                 ON nd.transaction_id = t.id
                AND nd.account_id = t.account_id
         WHERE t.financial_account_id = :financialAccountId
           AND t.source = 'MANUAL'
           AND t.credit_debit_type = 'DEBIT'
           AND t.amount = :amount
           AND t.date BETWEEN :startDate AND :endDate
           AND t.deleted_at IS NULL
           AND nd.id IS NULL
         ORDER BY ABS(t.date - :referenceDate), t.created_at DESC
         LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findManualNfceLinkCandidates(
        financialAccountId: UUID,
        amount: BigDecimal,
        startDate: LocalDate,
        endDate: LocalDate,
        referenceDate: LocalDate,
    ): List<TransactionEntity>

    @Query(
        """
        UPDATE banking.transactions
           SET deleted_at = :deletedAt,
               updated_at = :deletedAt
         WHERE id = :id
           AND account_id = :accountId
           AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDelete(
        id: UUID,
        accountId: UUID,
        deletedAt: Instant,
    ): Int

    @Query(
        """
        SELECT DISTINCT financial_account_id
          FROM banking.transactions
         WHERE account_id = :accountId
           AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findFinancialAccountIdsByAccount(accountId: UUID): List<UUID>

    @Query(
        """
        SELECT MIN(date)
          FROM banking.transactions
         WHERE financial_account_id = CAST(:financialAccountId AS uuid)
           AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findEarliestTransactionDate(financialAccountId: String): LocalDate?

    @Query(
        """
        SELECT MIN(date)
          FROM banking.transactions
         WHERE financial_account_id = CAST(:financialAccountId AS uuid)
           AND type = 'BALANCE_ADJUSTMENT'
           AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findEarliestBalanceAdjustmentDate(financialAccountId: String): LocalDate?

    @Query(
        """
        SELECT t.* FROM banking.transactions t
        LEFT JOIN banking.credit_card_bills b
               ON b.id = t.bill_id
              AND b.deleted_at IS NULL
         WHERE t.account_id = :accountId
           AND (
               CAST(:financialAccountId AS uuid) IS NULL
               OR t.financial_account_id = CAST(:financialAccountId AS uuid)
           )
           AND (
               (
                   t.installment_number > 1
                   AND DATE_TRUNC('month', COALESCE(b.due_date, t.date)) = DATE_TRUNC('month', MAKE_DATE(:year, :month, 1))
               )
               OR
               (
                   (t.installment_number IS NULL OR t.installment_number <= 1)
                   AND t.date >= MAKE_DATE(:year, :month, 1)
                   AND t.date < (MAKE_DATE(:year, :month, 1) + INTERVAL '1 month')
               )
           )
           AND t.category_id IN (:categoryIds)
           AND t.deleted_at IS NULL
         ORDER BY t.date DESC, t.created_at DESC
        """,
        nativeQuery = true,
    )
    fun findInDateRangeByCategoryIds(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
        categoryIds: List<UUID>,
    ): List<TransactionEntity>

    @Query(
        """
        SELECT t.* FROM banking.transactions t
        LEFT JOIN banking.credit_card_bills b
               ON b.id = t.bill_id
              AND b.deleted_at IS NULL
         WHERE t.account_id = :accountId
           AND (
               CAST(:financialAccountId AS uuid) IS NULL
               OR t.financial_account_id = CAST(:financialAccountId AS uuid)
           )
           AND (
               (
                   t.installment_number > 1
                   AND DATE_TRUNC('month', COALESCE(b.due_date, t.date)) = DATE_TRUNC('month', MAKE_DATE(:year, :month, 1))
               )
               OR
               (
                   (t.installment_number IS NULL OR t.installment_number <= 1)
                   AND t.date >= MAKE_DATE(:year, :month, 1)
                   AND t.date < (MAKE_DATE(:year, :month, 1) + INTERVAL '1 month')
               )
           )
           AND t.deleted_at IS NULL
         ORDER BY t.date DESC, t.created_at DESC
        """,
        nativeQuery = true,
    )
    fun findInDateRange(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
    ): List<TransactionEntity>

    @Query(
        """
        SELECT t.* FROM banking.transactions t
        LEFT JOIN banking.credit_card_bills b
               ON b.id = t.bill_id
              AND b.deleted_at IS NULL
         WHERE t.account_id = :accountId
           AND (
               CAST(:financialAccountId AS uuid) IS NULL
               OR t.financial_account_id = CAST(:financialAccountId AS uuid)
           )
           AND (
               (
                   t.installment_number > 1
                   AND DATE_TRUNC('month', COALESCE(b.due_date, t.date)) = DATE_TRUNC('month', MAKE_DATE(:year, :month, 1))
               )
               OR
               (
                   (t.installment_number IS NULL OR t.installment_number <= 1)
                   AND t.date >= MAKE_DATE(:year, :month, 1)
                   AND t.date < (MAKE_DATE(:year, :month, 1) + INTERVAL '1 month')
               )
           )
           AND t.category_id IS NULL
           AND t.deleted_at IS NULL
         ORDER BY t.date DESC, t.created_at DESC
        """,
        nativeQuery = true,
    )
    fun findInDateRangeUncategorized(
        accountId: UUID,
        financialAccountId: UUID?,
        year: Int,
        month: Int,
    ): List<TransactionEntity>
}
