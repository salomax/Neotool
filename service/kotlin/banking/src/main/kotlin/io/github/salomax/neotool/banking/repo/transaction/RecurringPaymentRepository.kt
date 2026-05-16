package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.domain.transaction.RecurringPayment
import java.time.LocalDate
import java.util.UUID

interface RecurringPaymentRepository {
    fun findById(
        id: UUID,
        accountId: UUID,
    ): RecurringPayment?

    fun findAllByAccountId(accountId: UUID): List<RecurringPayment>

    fun findAllByFinancialAccountId(
        financialAccountId: UUID,
        accountId: UUID,
    ): List<RecurringPayment>

    fun findActiveWithDueBefore(cutoffDate: LocalDate): List<RecurringPayment>

    fun findAllActive(): List<RecurringPayment>

    fun save(template: RecurringPayment): RecurringPayment

    fun update(template: RecurringPayment): RecurringPayment

    fun softDelete(
        id: UUID,
        accountId: UUID,
    )
}
