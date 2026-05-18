package io.github.salomax.neotool.banking.graphql.recurring

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.RecurringFrequency
import io.github.salomax.neotool.banking.domain.transaction.RecurringPaymentStatus
import io.github.salomax.neotool.banking.domain.transaction.TransactionType
import io.github.salomax.neotool.banking.graphql.merchant.MerchantDTO
import io.github.salomax.neotool.banking.graphql.transaction.TransactionCategoryDTO
import java.time.LocalDate
import java.util.UUID

data class RecurringPaymentDTO(
    val id: UUID,
    val financialAccountId: UUID?,
    val name: String,
    val creditDebitType: CreditDebitType,
    val type: TransactionType?,
    val expectedAmount: Double?,
    val currencyCode: String,
    val frequency: RecurringFrequency,
    val dayOfMonth: Int?,
    val intervalDays: Int?,
    val remindDaysBefore: Int,
    val status: RecurringPaymentStatus,
    val nextDueDate: LocalDate,
    val lastPaidDate: LocalDate?,
    val notes: String?,
    val category: TransactionCategoryDTO?,
    val merchant: MerchantDTO?,
)
