package io.github.salomax.neotool.banking.domain.pdfimport

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PdfBillExtractionResult(
    val bankName: String? = null,
    val statementPeriod: DateRange? = null,
    val cards: List<PdfExtractedCard> = emptyList(),
    val extractionConfidence: Double = 0.0,
    val needsReview: Boolean = false,
    val warnings: List<String> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PdfExtractedCard(
    val lastFourDigits: String = "",
    val holderName: String? = null,
    val closingDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val declaredTotal: BigDecimal = BigDecimal.ZERO,
    val computedTotal: BigDecimal = BigDecimal.ZERO,
    val totalMatchesDeclared: Boolean = false,
    val transactions: List<PdfExtractedTransaction> = emptyList(),
    val matchedFinancialAccountId: UUID? = null,
    val matchAmbiguous: Boolean = false,
    val matchCandidates: List<PdfMatchCandidate> = emptyList(),
    val confidence: Double = 0.0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PdfExtractedTransaction(
    val date: LocalDate? = null,
    val description: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val creditDebitType: CreditDebitType = CreditDebitType.DEBIT,
    val installment: InstallmentInfo? = null,
    val confidence: Double = 0.0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class InstallmentInfo(
    val current: Int,
    val total: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class DateRange(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PdfMatchCandidate(
    val id: UUID,
    val name: String,
)
