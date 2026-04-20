package io.github.salomax.neotool.banking.service.import.parser

import io.github.salomax.neotool.banking.domain.import.*
import io.github.salomax.neotool.banking.model.domain.imports.ImportTextDecoder
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.service.import.parser.csv.BankCsvReader
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvAmountPattern
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvDatePattern
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvFingerprint
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvParserSupport
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvReaderMatch
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType

/**
 * Reader for Banco do Brasil account statement CSV layout.
 */
object CsvBancoDoBrasilParser : BankCsvReader {
    private val DATE_HEADERS = setOf("data")
    private val LAUNCH_HEADERS = setOf("lancamento", "tipo_lancamento")
    private val DETAILS_HEADERS = setOf("detalhes", "historico", "descricao")
    private val AMOUNT_HEADERS = setOf("valor", "valor_rs")
    private val DOC_HEADERS = setOf("n_documento", "numero_documento", "nro_documento", "documento")
    private val TYPE_HEADERS = setOf("tipo_lancamento")
    private val BB_HINT_HEADERS = setOf("data", "lancamento", "detalhes", "n_documento", "valor", "tipo_lancamento")

    override fun id(): String = "csv-banco-do-brasil"

    override fun supports(fingerprint: CsvFingerprint): CsvReaderMatch {
        var score = 0.0
        val reasons = mutableListOf<String>()
        val columns = fingerprint.normalizedColumns

        val hasDate = columns.anyMatches(DATE_HEADERS)
        val hasLaunch = columns.anyMatches(LAUNCH_HEADERS)
        val hasDetails = columns.anyMatches(DETAILS_HEADERS)
        val hasAmount = columns.anyMatches(AMOUNT_HEADERS)
        val hasDoc = columns.anyMatches(DOC_HEADERS)
        val hasType = columns.anyMatches(TYPE_HEADERS)
        val hintMatches = columns.count { col -> BB_HINT_HEADERS.any { hint -> col == hint || col.contains(hint) } }

        if (fingerprint.hasHeader) {
            score += 0.05
            reasons += "has_header"
        }
        if (fingerprint.delimiter == ',') {
            score += 0.05
            reasons += "delimiter_comma"
        }

        if (hasDate && hasAmount && (hasLaunch || hasDetails)) {
            score += 0.55
            reasons += "core_columns_match"
        }
        if (hasLaunch && hasDetails) {
            score += 0.15
            reasons += "launch_and_details_columns"
        } else if (hasLaunch || hasDetails) {
            score += 0.08
            reasons += "launch_or_details_column"
        }
        if (hasDoc) {
            score += 0.06
            reasons += "document_column_present"
        }
        if (hasType) {
            score += 0.08
            reasons += "type_column_present"
        }
        if (hintMatches >= 4) {
            score += 0.08
            reasons += "bb_header_hints"
        }
        if (
            fingerprint.datePatterns.contains(CsvDatePattern.BR_DD_MM_YYYY_SLASH) ||
            fingerprint.datePatterns.contains(CsvDatePattern.BR_DD_MM_YYYY_DASH)
        ) {
            score += 0.02
            reasons += "br_date_pattern"
        }
        if (
            fingerprint.amountPatterns.contains(CsvAmountPattern.COMMA_DECIMAL) ||
            fingerprint.amountPatterns.contains(CsvAmountPattern.THOUSANDS_DOT_DECIMAL_COMMA)
        ) {
            score += 0.01
            reasons += "br_amount_pattern"
        }

        return CsvReaderMatch(
            readerId = id(),
            score = score.coerceIn(0.0, 1.0),
            reasons = reasons.distinct(),
        )
    }

    override fun parse(bytes: ByteArray): List<ParsedTransaction> {
        val text = ImportTextDecoder.decode(bytes).text
        val lines =
            text.lineSequence()
                .map { it.trimEnd('\r') }
                .filter { it.isNotBlank() }
                .toList()
        if (lines.size < 2) return emptyList()

        val delimiter = CsvParserSupport.detectDelimiter(lines.first())
        val header = CsvParserSupport.parseCsvLine(lines.first(), delimiter).map(CsvParserSupport::normalizeColumnName)
        if (header.isEmpty()) return emptyList()

        val dateIdx = header.indexOfFirstHeader(DATE_HEADERS)
        val launchIdx = header.indexOfFirstHeader(LAUNCH_HEADERS)
        val detailsIdx = header.indexOfFirstHeader(DETAILS_HEADERS)
        val amountIdx = header.indexOfFirstHeader(AMOUNT_HEADERS)

        if (dateIdx == null || amountIdx == null || (launchIdx == null && detailsIdx == null)) {
            return emptyList()
        }

        return lines.drop(1).mapNotNull { line ->
            val row = CsvParserSupport.parseCsvLine(line, delimiter)
            val date = CsvParserSupport.parseDate(row.getOrNull(dateIdx).orEmpty()) ?: return@mapNotNull null
            val amount = CsvParserSupport.parseAmount(row.getOrNull(amountIdx).orEmpty()) ?: return@mapNotNull null
            if (amount.signum() == 0) return@mapNotNull null

            val launch = launchIdx?.let { row.getOrNull(it)?.trim().orEmpty() }.orEmpty()
            val details = detailsIdx?.let { row.getOrNull(it)?.trim().orEmpty() }.orEmpty()
            val description = composeDescription(launch, details).ifBlank { "Imported transaction" }

            ParsedTransaction(
                providerTransactionId = null,
                creditDebitType = if (amount.signum() < 0) CreditDebitType.DEBIT else CreditDebitType.CREDIT,
                amount = amount.abs(),
                date = date,
                transactionDatetime = null,
                description = description,
                descriptionRaw = null,
                counterpartyName = null,
                counterpartyDocument = null,
                currencyCode = "BRL",
                totalInstallments = null,
            )
        }
    }

    private fun composeDescription(
        launch: String,
        details: String,
    ): String {
        if (details.isBlank()) return launch
        if (launch.isBlank()) return details
        if (details.equals(launch, ignoreCase = true)) return details
        if (details.contains(launch, ignoreCase = true)) return details
        return "$launch - $details"
    }

    private fun List<String>.anyMatches(candidates: Set<String>): Boolean =
        any { column -> candidates.any { candidate -> column == candidate || column.contains(candidate) } }

    private fun List<String>.indexOfFirstHeader(candidates: Set<String>): Int? =
        indexOfFirst { header -> candidates.any { candidate -> header == candidate || header.contains(candidate) } }
            .takeIf { it >= 0 }
}
