package io.github.salomax.neotool.banking.service.billimport

import io.github.salomax.neotool.banking.domain.billimport.*
import jakarta.inject.Singleton
import java.text.Normalizer
import java.time.LocalDate

@Singleton
class AssistantBillTransactionExtractor {

    fun extract(rawText: String, meta: AssistantBillExtractionMeta): AssistantBillExtractionResult {
        val lines = rawText.lines()
        val closingDate = LocalDate.parse(meta.closingDate)
        val transactionMatchers = buildAssistantTransactionMatchers(meta.transactionPattern)
        val installmentRegex = meta.transactionPattern.installmentPattern?.let { Regex(it) }
        val warnings = mutableListOf<String>()

        val cards = extractCards(lines, meta, closingDate, transactionMatchers, installmentRegex, warnings)
        return AssistantBillExtractionResult(cards = cards, warnings = warnings)
    }

    // --- Regex builder ---

    private fun buildAssistantTransactionMatchers(pattern: AssistantBillTransactionPattern): List<AssistantTransactionMatcher> {
        val fmt = pattern.dateFormat.uppercase().replace(" ", "_").replace("-", "_")
        val datePart =
            when {
                fmt.startsWith("DD_MMM") -> """(\d{1,2}\s+\w{3})"""
                fmt.contains("YYYY") -> """(\d{2}/\d{2}/\d{4})"""
                else -> """(\d{2}/\d{2})"""
            }
        val optionalRowIndex = """(?:\d+\s+)?"""

        val escapedCurrencies =
            pattern.currencySymbols
                .ifEmpty { listOf("R$") }
                .joinToString("|") { Regex.escape(it) }

        val amountPat =
            if (pattern.decimalSeparator.lowercase() == "period")
                """([\-−]?(?:(?:$escapedCurrencies)\s*)?[\-−]?[\d,]+\.\d{2})"""
            else
                """([\-−]?(?:(?:$escapedCurrencies)\s*)?[\-−]?[\d.]+,\d{2})"""

        val baseFlags = setOf(RegexOption.IGNORE_CASE)
        val noCardRegex = Regex("""$optionalRowIndex$datePart\s+(.+?)\s+$amountPat""", baseFlags)
        val lastFourPattern = pattern.lastFourPattern
        if (lastFourPattern == null) {
            return listOf(AssistantTransactionMatcher(regex = noCardRegex, extractsInlineLastFour = false))
        }

        val inlineRegexes =
            normalizedInlineLastFourPatterns(lastFourPattern).map { normalizedPattern ->
                // lastFourPattern already contains exactly one capturing group (the 4-digit suffix).
                // Do NOT wrap it in another group — that would shift all subsequent group indices.
                AssistantTransactionMatcher(
                    regex = Regex("""$optionalRowIndex$datePart\s+${normalizedPattern}\s+(.+?)\s+$amountPat""", baseFlags),
                    extractsInlineLastFour = true,
                )
            }
        return inlineRegexes + AssistantTransactionMatcher(regex = noCardRegex, extractsInlineLastFour = false)
    }

    // --- Card extraction ---

    private fun extractCards(
        lines: List<String>,
        meta: AssistantBillExtractionMeta,
        closingDate: LocalDate,
        transactionMatchers: List<AssistantTransactionMatcher>,
        installmentRegex: Regex?,
        warnings: MutableList<String>,
    ): List<AssistantExtractedCard> =
        if (transactionMatchers.any { it.extractsInlineLastFour }) {
            extractCardsInlineId(lines, meta, closingDate, transactionMatchers, installmentRegex, warnings)
        } else {
            extractCardsByBlocks(lines, meta, closingDate, transactionMatchers.first().regex, installmentRegex, warnings)
        }

    /**
     * Standard path: each card has its own section identified by anchor_start.
     * One AssistantExtractedCard is produced per AssistantBillCardBlock.
     */
    private fun extractCardsByBlocks(
        lines: List<String>,
        meta: AssistantBillExtractionMeta,
        closingDate: LocalDate,
        txRegex: Regex,
        installmentRegex: Regex?,
        warnings: MutableList<String>,
    ): List<AssistantExtractedCard> {
        val startLines = findStartLines(lines, meta.cardBlocks, warnings)

        return meta.cardBlocks.mapIndexed { index, block ->
            val startLine = startLines[index] ?: return@mapIndexed buildAssistantExtractedCard(block.lastFour ?: "", block.declaredTotal, emptyList())

            val endLine = startLines.drop(index + 1).firstOrNull { it != null } ?: lines.size
            val blockLines = if (startLine + 1 >= endLine) emptyList() else lines.subList(startLine + 1, endLine)

            val transactions =
                parseTransactionLines(
                    lines = blockLines,
                    closingDate = closingDate,
                    transactionMatchers = listOf(AssistantTransactionMatcher(regex = txRegex, extractsInlineLastFour = false)),
                    blockLastFour = block.lastFour ?: "",
                    dateFormat = meta.transactionPattern.dateFormat,
                    installmentRegex = installmentRegex,
                    warnings = warnings,
                )
            buildAssistantExtractedCard(block.lastFour ?: "", block.declaredTotal, transactions)
        }
    }

