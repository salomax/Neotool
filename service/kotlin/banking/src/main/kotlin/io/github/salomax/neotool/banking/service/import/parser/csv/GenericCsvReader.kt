package io.github.salomax.neotool.banking.service.import.parser.csv

import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.model.domain.imports.ImportTextDecoder
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Generic CSV reader that infers minimum required fields:
 * - date
 * - amount
 * - description
 */
object GenericCsvReader : BankCsvReader {
    private val DATE_HEADERS =
        setOf(
            "data",
            "date",
            "posted_at",
            "transaction_date",
            "lancamento",
            "dt_lancamento",
        )
    private val AMOUNT_HEADERS =
        setOf(
            "valor",
            "amount",
            "value",
            "valor_rs",
            "gross_value",
            "net_value",
            "quantia",
        )
    private val DEBIT_HEADERS = setOf("debit", "debito", "saidas", "saida")
    private val CREDIT_HEADERS = setOf("credit", "credito", "entradas", "entrada")
    private val DESCRIPTION_HEADERS =
        setOf(
            "descricao",
            "description",
            "lancamento",
            "tipo_lancamento",
            "historico",
            "memo",
            "details",
            "title",
            "narrative",
            "desc",
        )
    private val ID_HEADERS =
        setOf(
            "identificador",
            "id",
            "transaction_id",
            "codigo",
            "hash",
            "chave",
        )

    private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val BR_DATE_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val BR_DATE_DASH = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val BR_DATE_SHORT_SLASH = DateTimeFormatter.ofPattern("dd/MM/yy")

    override fun id(): String = "csv-generic"

    override fun supports(fingerprint: CsvFingerprint): CsvReaderMatch {
        var score = 0.0
        val reasons = mutableListOf<String>()

        if (fingerprint.datePatterns.isNotEmpty()) {
            score += 0.40
            reasons += "date_pattern_detected"
        }
        if (fingerprint.amountPatterns.isNotEmpty()) {
            score += 0.40
            reasons += "amount_pattern_detected"
        }
        if (fingerprint.hasHeader) {
            score += 0.10
            reasons += "has_header"
        }
        if (fingerprint.normalizedColumns.any { DESCRIPTION_HEADERS.any(it::contains) }) {
            score += 0.10
            reasons += "description_column_hint"
        }

        return CsvReaderMatch(
            readerId = id(),
            score = score.coerceIn(0.0, 1.0),
            reasons = reasons,
        )
    }

