package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.BillStatus
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBill
import java.time.LocalDate
import java.util.UUID

/**
 * Domain repository for [CreditCardBill].
 * All queries are scoped by accountId (tenant).
 */
interface CreditCardBillRepository {
    fun findById(
        id: UUID,
        accountId: UUID,
    ): CreditCardBill?

    fun findByReferenceMonth(
        financialAccountId: UUID,
        accountId: UUID,
        referenceMonth: LocalDate,
    ): CreditCardBill?

    /**
     * All non-deleted bills for the account ordered by [CreditCardBill.referenceMonth] descending.
     */
    fun listByAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<CreditCardBill>

    /**
     * Returns the bill for the given business [referenceMonth], creating a new OPEN bill if none exists.
     * When multiple active bills share the same reference month, one is selected deterministically.
     */
    fun findOrCreate(
        financialAccountId: UUID,
        accountId: UUID,
        closingDate: LocalDate,
        dueDate: LocalDate,
        referenceMonth: LocalDate,
    ): CreditCardBill

    /**
     * Recomputes [CreditCardBill.totalAmount] from non-deleted transactions linked to [billId]
     * and updates [CreditCardBill.status] based on [closingDate]/[dueDate] and today's date.
     * Returns the updated bill.
     */
    fun recomputeTotal(
        billId: UUID,
        accountId: UUID,
    ): CreditCardBill

    /**
     * Sets the bill status (e.g. PAID). Used after full payment; does not recompute totalAmount.
     */
    fun setStatus(
        billId: UUID,
        status: BillStatus,
        accountId: UUID,
    )

    /**
     * Soft-deletes the bill when it has no active transactions.
     * Returns true when the bill was deleted.
     */
    fun softDeleteIfOrphan(
        billId: UUID,
        accountId: UUID,
    ): Boolean
}
