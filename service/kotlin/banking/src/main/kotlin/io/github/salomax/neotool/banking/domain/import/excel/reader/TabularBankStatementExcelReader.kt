package io.github.salomax.neotool.banking.domain.import.excel.reader

import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutputMapper
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.import.fingerprint.ExcelFingerprintClassifier
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Singleton
open class TabularBankStatementExcelReader : DeterministicExcelReader {
    override val readerId: String = "excel-reader-bank-statement-tabular-v1"
    override val supportedProfileIds: Set<String> = setOf(ExcelFingerprintClassifier.PROFILE_BANK_STATEMENT_TABULAR_V1)

    override fun parse(input: DeterministicExcelReaderInput): CanonicalReaderOutput {
        val warnings = mutableListOf<String>()
        var shortDateWithoutReferenceYear = false
        val referenceYear = readReferenceYear(input.extractionMetadata)

        var mapping: HeaderMapping? = null
        val rows = mutableListOf<ParsedTransaction>()

        input.extractedText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("## sheet[")) {
                mapping = null
                return@forEach
            }
            if (!line.startsWith("|") || !line.endsWith("|")) return@forEach

            val cells = parsePipeCells(line)
            if (cells.isEmpty()) return@forEach
            if (isSeparatorRow(cells)) return@forEach

            val payload = normalizeTablePayload(cells)
            if (payload.isEmpty()) return@forEach

            val header = detectHeaderMapping(payload)
            if (header != null) {
                mapping = header
                return@forEach
            }

