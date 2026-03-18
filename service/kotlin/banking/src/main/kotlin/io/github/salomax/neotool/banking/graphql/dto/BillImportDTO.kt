package io.github.salomax.neotool.banking.graphql.dto

import io.github.salomax.neotool.banking.domain.billimport.BillImportJobStatus
import io.github.salomax.neotool.banking.domain.billimport.BillImportReviewMode
import io.github.salomax.neotool.banking.domain.billimport.ImportDocumentType
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class BillImportJobDTO(
    val id: UUID,
    val importType: ImportDocumentType,
    val status: BillImportJobStatus,
    val fileName: String?,
    val createdAt: OffsetDateTime,
    val reviewPayload: BillImportReviewDTO?,
    val errorMessage: String?,
    val errorCode: String?,
    val errorParameters: Map<String, Any?>?,
)

data class BillImportReviewDTO(
    val bankName: String?,
    val reviewMode: BillImportReviewMode,
    val statementPeriod: DateRangeDTO?,
    val cards: List<BillImportReviewCardDTO>,
)

data class DateRangeDTO(
    val from: LocalDate?,
    val to: LocalDate?,
)

data class BillImportReviewCardDTO(
    val reviewCardId: String,
    val lastFourDigits: String?,
    val holderName: String?,
    val closingDate: LocalDate?,
    val dueDate: LocalDate?,
    val declaredTotal: Double,
    val computedTotal: Double,
    val totalMatchesDeclared: Boolean,
    val transactions: List<BillImportReviewTransactionDTO>,
    val matchedFinancialAccountId: UUID?,
    val matchAmbiguous: Boolean,
    val matchCandidates: List<BillImportMatchCandidateDTO>,
)

data class BillImportMatchCandidateDTO(
    val id: UUID,
    val name: String,
)

data class BillImportReviewTransactionDTO(
    val date: LocalDate?,
    val description: String,
    val amount: Double,
    val creditDebitType: CreditDebitType,
    val installment: InstallmentInfoDTO?,
    val isBillPayment: Boolean,
)

data class InstallmentInfoDTO(
    val current: Int,
    val total: Int,
)
