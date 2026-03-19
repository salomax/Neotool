package io.github.salomax.neotool.banking.domain.import.statement

import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate

data class AssistantStatementExtractionResult(
    val rows: List<ParsedTransaction>,
    val warnings: List<String> = emptyList(),
)

@Singleton
class AssistantStatementTransactionExtractor {
    fun extract(
        rawText: String,
        meta: AssistantStatementExtractionMeta,
    ): AssistantStatementExtractionResult {
        val warnings = mutableListOf<String>()
        val anchorDate = resolveAnchorDate(meta.statementPeriod)
        val config = meta.parserConfig
        val rows =
            when (normalizeToken(config.layoutType)) {
                LAYOUT_DATE_GROUPED_MULTILINE ->
                    parseDateGroupedMultiline(
                        lines = rawText.lines(),
                        config = config,
                        anchorDate = anchorDate,
                        warnings = warnings,
                    )
                LAYOUT_INLINE_ROWS ->
                    parseInlineRows(
                        lines = rawText.lines(),
                        config = config,
                        anchorDate = anchorDate,
                        warnings = warnings,
                    )
                else -> {
                    warnings += "unsupported_layout_type:${config.layoutType}"
                    emptyList()
                }
            }

        if (rows.isEmpty()) {
            warnings += "no_transaction_rows_parsed"
        }

        return AssistantStatementExtractionResult(rows = rows, warnings = warnings.distinct())
    }

    private fun parseDateGroupedMultiline(
        lines: List<String>,
        config: AssistantStatementParserConfig,
        anchorDate: LocalDate,
        warnings: MutableList<String>,
    ): List<ParsedTransaction> {
        val dateHeaderRegex =
            config.dateHeaderPattern
                ?.takeIf { it.isNotBlank() }
                ?.let(::compileRegex)
                ?: run {
                    warnings += "missing_date_header_pattern"
                    return emptyList()
                }
        val amountRegex = compileRegex(config.amountPattern)
        val creditRegexes = compileRegexes(config.creditPatterns)
        val debitRegexes = compileRegexes(config.debitPatterns)
        val skipRegexes = compileRegexes(config.skipLinePatterns)

        val rows = mutableListOf<ParsedTransaction>()
        val currentBlock = mutableListOf<String>()
        var currentDate: LocalDate? = null

        fun flushCurrentBlock() {
            if (currentBlock.isEmpty()) {
                return
            }
            val blockDate = currentDate
            if (blockDate == null) {
                warnings += "transaction_block_before_date_header"
                currentBlock.clear()
                return
            }
            parseTransactionBlock(
                lines = currentBlock.toList(),
                date = blockDate,
                amountRegex = amountRegex,
                config = config,
                creditRegexes = creditRegexes,
                debitRegexes = debitRegexes,
                warnings = warnings,
            )?.let(rows::add)
            currentBlock.clear()
        }

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) {
                return@forEach
            }

            val headerDateText = extractCapture(dateHeaderRegex, line)
            if (headerDateText != null) {
                flushCurrentBlock()
                currentDate = parseConfiguredDate(headerDateText, config.dateFormat, anchorDate, warnings, "date_header")
                return@forEach
            }

            if (matchesAny(skipRegexes, line)) {
                flushCurrentBlock()
                return@forEach
            }

            if (amountRegex.find(line) != null) {
                flushCurrentBlock()
                if (currentDate == null) {
                    warnings += "transaction_line_before_date_header:${line.take(120)}"
                    return@forEach
                }
                currentBlock += line
                return@forEach
            }

