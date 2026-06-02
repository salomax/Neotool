package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.RecurringPayment
import io.github.salomax.neotool.banking.model.transaction.RecurringPaymentMapper
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class DefaultRecurringPaymentRepository(
    private val entityRepository: RecurringPaymentEntityRepository,
) : RecurringPaymentRepository {
    override fun findById(
        id: UUID,
        accountId: UUID,
    ): RecurringPayment? =
        entityRepository.findByIdAndAccountIdAndDeletedAtIsNull(id, accountId)?.let(RecurringPaymentMapper::toDomain)

    override fun findAllByAccountId(accountId: UUID): List<RecurringPayment> =
        entityRepository.findByAccountIdAndDeletedAtIsNullOrderByNextDueDateAsc(accountId)
            .map(RecurringPaymentMapper::toDomain)

    override fun findAllByFinancialAccountId(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<RecurringPayment> =
        entityRepository
            .findByFinancialAccountIdAndAccountIdAndDeletedAtIsNullOrderByNextDueDateAsc(financialAccountId, accountId)
            .map(RecurringPaymentMapper::toDomain)

    override fun findActiveDueForReminder(referenceDate: LocalDate): List<RecurringPayment> =
        entityRepository.findActiveDueForReminder(referenceDate).map(RecurringPaymentMapper::toDomain)

    override fun findAllActive(): List<RecurringPayment> =
        entityRepository.findAllActive().map(RecurringPaymentMapper::toDomain)

    @Transactional
    override fun save(template: RecurringPayment): RecurringPayment =
        entityRepository.save(RecurringPaymentMapper.toEntity(template)).let(RecurringPaymentMapper::toDomain)

    @Transactional
    override fun update(template: RecurringPayment): RecurringPayment {
        val id = requireNotNull(template.id) { "Recurring payment id is required for update" }
        val entity =
            entityRepository.findById(id).orElseThrow {
                NoSuchElementException("Recurring payment $id not found")
            }
        RecurringPaymentMapper.updateEntity(entity, template)
        return entityRepository.update(entity).let(RecurringPaymentMapper::toDomain)
    }

    @Transactional
    override fun softDelete(
        id: UUID,
        accountId: UUID,
    ) {
        entityRepository.softDelete(id, accountId, Instant.now())
    }
}
