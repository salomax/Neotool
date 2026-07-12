package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.model.transaction.CreditCardBillEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.GenericRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA repository for banking.credit_card_bills (entity layer).
 */
@Repository
interface CreditCardBillEntityRepository : GenericRepository<CreditCardBillEntity, UUID> {
    fun findByIdAndAccountIdAndDeletedAtIsNull(
        id: UUID,
        accountId: UUID,
    ): CreditCardBillEntity?

    fun findByFinancialAccountIdAndAccountIdAndReferenceMonthAndDeletedAtIsNull(
        financialAccountId: UUID,
        accountId: UUID,
        referenceMonth: LocalDate,
    ): CreditCardBillEntity?

    fun findByFinancialAccountIdAndAccountIdAndDeletedAtIsNullOrderByReferenceMonthDesc(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<CreditCardBillEntity>

    fun findByFinancialAccountIdAndAccountIdAndClosingDateAndDeletedAtIsNull(
        financialAccountId: UUID,
        accountId: UUID,
        closingDate: LocalDate,
    ): CreditCardBillEntity?

    @Query(
        """
        SELECT COUNT(*)
          FROM banking.credit_card_bills
         WHERE financial_account_id = :financialAccountId
           AND deleted_at IS NULL
           AND status IN ('OPEN', 'CLOSED')
        """,
        nativeQuery = true,
    )
    fun countOpenByFinancialAccountId(financialAccountId: UUID): Long

    @Query(
        """
        UPDATE banking.credit_card_bills
           SET deleted_at = :now,
               updated_at = :now
         WHERE financial_account_id = :financialAccountId
           AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun softDeleteByFinancialAccountId(
        financialAccountId: UUID,
        now: Instant,
    ): Int

    @Query(
        """
        UPDATE banking.credit_card_bills
           SET deleted_at = :now,
               updated_at = :now
         WHERE id = :billId
           AND account_id = :accountId
           AND deleted_at IS NULL
           AND NOT EXISTS (
               SELECT 1
                 FROM banking.transactions t
                WHERE t.bill_id = :billId
                  AND t.deleted_at IS NULL
           )
        """,
        nativeQuery = true,
    )
    fun softDeleteIfOrphan(
        billId: UUID,
        accountId: UUID,
        now: Instant,
    ): Int

    /**
     * Creates the bill if it does not already exist for the reference month.
     * ON CONFLICT DO NOTHING is safe against uq_credit_card_bills_account_reference_month_active.
     * Caller must re-read the row after this call.
     */
    @Query(
        """
        INSERT INTO banking.credit_card_bills
            (financial_account_id, account_id, closing_date, due_date, reference_month, status, created_at, updated_at)
        VALUES
            (:financialAccountId, :accountId, :closingDate, :dueDate, :referenceMonth, 'OPEN', NOW(), NOW())
        ON CONFLICT (financial_account_id, reference_month) WHERE deleted_at IS NULL DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        financialAccountId: UUID,
        accountId: UUID,
        closingDate: LocalDate,
        dueDate: LocalDate,
        referenceMonth: LocalDate,
    )

    @Query(
        """
        UPDATE banking.credit_card_bills
           SET closing_date = :closingDate,
               due_date = :dueDate,
               reference_month = :referenceMonth,
               updated_at = :updatedAt
         WHERE id = :billId
           AND account_id = :accountId
           AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun updateCycleMetadata(
        billId: UUID,
        accountId: UUID,
        closingDate: LocalDate,
        dueDate: LocalDate,
        referenceMonth: LocalDate,
        updatedAt: Instant,
    )

    @Query(
        """
        UPDATE banking.credit_card_bills b
           SET total_amount = (
               SELECT COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'), 0) -
                      COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT'), 0)
               FROM banking.transactions t
               WHERE t.bill_id = :billId
                 AND t.deleted_at IS NULL
           ),
           status     = CAST(:status AS TEXT),
           updated_at = :updatedAt
         WHERE b.id = :billId
        """,
        nativeQuery = true,
    )
    fun updateTotalAndStatus(
        billId: UUID,
        status: String,
        updatedAt: Instant,
    )

    @Query(
        """
        SELECT COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'DEBIT'), 0) -
               COALESCE(SUM(amount) FILTER (WHERE credit_debit_type = 'CREDIT'), 0)
          FROM banking.transactions t
         WHERE t.bill_id = :billId
           AND t.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun computeCurrentTotal(billId: UUID): BigDecimal

    @Query(
        """
        UPDATE banking.credit_card_bills
           SET status = CAST(:status AS TEXT),
               updated_at = :updatedAt
         WHERE id = :billId
           AND account_id = :accountId
        """,
        nativeQuery = true,
    )
    fun updateStatus(
        billId: UUID,
        accountId: UUID,
        status: String,
        updatedAt: Instant,
    )
}
