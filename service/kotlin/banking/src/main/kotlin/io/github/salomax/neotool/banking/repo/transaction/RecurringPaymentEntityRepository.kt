package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.model.transaction.RecurringPaymentEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
interface RecurringPaymentEntityRepository : JpaRepository<RecurringPaymentEntity, UUID> {
    fun findByIdAndAccountIdAndDeletedAtIsNull(
        id: UUID,
        accountId: UUID,
    ): RecurringPaymentEntity?

    fun findByAccountIdAndDeletedAtIsNullOrderByNextDueDateAsc(accountId: UUID): List<RecurringPaymentEntity>

    fun findByFinancialAccountIdAndAccountIdAndDeletedAtIsNullOrderByNextDueDateAsc(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<RecurringPaymentEntity>

    @Query(
        value = """
            SELECT * FROM banking.recurring_payments
             WHERE status = 'ACTIVE'
               AND deleted_at IS NULL
               AND next_due_date <= (:referenceDate + (remind_days_before * INTERVAL '1 day'))
        """,
        nativeQuery = true,
    )
    fun findActiveDueForReminder(referenceDate: LocalDate): List<RecurringPaymentEntity>

    @Query(
        value = """
            SELECT * FROM banking.recurring_payments
             WHERE status = 'ACTIVE'
               AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findAllActive(): List<RecurringPaymentEntity>

    @Query(
        value = """
            UPDATE banking.recurring_payments
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
}