            if (currentBlock.isNotEmpty()) {
                currentBlock += line
            }
        }

        flushCurrentBlock()
        return rows
    }

    private fun parseInlineRows(
        lines: List<String>,
        config: AssistantStatementParserConfig,
        anchorDate: LocalDate,
        warnings: MutableList<String>,
    ): List<ParsedTransaction> {
        val inlineDateRegex =
            config.inlineTransactionDatePattern
                ?.takeIf { it.isNotBlank() }
                ?.let(::compileRegex)
                ?: run {
                    warnings += "missing_inline_transaction_date_pattern"
                    return emptyList()
                }
        val amountRegex = compileRegex(config.amountPattern)
        val creditRegexes = compileRegexes(config.creditPatterns)
        val debitRegexes = compileRegexes(config.debitPatterns)
        val skipRegexes = compileRegexes(config.skipLinePatterns)
        val rows = mutableListOf<ParsedTransaction>()

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || matchesAny(skipRegexes, line)) {
                return@forEach
            }

            val dateMatch = inlineDateRegex.find(line) ?: return@forEach
            val amountMatch = amountRegex.find(line) ?: return@forEach
            val dateText = extractCapture(dateMatch) ?: return@forEach
            val date =
                parseConfiguredDate(dateText, config.dateFormat, anchorDate, warnings, "inline_date")
                    ?: return@forEach

            buildParsedTransaction(
                lineFragments = listOf(line),
                date = date,
                amountMatch = amountMatch,
                amountSource = line,
                description = stripRanges(line, listOf(dateMatch.range, amountMatch.range)),
                config = config,
                creditRegexes = creditRegexes,
                debitRegexes = debitRegexes,
                warnings = warnings,
            )?.let(rows::add)
        }

        return rows
    }

    private fun parseTransactionBlock(
        lines: List<String>,
        date: LocalDate,
        amountRegex: Regex,
        config: AssistantStatementParserConfig,
        creditRegexes: List<Regex>,
        debitRegexes: List<Regex>,
        warnings: MutableList<String>,
    ): ParsedTransaction? {
        val firstLine = lines.firstOrNull()?.trim().orEmpty()
        if (firstLine.isBlank()) {
            return null
        }
        val amountMatch =
            amountRegex.find(firstLine)
                ?: run {
                    warnings += "amount_not_found_in_transaction_block:${firstLine.take(120)}"
                    return null
                }
        val rawBlock = lines.joinToString("\n")
        val descriptionParts = mutableListOf<String>()
        descriptionParts += stripRanges(firstLine, listOf(amountMatch.range))
        descriptionParts += lines.drop(1).map(String::trim).filter(String::isNotEmpty)
        val description = normalizeSpaces(descriptionParts.joinToString(" "))
        if (description.isBlank()) {
            warnings += "empty_description_in_transaction_block:${firstLine.take(120)}"
            return null
        }

        return buildParsedTransaction(
            lineFragments = lines,
            date = date,
            amountMatch = amountMatch,
            amountSource = rawBlock,
            description = description,
            config = config,
            creditRegexes = creditRegexes,
            debitRegexes = debitRegexes,
            warnings = warnings,
        )
    }

    private fun buildParsedTransaction(
        lineFragments: List<String>,
        date: LocalDate,
        amountMatch: MatchResult,
        amountSource: String,
        description: String,
        config: AssistantStatementParserConfig,
        creditRegexes: List<Regex>,
        debitRegexes: List<Regex>,
        warnings: MutableList<String>,
    ): ParsedTransaction? {
        val amountText = extractCapture(amountMatch) ?: amountMatch.value
        val creditDebitType =
            inferCreditDebitType(
                text = lineFragments.joinToString("\n"),
                amountText = amountText,
                defaultValue = config.defaultCreditDebitType,
                creditRegexes = creditRegexes,
                debitRegexes = debitRegexes,
            )
        if (creditDebitType == null) {
            warnings += "unable_to_infer_credit_debit_type:${description.take(120)}"
            return null
        }
        val amount =
            parseAmount(amountText, config.decimalSeparator)
                ?: run {
                    warnings += "invalid_amount:${amountText.take(80)}"
                    return null
                }

        return ParsedTransaction(
            creditDebitType = creditDebitType,
            amount = amount.abs(),
            date = date,
            description = description,
            descriptionRaw = amountSource,
        )
    }

    private fun inferCreditDebitType(
        text: String,
        amountText: String,
        defaultValue: String?,
        creditRegexes: List<Regex>,
        debitRegexes: List<Regex>,
    ): CreditDebitType? {
        if (matchesAny(creditRegexes, text)) {
            return CreditDebitType.CREDIT
        }
        if (matchesAny(debitRegexes, text)) {
            return CreditDebitType.DEBIT
        }
        if (amountText.contains("−") || amountText.trimStart().startsWith("-")) {
            return CreditDebitType.DEBIT
        }
        if (amountText.trimStart().startsWith("+")) {
            return CreditDebitType.CREDIT
        }
        return when (defaultValue?.trim()?.uppercase()) {
            "DEBIT" -> CreditDebitType.DEBIT
            "CREDIT" -> CreditDebitType.CREDIT
            else -> null
        }
    }

    private fun parseConfiguredDate(
        raw: String,
        dateFormat: String,
        anchorDate: LocalDate,
        warnings: MutableList<String>,
        context: String,
    ): LocalDate? =
        runCatching {
            when (normalizeToken(dateFormat)) {
                "DD_MMM_YYYY" -> parseDdMmmYyyy(raw, anchorDate)
                "DD_MM_YYYY" -> parseDdMmYyyy(raw)
                "DD_MM" -> parseDdMm(raw, anchorDate)
                else -> error("Unsupported date format: $dateFormat")
            }
        }.onFailure {
            warnings += "invalid_${context}:${raw.take(80)}"
        }.getOrNull()

    private fun parseDdMmmYyyy(
        raw: String,
        anchorDate: LocalDate,
    ): LocalDate {
        val parts = raw.trim().split(Regex("\\s+"))
        require(parts.size >= 2) { "Expected day and month in '$raw'" }
        val day = parts[0].toInt()
        val month = parsePtMonth(parts[1])
        val year = parts.getOrNull(2)?.toIntOrNull() ?: anchorDate.year
        var date = LocalDate.of(year, month, day)
        if (parts.size < 3 && date.isAfter(anchorDate)) {
            date = date.minusYears(1)
        }
        return date
    }

    private fun parseDdMmYyyy(raw: String): LocalDate {
        val parts = raw.trim().split(Regex("[/\\-]"))
        require(parts.size == 3) { "Expected dd/mm/yyyy in '$raw'" }
        return LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
    }

    private fun parseDdMm(
        raw: String,
        anchorDate: LocalDate,
    ): LocalDate {
        val parts = raw.trim().split(Regex("[/\\-]"))
        require(parts.size == 2) { "Expected dd/mm in '$raw'" }
        val day = parts[0].toInt()
        val month = parts[1].toInt()
        var date = LocalDate.of(anchorDate.year, month, day)
        if (date.isAfter(anchorDate)) {
            date = date.minusYears(1)
        }
        return date
    }

    private fun parsePtMonth(abbr: String): Int =
        when (abbr.trim().uppercase()) {
            "JAN" -> 1
            "FEV", "FEB" -> 2
            "MAR" -> 3
            "ABR", "APR" -> 4
            "MAI", "MAY" -> 5
            "JUN" -> 6
            "JUL" -> 7
            "AGO", "AUG" -> 8
            "SET", "SEP" -> 9
            "OUT", "OCT" -> 10
            "NOV" -> 11
            "DEZ", "DEC" -> 12
            else -> error("Unknown month abbreviation: $abbr")
        }

    private fun parseAmount(
        raw: String,
        decimalSeparator: String,
    ): BigDecimal? {
        val clean =
            raw
                .replace("−", "-")
                .replace(Regex("[^0-9,.-]"), "")
        val normalized =
            if (decimalSeparator.equals("comma", ignoreCase = true)) {
                clean.replace(".", "").replace(",", ".")
            } else {
                clean.replace(",", "")
            }
        return normalized.toBigDecimalOrNull()
    }

    private fun stripRanges(
        text: String,
        ranges: List<IntRange>,
    ): String {
        if (ranges.isEmpty()) {
            return normalizeSpaces(text)
        }
        val ordered = ranges.sortedBy { it.first }
        val builder = StringBuilder()
        var cursor = 0
        ordered.forEach { range ->
            if (range.first > cursor) {
                builder.append(text.substring(cursor, range.first)).append(' ')
            }
            cursor = maxOf(cursor, range.last + 1)
        }
        if (cursor < text.length) {
            builder.append(text.substring(cursor))
        }
        return normalizeSpaces(builder.toString())
    }

    private fun normalizeSpaces(value: String): String = value.replace(Regex("\\s+"), " ").trim()

    private fun resolveAnchorDate(statementPeriod: AssistantStatementPeriod?): LocalDate =
        statementPeriod?.to
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: statementPeriod?.from?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()

    private fun compileRegex(pattern: String): Regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE))

    private fun compileRegexes(patterns: List<String>): List<Regex> =
        patterns.filter { it.isNotBlank() }.map(::compileRegex)

    private fun matchesAny(
        regexes: List<Regex>,
        text: String,
    ): Boolean = regexes.any { it.containsMatchIn(text) }

    private fun extractCapture(match: MatchResult): String? =
        match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun extractCapture(
        regex: Regex,
        text: String,
    ): String? = regex.find(text)?.let(::extractCapture)

    private fun normalizeToken(value: String): String = value.trim().uppercase().replace(" ", "_").replace("-", "_")

    private companion object {
        const val LAYOUT_DATE_GROUPED_MULTILINE = "DATE_GROUPED_MULTILINE"
        const val LAYOUT_INLINE_ROWS = "INLINE_ROWS"
    }
}
