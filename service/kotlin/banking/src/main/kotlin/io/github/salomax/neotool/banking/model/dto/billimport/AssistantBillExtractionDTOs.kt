package io.github.salomax.neotool.banking.model.dto.billimport

import java.time.LocalDate

data class AssistantBillInstallment(val current: Int, val total: Int)

data class AssistantExtractedTransaction(
    val date: LocalDate,
    val description: String,
    val amount: Double,
    /** ISO 4217 currency code inferred from the amount symbol: "BRL", "USD", "EUR", "GBP". */
    val currencyCode: String,
    val creditDebitType: String,
    val cardLastFour: String,
    val installment: AssistantBillInstallment?,
    val isBillPayment: Boolean,
)

data class AssistantExtractedCard(
    val lastFour: String,
    val declaredTotal: Double?,
    val computedTotal: Double,
    val totalMatchesDeclared: Boolean,
    val transactions: List<AssistantExtractedTransaction>,
)

data class AssistantBillExtractionResult(
    val cards: List<AssistantExtractedCard>,
    val warnings: List<String>,
)
