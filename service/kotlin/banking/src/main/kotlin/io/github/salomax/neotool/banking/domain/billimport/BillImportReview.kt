package io.github.salomax.neotool.banking.domain.billimport

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.CategorySource
import java.util.UUID
import java.math.BigDecimal
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
enum class BillImportReviewMode {
    CARD_BUCKET_MATCHING,
    TX_CARD_ASSIGNMENT,
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BillImportReview(
    val bankName: String? = null,
    val reviewMode: BillImportReviewMode = BillImportReviewMode.CARD_BUCKET_MATCHING,
    val statementPeriod: DateRange? = null,
    val cards: List<BillImportReviewCard> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BillImportReviewCard(
    val reviewCardId: String = "",
    val lastFourDigits: String? = null,
    val holderName: String? = null,
    val closingDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val declaredTotal: BigDecimal = BigDecimal.ZERO,
    val computedTotal: BigDecimal = BigDecimal.ZERO,
    val totalMatchesDeclared: Boolean = false,
    val transactions: List<BillImportReviewTransaction> = emptyList(),
    val matchedFinancialAccountId: UUID? = null,
    val matchAmbiguous: Boolean = false,
    val matchCandidates: List<BillImportMatchCandidate> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BillImportReviewTransaction(
    val date: LocalDate? = null,
    val description: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val creditDebitType: CreditDebitType = CreditDebitType.DEBIT,
    val installment: InstallmentInfo? = null,
    val isBillPayment: Boolean = false,
    val categoryId: UUID? = null,
    val categorySource: CategorySource? = null,
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
data class BillImportMatchCandidate(
    val id: UUID,
    val name: String,
)
