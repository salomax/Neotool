package io.github.salomax.neotool.banking.repo

import io.github.salomax.neotool.banking.domain.transaction.BillStatus
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBill
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBillRepository
import io.github.salomax.neotool.banking.model.transaction.CreditCardBillMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Default implementation of [CreditCardBillRepository] using JPA + native SQL.
 */
@Singleton
open class DefaultCreditCardBillRepository(
    private val entityRepository: CreditCardBillEntityRepository,
) : CreditCardBillRepository {

    override fun findById(id: UUID, accountId: UUID): CreditCardBill? =
        entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(id, accountId)
            ?.let(CreditCardBillMapper::toDomain)

    override fun findByClosingDate(financialAccountId: UUID, closingDate: LocalDate): CreditCardBill? =
        entityRepository.findByFinancialAccountIdAndClosingDateAndDeletedAtIsNull(financialAccountId, closingDate)
            ?.let(CreditCardBillMapper::toDomain)

    override fun listByAccount(financialAccountId: UUID, accountId: UUID): List<CreditCardBill> =
        entityRepository
            .findByFinancialAccountIdAndAccountIdAndDeletedAtIsNullOrderByClosingDateDesc(financialAccountId, accountId)
            .map(CreditCardBillMapper::toDomain)

    @Transactional
    override fun findOrCreate(
        financialAccountId: UUID,
        accountId: UUID,
        closingDate: LocalDate,
        dueDate: LocalDate,
    ): CreditCardBill {
        // Safe concurrent creation: ON CONFLICT DO NOTHING on unique(financial_account_id, closing_date).
        entityRepository.insertIfAbsent(financialAccountId, accountId, closingDate, dueDate)
        // Re-read to get the DB-generated id (whether newly created or pre-existing).
        return entityRepository
            .findByFinancialAccountIdAndClosingDateAndDeletedAtIsNull(financialAccountId, closingDate)!!
            .let(CreditCardBillMapper::toDomain)
    }

    @Transactional
    override fun recomputeTotal(billId: UUID, accountId: UUID): CreditCardBill {
        val entity = entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(billId, accountId)
            ?: throw NoSuchElementException("CreditCardBill $billId not found")
        val newStatus = computeStatus(entity.closingDate, entity.dueDate, entity.status)
        entityRepository.updateTotalAndStatus(
            billId = billId,
            status = newStatus.name,
            updatedAt = Instant.now(),
        )
        return entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(billId, accountId)!!
            .let(CreditCardBillMapper::toDomain)
    }

    override fun setStatus(billId: UUID, status: BillStatus, accountId: UUID) {
        entityRepository.updateStatus(billId, accountId, status.name, Instant.now())
    }

    private fun computeStatus(closingDate: LocalDate, dueDate: LocalDate, current: BillStatus): BillStatus {
        if (current == BillStatus.PAID) return BillStatus.PAID
        val today = LocalDate.now()
        return when {
            closingDate >= today -> BillStatus.OPEN
            dueDate < today     -> BillStatus.OVERDUE
            else                -> BillStatus.CLOSED
        }
    }
}