            val activeMapping = mapping ?: return@forEach
            val parsed =
                parseRow(
                    payload = payload,
                    mapping = activeMapping,
                    referenceYear = referenceYear,
                    onShortDateWithoutReferenceYear = { shortDateWithoutReferenceYear = true },
                ) ?: return@forEach
            rows += parsed
        }

        if (rows.isEmpty()) {
            warnings += "no_transaction_rows_parsed"
        }
        if (shortDateWithoutReferenceYear) {
            warnings += "short_date_without_reference_year"
        }
        if (referenceYear == null) {
            warnings += "missing_reference_year"
        }

        return CanonicalReaderOutputMapper.fromParsedTransactions(
            rows = rows,
            processorType = CanonicalProcessorType.DETERMINISTIC,
            readerId = readerId,
            confidence = if (rows.isEmpty()) 0.0 else 0.95,
            warnings = warnings.distinct(),
            rawMetadata =
                input.extractionMetadata +
                    mapOf(
                        "profile_id" to input.profileId,
                        "document_type" to input.documentType,
                        "parsed_transaction_count" to rows.size,
                        "reference_year" to referenceYear,
                    ),
        )
    }

    private fun parseRow(
        payload: List<String>,
        mapping: HeaderMapping,
        referenceYear: Int?,
        onShortDateWithoutReferenceYear: () -> Unit,
    ): ParsedTransaction? {
        val dateRaw = payload.getOrNull(mapping.dateIndex).orEmpty()
        val date = parseDate(dateRaw, referenceYear)
        if (date == null) {
            if (isShortDate(dateRaw) && referenceYear == null) {
                onShortDateWithoutReferenceYear()
            }
            return null
        }

        val description = payload.getOrNull(mapping.descriptionIndex).orEmpty().trim().ifBlank { "(sem descricao)" }
        val amountSigned = parseSignedAmount(payload, mapping) ?: return null
        val creditDebitType =
            if (amountSigned.signum() < 0) {
                CreditDebitType.DEBIT
            } else {
                CreditDebitType.CREDIT
            }

        return ParsedTransaction(
            providerTransactionId = null,
            creditDebitType = creditDebitType,
            amount = amountSigned.abs().setScale(2, RoundingMode.HALF_UP),
            date = date,
            description = description,
            descriptionRaw = description,
            currencyCode = "BRL",
            transactionDatetime = null,
            counterpartyName = null,
            counterpartyDocument = null,
            totalInstallments = null,
        )
    }

    private fun parseSignedAmount(
        payload: List<String>,
        mapping: HeaderMapping,
    ): BigDecimal? {
        mapping.amountIndex?.let { idx ->
            return parseAmount(payload.getOrNull(idx))
        }
        val credit = mapping.creditIndex?.let { idx -> parseAmount(payload.getOrNull(idx)) } ?: BigDecimal.ZERO
        val debit = mapping.debitIndex?.let { idx -> parseAmount(payload.getOrNull(idx)) } ?: BigDecimal.ZERO
        if (credit.signum() == 0 && debit.signum() == 0) return null
        return credit.subtract(debit.abs())
    }

    private fun detectHeaderMapping(payload: List<String>): HeaderMapping? {
        val normalized = payload.map(::normalizeToken)
        val dateIndex = normalized.indexOfFirst { token -> token == "data" || token == "date" }
        val descriptionIndex =
            normalized.indexOfFirst { token ->
                token.contains("descricao") || token.contains("historico") || token.contains("lancamento")
            }
        val amountIndex = normalized.indexOfFirst { token -> token == "valor" || token == "amount" }
        val creditIndex = normalized.indexOfFirst { token -> token == "credito" }
        val debitIndex = normalized.indexOfFirst { token -> token == "debito" }

        if (dateIndex < 0 || descriptionIndex < 0) return null
        if (amountIndex < 0 && (creditIndex < 0 || debitIndex < 0)) return null

        return HeaderMapping(
            dateIndex = dateIndex,
            descriptionIndex = descriptionIndex,
            amountIndex = amountIndex.takeIf { it >= 0 },
            creditIndex = creditIndex.takeIf { it >= 0 },
            debitIndex = debitIndex.takeIf { it >= 0 },
        )
    }

    private fun normalizeTablePayload(cells: List<String>): List<String> {
        if (cells.isEmpty()) return emptyList()
        val firstToken = normalizeToken(cells.first())
        val payload =
            if (firstToken == "row" || firstToken.matches(ROW_NUMBER_REGEX)) {
                cells.drop(1)
            } else {
                cells
            }
        return payload.map { it.trim() }.dropLastWhile { it.isBlank() }
    }

    private fun parsePipeCells(line: String): List<String> =
        line
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { it.trim() }

    private fun isSeparatorRow(cells: List<String>): Boolean =
        cells.isNotEmpty() && cells.all { cell -> normalizeToken(cell).replace("-", "").isBlank() }

    private fun parseDate(
        raw: String,
        referenceYear: Int?,
    ): LocalDate? {
        val value = raw.trim()
        if (value.isBlank()) return null
        if (isShortDate(value)) {
            val parts = value.split("/")
            val day = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val year = referenceYear ?: return null
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        val parts = value.split("/")
        if (parts.size == 3) {
            val day = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val year = parts[2].toIntOrNull() ?: return null
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        return try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun isShortDate(value: String): Boolean = SHORT_DATE_REGEX.matches(value.trim())

    private fun parseAmount(raw: String?): BigDecimal? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "-") return null

        val hasParentheses = trimmed.startsWith("(") && trimmed.endsWith(")")
        val normalizedSign =
            trimmed
                .removePrefix("(")
                .removeSuffix(")")
                .replace("R$", "", ignoreCase = true)
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".")
                .trim()
        val parsed = normalizedSign.toBigDecimalOrNull() ?: return null
        return if (hasParentheses) parsed.negate() else parsed
    }

    private fun readReferenceYear(metadata: Map<String, Any?>): Int? =
        when (val value = metadata["reference_year"] ?: metadata["statement_year"]) {
            null -> null
            is Number -> value.toInt()
            else -> value.toString().toIntOrNull()
        }

    private fun normalizeToken(value: String): String =
        Normalizer
            .normalize(value, Normalizer.Form.NFD)
            .replace(ACCENT_REGEX, "")
            .lowercase()
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private data class HeaderMapping(
        val dateIndex: Int,
        val descriptionIndex: Int,
        val amountIndex: Int?,
        val creditIndex: Int?,
        val debitIndex: Int?,
    )

    private companion object {
        private val ACCENT_REGEX = Regex("\\p{M}+")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val SHORT_DATE_REGEX = Regex("""\d{1,2}/\d{1,2}""")
        private val ROW_NUMBER_REGEX = Regex("""\d+""")
    }
}
