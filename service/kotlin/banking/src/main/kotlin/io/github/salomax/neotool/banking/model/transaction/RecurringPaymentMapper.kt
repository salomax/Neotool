package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.RecurringFrequency
import io.github.salomax.neotool.banking.domain.transaction.RecurringPayment
import io.github.salomax.neotool.banking.domain.transaction.RecurringPaymentStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType

object RecurringPaymentMapper {
    fun toDomain(entity: RecurringPaymentEntity): RecurringPayment =
        RecurringPayment(
            id = entity.id,
            accountId = entity.accountId,
            financialAccountId = entity.financialAccountId,
            name = entity.name,
            creditDebitType = CreditDebitType.valueOf(entity.creditDebitType),
            type = entity.type?.let(TransactionType::valueOf),
            expectedAmount = entity.expectedAmount,
            currencyCode = entity.currencyCode,
            categoryId = entity.categoryId,
            merchantId = entity.merchantId,
            frequency = RecurringFrequency.valueOf(entity.frequency),
            dayOfMonth = entity.dayOfMonth?.toInt(),
            intervalDays = entity.intervalDays,
            remindDaysBefore = entity.remindDaysBefore.toInt(),
            status = RecurringPaymentStatus.valueOf(entity.status),
            nextDueDate = entity.nextDueDate,
            lastPaidDate = entity.lastPaidDate,
            notes = entity.notes,
            deletedAt = entity.deletedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: RecurringPayment): RecurringPaymentEntity =
        RecurringPaymentEntity(
            id = null,
            accountId = domain.accountId,
            financialAccountId = domain.financialAccountId,
            name = domain.name,
            creditDebitType = domain.creditDebitType.name,
            type = domain.type?.name,
            expectedAmount = domain.expectedAmount,
            currencyCode = domain.currencyCode,
            categoryId = domain.categoryId,
            merchantId = domain.merchantId,
            frequency = domain.frequency.name,
            dayOfMonth = domain.dayOfMonth?.toShort(),
            intervalDays = domain.intervalDays,
            remindDaysBefore = domain.remindDaysBefore.toShort(),
            status = domain.status.name,
            nextDueDate = domain.nextDueDate,
            lastPaidDate = domain.lastPaidDate,
            notes = domain.notes,
            deletedAt = domain.deletedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )

    fun updateEntity(
        entity: RecurringPaymentEntity,
        domain: RecurringPayment,
    ) {
        entity.accountId = domain.accountId
        entity.financialAccountId = domain.financialAccountId
        entity.name = domain.name
        entity.creditDebitType = domain.creditDebitType.name
        entity.type = domain.type?.name
        entity.expectedAmount = domain.expectedAmount
        entity.currencyCode = domain.currencyCode
        entity.categoryId = domain.categoryId
        entity.merchantId = domain.merchantId
        entity.frequency = domain.frequency.name
        entity.dayOfMonth = domain.dayOfMonth?.toShort()
        entity.intervalDays = domain.intervalDays
        entity.remindDaysBefore = domain.remindDaysBefore.toShort()
        entity.status = domain.status.name
        entity.nextDueDate = domain.nextDueDate
        entity.lastPaidDate = domain.lastPaidDate
        entity.notes = domain.notes
        entity.deletedAt = domain.deletedAt
        entity.updatedAt = domain.updatedAt
    }
}