    /**
     * Inline-card path: card number is embedded in each transaction row via last_four_pattern.
     * Transactions are collected across all block sections, then grouped by extracted card number.
     * One AssistantExtractedCard is produced per distinct card number found.
     */
    private fun extractCardsInlineId(
        lines: List<String>,
        meta: AssistantBillExtractionMeta,
        closingDate: LocalDate,
        transactionMatchers: List<AssistantTransactionMatcher>,
        installmentRegex: Regex?,
        warnings: MutableList<String>,
    ): List<AssistantExtractedCard> {
        if (meta.cardBlocks.isEmpty()) {
            val allTransactions =
                parseTransactionLines(
                    lines = lines,
                    closingDate = closingDate,
                    transactionMatchers = transactionMatchers,
                    blockLastFour = "",
                    dateFormat = meta.transactionPattern.dateFormat,
                    installmentRegex = installmentRegex,
                    warnings = warnings,
                )
            return groupTransactionsByCard(meta, allTransactions)
        }

        val startLines = findStartLines(lines, meta.cardBlocks, warnings)

        val allTransactions = mutableListOf<AssistantExtractedTransaction>()
        for ((index, block) in meta.cardBlocks.withIndex()) {
            val startLine = startLines[index] ?: continue
            val endLine = startLines.drop(index + 1).firstOrNull { it != null } ?: lines.size
            val blockLines = if (startLine + 1 >= endLine) emptyList() else lines.subList(startLine + 1, endLine)
            allTransactions +=
                parseTransactionLines(
                    lines = blockLines,
                    closingDate = closingDate,
                    transactionMatchers = transactionMatchers,
                    blockLastFour = "",
                    dateFormat = meta.transactionPattern.dateFormat,
                    installmentRegex = installmentRegex,
                    warnings = warnings,
                )
        }

        return groupTransactionsByCard(meta, allTransactions)
    }

    private fun groupTransactionsByCard(
        meta: AssistantBillExtractionMeta,
        allTransactions: List<AssistantExtractedTransaction>,
    ): List<AssistantExtractedCard> {
        // Group by extracted card number; preserve document order of first appearance
        val grouped = LinkedHashMap<String, MutableList<AssistantExtractedTransaction>>()
        for (tx in allTransactions) {
            grouped.getOrPut(tx.cardLastFour) { mutableListOf() } += tx
        }

        return grouped.map { (lastFour, transactions) ->
            val declaredTotal = meta.cardBlocks.find { it.lastFour == lastFour }?.declaredTotal
            buildAssistantExtractedCard(lastFour, declaredTotal, transactions)
        }
    }

    private fun findStartLines(lines: List<String>, blocks: List<AssistantBillCardBlock>, warnings: MutableList<String>): List<Int?> {
        val startLines = mutableListOf<Int?>()
        var searchFrom = 0
        for (block in blocks) {
            val found = findAnchorLine(lines, block.anchorStart, searchFrom)
            startLines += found
            if (found != null) searchFrom = found + 1
            else warnings += "anchor_start '${block.anchorStart}' not found in document"
        }
        return startLines
    }

    private fun findAnchorLine(lines: List<String>, anchor: String, startFrom: Int): Int? {
        val start = maxOf(0, startFrom)
        val normalizedAnchor = nfc(anchor)
        for (i in start until lines.size) {
            if (nfc(lines[i]).contains(normalizedAnchor)) return i
        }
        return null
    }

    private fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

    // --- Transaction line parsing ---

