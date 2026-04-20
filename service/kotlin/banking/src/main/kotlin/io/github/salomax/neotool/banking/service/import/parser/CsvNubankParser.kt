package io.github.salomax.neotool.banking.service.import.parser

import io.github.salomax.neotool.banking.domain.import.*
import io.github.salomax.neotool.banking.model.domain.imports.ImportTextDecoder
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.service.import.parser.csv.BankCsvReader
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvAmountPattern
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvDatePattern
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvFingerprint
import io.github.salomax.neotool.banking.service.import.parser.csv.CsvReaderMatch
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses Nubank CSV formats:
 * - Credit card: date,transaction_type,title,amount,category,charges,Identificador
 * - Account: Data,Valor,Identificador,Descrição
 */
object CsvNubankParser : BankCsvReader {
    private val CREDIT_CARD_HEADER = "date,transaction_type,title,amount,category,charges,Identificador"
    private val ACCOUNT_HEADER_NORMALIZED = "data,valor,identificador,descricao"
    private val CREDIT_CARD_COLUMNS =
        listOf("date", "transaction_type", "title", "amount", "category", "charges", "identificador")
    private val ACCOUNT_COLUMNS = listOf("data", "valor", "identificador", "descricao")
    private val ACCOUNT_MIN_COLUMNS = setOf("data", "valor", "identificador")
    private val CARD_MIN_COLUMNS = setOf("date", "transaction_type", "title", "amount", "identificador")
    private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
    private val DATE_FORMAT_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private val DEBIT_TYPES = setOf("purchase", "debit", "debito", "compra")
    private val CREDIT_TYPES = setOf("payment", "credit", "credito", "pagamento")

    private enum class CsvFormat {
        CREDIT_CARD,
        ACCOUNT,
    }

    override fun id(): String = "csv-nubank"

    override fun supports(fingerprint: CsvFingerprint): CsvReaderMatch {
        var score = 0.0
        val reasons = mutableListOf<String>()

        if (fingerprint.hasHeader) {
            score += 0.05
            reasons += "has_header"
        }

        if (fingerprint.delimiter == ',') {
            score += 0.05
            reasons += "delimiter_comma"
        }

        val columns = fingerprint.normalizedColumns
        when {
            columns == CREDIT_CARD_COLUMNS -> {
                score += 0.85
                reasons += "exact_nubank_card_header"
            }
            columns == ACCOUNT_COLUMNS -> {
                score += 0.85
                reasons += "exact_nubank_account_header"
            }
            columns.toSet().containsAll(CARD_MIN_COLUMNS) -> {
                score += 0.55
                reasons += "nubank_card_min_columns"
            }
            columns.toSet().containsAll(ACCOUNT_MIN_COLUMNS) -> {
                score += 0.55
                reasons += "nubank_account_min_columns"
            }
        }

        if (fingerprint.datePatterns.contains(
                CsvDatePattern.BR_DD_MM_YYYY_SLASH,
            ) && columns.toSet().containsAll(ACCOUNT_MIN_COLUMNS)
        ) {
            score += 0.03
            reasons += "account_date_pattern_match"
        }
        if (fingerprint.datePatterns.contains(
                CsvDatePattern.ISO_YYYY_MM_DD,
            ) && columns.toSet().containsAll(CARD_MIN_COLUMNS)
        ) {
            score += 0.03
            reasons += "card_date_pattern_match"
        }

        if (
            fingerprint.amountPatterns.contains(CsvAmountPattern.DOT_DECIMAL) ||
            fingerprint.amountPatterns.contains(CsvAmountPattern.COMMA_DECIMAL)
        ) {
            score += 0.02
            reasons += "amount_pattern_match"
        }

        if (fingerprint.descriptionTokens.intersect(
                setOf("pix", "transferencia", "pagamento", "boleto"),
            ).isNotEmpty()
        ) {
            score += 0.02
            reasons += "description_token_match"
        }

        return CsvReaderMatch(
            readerId = id(),
            score = score.coerceIn(0.0, 1.0),
            reasons = reasons.distinct(),
        )
    }

    /**
     * Parses Nubank CSV bytes. Skips header row; skips rows with blank Identificador.
     * @return List of parsed transactions (empty if header mismatch or parse error)
     */
    override fun parse(bytes: ByteArray): List<ParsedTransaction> {
        val text = ImportTextDecoder.decode(bytes).text
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val header = normalizeHeader(lines.first())
        val delimiter = detectDelimiter(lines.first())
        val format = detectFormat(header) ?: return emptyList()

        return lines.drop(1).mapNotNull { line -> parseRow(line, format, delimiter) }
    }

    private fun detectFormat(header: String): CsvFormat? =
        when {
            header.equals(CREDIT_CARD_HEADER, ignoreCase = true) -> CsvFormat.CREDIT_CARD
            header == ACCOUNT_HEADER_NORMALIZED -> CsvFormat.ACCOUNT
            else -> null
        }

