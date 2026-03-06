package io.github.salomax.neotool.banking.domain.import.parser.csv

import io.github.salomax.neotool.banking.domain.import.ImportTextDecoder
import java.text.Normalizer
import kotlin.math.max

/**
 * Builds a structural fingerprint from CSV bytes.
 */
object CsvFingerprintBuilder {
    private const val DEFAULT_ENCODING = "UTF-8"
    private val DELIMITER_CANDIDATES = charArrayOf(',', ';', '\t', '|')
    private val HEADER_HINTS =
        setOf(
            "data",
            "date",
            "valor",
            "amount",
            "descricao",
            "description",
            "historico",
            "title",
            "identificador",
            "id",
            "transaction_type",
            "category",
            "charges",
        )
    private val DESCRIPTION_HEADER_HINTS =
        setOf(
            "descricao",
            "description",
            "historico",
            "memo",
            "details",
            "title",
            "narrative",
            "desc",
        )
    private val DESCRIPTION_KEYWORDS =
        setOf(
            "pix",
            "transferencia",
            "pagamento",
            "boleto",
            "compra",
            "debito",
            "credito",
            "ted",
            "doc",
            "saque",
            "deposito",
            "estorno",
            "tarifa",
            "rendimento",
            "juros",
        )

    fun build(
        bytes: ByteArray,
        maxLines: Int = 60,
    ): CsvFingerprint {
        val decoded = ImportTextDecoder.decode(bytes)
        val text = decoded.text
        val lines =
            text
                .lineSequence()
                .map { it.trimEnd('\r') }
                .filter { it.isNotBlank() }
                .take(max(maxLines, 1))
                .toList()

        if (lines.isEmpty()) {
            return CsvFingerprint(
                delimiter = ',',
                encoding = decoded.encoding.ifBlank { DEFAULT_ENCODING },
                hasHeader = false,
                normalizedColumns = emptyList(),
                datePatterns = emptySet(),
                amountPatterns = emptySet(),
                descriptionTokens = emptySet(),
            )
        }

        val delimiter = detectDelimiter(lines)
        val parsedRows = lines.map { parseCsvLine(it, delimiter) }
        val hasHeader = detectHeader(parsedRows)
        val normalizedColumns = if (hasHeader) normalizeColumns(parsedRows.first()) else emptyList()
        val dataRows = if (hasHeader) parsedRows.drop(1) else parsedRows

        return CsvFingerprint(
            delimiter = delimiter,
            encoding = decoded.encoding.ifBlank { DEFAULT_ENCODING },
            hasHeader = hasHeader,
            normalizedColumns = normalizedColumns,
            datePatterns = detectDatePatterns(dataRows),
            amountPatterns = detectAmountPatterns(dataRows),
            descriptionTokens = detectDescriptionTokens(dataRows, normalizedColumns, hasHeader),
        )
    }

    private fun detectDelimiter(lines: List<String>): Char {
        val sample = lines.take(10)
        return DELIMITER_CANDIDATES.maxByOrNull { candidate ->
            val parsed = sample.map { parseCsvLine(it, candidate) }
            val columnCounts = parsed.map { it.size }
            val multiColumnRows = columnCounts.count { it > 1 }
            val modeCount =
                columnCounts
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.value
                    ?: 0
            val rawHits = sample.sumOf { line -> line.count { it == candidate } }
            (multiColumnRows * 100) + (modeCount * 10) + rawHits
        } ?: ','
    }

