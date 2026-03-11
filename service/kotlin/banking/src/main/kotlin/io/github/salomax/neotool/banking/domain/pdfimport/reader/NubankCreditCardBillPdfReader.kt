package io.github.salomax.neotool.banking.domain.pdfimport.reader

import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutputMapper
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

private const val RECORD_TYPE_CARD_SUMMARY = "PDF_CARD_SUMMARY"
private const val RECORD_TYPE_CARD_TRANSACTION = "PDF_CARD_TRANSACTION"
private val INSTALLMENT_REGEX = Regex("""(?i)\b(\d{1,2})\s*/\s*(\d{1,2})\b""")
private val CARD_LAST_FOUR_REGEX = Regex("""(?i)(?:final|terminado em|ending)\s*(\d{4})""")
private val TOTAL_REGEX = Regex("""(?i)total(?:\s+da)?\s+fatura[^0-9\-]*([\-+]?\d[\d.,]*)""")

@Singleton
open class NubankCreditCardBillPdfReader : DeterministicPdfReader {
    override val readerId: String = "pdf-reader-nubank-credit-card-v1"
    override val supportedProfileIds: Set<String> = setOf("pdf-nubank-credit-card-bill-v1")

    override fun parse(input: DeterministicPdfReaderInput): CanonicalReaderOutput {
        val warnings = mutableListOf<String>()
        val metadata = input.extractionMetadata
        val statementYear = readStatementYear(metadata)
        if (statementYear == null) {
            warnings += "missing_statement_year"
        }

        val cardLastFourDigits = extractCardLastFourDigits(input.extractedText, metadata)
        if (cardLastFourDigits == null) {
            warnings += "missing_card_last_four_digits"
        }

        val transactionRecords = parseTransactions(input.extractedText, statementYear, cardLastFourDigits, warnings)
        val declaredTotal = extractDeclaredTotal(input.extractedText)
        val computedSigned =
            transactionRecords.fold(BigDecimal.ZERO) { acc, tx ->
                val amount = tx["amount"] as BigDecimal
                val signed =
                    if (tx["credit_debit_type"] == CreditDebitType.CREDIT.name) {
                        amount.negate()
                    } else {
                        amount
                    }
                acc + signed
            }
        val computedTotal = computedSigned.setScale(2)
        val totalMatchesDeclared =
            if (declaredTotal == null) {
                false
            } else {
                abs(declaredTotal.subtract(computedTotal).toDouble()) <= 0.10
            }

        val summaryRecord =
            linkedMapOf<String, Any?>(
                "record_type" to RECORD_TYPE_CARD_SUMMARY,
                "card_last_four_digits" to cardLastFourDigits,
                "declared_total" to declaredTotal,
                "computed_total" to computedTotal,
                "total_matches_declared" to totalMatchesDeclared,
                "confidence" to if (transactionRecords.isEmpty()) 0.0 else 0.98,
            )

        if (transactionRecords.isEmpty()) {
            warnings += "no_transaction_rows_parsed"
        }

        return CanonicalReaderOutput(
            schemaId = CanonicalReaderOutputMapper.SCHEMA_ID,
            schemaVersion = CanonicalReaderOutputMapper.SCHEMA_VERSION,
            processorType = CanonicalProcessorType.DETERMINISTIC,
            readerId = readerId,
            confidence = if (transactionRecords.isEmpty()) 0.0 else 0.98,
            warnings = warnings.distinct(),
            normalizedRecords = listOf(summaryRecord) + transactionRecords,
            rawMetadata =
                metadata +
                    mapOf(
                        "profile_id" to input.profileId,
                        "document_type" to input.documentType,
                        "statement_year" to statementYear,
                        "parsed_transaction_count" to transactionRecords.size,
                    ),
        )
    }

