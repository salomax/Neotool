package io.github.salomax.neotool.banking.domain.transaction

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Domain model for banking.credit_card_bills (fatura de cartão de crédito).
 *
 * One row per billing cycle per credit card account.
 *
 * [closingDate] is the last day of the billing cycle — derived from [CardData.closingDay].
 * [dueDate]    is the payment due date — derived from [CardData.dueDay] relative to [closingDate].
 * [referenceMonth] is the business bill month (first day of the due month), used for identity and ordering.
 * [totalAmount] = SUM(DEBIT) - SUM(CREDIT) across non-deleted transactions for this bill.
 *                 Recomputed synchronously on every transaction mutation.
 */
data class CreditCardBill(
    val id: UUID? = null,
    val financialAccountId: UUID,
    val accountId: UUID,
    val closingDate: LocalDate,
    val dueDate: LocalDate,
    val referenceMonth: LocalDate,
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val minimumPayment: BigDecimal? = null,
    val status: BillStatus,
    val providerBillId: String? = null,
    val deletedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
