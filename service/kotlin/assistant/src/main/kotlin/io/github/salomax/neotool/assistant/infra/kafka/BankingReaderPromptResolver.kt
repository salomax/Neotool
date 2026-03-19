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
            CREDIT_CARD_BILL_PDF_TUPLE,
            CREDIT_CARD_BILL_XLSX_TUPLE,
            CREDIT_CARD_BILL_XLS_TUPLE,
            ->
                BankingReaderPromptSelection(
                    promptId = parsePromptId(config.creditCardBillPromptId),
                    fallbackSystemPrompt = CREDIT_CARD_BILL_META_SYSTEM_PROMPT,
                    purpose = "banking_bill_reader_credit_card_bill",
                    readerId = "assistant-banking-bill-reader-v2",
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
        private const val CREDIT_CARD_BILL_PDF_TUPLE = "CREDIT_CARD_BILL|CREDIT|PDF"
        private const val CREDIT_CARD_BILL_XLSX_TUPLE = "CREDIT_CARD_BILL|CREDIT|XLSX"
        private const val CREDIT_CARD_BILL_XLS_TUPLE = "CREDIT_CARD_BILL|CREDIT|XLS"
        private const val BANK_STATEMENT_PDF_TUPLE = "BANK_STATEMENT|BANK|PDF"
        private const val BANK_STATEMENT_XLSX_TUPLE = "BANK_STATEMENT|BANK|XLSX"
        private const val BANK_STATEMENT_XLS_TUPLE = "BANK_STATEMENT|BANK|XLS"

        private const val CREDIT_CARD_BILL_META_SYSTEM_PROMPT =
            """
            You are a banking document parser.
            This document is a credit card statement.
            Read the provided extracted text and return ONLY valid JSON describing the document structure.
            Do NOT extract individual transactions — return only metadata and the boundaries of the transaction section(s).
            Return a JSON object with exactly these fields:
            - bank_name (string or null)
            - closing_date (YYYY-MM-DD): the statement closing date
            - due_date (YYYY-MM-DD): the payment due date
            - declared_total (number or null): the overall statement total (e.g. "Total a Pagar", "Total da Fatura"). null if not found — never guess.
            - card_blocks (array): one entry per card only when the document is split into per-card sections identified by section headers.
              Leave this array empty when card IDs appear inline inside transaction rows and there are no per-card sections.
              Each entry:
              - last_four (string or null): the 4-digit card suffix extracted from the section header (e.g. "9745", "5048").
                Set to null only when last_four_pattern is set in transaction_pattern (meaning card ID appears inline per transaction row).
              - declared_total (number or null): the card-level total if present; null otherwise
              - anchor_start: a literal substring from the document that marks the START of this card's transaction list.
                Must be the section header line that contains the card number and appears immediately before the first transaction row.
                CRITICAL: do NOT use page headers that repeat across pages (e.g. lines containing the cardholder full name + billing month + card number). Those are page headers, not section headers. A true section header is followed directly by transaction rows.
            - transaction_pattern (object): structured description of the transaction format — the extractor builds the regex from this.
              Fields:
              - date_format (string): how dates appear in transaction rows.
                "DD_MMM" for "08 FEV", "05 Nov", "21 Out" (day + 3-letter month abbreviation).
                "DD_MM" for "08/02", "05/02" (day/month with slash).
                "DD_MM_YYYY" for "08/02/2026" (day/month/year with slash).
              - last_four_pattern (string or null): a regex with ONE capturing group that extracts the 4-digit card suffix
                embedded WITHIN each transaction row.
                Set to null when each card has its own dedicated section (most common case — do NOT set this).
                Set ONLY when card numbers appear inline inside transaction rows and the extractor must scan the full document.
                Example: rows like "08 FEV •••• 5048 Godaddy R$ 64,99" → use "[^\\w\\s]+\\s*(\\d{4})" to capture "5048".
                Example: rows like "08 FEV ****1234 Godaddy R$ 64,99" → use "\\*{4}(\\d{4})" to capture "1234".
                IMPORTANT: when last_four_pattern is set and the document does NOT have dedicated per-card sections,
                card_blocks must be [].
                The extractor groups transactions by the extracted card number automatically — do NOT create one
                card_blocks entry per card and do NOT use transaction rows as anchor_start.
                Some rows may omit the card number (for example bill payments, IOF, taxes, or adjustments). That is valid.
                Those rows must still be parsed from the full document and will remain without a card suffix.
              - currency_symbols (array of strings): ALL distinct currency symbols present in transaction amounts throughout the document.
                Scan the entire document — foreign transactions (travel, streaming, international purchases) may use US$, USD, €, EUR, £, GBP, etc.
                Include every symbol found: e.g. ["R$"] for BRL-only, ["R$", "US$"] when USD transactions are present.
                IMPORTANT: never return an empty array; never omit non-BRL symbols even if there are only a few foreign transactions.
              - decimal_separator (string): "comma" for pt-BR format (R$ 49,40); "period" for US format ($49.40).
              - installment_pattern (string or null): a regex with two capturing groups — (1) current installment, (2) total —
                when installment annotations appear in descriptions (e.g. "(4/7)", "03/12").
                Example: "(\\d{1,2})/(\\d{1,2})" matches "4/7" or "03/12".
                Set to null if no installment patterns are present.
            Critical rules for anchor_start:
            - It MUST be a literal substring that exists exactly as written in the document text provided.
            - anchor_start is the section header line for that card — the line that contains the card number and appears immediately before the first transaction row of that card's section.
            - The extractor automatically uses the next card's anchor_start as the end boundary, so you only need to identify where each card's section STARTS.
            - Do NOT invent anchors. Only use text that literally appears in the document.
            - Avoid section headers from summary pages or table of contents — only use the header from the actual transaction section.
            - When a PDF renders tables with pipe characters (e.g. "| 08 FEV | Mercado Livre | R$ 49,40 |"), the input may be normalized
              into plain text rows before extraction. Prefer the section header line that identifies the card block, such as
              "Orival Onishi (Cartão 1295)", instead of transaction rows or generic table headers like "Data Descrição País Valor".
            - If the card suffix appears inline in transaction rows such as "15 SET •••• 4416 ...", this is a full-document parsing case.
              Do NOT treat those transaction rows as card section anchors. Set card_blocks to [] and rely on last_four_pattern.
            """

        private const val BANK_STATEMENT_SYSTEM_PROMPT =
            """
            You are a banking document parser.
            This document is a bank statement.
            Read the provided extracted statement text and return ONLY valid JSON with parsing metadata.
            Do NOT extract final transactions here. Your job is to describe how banking should parse them deterministically.
            Required top-level fields:
            - bank_name (string or null)
            - statement_period (object with from/to)
            - parser_config (object)
            - warnings (array of strings)
            parser_config fields:
            - layout_type: DATE_GROUPED_MULTILINE or INLINE_ROWS
            - date_format: DD_MMM_YYYY, DD_MM_YYYY, or DD_MM
            - date_header_pattern (regex with ONE capturing group for the date text) when layout_type = DATE_GROUPED_MULTILINE; null otherwise
            - inline_transaction_date_pattern (regex with ONE capturing group for the date text) when layout_type = INLINE_ROWS; null otherwise
            - amount_pattern (regex with ONE capturing group for the amount text)
            - decimal_separator: comma or period
            - credit_patterns (array of regex strings): line/block patterns that imply CREDIT
            - debit_patterns (array of regex strings): line/block patterns that imply DEBIT
            - skip_line_patterns (array of regex strings): totals, balances, section headers, and any non-transaction lines the parser must ignore
            - default_credit_debit_type (DEBIT or CREDIT or null): fallback only when no pattern and no sign can infer it
            Date rules:
            - statement_period.from and statement_period.to must be YYYY-MM-DD when known, or null when unknown.
            - If source dates are DD/MM/YYYY, normalize them to YYYY-MM-DD.
            Metadata rules:
            - A single date heading may apply to multiple consecutive transaction blocks until the next date heading appears.
              Example: after a line like "04 JAN 2025", banking must parse every following transaction block as 2025-01-04 until the next date heading.
            - Treat each amount-bearing transaction block as a separate transaction, even when the block spans multiple lines
              with bank, branch, account, or continuation lines below it.
            - Do not merge adjacent PIX/TED/transfer transactions that share the same day.
              Different amounts or counterparties on the same date are different transaction blocks.
            - Summary lines such as "Total de saídas", "Total de entradas", saldo lines, or day totals are not transactions and must appear in skip_line_patterns.
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