    private fun parseTransactionLines(
        lines: List<String>,
        closingDate: LocalDate,
        transactionMatchers: List<AssistantTransactionMatcher>,
        blockLastFour: String,
        dateFormat: String,
        installmentRegex: Regex?,
        warnings: MutableList<String>,
    ): List<AssistantExtractedTransaction> {
        val transactions = mutableListOf<AssistantExtractedTransaction>()

        for (line in lines) {
            for ((matcher, match) in findMatches(line, transactionMatchers)) {
                val minGroups = if (matcher.extractsInlineLastFour) 5 else 4
                if (match.groupValues.size < minGroups) {
                    warnings += "transaction_regex matched but has < ${minGroups - 1} capture groups on line: $line"
                    continue
                }

                val dateRaw: String
                val lastFour: String
                val descriptionRaw: String
                val amountRaw: String

                if (matcher.extractsInlineLastFour) {
                    dateRaw = match.groupValues[1]
                    lastFour = match.groupValues[2].trim()
                    descriptionRaw = match.groupValues[3].trim()
                    amountRaw = match.groupValues[4]
                } else {
                    dateRaw = match.groupValues[1]
                    val parsedDescriptionRaw = match.groupValues[2].trim()
                    val inlineCardMatch =
                        if (blockLastFour.isBlank()) {
                            extractLeadingInlineLastFour(parsedDescriptionRaw)
                        } else {
                            null
                        }
                    lastFour = inlineCardMatch?.first ?: blockLastFour
                    descriptionRaw = inlineCardMatch?.second ?: parsedDescriptionRaw
                    amountRaw = match.groupValues[3]
                }

                val date =
                    try {
                        parseDate(dateRaw, dateFormat, closingDate)
                    } catch (e: Exception) {
                        warnings += "Failed to parse date '$dateRaw' on line: $line — ${e.message}"
                        continue
                    }

                val (amount, creditDebitType) =
                    try {
                        parseAmount(amountRaw)
                    } catch (e: Exception) {
                        warnings += "Failed to parse amount '$amountRaw' on line: $line — ${e.message}"
                        continue
                    }

                if (amount == 0.0) {
                    continue
                }

                val (description, installment) = extractAssistantBillInstallment(descriptionRaw, installmentRegex)

                val isBillPayment = detectIsBillPayment(description)
                val finalCreditDebitType = if (isBillPayment) "CREDIT" else creditDebitType

                transactions +=
                    AssistantExtractedTransaction(
                        date = date,
                        description = description,
                        amount = amount,
                        currencyCode = inferCurrencyCode(amountRaw),
                        creditDebitType = finalCreditDebitType,
                        cardLastFour = lastFour,
                        installment = installment,
                        isBillPayment = isBillPayment,
                    )
            }
        }

        return deduplicateTransactions(transactions)
    }

    private fun deduplicateTransactions(
        transactions: List<AssistantExtractedTransaction>,
    ): List<AssistantExtractedTransaction> {
        val deduplicated = mutableListOf<AssistantExtractedTransaction>()

        for (transaction in transactions) {
            val duplicateIndex =
                deduplicated.indexOfFirst { existing -> arePotentialDuplicates(existing, transaction) }
            if (duplicateIndex == -1) {
                deduplicated += transaction
            } else {
                deduplicated[duplicateIndex] = mergeDuplicateTransactions(deduplicated[duplicateIndex], transaction)
            }
        }

        return deduplicated
    }

    private fun arePotentialDuplicates(
        existing: AssistantExtractedTransaction,
        candidate: AssistantExtractedTransaction,
    ): Boolean {
        if (existing.date != candidate.date) return false
        if (existing.amount != candidate.amount) return false
        if (existing.currencyCode != candidate.currencyCode) return false
        if (normalizeForSearch(existing.description) != normalizeForSearch(candidate.description)) return false
        if (existing.cardLastFour.isNotBlank() && candidate.cardLastFour.isNotBlank() && existing.cardLastFour != candidate.cardLastFour) {
            return false
        }
        val existingAssistantBillInstallment = existing.installment
        val candidateAssistantBillInstallment = candidate.installment
        if (existingAssistantBillInstallment != null && candidateAssistantBillInstallment != null && existingAssistantBillInstallment != candidateAssistantBillInstallment) {
            return false
        }
        return true
    }

    private fun mergeDuplicateTransactions(
        first: AssistantExtractedTransaction,
        second: AssistantExtractedTransaction,
    ): AssistantExtractedTransaction {
        val preferred = if (transactionRichness(second) > transactionRichness(first)) second else first
        val mergedIsBillPayment = first.isBillPayment || second.isBillPayment
        return preferred.copy(
            description = preferRicherDescription(first.description, second.description),
            creditDebitType = if (mergedIsBillPayment) "CREDIT" else preferred.creditDebitType,
            cardLastFour = first.cardLastFour.ifBlank { second.cardLastFour },
            installment = first.installment ?: second.installment,
            isBillPayment = mergedIsBillPayment,
        )
    }

