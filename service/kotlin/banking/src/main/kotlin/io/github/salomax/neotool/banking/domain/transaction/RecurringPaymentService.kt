package io.github.salomax.neotool.banking.domain.transaction

import io.github.salomax.neotool.banking.repo.transaction.RecurringPaymentRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import jakarta.inject.Singleton
import java.time.LocalDate

@Singleton
class RecurringPaymentService(
    private val recurringPaymentRepository: RecurringPaymentRepository,
) {
    fun validateTemplate(template: RecurringPayment) {
        if (template.dayOfMonth != null && template.dayOfMonth !in 1..31) {
            throw invalidInput("dayOfMonth", "must be between 1 and 31")
        }

        when (template.frequency) {
            RecurringFrequency.MONTHLY -> {
                if (template.dayOfMonth == null) {
                    throw invalidInput("dayOfMonth", "is required when frequency is MONTHLY")
                }
            }
            RecurringFrequency.CUSTOM -> {
                val intervalDays = template.intervalDays
                    ?: throw invalidInput("intervalDays", "is required when frequency is CUSTOM")
                if (intervalDays <= 0) {
                    throw invalidInput("intervalDays", "must be greater than 0")
                }
            }
            RecurringFrequency.WEEKLY -> {
                val intervalDays = template.intervalDays
                if (intervalDays != null && intervalDays <= 0) {
                    throw invalidInput("intervalDays", "must be greater than 0")
                }
            }
            RecurringFrequency.YEARLY -> Unit
        }
    }

    fun computeNextDueDate(template: RecurringPayment): LocalDate {
        val from = template.nextDueDate
        return when (template.frequency) {
            RecurringFrequency.MONTHLY -> {
                val day = template.dayOfMonth ?: from.dayOfMonth
                val next = from.plusMonths(1)
                next.withDayOfMonth(minOf(day, next.lengthOfMonth()))
            }
            RecurringFrequency.WEEKLY -> {
                val days = template.intervalDays?.coerceAtLeast(1) ?: 7
                from.plusDays(days.toLong())
            }
            RecurringFrequency.YEARLY -> from.plusYears(1)
            RecurringFrequency.CUSTOM -> {
                val days = template.intervalDays?.coerceAtLeast(1)
                    ?: throw IllegalStateException("intervalDays is required for CUSTOM recurrence")
                from.plusDays(days.toLong())
            }
        }
    }

    fun upcomingDates(
        template: RecurringPayment,
        from: LocalDate,
        days: Long,
    ): List<LocalDate> {
        val cutoff = from.plusDays(days)
        val result = mutableListOf<LocalDate>()
        var cursor = template.nextDueDate
        while (cursor.isBefore(from)) {
            cursor = computeNextDueDate(template.copy(nextDueDate = cursor))
        }
        while (!cursor.isAfter(cutoff)) {
            result += cursor
            cursor = computeNextDueDate(template.copy(nextDueDate = cursor))
        }
        return result
    }

    fun confirmPayment(
        template: RecurringPayment,
        paymentDate: LocalDate,
    ): RecurringPayment {
        var next = template.nextDueDate
        while (!next.isAfter(paymentDate)) {
            next = computeNextDueDate(template.copy(nextDueDate = next))
        }
        return recurringPaymentRepository.update(
            template.copy(
                lastPaidDate = paymentDate,
                nextDueDate = next,
            ),
        )
    }

    private fun invalidInput(
        field: String,
        reason: String,
    ): ValidationException =
        ValidationException(
            errorCode = CommonErrorCode.INVALID_INPUT,
            field = field,
            parameters = mapOf("reason" to reason),
        )
}
