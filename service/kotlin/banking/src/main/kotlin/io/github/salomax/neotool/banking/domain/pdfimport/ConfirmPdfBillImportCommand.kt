package io.github.salomax.neotool.banking.domain.pdfimport

import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ConfirmPdfBillImportCommand(
    val jobId: UUID,
    val cards: List<ConfirmPdfCardCommand>,
)

data class ConfirmPdfCardCommand(
    val lastFourDigits: String,
    val financialAccountId: UUID,
    val transactions: List<ConfirmPdfTransactionCommand>,
)

data class ConfirmPdfTransactionCommand(
    val date: LocalDate,
    val description: String,
    val amount: BigDecimal,
    val creditDebitType: CreditDebitType,
    val include: Boolean,
    val isBillPayment: Boolean = false,
)
