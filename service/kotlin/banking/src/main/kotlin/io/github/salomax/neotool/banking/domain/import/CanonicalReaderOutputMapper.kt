package io.github.salomax.neotool.banking.domain.import

import io.github.salomax.neotool.banking.domain.import.parser.csv.GenericCsvReader
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

object CanonicalReaderOutputMapper {
    const val SCHEMA_ID = "banking.reader-output.canonical"
    const val SCHEMA_VERSION = "1.0"
    private const val RECORD_TYPE_TRANSACTION = "TRANSACTION"

    fun fromOfx(
        rows: List<ParsedTransaction>,
        rawMetadata: Map<String, Any?> = emptyMap(),
    ): CanonicalReaderOutput =
        fromParsedTransactions(
            rows = rows,
            processorType = CanonicalProcessorType.DETERMINISTIC,
            readerId = "ofx-parser",
            confidence = 1.0,
            warnings = emptyList(),
            rawMetadata = rawMetadata,
        )

    fun fromCsv(
        rows: List<ParsedTransaction>,
        readerId: String,
        confidence: Double,
        warnings: List<String> = emptyList(),
        rawMetadata: Map<String, Any?> = emptyMap(),
    ): CanonicalReaderOutput {
        val processorType =
            if (readerId == GenericCsvReader.id()) {
                CanonicalProcessorType.AUTO
            } else {
                CanonicalProcessorType.DETERMINISTIC
            }
        return fromParsedTransactions(
            rows = rows,
            processorType = processorType,
            readerId = readerId,
            confidence = confidence,
            warnings = warnings,
            rawMetadata = rawMetadata,
        )
    }

    fun fromParsedTransactions(
        rows: List<ParsedTransaction>,
        processorType: CanonicalProcessorType,
        readerId: String,
        confidence: Double,
        warnings: List<String> = emptyList(),
        rawMetadata: Map<String, Any?> = emptyMap(),
        schemaId: String = SCHEMA_ID,
        schemaVersion: String = SCHEMA_VERSION,
    ): CanonicalReaderOutput =
        CanonicalReaderOutput(
            schemaId = schemaId,
            schemaVersion = schemaVersion,
            processorType = processorType,
            readerId = readerId,
            confidence = confidence,
            warnings = warnings,
            normalizedRecords = rows.map { it.toCanonicalRecord() },
            rawMetadata = rawMetadata,
        )

    /**
     * Converts canonical transaction records back to [ParsedTransaction].
     * Used in tests to assert mapping has no data loss for OFX/CSV flows.
     */
    fun toParsedTransactions(output: CanonicalReaderOutput): List<ParsedTransaction> =
        output.normalizedRecords.mapNotNull(::recordToParsedTransaction)

    private fun ParsedTransaction.toCanonicalRecord(): Map<String, Any?> =
        linkedMapOf(
            "record_type" to RECORD_TYPE_TRANSACTION,
            "provider_transaction_id" to providerTransactionId,
            "credit_debit_type" to creditDebitType.name,
            "amount" to amount,
            "date" to date.toString(),
            "transaction_datetime" to transactionDatetime?.toString(),
            "description" to description,
            "description_raw" to descriptionRaw,
            "counterparty_name" to counterpartyName,
            "counterparty_document" to counterpartyDocument,
            "currency_code" to currencyCode,
            "total_installments" to totalInstallments,
        )

    private fun recordToParsedTransaction(record: Map<String, Any?>): ParsedTransaction? {
        val recordType = record["record_type"]?.toString()
        if (recordType != RECORD_TYPE_TRANSACTION) return null

        val creditDebitType =
            runCatching { CreditDebitType.valueOf(record["credit_debit_type"]?.toString().orEmpty()) }
                .getOrNull()
                ?: return null

        val amount = record["amount"].toBigDecimalOrNull() ?: return null
        val date = record["date"].toLocalDateOrNull() ?: return null

        return ParsedTransaction(
            providerTransactionId = record["provider_transaction_id"]?.toString(),
            creditDebitType = creditDebitType,
            amount = amount,
            date = date,
            transactionDatetime = record["transaction_datetime"].toInstantOrNull(),
            description = record["description"]?.toString().orEmpty(),
            descriptionRaw = record["description_raw"]?.toString(),
            counterpartyName = record["counterparty_name"]?.toString(),
            counterpartyDocument = record["counterparty_document"]?.toString(),
            currencyCode = record["currency_code"]?.toString() ?: "BRL",
            totalInstallments = record["total_installments"].toIntOrNull(),
        )
    }

    private fun Any?.toBigDecimalOrNull(): BigDecimal? =
        when (this) {
            null -> null
            is BigDecimal -> this
            is Number -> this.toString().toBigDecimalOrNull()
            else -> this.toString().toBigDecimalOrNull()
        }

    private fun Any?.toLocalDateOrNull(): LocalDate? =
        when (this) {
            null -> null
            is LocalDate -> this
            else -> runCatching { LocalDate.parse(this.toString()) }.getOrNull()
        }

    private fun Any?.toInstantOrNull(): Instant? =
        when (this) {
            null -> null
            is Instant -> this
            else -> runCatching { Instant.parse(this.toString()) }.getOrNull()
        }

    private fun Any?.toIntOrNull(): Int? =
        when (this) {
            null -> null
            is Int -> this
            is Number -> this.toInt()
            else -> this.toString().toIntOrNull()
        }
}