    private fun transactionRichness(transaction: AssistantExtractedTransaction): Int {
        var score = 0
        if (transaction.cardLastFour.isNotBlank()) score += 4
        if (transaction.installment != null) score += 2
        if (transaction.isBillPayment) score += 2
        score += minOf(transaction.description.trim().length, 80) / 10
        return score
    }

    private fun preferRicherDescription(
        first: String,
        second: String,
    ): String {
        val firstTrimmed = first.trim()
        val secondTrimmed = second.trim()
        return if (secondTrimmed.length > firstTrimmed.length) secondTrimmed else firstTrimmed
    }

    private fun findMatches(
        line: String,
        transactionMatchers: List<AssistantTransactionMatcher>,
    ): List<Pair<AssistantTransactionMatcher, MatchResult>> {
        val normalizedLine = normalizeMaskedCardGlyphs(line)
        val results = mutableListOf<Pair<AssistantTransactionMatcher, MatchResult>>()
        var startIndex = 0

        while (startIndex < normalizedLine.length) {
            val nextMatch =
                transactionMatchers
                    .mapNotNull { matcher -> matcher.regex.find(normalizedLine, startIndex)?.let { matcher to it } }
                    .minWithOrNull(
                        compareBy<Pair<AssistantTransactionMatcher, MatchResult>> { it.second.range.first }
                            .thenByDescending { it.first.extractsInlineLastFour },
                    )
                    ?: break

            results += nextMatch
            val nextIndex = nextMatch.second.range.last + 1
            if (nextIndex <= startIndex) {
                break
            }
            startIndex = nextIndex
        }

        return results
    }

    private fun normalizeMaskedCardGlyphs(line: String): String =
        line
            .replace('•', '*')
            .replace('●', '*')
            .replace('▪', '*')
            .replace('·', '*')
            .replace(Regex("""(\*{4})\s+(?=\d{4}\b)"""), "$1")

    private fun extractLeadingInlineLastFour(descriptionRaw: String): Pair<String, String>? {
        val normalizedDescription = normalizeMaskedCardGlyphs(descriptionRaw)
        val match = Regex("""^\*{4}\s*(\d{4})\s+(.+)$""").find(normalizedDescription) ?: return null
        return match.groupValues[1] to match.groupValues[2].trim()
    }

    private fun normalizedInlineLastFourPatterns(lastFourPattern: String): List<String> {
        val normalized = linkedSetOf(lastFourPattern)
        normalized +=
            lastFourPattern
                .replace("""\\.{4}""", """\*{4}""")
                .replace(""".{4}""", """\*{4}""")
        return normalized.toList()
    }

    private fun buildAssistantExtractedCard(
        lastFour: String,
        declaredTotal: Double?,
        transactions: List<AssistantExtractedTransaction>,
    ): AssistantExtractedCard {
        val computedTotal =
            transactions
                .filter { !it.isBillPayment }
                .sumOf { if (it.creditDebitType == "DEBIT") it.amount else -it.amount }
        val totalMatchesDeclared =
            declaredTotal != null && Math.abs(computedTotal - declaredTotal) <= 0.01
        return AssistantExtractedCard(
            lastFour = lastFour,
            declaredTotal = declaredTotal,
            computedTotal = computedTotal,
            totalMatchesDeclared = totalMatchesDeclared,
            transactions = transactions,
        )
    }

    // --- Date parsing ---

    private fun parseDate(raw: String, format: String, anchor: LocalDate): LocalDate {
        val fmt = format.uppercase().replace(" ", "_").replace("-", "_")
        return when {
            fmt.startsWith("DD_MMM") -> parseDdMmm(raw.trim(), anchor)
            fmt.contains("YYYY") -> parseDdMmYyyy(raw.trim())
            else -> parseDdMm(raw.trim(), anchor)
        }
    }

    private fun parseDdMmm(raw: String, anchor: LocalDate): LocalDate {
        val parts = raw.split(Regex("\\s+"))
        val day = parts[0].toInt()
        val month = parsePtMonth(parts[1])
        val year = if (parts.size >= 3) parts[2].toInt() else anchor.year
        var date = LocalDate.of(year, month, day)
        if (date.isAfter(anchor)) date = date.minusYears(1)
        return date
    }

