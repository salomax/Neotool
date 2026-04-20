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
import java.math.BigDecimal

/**
 * Reader for common Itaú account CSV layouts.
 */
object CsvItauParser : BankCsvReader {
    private val DATE_HEADERS = setOf("data", "data_lancamento", "dt_lancamento", "data_transacao", "lancamento")
    private val DESCRIPTION_HEADERS = setOf("historico", "descricao", "descricao_lancamento", "detalhe", "memo")
    private val AMOUNT_HEADERS = setOf("valor", "valor_lancamento", "valor_rs", "amount")
    private val DEBIT_HEADERS = setOf("debito", "valor_debito", "saidas", "saida")
    private val CREDIT_HEADERS = setOf("credito", "valor_credito", "entradas", "entrada")
    private val ID_HEADERS = setOf("documento", "doc", "identificador", "codigo", "id")
    private val ITAU_HINT_HEADERS = setOf("historico", "lancamento", "agencia", "conta", "saldo")

    override fun id(): String = "csv-itau"

    override fun supports(fingerprint: CsvFingerprint): CsvReaderMatch {
        var score = 0.0
        val reasons = mutableListOf<String>()
        val columns = fingerprint.normalizedColumns
        val hasDate = columns.anyMatches(DATE_HEADERS)
        val hasDescription = columns.anyMatches(DESCRIPTION_HEADERS)
        val hasAmount = columns.anyMatches(AMOUNT_HEADERS)
        val hasDebitCredit = columns.anyMatches(DEBIT_HEADERS) && columns.anyMatches(CREDIT_HEADERS)
        val hasItauHints = columns.anyMatches(ITAU_HINT_HEADERS)

        if (fingerprint.hasHeader) {
            score += 0.05
            reasons += "has_header"
        }

        if (fingerprint.delimiter == ';') {
            score += 0.05
            reasons += "delimiter_semicolon"
        }

        if (hasDate && hasDescription && (hasAmount || hasDebitCredit)) {
            score += 0.60
            reasons += "core_columns_match"
        }

        if (hasItauHints) {
            score += 0.20
            reasons += "itau_header_hints"
        }

        if (
            fingerprint.datePatterns.contains(CsvDatePattern.BR_DD_MM_YYYY_SLASH) ||
            fingerprint.datePatterns.contains(CsvDatePattern.BR_DD_MM_YYYY_DASH)
        ) {
            score += 0.05
            reasons += "br_date_pattern"
        }

        if (
            fingerprint.amountPatterns.contains(CsvAmountPattern.COMMA_DECIMAL) ||
            fingerprint.amountPatterns.contains(CsvAmountPattern.THOUSANDS_DOT_DECIMAL_COMMA) ||
            fingerprint.amountPatterns.contains(CsvAmountPattern.DOT_DECIMAL)
        ) {
            score += 0.03
            reasons += "amount_pattern_match"
        }

        if (fingerprint.descriptionTokens.intersect(setOf("pix", "ted", "doc", "pagamento")).isNotEmpty()) {
            score += 0.02
            reasons += "description_token_match"
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
        val descriptionIdx = header.indexOfFirstHeader(DESCRIPTION_HEADERS)
        val amountIdx = header.indexOfFirstHeader(AMOUNT_HEADERS)
        val debitIdx = header.indexOfFirstHeader(DEBIT_HEADERS)
        val creditIdx = header.indexOfFirstHeader(CREDIT_HEADERS)
        val idIdx = header.indexOfFirstHeader(ID_HEADERS)

        if (dateIdx == null || descriptionIdx == null || (amountIdx == null && debitIdx == null && creditIdx == null)) {
            return emptyList()
        }

        return lines.drop(1).mapNotNull { line ->
            val row = CsvParserSupport.parseCsvLine(line, delimiter)
            val date = CsvParserSupport.parseDate(row.getOrNull(dateIdx).orEmpty()) ?: return@mapNotNull null
            val amount = resolveAmount(row, amountIdx, debitIdx, creditIdx) ?: return@mapNotNull null
            if (amount.signum() == 0) return@mapNotNull null

            val description = row.getOrNull(descriptionIdx)?.trim().orEmpty().ifBlank { "Imported transaction" }
            val providerId = idIdx?.let { row.getOrNull(it)?.trim()?.takeIf(String::isNotBlank) }

            ParsedTransaction(
                providerTransactionId = providerId,
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

    private fun resolveAmount(
        row: List<String>,
        amountIdx: Int?,
        debitIdx: Int?,
        creditIdx: Int?,
    ): BigDecimal? {
        amountIdx?.let { idx ->
            CsvParserSupport.parseAmount(row.getOrNull(idx).orEmpty())?.let { return it }
        }

        val debit =
            debitIdx?.let {
                CsvParserSupport.parseAmount(
                    row.getOrNull(it).orEmpty(),
                )?.abs()
            } ?: BigDecimal.ZERO
        val credit =
            creditIdx?.let {
                CsvParserSupport.parseAmount(
                    row.getOrNull(it).orEmpty(),
                )?.abs()
            } ?: BigDecimal.ZERO
        if (debit == BigDecimal.ZERO && credit == BigDecimal.ZERO) return null
        return credit.subtract(debit)
    }

    private fun List<String>.anyMatches(candidates: Set<String>): Boolean =
        any { column -> candidates.any { candidate -> column == candidate || column.contains(candidate) } }

    private fun List<String>.indexOfFirstHeader(candidates: Set<String>): Int? =
        indexOfFirst { header -> candidates.any { candidate -> header == candidate || header.contains(candidate) } }
            .takeIf { it >= 0 }
}