    private fun parseRow(
        line: String,
        format: CsvFormat,
        delimiter: Char,
    ): ParsedTransaction? =
        when (format) {
            CsvFormat.CREDIT_CARD -> parseCreditCardRow(line, delimiter)
            CsvFormat.ACCOUNT -> parseAccountRow(line, delimiter)
        }

    private fun parseCreditCardRow(
        line: String,
        delimiter: Char,
    ): ParsedTransaction? {
        val fields = parseCsvLine(line, delimiter)
        if (fields.size < 7) return null
        val dateStr = fields.getOrNull(0)?.trim() ?: return null
        val transactionType = fields.getOrNull(1)?.trim()?.lowercase() ?: return null
        val title = fields.getOrNull(2)?.trim() ?: ""
        val amountStr = fields.getOrNull(3)?.trim() ?: return null
        val category = fields.getOrNull(4)?.trim()
        val chargesStr = fields.getOrNull(5)?.trim()
        val identificador = fields.getOrNull(6)?.trim()
        if (identificador.isNullOrBlank()) return null

        val date = runCatching { LocalDate.parse(dateStr, DATE_FORMAT) }.getOrNull() ?: return null
        val amount = parseAmount(amountStr) ?: return null
        if (amount.signum() < 0) return null

        val creditDebitType =
            when (transactionType) {
                in DEBIT_TYPES -> CreditDebitType.DEBIT
                in CREDIT_TYPES -> CreditDebitType.CREDIT
                else -> CreditDebitType.DEBIT
            }

        val totalInstallments = chargesStr?.toIntOrNull()?.takeIf { it >= 1 }

        return ParsedTransaction(
            providerTransactionId = identificador,
            creditDebitType = creditDebitType,
            amount = amount,
            date = date,
            transactionDatetime = null,
            description = title,
            descriptionRaw = category,
            counterpartyName = null,
            counterpartyDocument = null,
            currencyCode = "BRL",
            totalInstallments = totalInstallments,
        )
    }

    private fun parseAccountRow(
        line: String,
        delimiter: Char,
    ): ParsedTransaction? {
        val fields = parseCsvLine(line, delimiter)
        if (fields.size < 4) return null

        val dateStr = fields.getOrNull(0)?.trim() ?: return null
        val amountStr = fields.getOrNull(1)?.trim() ?: return null
        val identificador = fields.getOrNull(2)?.trim()
        val description = fields.getOrNull(3)?.trim().orEmpty()

        if (identificador.isNullOrBlank()) return null

        val date = runCatching { LocalDate.parse(dateStr, DATE_FORMAT_BR) }.getOrNull() ?: return null
        val amountRaw = parseAmount(amountStr) ?: return null
        val creditDebitType = if (amountRaw.signum() < 0) CreditDebitType.DEBIT else CreditDebitType.CREDIT

        return ParsedTransaction(
            providerTransactionId = identificador,
            creditDebitType = creditDebitType,
            amount = amountRaw.abs(),
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

    private fun parseAmount(raw: String): BigDecimal? {
        val clean = raw.trim().replace(" ", "")
        val normalized =
            when {
                clean.contains(",") && clean.contains(".") && clean.indexOf('.') < clean.indexOf(',') ->
                    clean.replace(".", "").replace(",", ".")
                clean.contains(",") -> clean.replace(",", ".")
                else -> clean
            }
        return runCatching { BigDecimal(normalized) }.getOrNull()
    }

    private fun normalizeHeader(value: String): String {
        val noBom = value.removePrefix("\uFEFF").trim()
        val normalized = Normalizer.normalize(noBom, Normalizer.Form.NFD)
        return normalized
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase()
    }

    private fun detectDelimiter(headerLine: String): Char =
        if (headerLine.count { it == ';' } > headerLine.count { it == ',' }) ';' else ','

    /**
     * Splits a CSV line by delimiter, respecting quoted fields.
     */
    private fun parseCsvLine(
        line: String,
        delimiter: Char,
    ): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    val end = line.indexOf('"', i + 1)
                    if (end == -1) {
                        result.add(line.substring(i + 1))
                        break
                    }
                    result.add(line.substring(i + 1, end))
                    i = end + 1
                    if (i < line.length && line[i] == delimiter) i++
                }
                ch == delimiter -> {
                    result.add("")
                    i++
                }
                else -> {
                    val nextDelimiter = line.indexOf(delimiter, i)
                    val value = if (nextDelimiter == -1) line.substring(i) else line.substring(i, nextDelimiter)
                    result.add(value)
                    i = if (nextDelimiter == -1) line.length else nextDelimiter + 1
                }
            }
        }
        return result
    }
}
