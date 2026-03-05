package io.github.salomax.neotool.banking.graphql.dto

import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportJobStatus
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PdfImportJobDTO(
    val id: UUID,
    val status: PdfImportJobStatus,
    val fileName: String?,
    val createdAt: Instant,
    val reviewPayload: PdfBillExtractionResultDTO?,
    val errorMessage: String?,
)

data class PdfBillExtractionResultDTO(
    val bankName: String?,
    val statementPeriod: DateRangeDTO?,
    val cards: List<PdfExtractedCardDTO>,
    val extractionConfidence: Double,
    val needsReview: Boolean,
    val warnings: List<String>,
)

data class DateRangeDTO(
    val from: LocalDate?,
    val to: LocalDate?,
)

data class PdfExtractedCardDTO(
    val lastFourDigits: String,
    val holderName: String?,
    val closingDate: LocalDate?,
    val dueDate: LocalDate?,
    val declaredTotal: Double,
    val computedTotal: Double,
    val totalMatchesDeclared: Boolean,
    val transactions: List<PdfExtractedTransactionDTO>,
    val matchedFinancialAccountId: UUID?,
    val matchAmbiguous: Boolean,
    val matchCandidates: List<PdfMatchCandidateDTO>,
    val confidence: Double,
)

data class PdfMatchCandidateDTO(
    val id: UUID,
    val name: String,
)

data class PdfExtractedTransactionDTO(
    val date: LocalDate?,
    val description: String,
    val amount: Double,
    val creditDebitType: CreditDebitType,
    val installment: InstallmentInfoDTO?,
    val confidence: Double,
)

data class InstallmentInfoDTO(
    val current: Int,
    val total: Int,
)