    private fun parseTransactions(
        extractedText: String,
        statementYear: Int?,
        cardLastFourDigits: String?,
        warnings: MutableList<String>,
    ): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        extractedText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("|") || !line.endsWith("|")) {
                return@forEach
            }
            val cells =
                line
                    .removePrefix("|")
                    .removeSuffix("|")
                    .split("|")
                    .map { it.trim() }

            if (cells.size < 3) return@forEach

            val first = cells[0]
            if (first.equals("data", ignoreCase = true) || first.contains("---")) return@forEach

            val date = parseDate(first, statementYear) ?: return@forEach
            val description = cells[1]
            val amountSigned = parseAmount(cells[2]) ?: return@forEach
            val normalizedDescription = description.ifBlank { "(sem descricao)" }
            val creditDebitType =
                if (amountSigned.signum() < 0 || normalizedDescription.uppercase(Locale.ROOT).contains("ESTORNO")) {
                    CreditDebitType.CREDIT
                } else {
                    CreditDebitType.DEBIT
                }
            val amount = amountSigned.abs().setScale(2)
            val installment = extractInstallment(normalizedDescription)

            rows +=
                linkedMapOf(
                    "record_type" to RECORD_TYPE_CARD_TRANSACTION,
                    "card_last_four_digits" to cardLastFourDigits,
                    "date" to date.toString(),
                    "description" to normalizedDescription,
                    "amount" to amount,
                    "credit_debit_type" to creditDebitType.name,
                    "installment_current" to installment?.first,
                    "installment_total" to installment?.second,
                    "confidence" to 0.98,
                )
        }

        if (rows.isNotEmpty() && statementYear == null) {
            warnings += "statement_year_inferred_from_row_values"
        }
        return rows
    }

    private fun parseDate(
        raw: String,
        statementYear: Int?,
    ): LocalDate? {
        val value = raw.trim()
        val parts = value.split("/").map { it.trim() }
        return when (parts.size) {
            3 -> {
                val day = parts[0].toIntOrNull() ?: return null
                val month = parts[1].toIntOrNull() ?: return null
                val year = parts[2].toIntOrNull() ?: return null
                runCatching { LocalDate.of(year, month, day) }.getOrNull()
            }
            2 -> {
                val year = statementYear ?: return null
                val day = parts[0].toIntOrNull() ?: return null
                val month = parts[1].toIntOrNull() ?: return null
                runCatching { LocalDate.of(year, month, day) }.getOrNull()
            }
            else -> runCatching { LocalDate.parse(value) }.getOrNull()
        }
    }

    private fun parseAmount(raw: String): BigDecimal? {
        val base =
            raw
                .replace("R$", "", ignoreCase = true)
                .replace(" ", "")
                .trim()
        val normalized =
            if (base.contains(",")) {
                // PT-BR decimal format (1.234,56)
                base.replace(".", "").replace(",", ".")
            } else {
                // Dot-decimal format (1234.56)
                base.replace(",", "")
            }
        return normalized.toBigDecimalOrNull()
    }

    private fun readStatementYear(metadata: Map<String, Any?>): Int? =
        when (val value = metadata["statement_year"] ?: metadata["reference_year"]) {
            null -> null
            is Number -> value.toInt()
            else -> value.toString().toIntOrNull()
        }

    private fun extractCardLastFourDigits(
        extractedText: String,
        metadata: Map<String, Any?>,
    ): String? {
        val fromMetadata = metadata["card_last_four_digits"]?.toString()?.filter(Char::isDigit)?.takeLast(4)
        if (!fromMetadata.isNullOrBlank() && fromMetadata.length == 4) {
            return fromMetadata
        }
        return CARD_LAST_FOUR_REGEX
            .find(extractedText)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun extractDeclaredTotal(extractedText: String): BigDecimal? =
        TOTAL_REGEX.find(extractedText)?.groupValues?.getOrNull(1)?.let(::parseAmount)

    private fun extractInstallment(description: String): Pair<Int, Int>? {
        val match = INSTALLMENT_REGEX.find(description) ?: return null
        val current = match.groupValues[1].toIntOrNull() ?: return null
        val total = match.groupValues[2].toIntOrNull() ?: return null
        return current to total
    }
}
