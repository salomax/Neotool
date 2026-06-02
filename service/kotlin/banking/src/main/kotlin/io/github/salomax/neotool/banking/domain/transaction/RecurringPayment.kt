package io.github.salomax.neotool.banking.domain.transaction

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RecurringPayment(
    val id: UUID? = null,
    val accountId: UUID,
    val financialAccountId: UUID?,
    val name: String,
    val creditDebitType: CreditDebitType,
    val type: TransactionType? = null,
    val expectedAmount: BigDecimal? = null,
    val currencyCode: String = "BRL",
    val categoryId: UUID? = null,
    val merchantId: UUID? = null,
    val frequency: RecurringFrequency,
    val dayOfMonth: Int? = null,
    val intervalDays: Int? = null,
    val remindDaysBefore: Int = 3,
    val status: RecurringPaymentStatus = RecurringPaymentStatus.ACTIVE,
    val nextDueDate: LocalDate,
    val lastPaidDate: LocalDate? = null,
    val notes: String? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
