package io.github.salomax.neotool.banking.infra.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobRepository
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportProcessor
import io.github.salomax.neotool.banking.domain.import.ImportRowError
import io.github.salomax.neotool.banking.domain.import.ImportValidationCounters
import io.github.salomax.neotool.banking.domain.import.ImportValidationGate
import io.github.salomax.neotool.banking.domain.import.ImportValidationPreDecision
import io.github.salomax.neotool.banking.domain.import.ImportValidationQuality
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.ImportReviewPayloadMapper
import io.github.salomax.neotool.banking.domain.importevent.ImportEventBus
import io.github.salomax.neotool.banking.domain.importevent.ImportJobStatusPayload
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.domain.transaction.SnapshotComputationService
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Singleton
@Requires(property = "kafka.enabled", value = "true")
@Requires(property = "kafka.consumers.import-status.enabled", notEquals = "false", defaultValue = "true")
@KafkaListener(
    groupId = "\${kafka.consumers.import-status.group-id:banking-service-import-status}",
    offsetReset = OffsetReset.EARLIEST,
)
class ImportReaderStatusConsumer(
    private val importJobRepository: ImportJobRepository,
    private val importProcessor: ImportProcessor,
    private val importEventBus: ImportEventBus,
    private val jobCompletedProducer: ImportJobCompletedProducer,
    private val snapshotComputationService: SnapshotComputationService,
    private val importValidationGate: ImportValidationGate,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Topic("\${kafka.topics.pdf.banking-reader-done:assistant.banking-pdf-reader.done}")
    fun onBankingReaderDone(event: Map<String, Any?>) {
        handleEvent("assistant.banking-pdf-reader.done", event) { job ->
            if (job.status.isTerminal()) {
                return@handleEvent job
            }

            val llmOutput = event["llm_output"] as? Map<*, *>
            if (llmOutput == null) {
                return@handleEvent markFailed(
                    job = job,
                    errorMessage = "assistant_done_invalid_payload: missing llm_output",
                    details =
                        mapOf(
                            "topic" to "assistant.banking-pdf-reader.done",
                            "reason" to "missing_llm_output",
                            "document_type" to event["document_type"]?.toString(),
                        ),
                )
            }

            val rowsResult = parseRowsFromLlmOutput(llmOutput)
            if (rowsResult.rows.isEmpty()) {
                return@handleEvent markFailed(
                    job = job,
                    errorMessage = "assistant_done_without_transactions",
                    details =
                        mapOf(
                            "topic" to "assistant.banking-pdf-reader.done",
                            "reason" to "no_transaction_rows",
                            "invalid_row_count" to rowsResult.invalidCount,
                            "document_type" to event["document_type"]?.toString(),
                        ),
                )
            }

            val quality = extractValidationQuality(event, llmOutput)
            val preDecision =
                importValidationGate.evaluateBeforeCommit(
                    quality = quality,
                    totalRows = rowsResult.rows.size,
                    invalidRowCount = rowsResult.invalidCount,
                )
            if (preDecision != null) {
                return@handleEvent applyPreDecision(
                    job = job,
                    totalRows = rowsResult.rows.size,
                    decision = preDecision,
                )
            }

            val result = importProcessor.process(job, rowsResult.rows)
            if (result.imported.isNotEmpty()) {
                val earliestDate = result.imported.minOf { it.date }
                try {
                    snapshotComputationService.recomputeChain(
                        financialAccountId = job.financialAccountId,
                        accountId = job.accountId,
                        fromYear = earliestDate.year,
                        fromMonth = earliestDate.monthValue,
                    )
                } catch (e: Exception) {
                    log.warn("Snapshot recompute failed after assistant done; sweeper will heal: jobId={}", job.id, e)
                }
            }

            val now = Instant.now()
            val importedCount = result.imported.size
            val skippedCount = result.skipped
            val duplicateReviewCount = result.duplicateCandidates.size
            val errorCount = result.errors.size
            val reviewPayloadJson: String? =
                ImportReviewPayloadMapper.toJson(
                    objectMapper = objectMapper,
                    duplicateCandidates = result.duplicateCandidates,
                )
            val finalStatus =
                importValidationGate.decideFinalStatus(
                    ImportValidationCounters(
                        importedCount = importedCount,
                        skippedCount = skippedCount,
                        duplicateReviewCount = duplicateReviewCount,
                        errorCount = errorCount,
                    ),
                )
            val errorDetailsJson: String? =
                when {
                    finalStatus == ImportJobStatus.FAILED && result.errors.isNotEmpty() ->
                        objectMapper.writeValueAsString(result.errors.take(50))
                    finalStatus == ImportJobStatus.FAILED && rowsResult.invalidCount > 0 ->
                        objectMapper.writeValueAsString(
                            listOf(
                                ImportRowError(
                                    rowIndex = -1,
                                    reason = "assistant_invalid_rows=${rowsResult.invalidCount}",
                                ),
                            ),
                        )
                    else -> null
                }

            val updated =
                job.copy(
                    status = finalStatus,
                    totalRows = rowsResult.rows.size,
                    importedCount = importedCount,
                    skippedCount = skippedCount,
                    duplicateReviewCount = duplicateReviewCount,
                    errorCount = errorCount,
                    errorDetails = errorDetailsJson,
                    reviewPayload = reviewPayloadJson,
                    completedAt = now,
                    updatedAt = now,
                )
            val saved = importJobRepository.update(updated)
            emitStatus(saved)
            publishCompleted(saved)
            saved
        }
    }

    @Topic("\${kafka.topics.pdf.banking-reader-failed:assistant.banking-pdf-reader.failed}")
    fun onBankingReaderFailed(event: Map<String, Any?>) {
        handleEvent("assistant.banking-pdf-reader.failed", event) { job ->
            if (job.status.isTerminal()) {
                return@handleEvent job
            }
            val error = event["error"] as? Map<*, *>
            val errorCode = error?.get("code")?.toString()
            val errorMessage = error?.get("message")?.toString()
            val summary =
                buildString {
                    append("assistant_reader_failed")
                    if (!errorCode.isNullOrBlank()) {
                        append(": ").append(errorCode)
                    }
                    if (!errorMessage.isNullOrBlank()) {
                        append(" - ").append(errorMessage)
                    }
                }
            markFailed(
                job = job,
                errorMessage = summary,
                details =
                    mapOf(
                        "topic" to "assistant.banking-pdf-reader.failed",
                        "schema_version" to event["schema_version"]?.toString(),
                        "document_type" to event["document_type"]?.toString(),
                        "error" to error,
                        "error_metadata" to (event["error_metadata"] as? Map<*, *>),
                    ),
            )
        }
    }

    private fun handleEvent(
        topic: String,
        event: Map<String, Any?>,
        action: (ImportJob) -> ImportJob?,
    ) {
        try {
            val callbackContext = event["callback_context"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val jobId = toUuid(callbackContext["job_id"]) ?: toUuid(event["job_id"])
            val accountId = toUuid(callbackContext["account_id"])
            if (jobId == null || accountId == null) {
                log.warn(
                    "Ignoring {} event due to missing callback identifiers: jobId={}, accountId={}",
                    topic,
                    jobId,
                    accountId,
                )
                return
            }
            val job =
                importJobRepository.findById(jobId, accountId)
                    ?: run {
                        log.debug("Ignoring {} event for non-import job: jobId={}, accountId={}", topic, jobId, accountId)
                        return
                    }
            action(job)
        } catch (e: Exception) {
            log.error("Failed handling {} event", topic, e)
        }
    }

    private fun markFailed(
        job: ImportJob,
        errorMessage: String,
        details: Map<String, Any?>,
    ): ImportJob {
        val now = Instant.now()
        val updated =
            job.copy(
                status = ImportJobStatus.FAILED,
                errorCount = (job.errorCount ?: 0) + 1,
                errorDetails = objectMapper.writeValueAsString(details),
                completedAt = now,
                updatedAt = now,
            )
        val saved = importJobRepository.update(updated)
        emitStatus(saved)
        publishCompleted(saved)
        log.warn("Import job marked FAILED by assistant status consumer: jobId={}, reason={}", job.id, errorMessage)
        return saved
    }

    private fun parseRowsFromLlmOutput(llmOutput: Map<*, *>): ParsedRowsResult {
        val transactionMaps = mutableListOf<Map<*, *>>()

        val topLevelTransactions = llmOutput["transactions"] as? List<*>
        if (topLevelTransactions != null) {
            topLevelTransactions.filterIsInstance<Map<*, *>>().forEach { transactionMaps += it }
        }

        val cards = llmOutput["cards"] as? List<*>
        if (cards != null) {
            cards.filterIsInstance<Map<*, *>>().forEach { card ->
                val cardTransactions = card["transactions"] as? List<*>
                if (cardTransactions != null) {
                    cardTransactions.filterIsInstance<Map<*, *>>().forEach { transactionMaps += it }
                }
            }
        }

        val rows = transactionMaps.mapNotNull(::toParsedTransaction)
        return ParsedRowsResult(rows = rows, invalidCount = transactionMaps.size - rows.size)
    }

    private fun extractValidationQuality(
        event: Map<String, Any?>,
        llmOutput: Map<*, *>,
    ): ImportValidationQuality {
        val parseMetadata = event["parse_metadata"] as? Map<*, *>
        val processorType =
            parseMetadata?.get("processor_type")
                ?.toString()
                ?.let(::toCanonicalProcessorType)
                ?: CanonicalProcessorType.LLM
        val parseMetadataConfidence = (parseMetadata?.get("confidence") as? Number)?.toDouble()
        val extractionConfidence = (llmOutput["extraction_confidence"] as? Number)?.toDouble()
        val confidence = parseMetadataConfidence ?: extractionConfidence
        val parseMetadataWarnings = parseWarnings(parseMetadata?.get("warnings"))
        val llmWarnings = parseWarnings(llmOutput["warnings"])
        return ImportValidationQuality(
            processorType = processorType,
            confidence = confidence?.coerceIn(0.0, 1.0),
            warnings = (parseMetadataWarnings + llmWarnings).distinct(),
        )
    }

    private fun applyPreDecision(
        job: ImportJob,
        totalRows: Int,
        decision: ImportValidationPreDecision,
    ): ImportJob =
        when (decision.status) {
            ImportJobStatus.REVIEW_REQUIRED -> markReviewRequired(job, totalRows, decision)
            ImportJobStatus.FAILED -> markFailed(job, "validation_gate_failed: ${decision.reason}", decision.details)
            else -> job
        }

    private fun markReviewRequired(
        job: ImportJob,
        totalRows: Int,
        decision: ImportValidationPreDecision,
    ): ImportJob {
        val now = Instant.now()
        val updated =
            job.copy(
                status = ImportJobStatus.REVIEW_REQUIRED,
                totalRows = totalRows,
                importedCount = 0,
                skippedCount = 0,
                duplicateReviewCount = 0,
                errorCount = 0,
                errorDetails = null,
                reviewPayload =
                    ImportReviewPayloadMapper.toJson(
                        objectMapper = objectMapper,
                        gate = decision.details,
                    ),
                completedAt = now,
                updatedAt = now,
            )
        val saved = importJobRepository.update(updated)
        emitStatus(saved)
        publishCompleted(saved)
        return saved
    }

    private fun toParsedTransaction(transaction: Map<*, *>): ParsedTransaction? {
        val date = parseDate(transaction["date"]) ?: return null
        val description = transaction["description"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val rawAmount = parseAmount(transaction["amount"]) ?: return null
        val creditDebitType = parseCreditDebitType(transaction["credit_debit_type"], rawAmount) ?: return null

        return ParsedTransaction(
            providerTransactionId = transaction["provider_transaction_id"]?.toString(),
            creditDebitType = creditDebitType,
            amount = rawAmount.abs(),
            date = date,
            transactionDatetime = parseInstant(transaction["transaction_datetime"]),
            description = description,
            descriptionRaw = transaction["description_raw"]?.toString() ?: description,
            counterpartyName = transaction["counterparty_name"]?.toString(),
            counterpartyDocument = transaction["counterparty_document"]?.toString(),
            currencyCode = transaction["currency_code"]?.toString() ?: "BRL",
            totalInstallments =
                ((transaction["installment"] as? Map<*, *>)?.get("total") as? Number)?.toInt()
                    ?: (transaction["total_installments"] as? Number)?.toInt(),
        )
    }

    private fun parseDate(value: Any?): LocalDate? {
        val text = value?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { LocalDate.parse(text) }.getOrElse {
            runCatching { LocalDate.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrNull()
        }
    }

    private fun parseInstant(value: Any?): Instant? {
        val text = value?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { Instant.parse(text) }.getOrNull()
    }

    private fun parseAmount(value: Any?): BigDecimal? =
        when (value) {
            null -> null
            is BigDecimal -> value
            is Number -> value.toString().toBigDecimalOrNull()
            else -> {
                val base =
                    value
                        .toString()
                        .replace("R$", "", ignoreCase = true)
                        .replace(" ", "")
                        .trim()
                val normalized =
                    if (base.contains(",")) {
                        base.replace(".", "").replace(",", ".")
                    } else {
                        base.replace(",", "")
                    }
                normalized.toBigDecimalOrNull()
            }
        }

    private fun parseCreditDebitType(
        value: Any?,
        amount: BigDecimal,
    ): CreditDebitType? {
        val explicit =
            value
                ?.toString()
                ?.trim()
                ?.uppercase()
                ?.let {
                    when (it) {
                        "DEBIT" -> CreditDebitType.DEBIT
                        "CREDIT" -> CreditDebitType.CREDIT
                        else -> null
                    }
                }
        return explicit ?: if (amount.signum() < 0) CreditDebitType.DEBIT else CreditDebitType.CREDIT
    }

    private fun toCanonicalProcessorType(value: String): CanonicalProcessorType? =
        runCatching { CanonicalProcessorType.valueOf(value.trim().uppercase()) }.getOrNull()

    private fun parseWarnings(value: Any?): List<String> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { warning ->
            val text = warning?.toString()?.trim()
            if (text.isNullOrEmpty()) null else text
        }
    }

    private fun emitStatus(job: ImportJob) {
        importEventBus.emit(
            ImportJobStatusPayload(
                jobId = requireNotNull(job.id),
                accountId = job.accountId,
                status = job.status,
                importedCount = job.importedCount,
                skippedCount = job.skippedCount,
                duplicateReviewCount = job.duplicateReviewCount,
                errorCount = job.errorCount,
            ),
        )
    }

    private fun publishCompleted(job: ImportJob) {
        jobCompletedProducer.publish(
            ImportJobCompletedEvent(
                jobId = requireNotNull(job.id),
                accountId = job.accountId,
                status = job.status,
                importedCount = job.importedCount,
                skippedCount = job.skippedCount,
                duplicateReviewCount = job.duplicateReviewCount,
                errorCount = job.errorCount,
            ),
        )
    }

    private fun toUuid(value: Any?): UUID? =
        when (value) {
            null -> null
            is UUID -> value
            else -> runCatching { UUID.fromString(value.toString()) }.getOrNull()
        }

    private data class ParsedRowsResult(
        val rows: List<ParsedTransaction>,
        val invalidCount: Int,
    )
}