    override fun parse(bytes: ByteArray): List<ParsedTransaction> {
        val fingerprint = CsvFingerprintBuilder.build(bytes)
        val text = ImportTextDecoder.decode(bytes).text
        val rawLines =
            text.lineSequence()
                .map { it.trimEnd('\r') }
                .filter { it.isNotBlank() }
                .toList()
        if (rawLines.isEmpty()) return emptyList()

        val rows = rawLines.map { parseCsvLine(it, fingerprint.delimiter) }
        val mapping = resolveMapping(rows, fingerprint)
        val dataRows = if (fingerprint.hasHeader) rows.drop(1) else rows
        val effectiveProviderIdIndex = resolveProviderIdIndex(dataRows, mapping.idIndex)

        return dataRows.mapNotNull { row ->
            val parsedDate = parseDate(row.getOrNull(mapping.dateIndex).orEmpty()) ?: return@mapNotNull null
            val parsedAmount = resolveAmount(row, mapping) ?: return@mapNotNull null
            if (parsedAmount.signum() == 0) return@mapNotNull null

            val description = resolveDescription(row, mapping).ifBlank { "Imported transaction" }
            val creditDebitType = if (parsedAmount.signum() < 0) CreditDebitType.DEBIT else CreditDebitType.CREDIT

            ParsedTransaction(
                providerTransactionId =
                    row.getOrNull(
                        effectiveProviderIdIndex ?: -1,
                    )?.trim()?.takeIf { it.isNotBlank() },
                creditDebitType = creditDebitType,
                amount = parsedAmount.abs(),
                date = parsedDate,
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

    private fun resolveMapping(
        rows: List<List<String>>,
        fingerprint: CsvFingerprint,
    ): GenericCsvMapping {
        val header = if (fingerprint.hasHeader) fingerprint.normalizedColumns else emptyList()
        val dataRows = if (fingerprint.hasHeader) rows.drop(1) else rows

        val inferredDateIdx = inferDateIndex(dataRows)
        val inferredAmountIdx = inferAmountIndex(dataRows, excluded = setOfNotNull(inferredDateIdx))
        val inferredDescriptionIdx =
            inferDescriptionIndex(dataRows, excluded = setOfNotNull(inferredDateIdx, inferredAmountIdx))

        val dateIndex = header.indexOfFirstHeader(DATE_HEADERS).takeIf { it >= 0 } ?: inferredDateIdx
        val debitIndex = header.indexOfFirstHeader(DEBIT_HEADERS).takeIf { it >= 0 }
        val creditIndex = header.indexOfFirstHeader(CREDIT_HEADERS).takeIf { it >= 0 }
        val explicitAmountIndex = header.indexOfFirstHeader(AMOUNT_HEADERS).takeIf { it >= 0 }
        val amountIndex =
            when {
                explicitAmountIndex != null -> explicitAmountIndex
                debitIndex != null || creditIndex != null -> null
                else -> inferredAmountIdx
            }
        val descriptionIndex =
            header.indexOfFirstHeader(
                DESCRIPTION_HEADERS,
            ).takeIf { it >= 0 } ?: inferredDescriptionIdx
        val idIndex = header.indexOfFirstHeader(ID_HEADERS).takeIf { it >= 0 }

        return GenericCsvMapping(
            dateIndex = dateIndex ?: 0,
            amountIndex = amountIndex,
            debitIndex = debitIndex,
            creditIndex = creditIndex,
            descriptionIndex = descriptionIndex ?: 0,
            idIndex = idIndex,
        )
    }

    private fun resolveAmount(
        row: List<String>,
        mapping: GenericCsvMapping,
    ): BigDecimal? {
        mapping.amountIndex?.let { idx ->
            parseAmount(row.getOrNull(idx).orEmpty())?.let { return it }
        }

        val debit = mapping.debitIndex?.let { parseAmount(row.getOrNull(it).orEmpty())?.abs() } ?: BigDecimal.ZERO
        val credit = mapping.creditIndex?.let { parseAmount(row.getOrNull(it).orEmpty())?.abs() } ?: BigDecimal.ZERO
        if (debit == BigDecimal.ZERO && credit == BigDecimal.ZERO) return null
        return credit.subtract(debit)
    }

    private fun resolveDescription(
        row: List<String>,
        mapping: GenericCsvMapping,
    ): String {
        val preferred = row.getOrNull(mapping.descriptionIndex)?.trim().orEmpty()
        if (preferred.isNotBlank()) return preferred

        val blocked =
            setOfNotNull(
                mapping.dateIndex,
                mapping.amountIndex,
                mapping.debitIndex,
                mapping.creditIndex,
                mapping.idIndex,
            )
        return row.mapIndexedNotNull { idx, value ->
            if (idx in blocked) {
                null
            } else {
                value.trim().takeIf { it.isNotBlank() }
            }
        }.joinToString(" ").trim()
    }

    private fun resolveProviderIdIndex(
        dataRows: List<List<String>>,
        candidateIndex: Int?,
    ): Int? {
        val idx = candidateIndex ?: return null
        val ids = dataRows.mapNotNull { row -> row.getOrNull(idx)?.trim()?.takeIf { it.isNotBlank() } }
        if (ids.size < 3) return idx

        val uniqueCount = ids.toSet().size
        val hasMeaningfulDuplicates = uniqueCount <= ids.size / 2
        return if (hasMeaningfulDuplicates) null else idx
    }

    private fun inferDateIndex(rows: List<List<String>>): Int? =
        bestIndexByScore(rows) { value -> if (parseDate(value) != null) 1.0 else 0.0 }

    private fun inferAmountIndex(
        rows: List<List<String>>,
        excluded: Set<Int>,
    ): Int? = bestIndexByScore(rows, excluded) { value -> if (parseAmount(value) != null) 1.0 else 0.0 }

    private fun inferDescriptionIndex(
        rows: List<List<String>>,
        excluded: Set<Int>,
    ): Int? {
        val maxColumns = rows.maxOfOrNull { it.size } ?: return null
        var bestIndex: Int? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (idx in 0 until maxColumns) {
            if (idx in excluded) continue
            val values = rows.mapNotNull { it.getOrNull(idx)?.trim() }.filter { it.isNotBlank() }
            if (values.isEmpty()) continue

            val avgLen = values.map { it.length }.average()
            val numericLike = values.count { parseAmount(it) != null || parseDate(it) != null }
            val score = avgLen - (numericLike * 2.0)
            if (score > bestScore) {
                bestScore = score
                bestIndex = idx
            }
        }
        return bestIndex
    }

    private fun bestIndexByScore(
        rows: List<List<String>>,
        excluded: Set<Int> = emptySet(),
        scoreFn: (String) -> Double,
    ): Int? {
        val maxColumns = rows.maxOfOrNull { it.size } ?: return null
        var bestIndex: Int? = null
        var bestScore = Double.NEGATIVE_INFINITY

        for (idx in 0 until maxColumns) {
            if (idx in excluded) continue
            val values = rows.mapNotNull { it.getOrNull(idx) }
            if (values.isEmpty()) continue
            val score = values.map(scoreFn).average()
            if (score > bestScore) {
                bestScore = score
                bestIndex = idx
            }
        }
        return bestIndex
    }

    private fun parseDate(value: String): LocalDate? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null

        return runCatching { LocalDate.parse(trimmed, ISO_DATE) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, BR_DATE_SLASH) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, BR_DATE_DASH) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, BR_DATE_SHORT_SLASH) }.getOrNull()
    }

    private fun parseAmount(raw: String): BigDecimal? {
        var clean = raw.trim()
        if (clean.isBlank()) return null

        val parenthesizedNegative = clean.startsWith("(") && clean.endsWith(")")
        clean = clean.removePrefix("(").removeSuffix(")")
        clean =
            clean.replace("R$", "", ignoreCase = true)
                .replace(" ", "")
                .replace("[^0-9,\\.\\-+]".toRegex(), "")
        if (clean.isBlank()) return null

        val normalized =
            when {
                clean.contains(",") && clean.contains(".") && clean.lastIndexOf(',') > clean.lastIndexOf('.') ->
                    clean.replace(".", "").replace(",", ".")
                clean.contains(",") && clean.contains(".") ->
                    clean.replace(",", "")
                clean.contains(",") -> clean.replace(",", ".")
                else -> clean
            }

        val parsed = runCatching { BigDecimal(normalized) }.getOrNull() ?: return null
        return if (parenthesizedNegative) parsed.negate() else parsed
    }

    private fun List<String>.indexOfFirstHeader(candidates: Set<String>): Int =
        indexOfFirst { header -> candidates.any { candidate -> header == candidate || header.contains(candidate) } }

    private fun parseCsvLine(
        line: String,
        delimiter: Char,
    ): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                ch == '"' -> {
                    inQuotes = !inQuotes
                }
                ch == delimiter && !inQuotes -> {
                    result.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}

private data class GenericCsvMapping(
    val dateIndex: Int,
    val amountIndex: Int?,
    val debitIndex: Int?,
    val creditIndex: Int?,
    val descriptionIndex: Int,
    val idIndex: Int?,
)
