package io.github.salomax.neotool.banking.model.dto.billimport

import io.github.salomax.neotool.banking.domain.billimport.InstallmentInfo
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ConfirmBillImportCommand(
    val jobId: UUID,
    val closingDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val cards: List<ConfirmBillReviewCardCommand>,
)

data class ConfirmBillReviewCardCommand(
    val reviewCardId: String,
    val financialAccountId: UUID,
    val transactions: List<ConfirmBillReviewTransactionCommand>,
)

data class ConfirmBillReviewTransactionCommand(
    val date: LocalDate,
    val description: String,
    val amount: BigDecimal,
    val creditDebitType: CreditDebitType,
    val include: Boolean,
    val isBillPayment: Boolean = false,
    val installment: InstallmentInfo? = null,
    val categoryId: UUID? = null,
    val categorySource: CategorySource? = null,
)
