package io.github.salomax.neotool.banking.service.billimport

import io.github.salomax.neotool.banking.domain.billimport.*
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType

internal object BillTransactionEnricher {
    private val INSTALLMENT_REGEX = Regex("""(?i)\(?\b(\d{1,2})\s*/\s*(\d{1,2})\b\)?""")
    private val BILL_PAYMENT_PATTERNS =
        listOf(
            Regex("""(?i)\bpagamento\s+recebido\b"""),
        )

    fun toBillReviewTransaction(row: ParsedTransaction): BillImportReviewTransaction =
        BillImportReviewTransaction(
            date = row.date,
            description = row.description,
            amount = row.amount,
            creditDebitType = row.creditDebitType,
            installment = detectInstallment(row.description, row.descriptionRaw),
            isBillPayment = isBillPayment(row.creditDebitType, row.description, row.descriptionRaw),
        )

    fun detectInstallment(
        description: String?,
        descriptionRaw: String? = null,
    ): InstallmentInfo? {
        val candidate = listOfNotNull(description, descriptionRaw).firstNotNullOfOrNull(::extractInstallment) ?: return null
        return InstallmentInfo(current = candidate.first, total = candidate.second)
    }

    fun isBillPayment(
        creditDebitType: CreditDebitType,
        description: String?,
        descriptionRaw: String? = null,
    ): Boolean {
        if (creditDebitType != CreditDebitType.CREDIT) return false
        val haystacks = listOfNotNull(description, descriptionRaw)
        return BILL_PAYMENT_PATTERNS.any { pattern -> haystacks.any { pattern.containsMatchIn(it) } }
    }

    private fun extractInstallment(text: String): Pair<Int, Int>? {
        val match = INSTALLMENT_REGEX.find(text) ?: return null
        val current = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        if (current < 1 || total < 1 || current > total) return null
        return current to total
    }
}
