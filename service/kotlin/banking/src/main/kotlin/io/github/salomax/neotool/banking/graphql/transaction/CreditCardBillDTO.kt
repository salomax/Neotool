package io.github.salomax.neotool.banking.graphql.transaction

import io.github.salomax.neotool.banking.domain.transaction.BillStatus
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class CreditCardBillDTO(
    val id: UUID,
    val financialAccountId: UUID,
    val closingDate: LocalDate,
    val dueDate: LocalDate,
    val referenceMonth: LocalDate,
    val totalAmount: Double,
    val minimumPayment: Double?,
    val status: BillStatus,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