    private fun detectHeader(rows: List<List<String>>): Boolean {
        if (rows.isEmpty()) return false

        if (rows.size == 1) {
            val first = rows.first()
            if (first.isEmpty()) return false
            val firstNormalized = first.map { normalizeCell(it) }
            val firstLooksValueLike = first.count { looksLikeDate(it) || looksLikeAmount(it) }
            val headerHints = firstNormalized.count { cell -> HEADER_HINTS.contains(cell) }
            val alphaLike = firstNormalized.count { cell -> looksLikeHeaderToken(cell) }
            val uniqueColumns = firstNormalized.toSet().size == firstNormalized.size

            var score = 0
            score += headerHints * 2
            score += alphaLike
            if (uniqueColumns) score += 1
            if (firstLooksValueLike == 0) score += 1
            if (headerHints > 0) score += 2

            return score >= 5
        }

        val first = rows.first()
        val second = rows[1]
        if (first.isEmpty()) return false

        val firstNormalized = first.map { normalizeCell(it) }
        val firstLooksValueLike = first.count { looksLikeDate(it) || looksLikeAmount(it) }
        val secondLooksValueLike = second.count { looksLikeDate(it) || looksLikeAmount(it) }
        val headerHints = firstNormalized.count { cell -> HEADER_HINTS.contains(cell) }
        val alphaLike = firstNormalized.count { cell -> looksLikeHeaderToken(cell) }
        val uniqueColumns = firstNormalized.toSet().size == firstNormalized.size

        var score = 0
        score += headerHints * 2
        score += alphaLike
        if (uniqueColumns) score += 1
        if (firstLooksValueLike == 0) score += 1
        if (secondLooksValueLike > firstLooksValueLike) score += 2
        if (headerHints > 0) score += 2

        return score >= 5
    }

    private fun normalizeColumns(headerRow: List<String>): List<String> =
        headerRow.mapIndexed { index, value ->
            normalizeCell(value).ifBlank { "column_${index + 1}" }
        }

    private fun detectDatePatterns(rows: List<List<String>>): Set<CsvDatePattern> {
        val result = linkedSetOf<CsvDatePattern>()
        rows.flatten().forEach { cell ->
            val v = cell.trim()
            when {
                ISO_DATE_REGEX.matches(v) -> result.add(CsvDatePattern.ISO_YYYY_MM_DD)
                BR_DATE_SLASH_REGEX.matches(v) -> result.add(CsvDatePattern.BR_DD_MM_YYYY_SLASH)
                BR_DATE_DASH_REGEX.matches(v) -> result.add(CsvDatePattern.BR_DD_MM_YYYY_DASH)
                BR_DATE_SHORT_SLASH_REGEX.matches(v) -> result.add(CsvDatePattern.BR_DD_MM_YY_SLASH)
            }
        }
        return result
    }

    private fun detectAmountPatterns(rows: List<List<String>>): Set<CsvAmountPattern> {
        val result = linkedSetOf<CsvAmountPattern>()
        rows.flatten().forEach { cell ->
            val v = normalizeAmountCell(cell)
            when {
                v.isBlank() -> {}
                THOUSANDS_DOT_DECIMAL_COMMA_REGEX.matches(v) -> result.add(CsvAmountPattern.THOUSANDS_DOT_DECIMAL_COMMA)
                THOUSANDS_COMMA_DECIMAL_DOT_REGEX.matches(v) -> result.add(CsvAmountPattern.THOUSANDS_COMMA_DECIMAL_DOT)
                COMMA_DECIMAL_REGEX.matches(v) -> result.add(CsvAmountPattern.COMMA_DECIMAL)
                DOT_DECIMAL_REGEX.matches(v) -> result.add(CsvAmountPattern.DOT_DECIMAL)
                INTEGER_REGEX.matches(v) -> result.add(CsvAmountPattern.INTEGER)
            }
        }
        return result
    }

    private fun detectDescriptionTokens(
        rows: List<List<String>>,
        normalizedColumns: List<String>,
        hasHeader: Boolean,
    ): Set<String> {
        if (rows.isEmpty()) return emptySet()

        val descriptionIndexes =
            if (hasHeader) {
                normalizedColumns.mapIndexedNotNull { idx, name ->
                    if (DESCRIPTION_HEADER_HINTS.any { hint -> name.contains(hint) }) idx else null
                }
            } else {
                emptyList()
            }

        val effectiveIndexes =
            when {
                descriptionIndexes.isNotEmpty() -> descriptionIndexes
                else -> listOf(inferDescriptionColumnIndex(rows))
            }

        val tokens = linkedSetOf<String>()
        rows.forEach { row ->
            effectiveIndexes.forEach { idx ->
                val text = row.getOrNull(idx)?.let(::normalizeTextForTokenScan).orEmpty()
                if (text.isBlank()) return@forEach
                DESCRIPTION_KEYWORDS.forEach { keyword ->
                    if (text.contains(keyword)) tokens.add(keyword)
                }
            }
        }
        return tokens
    }

