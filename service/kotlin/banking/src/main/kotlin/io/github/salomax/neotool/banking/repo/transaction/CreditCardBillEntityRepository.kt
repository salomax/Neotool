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

    fun findByFinancialAccountIdAndAccountIdAndClosingDateAndDeletedAtIsNull(
        financialAccountId: UUID,
        accountId: UUID,
        closingDate: LocalDate,
    ): CreditCardBillEntity?

    fun findByFinancialAccountIdAndAccountIdAndDeletedAtIsNullOrderByClosingDateDesc(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<CreditCardBillEntity>

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

    /**
     * Creates the bill if it does not already exist.
     * ON CONFLICT DO NOTHING is safe against the unique constraint
     * uq_credit_card_bills_account_closing (financial_account_id, closing_date).
     * Caller must re-read the row after this call.
     */
    @Query(
        """
        INSERT INTO banking.credit_card_bills
            (financial_account_id, account_id, closing_date, due_date, status, created_at, updated_at)
        VALUES
            (:financialAccountId, :accountId, :closingDate, :dueDate, 'OPEN', NOW(), NOW())
        ON CONFLICT (financial_account_id, closing_date) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        financialAccountId: UUID,
        accountId: UUID,
        closingDate: LocalDate,
        dueDate: LocalDate,
    )

    /**
     * Recomputes total_amount as net spend (SUM DEBIT - SUM CREDIT) from non-deleted transactions
     * linked to this bill, then updates status and updated_at in a single statement.
     */
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

    /**
     * Returns the current net spend for a bill: SUM(DEBIT) - SUM(CREDIT) from non-deleted transactions.
     * Used to determine whether a PAID bill should revert status when a payment is deleted.
     */
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

    /**
     * Updates only status and updated_at (used when marking bill as PAID after full payment).
     */
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
