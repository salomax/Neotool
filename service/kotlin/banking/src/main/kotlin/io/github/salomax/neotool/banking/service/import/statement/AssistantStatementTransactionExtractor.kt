package io.github.salomax.neotool.banking.service.import.statement

import io.github.salomax.neotool.banking.domain.import.*
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.model.dto.imports.AssistantStatementExtractionResult
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate

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
        val dateHeaderRegexes = buildDateHeaderRegexes(config.dateHeaderPattern, config.dateFormat, warnings)
        val amountRegex = compileRegex(config.amountPattern)
        val creditRegexes = compileRegexes(config.creditPatterns)
        val debitRegexes = compileRegexes(config.debitPatterns)
        val skipRegexes = compileRegexes(config.skipLinePatterns)
        val ignoreLineKinds = normalizeIgnoreLineKinds(config.ignoreLineKinds)

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

            val headerDateText = extractFirstCapture(dateHeaderRegexes, line)
            if (headerDateText != null) {
                flushCurrentBlock()
                currentDate = parseConfiguredDate(headerDateText, config.dateFormat, anchorDate, warnings, "date_header")
                return@forEach
            }

            if (shouldIgnoreLine(line, currentBlock.isNotEmpty(), ignoreLineKinds, skipRegexes)) {
                flushCurrentBlock()
                return@forEach
            }

            if (findAmountMatch(amountRegex, line) != null) {
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
        val ignoreLineKinds = normalizeIgnoreLineKinds(config.ignoreLineKinds)
        val rows = mutableListOf<ParsedTransaction>()

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || shouldIgnoreLine(line, false, ignoreLineKinds, skipRegexes)) {
                return@forEach
            }

            val dateMatch = inlineDateRegex.find(line) ?: return@forEach
            val amountMatch = findAmountMatch(amountRegex, line) ?: return@forEach
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
            findAmountMatch(amountRegex, firstLine)
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
                signRule = config.signRule,
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
        signRule: String?,
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
        when (normalizeToken(signRule.orEmpty())) {
            "KEYWORD_BASED" -> inferByKeywords(text)?.let { return it }
            "NEGATIVE_IS_DEBIT" -> inferByAmountSign(amountText)?.let { return it }
            "DEFAULT_TYPE" -> {}
        }
        inferByAmountSign(amountText)?.let { return it }
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
        val normalized =
            raw
                .trim()
                .replace(Regex("\\bDE\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        val parts = normalized.split(Regex("\\s+"))
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
            "JAN", "JANEIRO" -> 1
            "FEV", "FEB", "FEVEREIRO", "FEBRUARY" -> 2
            "MAR", "MARCO", "MARÇO", "MARCH" -> 3
            "ABR", "APR", "ABRIL", "APRIL" -> 4
            "MAI", "MAY", "MAIO" -> 5
            "JUN", "JUNHO" -> 6
            "JUL", "JULHO" -> 7
            "AGO", "AUG", "AGOSTO", "AUGUST" -> 8
            "SET", "SEP", "SETEMBRO", "SEPTEMBER" -> 9
            "OUT", "OCT", "OUTUBRO", "OCTOBER" -> 10
            "NOV", "NOVEMBRO" -> 11
            "DEZ", "DEC", "DEZEMBRO", "DECEMBER" -> 12
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

    private fun shouldIgnoreLine(
        line: String,
        currentBlockOpen: Boolean,
        ignoreLineKinds: Set<String>,
        legacySkipRegexes: List<Regex>,
    ): Boolean {
        if (matchesAny(legacySkipRegexes, line)) {
            return true
        }
        val normalized = normalizeForSearch(line)
        if ("DAY_TOTAL" in ignoreLineKinds && DAY_TOTAL_KEYWORDS.any { normalized.contains(it) }) {
            return true
        }
        if ("RUNNING_BALANCE" in ignoreLineKinds && RUNNING_BALANCE_KEYWORDS.any { normalized.contains(it) }) {
            return true
        }
        if (!currentBlockOpen && "HEADER" in ignoreLineKinds && HEADER_KEYWORDS.any { normalized.contains(it) }) {
            return true
        }
        if (!currentBlockOpen && "FOOTER" in ignoreLineKinds && FOOTER_KEYWORDS.any { normalized.contains(it) }) {
            return true
        }
        if (!currentBlockOpen && "SECTION_HEADER" in ignoreLineKinds && SECTION_HEADER_KEYWORDS.any { normalized.contains(it) }) {
            return true
        }
        return false
    }

    private fun inferByAmountSign(amountText: String): CreditDebitType? =
        when {
            amountText.contains("−") || amountText.trimStart().startsWith("-") -> CreditDebitType.DEBIT
            amountText.trimStart().startsWith("+") -> CreditDebitType.CREDIT
            else -> null
        }

    private fun inferByKeywords(text: String): CreditDebitType? {
        val normalized = normalizeForSearch(text)
        if (CREDIT_KEYWORDS.any { normalized.contains(it) }) {
            return CreditDebitType.CREDIT
        }
        if (DEBIT_KEYWORDS.any { normalized.contains(it) }) {
            return CreditDebitType.DEBIT
        }
        return null
    }

    private fun normalizeIgnoreLineKinds(kinds: List<String>): Set<String> =
        kinds.map(::normalizeToken).toSet()

    private fun normalizeForSearch(text: String): String {
        val nfd = Normalizer.normalize(text.uppercase(), Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{M}+"), "")
    }

    private fun resolveAnchorDate(statementPeriod: AssistantStatementPeriod?): LocalDate =
        statementPeriod?.to
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: statementPeriod?.from?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()

    private fun compileRegex(pattern: String): Regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE))

    private fun buildDateHeaderRegexes(
        configuredPattern: String?,
        dateFormat: String,
        warnings: MutableList<String>,
    ): List<Regex> {
        val regexes = mutableListOf<Regex>()
        configuredPattern
            ?.takeIf { it.isNotBlank() }
            ?.let { regexes += compileRegex(it) }

        val fallbackPattern =
            when (normalizeToken(dateFormat)) {
                "DD_MMM_YYYY" -> "^(\\d{2}(?:\\s+DE)?\\s+[A-ZÇ]{3,}(?:\\s+DE)?\\s+\\d{4})\\b"
                "DD_MM_YYYY" -> "^(\\d{2}[/-]\\d{2}[/-]\\d{4})\\b"
                "DD_MM" -> "^(\\d{2}[/-]\\d{2})\\b"
                else -> null
            }

        fallbackPattern
            ?.takeIf { configuredPattern.isNullOrBlank() || configuredPattern != it }
            ?.let { regexes += compileRegex(it) }

        if (regexes.isEmpty()) {
            warnings += "missing_date_header_pattern"
        }
        return regexes
    }

    private fun compileRegexes(patterns: List<String>): List<Regex> =
        patterns.filter { it.isNotBlank() }.map(::compileRegex)

    private fun findAmountMatch(
        regex: Regex,
        text: String,
    ): MatchResult? =
        regex
            .findAll(text)
            .filter { match ->
                val value = extractCapture(match) ?: match.value
                looksLikeMonetaryAmount(value)
            }.lastOrNull()

    private fun looksLikeMonetaryAmount(value: String): Boolean {
        val normalized =
            value
                .trim()
                .replace("−", "-")
                .replace(Regex("[^0-9,.-]"), "")
        return AMOUNT_WITH_DECIMALS_REGEX.matches(normalized)
    }

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

    private fun extractFirstCapture(
        regexes: List<Regex>,
        text: String,
    ): String? = regexes.firstNotNullOfOrNull { regex -> extractCapture(regex, text) }

    private fun normalizeToken(value: String): String = value.trim().uppercase().replace(" ", "_").replace("-", "_")

    private companion object {
        const val LAYOUT_DATE_GROUPED_MULTILINE = "DATE_GROUPED_MULTILINE"
        const val LAYOUT_INLINE_ROWS = "INLINE_ROWS"
        val DAY_TOTAL_KEYWORDS =
            listOf("TOTAL DE SAIDAS", "TOTAL DE ENTRADAS", "TOTAL DO DIA", "TOTAL CREDITADO", "TOTAL DEBITADO")
        val RUNNING_BALANCE_KEYWORDS =
            listOf("SALDO", "BALANCE", "DISPONIVEL", "SALDO FINAL", "SALDO ANTERIOR")
        val HEADER_KEYWORDS =
            listOf("EXTRATO", "STATEMENT", "PERIODO", "PERIODO DO EXTRATO", "TITULAR", "CLIENTE")
        val FOOTER_KEYWORDS =
            listOf("PAGINA ", "PAGE ", "EMITIDO EM", "GERADO EM", "DOCUMENTO EMITIDO")
        val SECTION_HEADER_KEYWORDS =
            listOf("LANCAMENTOS", "MOVIMENTACOES", "TRANSACOES", "DETALHAMENTO", "RESUMO")
        val CREDIT_KEYWORDS =
            listOf("RECEB", "DEPOSITO", "SALARIO", "ESTORNO", "CREDITO", "ENTRADA", "RENDIMENTO")
        val DEBIT_KEYWORDS =
            listOf("ENVIAD", "PAGAMENTO", "COMPRA", "SAQUE", "TARIFA", "DEBITO", "SAIDA", "RETIRADA", "BOLETO")
        val AMOUNT_WITH_DECIMALS_REGEX = Regex("^[+-]?(?:\\d{1,3}(?:[.,]\\d{3})*|\\d+)[.,]\\d{2}$")
    }
}