    private fun parseDdMm(raw: String, anchor: LocalDate): LocalDate {
        val parts = raw.split("/")
        val day = parts[0].toInt()
        val month = parts[1].toInt()
        var date = LocalDate.of(anchor.year, month, day)
        if (date.isAfter(anchor)) date = date.minusYears(1)
        return date
    }

    private fun parseDdMmYyyy(raw: String): LocalDate {
        val parts = raw.split(Regex("[/\\-]"))
        return LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
    }

    private fun parsePtMonth(abbr: String): Int =
        when (abbr.uppercase()) {
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
            else -> throw IllegalArgumentException("Unknown month abbreviation: $abbr")
        }

    // --- Amount parsing ---

    private fun parseAmount(raw: String): Pair<Double, String> {
        val isCredit = raw.contains("−") || raw.trimStart().startsWith("-")
        val clean =
            raw
                .replace("−", "")
                .replace("-", "")
                // Order matters: multi-char symbols before single-char ones (e.g. "US$" before "$")
                .replace(Regex("R\\$|US\\$|AU\\$|CA\\$|USD|EUR|€|GBP|£|\\$"), "")
                .replace("\\s".toRegex(), "")
        // pt-BR: 1.234,56 → 1234.56
        val normalized =
            if (clean.contains(",")) {
                clean.replace(".", "").replace(",", ".")
            } else {
                clean
            }
        val amount = normalized.toDouble()
        return Pair(amount, if (isCredit) "CREDIT" else "DEBIT")
    }

    private fun inferCurrencyCode(amountRaw: String): String =
        when {
            amountRaw.contains("US$") || amountRaw.uppercase().contains("USD") -> "USD"
            amountRaw.contains("€") || amountRaw.uppercase().contains("EUR") -> "EUR"
            amountRaw.contains("£") || amountRaw.uppercase().contains("GBP") -> "GBP"
            else -> "BRL"
        }

    // --- AssistantBillInstallment extraction ---

    private fun extractAssistantBillInstallment(
        description: String,
        installmentRegex: Regex?,
    ): Pair<String, AssistantBillInstallment?> {
        if (installmentRegex == null) return Pair(description, null)
        val match = installmentRegex.find(description) ?: return Pair(description, null)
        val current = match.groupValues.getOrNull(1)?.toIntOrNull()
        val total = match.groupValues.getOrNull(2)?.toIntOrNull()
        if (current == null || total == null) return Pair(description, null)
        val cleanedDescription =
            description
                .replace(match.value, "")
                .replace(Regex("\\(\\s*\\)"), "")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
        return Pair(cleanedDescription, AssistantBillInstallment(current, total))
    }

    // --- Bill payment detection ---

    private fun detectIsBillPayment(description: String): Boolean {
        val normalized = normalizeForSearch(description)
        return BILL_PAYMENT_KEYWORDS.any { normalized.contains(it) }
    }

    private fun normalizeForSearch(text: String): String {
        val nfd = Normalizer.normalize(text.uppercase(), Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{M}"), "")
    }

    private companion object {
        private val BILL_PAYMENT_KEYWORDS =
            listOf(
                "PAGAMENTO DE FATURA",
                "PAGAMENTO FATURA",
                "PAGTO FATURA",
                "PGTO FATURA",
                "PAGAMENTO DE CARTAO",
                "PAGAMENTO CARTAO",
                "PAGAMENTO EM ",
                // Banco do Brasil statements often label card bill payments this way.
                "PGTO DEBITO CONTA",
                "PAG DEBITO AUTOMATICO",
                "PAGAMENTO VIA PIX",
                "PAGAMENTO APP",
            )
    }
}

data class AssistantBillInstallment(val current: Int, val total: Int)

data class AssistantExtractedTransaction(
    val date: LocalDate,
    val description: String,
    val amount: Double,
    /** ISO 4217 currency code inferred from the amount symbol: "BRL", "USD", "EUR", "GBP". */
    val currencyCode: String,
    val creditDebitType: String,
    val cardLastFour: String,
    val installment: AssistantBillInstallment?,
    val isBillPayment: Boolean,
)

data class AssistantExtractedCard(
    val lastFour: String,
    val declaredTotal: Double?,
    val computedTotal: Double,
    val totalMatchesDeclared: Boolean,
    val transactions: List<AssistantExtractedTransaction>,
)

data class AssistantBillExtractionResult(
    val cards: List<AssistantExtractedCard>,
    val warnings: List<String>,
)

private data class AssistantTransactionMatcher(
    val regex: Regex,
    val extractsInlineLastFour: Boolean,
)