    private fun inferDescriptionColumnIndex(rows: List<List<String>>): Int {
        val maxColumns = rows.maxOfOrNull { it.size } ?: 0
        if (maxColumns <= 1) return 0

        var bestIndex = 0
        var bestScore = Double.NEGATIVE_INFINITY
        for (idx in 0 until maxColumns) {
            val values = rows.mapNotNull { it.getOrNull(idx)?.trim() }.filter { it.isNotBlank() }
            if (values.isEmpty()) continue
            val avgLength = values.map { it.length }.average()
            val valueLikeCount = values.count { looksLikeDate(it) || looksLikeAmount(it) }
            val score = avgLength - (valueLikeCount.toDouble() * 4.0)
            if (score > bestScore) {
                bestScore = score
                bestIndex = idx
            }
        }
        return bestIndex
    }

    private fun looksLikeHeaderToken(cell: String): Boolean {
        if (cell.isBlank()) return false
        val letters = cell.count { it.isLetter() }
        val digits = cell.count { it.isDigit() }
        if (letters == 0) return false
        return letters >= digits
    }

    private fun normalizeCell(value: String): String {
        val noBom = value.removePrefix("\uFEFF").trim()
        val normalized =
            Normalizer.normalize(noBom, Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
                .lowercase()
        return normalized
            .replace("[^a-z0-9]+".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .trim('_')
    }

    private fun normalizeAmountCell(value: String): String =
        value.trim()
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")

    private fun normalizeTextForTokenScan(value: String): String {
        val normalized =
            Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
                .lowercase()
        return normalized.replace("[^a-z0-9 ]+".toRegex(), " ")
    }

    private fun looksLikeDate(value: String): Boolean {
        val v = value.trim()
        return ISO_DATE_REGEX.matches(v) ||
            BR_DATE_SLASH_REGEX.matches(v) ||
            BR_DATE_DASH_REGEX.matches(v) ||
            BR_DATE_SHORT_SLASH_REGEX.matches(v)
    }

    private fun looksLikeAmount(value: String): Boolean {
        val v = normalizeAmountCell(value)
        return THOUSANDS_DOT_DECIMAL_COMMA_REGEX.matches(v) ||
            THOUSANDS_COMMA_DECIMAL_DOT_REGEX.matches(v) ||
            COMMA_DECIMAL_REGEX.matches(v) ||
            DOT_DECIMAL_REGEX.matches(v) ||
            INTEGER_REGEX.matches(v)
    }

    /**
     * Splits a CSV line by delimiter, respecting quoted fields and escaped quotes.
     */
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

private val ISO_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
private val BR_DATE_SLASH_REGEX = Regex("^\\d{2}/\\d{2}/\\d{4}$")
private val BR_DATE_DASH_REGEX = Regex("^\\d{2}-\\d{2}-\\d{4}$")
private val BR_DATE_SHORT_SLASH_REGEX = Regex("^\\d{2}/\\d{2}/\\d{2}$")

private val DOT_DECIMAL_REGEX = Regex("^[+-]?\\d+\\.\\d{2,}$")
private val COMMA_DECIMAL_REGEX = Regex("^[+-]?\\d+,\\d{2,}$")
private val THOUSANDS_DOT_DECIMAL_COMMA_REGEX = Regex("^[+-]?\\d{1,3}(\\.\\d{3})+,\\d{2,}$")
private val THOUSANDS_COMMA_DECIMAL_DOT_REGEX = Regex("^[+-]?\\d{1,3}(,\\d{3})+\\.\\d{2,}$")
private val INTEGER_REGEX = Regex("^[+-]?\\d+$")
