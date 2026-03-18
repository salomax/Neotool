package io.github.salomax.neotool.banking.domain.billimport

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate

@Singleton
class AssistantBillReviewBuilder(
    private val objectMapper: ObjectMapper,
    private val metaValidator: AssistantBillMetaValidator,
    private val transactionExtractor: AssistantBillTransactionExtractor,
) {
    fun build(
        extractedText: String?,
        assistantOutput: Any?,
    ): BillImportReview? {
        if (extractedText.isNullOrBlank() || assistantOutput == null) return null

        val meta =
            runCatching { objectMapper.convertValue(assistantOutput, AssistantBillExtractionMeta::class.java) }
                .getOrNull()
                ?: return null

        val errors = metaValidator.validate(meta)
        if (errors.isNotEmpty()) {
            return null
        }

        val extractionResult = transactionExtractor.extract(extractedText, meta)
        val reviewCards =
            extractionResult.cards.mapIndexed { index, card ->
                BillImportReviewCard(
                    reviewCardId = card.lastFour.takeIf { it.isNotBlank() } ?: "card-${index + 1}",
                    lastFourDigits = card.lastFour.takeIf { it.isNotBlank() },
                    closingDate = LocalDate.parse(meta.closingDate),
                    dueDate = LocalDate.parse(meta.dueDate),
                    declaredTotal = card.declaredTotal?.toBigDecimalExact() ?: BigDecimal.ZERO,
                    computedTotal = card.computedTotal.toBigDecimalExact(),
                    totalMatchesDeclared = card.totalMatchesDeclared,
                    transactions = card.transactions.map { it.toReviewTransaction() },
                )
            }

        val reviewMode =
            if (meta.transactionPattern.lastFourPattern != null && reviewCards.none { !it.lastFourDigits.isNullOrBlank() }) {
                BillImportReviewMode.TX_CARD_ASSIGNMENT
            } else {
                BillImportReviewMode.CARD_BUCKET_MATCHING
            }

        return BillImportReview(
            bankName = meta.bankName,
            reviewMode = reviewMode,
            cards = reviewCards,
        )
    }

    private fun AssistantExtractedTransaction.toReviewTransaction(): BillImportReviewTransaction =
        BillImportReviewTransaction(
            date = date,
            description = description,
            amount = amount.toBigDecimalExact(),
            creditDebitType =
                if (creditDebitType == "CREDIT") {
                    CreditDebitType.CREDIT
                } else {
                    CreditDebitType.DEBIT
                },
            installment = installment?.let { InstallmentInfo(current = it.current, total = it.total) },
            isBillPayment = isBillPayment,
        )

    private fun Double.toBigDecimalExact(): BigDecimal = BigDecimal.valueOf(this)
}
