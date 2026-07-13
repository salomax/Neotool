package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.BillStatus
import io.github.salomax.neotool.banking.domain.transaction.CreditCardBill
import io.github.salomax.neotool.banking.model.transaction.CreditCardBillMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Default implementation of [CreditCardBillRepository] using JPA + native SQL.
 */
@Singleton
open class DefaultCreditCardBillRepository(
    private val entityRepository: CreditCardBillEntityRepository,
    @PersistenceContext private val entityManager: EntityManager,
) : CreditCardBillRepository {
    override fun findById(
        id: UUID,
        accountId: UUID,
    ): CreditCardBill? =
        entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(id, accountId)
            ?.let(CreditCardBillMapper::toDomain)

    override fun findByReferenceMonth(
        financialAccountId: UUID,
        accountId: UUID,
        referenceMonth: LocalDate,
    ): CreditCardBill? =
        entityRepository
            .findByFinancialAccountIdAndAccountIdAndReferenceMonthAndDeletedAtIsNull(
                financialAccountId,
                accountId,
                referenceMonth,
            )
            ?.let(CreditCardBillMapper::toDomain)

    override fun listByAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<CreditCardBill> =
        entityRepository
            .findByFinancialAccountIdAndAccountIdAndDeletedAtIsNullOrderByReferenceMonthDesc(
                financialAccountId,
                accountId,
            )
            .map(CreditCardBillMapper::toDomain)

    @Transactional
    override fun findOrCreate(
        financialAccountId: UUID,
        accountId: UUID,
        closingDate: LocalDate,
        dueDate: LocalDate,
        referenceMonth: LocalDate,
    ): CreditCardBill {
        val existing =
            entityRepository.findByFinancialAccountIdAndAccountIdAndReferenceMonthAndDeletedAtIsNull(
                financialAccountId,
                accountId,
                referenceMonth,
            )
        if (existing != null) {
            if (existing.closingDate != closingDate || existing.dueDate != dueDate) {
                entityRepository.updateCycleMetadata(
                    billId = existing.id!!,
                    accountId = accountId,
                    closingDate = closingDate,
                    dueDate = dueDate,
                    referenceMonth = referenceMonth,
                    updatedAt = Instant.now(),
                )
            }
            return refreshAndMap(existing.id!!, accountId)
        }

        val deleted =
            entityRepository.findByFinancialAccountIdAndAccountIdAndClosingDateAndDeletedAtIsNotNull(
                financialAccountId,
                accountId,
                closingDate,
            )
        if (deleted != null) {
            entityRepository.undelete(
                billId = deleted.id!!,
                accountId = accountId,
                closingDate = closingDate,
                dueDate = dueDate,
                referenceMonth = referenceMonth,
                updatedAt = Instant.now(),
            )
            return refreshAndMap(deleted.id!!, accountId)
        }

        entityRepository.insertIfAbsent(
            financialAccountId = financialAccountId,
            accountId = accountId,
            closingDate = closingDate,
            dueDate = dueDate,
            referenceMonth = referenceMonth,
        )
        entityManager.clear()

        val created =
            entityRepository.findByFinancialAccountIdAndAccountIdAndReferenceMonthAndDeletedAtIsNull(
                financialAccountId,
                accountId,
                referenceMonth,
            ) ?: throw IllegalStateException(
                "CreditCardBill for account $financialAccountId and referenceMonth $referenceMonth " +
                    "was not found after insertIfAbsent",
            )
        if (created.closingDate != closingDate || created.dueDate != dueDate) {
            entityRepository.updateCycleMetadata(
                billId = created.id!!,
                accountId = accountId,
                closingDate = closingDate,
                dueDate = dueDate,
                referenceMonth = referenceMonth,
                updatedAt = Instant.now(),
            )
            return refreshAndMap(created.id!!, accountId)
        }
        return CreditCardBillMapper.toDomain(created)
    }

    @Transactional
    override fun recomputeTotal(
        billId: UUID,
        accountId: UUID,
    ): CreditCardBill {
        val entity =
            entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(billId, accountId)
                ?: throw NoSuchElementException("CreditCardBill $billId not found")
        val newTotal = entityRepository.computeCurrentTotal(billId)
        val newStatus = computeStatus(entity.closingDate, entity.dueDate, entity.status, newTotal)
        entityRepository.updateTotalAndStatus(
            billId = billId,
            status = newStatus.name,
            updatedAt = Instant.now(),
        )
        entityManager.clear()
        return entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(billId, accountId)!!
            .let(CreditCardBillMapper::toDomain)
    }

    override fun setStatus(
        billId: UUID,
        status: BillStatus,
        accountId: UUID,
    ) {
        entityRepository.updateStatus(billId, accountId, status.name, Instant.now())
        entityManager.clear()
    }

    @Transactional
    override fun softDeleteIfOrphan(
        billId: UUID,
        accountId: UUID,
    ): Boolean {
        val deleted = entityRepository.softDeleteIfOrphan(billId, accountId, Instant.now())
        if (deleted > 0) {
            entityManager.clear()
        }
        return deleted > 0
    }

    private fun computeStatus(
        closingDate: LocalDate,
        dueDate: LocalDate,
        current: BillStatus,
        newTotal: BigDecimal,
    ): BillStatus {
        if (current == BillStatus.PAID && newTotal <= BigDecimal.ZERO) return BillStatus.PAID
        val today = LocalDate.now()
        return when {
            closingDate >= today -> BillStatus.OPEN
            dueDate < today -> BillStatus.OVERDUE
            else -> BillStatus.CLOSED
        }
    }

    private fun refreshAndMap(
        billId: UUID,
        accountId: UUID,
    ): CreditCardBill {
        entityManager.clear()
        return entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(billId, accountId)
            ?.let(CreditCardBillMapper::toDomain)
            ?: throw IllegalStateException("CreditCardBill $billId was not found after refresh for account $accountId")
    }
}
