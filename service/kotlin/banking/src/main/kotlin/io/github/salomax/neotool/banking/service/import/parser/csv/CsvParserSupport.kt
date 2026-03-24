package io.github.salomax.neotool.banking.service.import.parser.csv

import io.github.salomax.neotool.banking.domain.import.*
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal object CsvParserSupport {
    private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val BR_DATE_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val BR_DATE_DASH = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    private val BR_DATE_SHORT_SLASH = DateTimeFormatter.ofPattern("dd/MM/yy")

    fun detectDelimiter(headerLine: String): Char =
        if (headerLine.count { it == ';' } > headerLine.count { it == ',' }) ';' else ','

    fun parseCsvLine(
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
                ch == '"' -> inQuotes = !inQuotes
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

    fun parseDate(value: String): LocalDate? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null

        return runCatching { LocalDate.parse(trimmed, ISO_DATE) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, BR_DATE_SLASH) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, BR_DATE_DASH) }.getOrNull()
            ?: runCatching { LocalDate.parse(trimmed, BR_DATE_SHORT_SLASH) }.getOrNull()
    }

    fun parseAmount(raw: String): BigDecimal? {
        var clean = raw.trim()
        if (clean.isBlank()) return null

        val parenthesizedNegative = clean.startsWith("(") && clean.endsWith(")")
        clean =
            clean.removePrefix("(").removeSuffix(")")
                .replace("R$", "", ignoreCase = true)
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

    fun normalizeColumnName(value: String): String {
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
}
