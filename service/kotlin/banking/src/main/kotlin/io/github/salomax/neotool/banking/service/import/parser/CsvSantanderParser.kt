package io.github.salomax.neotool.banking.service.import.parser

import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.model.domain.imports.ImportTextDecoder
import io.github.salomax.neotool.banking.service.import.parser.csv.BankCsvReader
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvAmountPattern
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvDatePattern
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvFingerprint
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvParserSupport
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvReaderMatch
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reader for common Santander account statement CSV layouts.
 */
object CsvSantanderParser : BankCsvReader {
    private val SAO_PAULO_ZONE = ZoneId.of("America/Sao_Paulo")
    private val TIME_FORMAT_HH_MM = DateTimeFormatter.ofPattern("HH:mm")
    private val TIME_FORMAT_HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val DATE_HEADERS = setOf("data", "data_lancamento")
    private val TIME_HEADERS = setOf("hora", "hora_lancamento")
    private val DESCRIPTION_HEADERS = setOf("descricao", "historico", "descricao_lancamento")
    private val AMOUNT_HEADERS = setOf("valor", "valor_rs", "amount")
    private val BALANCE_HEADERS = setOf("saldo", "saldo_rs")
    private val ID_HEADERS = setOf("documento", "doc", "identificador", "id", "lancamento")
    private val SANTANDER_HINT_HEADERS = setOf("data", "hora", "descricao", "valor", "saldo")

    override fun id(): String = "csv-santander"

    override fun supports(fingerprint: CsvFingerprint): CsvReaderMatch {
        var score = 0.0
        val reasons = mutableListOf<String>()
        val columns = fingerprint.normalizedColumns
        val hasDate = columns.anyMatches(DATE_HEADERS)
        val hasTime = columns.anyMatches(TIME_HEADERS)
        val hasDescription = columns.anyMatches(DESCRIPTION_HEADERS)
        val hasAmount = columns.anyMatches(AMOUNT_HEADERS)
        val hasBalance = columns.anyMatches(BALANCE_HEADERS)
        val hasSantanderHints = columns.intersect(SANTANDER_HINT_HEADERS).size >= 4

        if (fingerprint.hasHeader) {
            score += 0.05
            reasons += "has_header"
        }
        if (fingerprint.delimiter == ';') {
            score += 0.05
            reasons += "delimiter_semicolon"
        }

        if (hasDate && hasTime && hasDescription && hasAmount) {
            score += 0.65
            reasons += "core_columns_match"
        } else if (hasDate && hasDescription && hasAmount) {
            score += 0.45
            reasons += "core_columns_without_time"
        }

        if (hasBalance) {
            score += 0.10
            reasons += "balance_column_present"
        }
        if (hasSantanderHints) {
            score += 0.08
            reasons += "santander_header_hints"
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
            score += 0.02
            reasons += "amount_pattern_match"
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
        val timeIdx = header.indexOfFirstHeader(TIME_HEADERS)
        val descriptionIdx = header.indexOfFirstHeader(DESCRIPTION_HEADERS)
        val amountIdx = header.indexOfFirstHeader(AMOUNT_HEADERS)
        val idIdx = header.indexOfFirstHeader(ID_HEADERS)
        if (dateIdx == null || descriptionIdx == null || amountIdx == null) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            val row = CsvParserSupport.parseCsvLine(line, delimiter)
            val date = CsvParserSupport.parseDate(row.getOrNull(dateIdx).orEmpty()) ?: return@mapNotNull null
            val amount = CsvParserSupport.parseAmount(row.getOrNull(amountIdx).orEmpty()) ?: return@mapNotNull null
            if (amount.signum() == 0) return@mapNotNull null

            val description = row.getOrNull(descriptionIdx)?.trim().orEmpty().ifBlank { "Imported transaction" }
            val providerId = idIdx?.let { row.getOrNull(it)?.trim()?.takeIf { value -> value.isNotBlank() } }
            val transactionDatetime =
                parseTransactionDatetime(
                    date,
                    timeIdx?.let {
                            idx ->
                        row.getOrNull(idx).orEmpty()
                    },
                )

            ParsedTransaction(
                providerTransactionId = providerId,
                creditDebitType = if (amount.signum() < 0) CreditDebitType.DEBIT else CreditDebitType.CREDIT,
                amount = amount.abs(),
                date = date,
                transactionDatetime = transactionDatetime,
                description = description,
                descriptionRaw = null,
                counterpartyName = null,
                counterpartyDocument = null,
                currencyCode = "BRL",
                totalInstallments = null,
            )
        }
    }

    private fun parseTransactionDatetime(
        date: java.time.LocalDate,
        timeRaw: String?,
    ): Instant? {
        val timeValue = timeRaw?.trim().orEmpty()
        if (timeValue.isBlank()) return null

        val time =
            runCatching { LocalTime.parse(timeValue, TIME_FORMAT_HH_MM) }.getOrNull()
                ?: runCatching { LocalTime.parse(timeValue, TIME_FORMAT_HH_MM_SS) }.getOrNull()
                ?: return null

        return LocalDateTime.of(date, time).atZone(SAO_PAULO_ZONE).toInstant()
    }

    private fun List<String>.anyMatches(candidates: Set<String>): Boolean =
        any { column -> candidates.any { candidate -> column == candidate || column.contains(candidate) } }

    private fun List<String>.indexOfFirstHeader(candidates: Set<String>): Int? =
        indexOfFirst { header -> candidates.any { candidate -> header == candidate || header.contains(candidate) } }
            .takeIf { it >= 0 }
}
