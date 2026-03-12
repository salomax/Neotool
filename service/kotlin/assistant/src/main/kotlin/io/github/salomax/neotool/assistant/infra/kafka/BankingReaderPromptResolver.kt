package io.github.salomax.neotool.assistant.infra.kafka

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class BankingReaderPromptResolver(
    private val config: BankingReaderPromptResolverConfig,
) {
    fun resolve(request: AssistantBankingReaderRequestedEvent): BankingReaderPromptSelection {
        val tupleKey =
            "${request.document_type.trim().uppercase()}|" +
                "${request.account_type.trim().uppercase()}|" +
                "${request.file_type.trim().uppercase()}"

        return when (tupleKey) {
            CREDIT_CARD_BILL_TUPLE ->
                BankingReaderPromptSelection(
                    promptId = parsePromptId(config.creditCardBillPromptId),
                    fallbackSystemPrompt = CREDIT_CARD_BILL_SYSTEM_PROMPT,
                    purpose = "banking_pdf_reader_credit_card_bill",
                    readerId = "assistant-banking-pdf-reader-v1",
                )
            BANK_STATEMENT_PDF_TUPLE,
            BANK_STATEMENT_XLSX_TUPLE,
            BANK_STATEMENT_XLS_TUPLE,
            ->
                BankingReaderPromptSelection(
                    promptId = parsePromptId(config.bankStatementPromptId),
                    fallbackSystemPrompt = BANK_STATEMENT_SYSTEM_PROMPT,
                    purpose = "banking_reader_bank_statement",
                    readerId = "assistant-banking-reader-v1",
                )
            else ->
                throw BankingReaderProcessingException(
                    code = "PROMPT_RESOLUTION_ERROR",
                    message = "No prompt mapping for tuple $tupleKey",
                    retryable = false,
                    stage = "PROMPT_SELECTION",
                )
        }
    }

    private fun parsePromptId(value: String?): UUID? {
        if (value.isNullOrBlank()) {
            return null
        }
        return runCatching { UUID.fromString(value.trim()) }.getOrElse {
            throw BankingReaderProcessingException(
                code = "PROMPT_RESOLUTION_ERROR",
                message = "Invalid prompt-id configuration: ${it.message}",
                retryable = false,
                stage = "PROMPT_SELECTION",
                cause = it,
            )
        }
    }

    companion object {
        private const val CREDIT_CARD_BILL_TUPLE = "CREDIT_CARD_BILL|CREDIT|PDF"
        private const val BANK_STATEMENT_PDF_TUPLE = "BANK_STATEMENT|BANK|PDF"
        private const val BANK_STATEMENT_XLSX_TUPLE = "BANK_STATEMENT|BANK|XLSX"
        private const val BANK_STATEMENT_XLS_TUPLE = "BANK_STATEMENT|BANK|XLS"

        private const val CREDIT_CARD_BILL_SYSTEM_PROMPT =
            """
            You are a banking document parser.
            Read the provided extracted statement text and return ONLY valid JSON.
            JSON object must contain these fields:
            - bank_name (string or null)
            - statement_period (object with from/to in YYYY-MM-DD)
            - cards (array of card objects)
            - extraction_confidence (number 0..1)
            - needs_review (boolean)
            - warnings (array of strings)
            Each card object must include:
            - last_four_digits (4 digits)
            - closing_date (YYYY-MM-DD)
            - due_date (YYYY-MM-DD)
            - declared_total (number)
            - computed_total (number)
            - total_matches_declared (boolean)
            - transactions (array)
            - confidence (number 0..1)
            Each transaction line in the statement may contain a card identifier inline (e.g. "•••• 5048 Dm*Godaddycoml").
            The 4-digit suffix after the bullets (e.g. "5048") identifies which card that transaction belongs to.
            Group each transaction under the card whose last_four_digits matches that suffix.
            A transaction must appear in exactly one card's transactions array — never duplicate across cards.
            Each transaction must include:
            - date (YYYY-MM-DD)
            - description (non-empty string)
            - amount (number, always positive; direction is represented by credit_debit_type)
            - credit_debit_type (DEBIT or CREDIT)
            - confidence (number 0..1)
            Optional transaction fields:
            - installment: present when the transaction is a recurring installment purchase (e.g. "Godaddy 03/12" or "PARC 3/12").
              Object with:
              - current: the installment sequence number (e.g. 3 for "3/12")
              - total: total number of installments (e.g. 12 for "3/12")
              Both must be integers >= 1. Omit or set to null for non-installment transactions.
              The amount field must contain only the single installment amount, not the full purchase total.
            - is_bill_payment (boolean)
            Hard rules for is_bill_payment (MUST follow):
            - Set is_bill_payment = true when description indicates paying a previous credit card bill balance.
            - Use case-insensitive and accent-insensitive matching for Portuguese variants such as:
              "PAGAMENTO DE FATURA", "PAGAMENTO FATURA", "PAGTO FATURA", "PGTO FATURA",
              "PAGAMENTO DE CARTAO", "PAGAMENTO CARTAO", "PAGAMENTO EM DD MMM".
            - Example: "PAGAMENTO DE FATURA ..." MUST be is_bill_payment = true.
            - If is_bill_payment = true, credit_debit_type MUST be CREDIT (bill payment reduces card balance).
            - Do not mark regular purchases, estorno/chargeback, cashback, or points redemption as bill payment
              unless the text explicitly indicates bill payment.
            Confidence rubric (avoid arbitrary scores):
            - Use only these values: 0.55, 0.75, 0.90, 0.98.
            - 0.98: all required fields extracted directly and unambiguously from the text.
            - 0.90: clear extraction with minor normalization (abbreviation/casing cleanup) and no ambiguity.
            - 0.75: partially inferred field(s) or mild ambiguity (e.g. date or card association not fully explicit).
            - 0.55: weak evidence or significant ambiguity; add a warning and set needs_review=true.
            - extraction_confidence should summarize the overall reliability and must never exceed
              the highest uncertainty present in cards/transactions.
            Confidence rules for is_bill_payment specifically:
            - 0.98: description contains a known exact pattern ("PAGAMENTO DE FATURA", "PAGTO FATURA", "PGTO FATURA",
              "PAGAMENTO DE CARTAO", "PAGAMENTO CARTAO", "PAGAMENTO EM DD MMM") — is_bill_payment = true.
            - 0.75: description contains "PAGAMENTO" but without "FATURA" or "CARTAO" — is_bill_payment = true but
              add a warning such as "Ambiguous bill payment: matched PAGAMENTO without FATURA/CARTAO".
            - 0.55: description is ambiguous and does not clearly indicate bill payment — is_bill_payment = false,
              add a warning, and set needs_review = true.
            """

        private const val BANK_STATEMENT_SYSTEM_PROMPT =
            """
            You are a banking document parser.
            Read the provided extracted statement text and return ONLY valid JSON in the banking reader contract.
            Required top-level fields:
            - bank_name (string or null)
            - statement_period (object with from/to)
            - transactions (array)
            - cards (array; for bank statements this is usually empty [])
            - extraction_confidence (number 0..1)
            - needs_review (boolean)
            - warnings (array of strings)
            Each transaction in top-level transactions must include:
            - date (YYYY-MM-DD)
            - description (non-empty string)
            - amount (number, always positive)
            - credit_debit_type (DEBIT or CREDIT)
            - confidence (number 0..1)
            Optional transaction fields:
            - transaction_datetime (ISO-8601 datetime)
            - provider_transaction_id (string)
            - description_raw (string)
            - counterparty_name (string)
            - counterparty_document (string)
            - currency_code (string, default BRL)
            - installment (object with current/total when applicable)
            Date rules:
            - statement_period.from and statement_period.to must be YYYY-MM-DD when known, or null when unknown.
            - If source dates are DD/MM/YYYY, normalize them to YYYY-MM-DD.
            - For transaction dates, also normalize to YYYY-MM-DD.
            Extraction rule:
            - Parse every transaction row from the statement into top-level transactions.
            - Do not return an empty transactions array when transaction rows are present in the source text.
            Keep unknown values as null when needed, never omit required fields.
            """
    }
}

@ConfigurationProperties("assistant.banking-reader.prompt-selection")
class BankingReaderPromptResolverConfig {
    var creditCardBillPromptId: String? = null
    var bankStatementPromptId: String? = null
}

data class BankingReaderPromptSelection(
    val promptId: UUID?,
    val fallbackSystemPrompt: String,
    val purpose: String,
    val readerId: String,
)
