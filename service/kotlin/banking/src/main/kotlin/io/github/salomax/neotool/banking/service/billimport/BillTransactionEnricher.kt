package io.github.salomax.neotool.banking.service.billimport

import io.github.salomax.neotool.banking.domain.billimport.BillImportReviewTransaction
import io.github.salomax.neotool.banking.domain.billimport.InstallmentInfo
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import java.text.Normalizer

internal object BillTransactionEnricher {
    private val INSTALLMENT_REGEX = Regex("""(?i)\(?\b(\d{1,2})\s*/\s*(\d{1,2})\b\)?""")
    private val BILL_PAYMENT_KEYWORDS =
        listOf(
            "PAGAMENTO DE FATURA",
            "PAGAMENTO FATURA",
            "PAGTO FATURA",
            "PGTO FATURA",
            "PAGAMENTO DE CARTAO",
            "PAGAMENTO CARTAO",
            "PAGAMENTO EM ",
            "PGTO DEBITO CONTA",
            "PAG DEBITO AUTOMATICO",
            "PAGAMENTO VIA PIX",
            "PAGAMENTO APP",
            "PAGAMENTO RECEBIDO",
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
        val candidate =
            listOfNotNull(
                description,
                descriptionRaw,
            ).firstNotNullOfOrNull(::extractInstallment) ?: return null
        return InstallmentInfo(current = candidate.first, total = candidate.second)
    }

    fun matchesBillPaymentDescription(description: String?): Boolean {
        if (description.isNullOrBlank()) return false
        val normalized = normalizeForSearch(description)
        return BILL_PAYMENT_KEYWORDS.any { normalized.contains(it) }
    }

    fun isBillPayment(
        creditDebitType: CreditDebitType,
        description: String?,
        descriptionRaw: String? = null,
    ): Boolean {
        if (creditDebitType != CreditDebitType.CREDIT) return false
        return listOfNotNull(description, descriptionRaw).any(::matchesBillPaymentDescription)
    }

    private fun normalizeForSearch(text: String): String {
        val nfd = Normalizer.normalize(text.uppercase(), Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{M}"), "")
    }

    private fun extractInstallment(text: String): Pair<Int, Int>? {
        val match = INSTALLMENT_REGEX.find(text) ?: return null
        val current = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        if (current < 1 || total < 1 || current > total) return null
        return current to total
    }
}
