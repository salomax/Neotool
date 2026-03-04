package io.github.salomax.neotool.banking.graphql.dto

import io.github.salomax.neotool.banking.domain.transaction.BillStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreditCardBillDTO(
    val id: UUID,
    val financialAccountId: UUID,
    val closingDate: LocalDate,
    val dueDate: LocalDate,
    val totalAmount: Double,
    val minimumPayment: Double?,
    val status: BillStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)
