package io.github.salomax.neotool.banking.job.import

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.client.import.messaging.ImportJobCompletedEvent
import io.github.salomax.neotool.banking.client.import.messaging.ImportJobCompletedProducer
import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ParsedTransaction
import io.github.salomax.neotool.banking.domain.transaction.CreditDebitType
import io.github.salomax.neotool.banking.model.dto.imports.ImportJobStatusPayload
import io.github.salomax.neotool.banking.model.dto.imports.ImportReviewPayloadMapper
import io.github.salomax.neotool.banking.model.dto.imports.ImportRowError
import io.github.salomax.neotool.banking.model.dto.imports.ImportValidationCounters
import io.github.salomax.neotool.banking.model.dto.imports.ImportValidationPreDecision
import io.github.salomax.neotool.banking.model.dto.imports.ImportValidationQuality
import io.github.salomax.neotool.banking.repo.import.ImportJobRepository
import io.github.salomax.neotool.banking.service.import.ImportProcessor
import io.github.salomax.neotool.banking.service.import.ImportValidationGate
import io.github.salomax.neotool.banking.service.import.event.ImportEventBus
import io.github.salomax.neotool.banking.service.import.statement.AssistantStatementExtractionMeta
import io.github.salomax.neotool.banking.service.import.statement.AssistantStatementTransactionExtractor
import io.github.salomax.neotool.banking.service.transaction.SnapshotComputationService
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
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
open class ImportReaderStatusConsumer(
    private val importJobRepository: ImportJobRepository,
    private val importProcessor: ImportProcessor,
    private val importEventBus: ImportEventBus,
    private val jobCompletedProducer: ImportJobCompletedProducer,
    private val snapshotComputationService: SnapshotComputationService,
    private val importValidationGate: ImportValidationGate,
    private val statementTransactionExtractor: AssistantStatementTransactionExtractor,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Topic("\${kafka.topics.pdf.banking-reader-done:assistant.banking-pdf-reader.done}")
    @Transactional
    open fun onBankingReaderDone(event: Map<String, Any?>) {
        handleEvent("assistant.banking-pdf-reader.done", event) { job ->
            if (job.status.isTerminal()) {
                return@handleEvent job
            }

            val llmOutput = event["llm_output"] as? Map<*, *>
            if (llmOutput == null) {
                log.warn(
                    "assistant.banking-pdf-reader.done payload has non-object llm_output: type={}, jobId={}",
                    event["llm_output"]?.javaClass?.name ?: "null",
                    job.id,
                )
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

            val rowsResult =
                parseRows(
                    job = job,
                    documentType = event["document_type"]?.toString(),
                    llmOutput = llmOutput,
                )
            val dateRange = extractDateRange(event["document_type"]?.toString(), llmOutput, rowsResult.rows)
            if (rowsResult.rows.isEmpty()) {
                return@handleEvent markFailed(
                    job = job,
                    errorMessage = "assistant_done_without_transactions",
                    assistantResponse = event,
                    documentType = event["document_type"]?.toString(),
                    dateFrom = dateRange.first,
                    dateTo = dateRange.second,
                    details =
                        mapOf(
                            "topic" to "assistant.banking-pdf-reader.done",
                            "reason" to "no_transaction_rows",
                            "invalid_row_count" to rowsResult.invalidCount,
                            "document_type" to event["document_type"]?.toString(),
                            "warnings" to rowsResult.warnings.take(20),
                        ),
                )
            }

            val quality = extractValidationQuality(event, llmOutput, rowsResult.warnings)
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
                    assistantResponse = event,
                    documentType = event["document_type"]?.toString(),
                    dateFrom = dateRange.first,
                    dateTo = dateRange.second,
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
                    documentType = event["document_type"]?.toString() ?: job.documentType,
                    assistantResponse = objectMapper.writeValueAsString(event),
                    dateFrom = dateRange.first,
                    dateTo = dateRange.second,
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
    @Transactional
    open fun onBankingReaderFailed(event: Map<String, Any?>) {
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
                assistantResponse = event,
                documentType = event["document_type"]?.toString(),
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
                        log.debug(
                            "Ignoring {} event for non-import job: jobId={}, accountId={}",
                            topic,
                            jobId,
                            accountId,
                        )
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
        assistantResponse: Any? = null,
        documentType: String? = null,
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null,
        details: Map<String, Any?>,
    ): ImportJob {
        val now = Instant.now()
        val updated =
            job.copy(
                status = ImportJobStatus.FAILED,
                documentType = documentType ?: job.documentType,
                assistantResponse =
                    assistantResponse?.let { objectMapper.writeValueAsString(it) } ?: job.assistantResponse,
                dateFrom = dateFrom ?: job.dateFrom,
                dateTo = dateTo ?: job.dateTo,
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

    private fun parseRows(
        job: ImportJob,
        documentType: String?,
        llmOutput: Map<*, *>,
    ): ParsedRowsResult =
        if (documentType == "BANK_STATEMENT" && llmOutput["parser_config"] is Map<*, *>) {
            parseRowsFromStatementMeta(job, llmOutput)
        } else {
            parseRowsFromLlmOutput(llmOutput, job.id)
        }

    private fun parseRowsFromStatementMeta(
        job: ImportJob,
        llmOutput: Map<*, *>,
    ): ParsedRowsResult {
        val extractedText = job.extractedText?.takeIf { it.isNotBlank() }
        if (extractedText == null) {
            log.warn("Cannot extract BANK_STATEMENT rows from parser_config without extractedText: jobId={}", job.id)
            return ParsedRowsResult(
                rows = emptyList(),
                invalidCount = 1,
                warnings = listOf("missing_extracted_text_for_statement_meta"),
            )
        }

        val meta =
            runCatching { objectMapper.convertValue(llmOutput, AssistantStatementExtractionMeta::class.java) }
                .getOrElse { error ->
                    log.warn(
                        "Failed converting BANK_STATEMENT parser_config to statement meta: jobId={}",
                        job.id,
                        error,
                    )
                    return ParsedRowsResult(
                        rows = emptyList(),
                        invalidCount = 1,
                        warnings = listOf("invalid_statement_parser_config"),
                    )
                }

        val result = statementTransactionExtractor.extract(extractedText, meta)
        return ParsedRowsResult(
            rows = result.rows,
            invalidCount = 0,
            warnings = result.warnings,
        )
    }

    private fun parseRowsFromLlmOutput(
        llmOutput: Map<*, *>,
        jobId: UUID?,
    ): ParsedRowsResult {
        val transactionMaps = mutableListOf<Map<*, *>>()
        var invalidCount = 0

        val candidates =
            buildList {
                add(NamedObject(name = "llm_output", value = llmOutput))
                WRAPPER_KEYS.forEach { key ->
                    if (!llmOutput.containsKey(key)) {
                        return@forEach
                    }
                    val raw = llmOutput[key]
                    val nested = raw as? Map<*, *>
                    if (nested == null) {
                        invalidCount += 1
                        log.warn(
                            "Ignoring llm_output.{} because value is not an object: type={}, jobId={}",
                            key,
                            raw?.javaClass?.name ?: "null",
                            jobId,
                        )
                        return@forEach
                    }
                    add(NamedObject(name = "llm_output.$key", value = nested))
                }
            }

        candidates.forEach { candidate ->
            val topLevelTransactions =
                extractFirstMapList(
                    owner = candidate.value,
                    keys = TRANSACTION_LIST_KEYS,
                    context = candidate.name,
                    jobId = jobId,
                )
            if (topLevelTransactions != null) {
                transactionMaps += topLevelTransactions.values
                invalidCount += topLevelTransactions.invalidCount
            }

            val cards =
                extractFirstMapList(
                    owner = candidate.value,
                    keys = CARD_LIST_KEYS,
                    context = candidate.name,
                    jobId = jobId,
                )
            if (cards != null) {
                invalidCount += cards.invalidCount
                cards.values.forEachIndexed { index, card ->
                    val cardTransactions =
                        extractFirstMapList(
                            owner = card,
                            keys = CARD_TRANSACTION_LIST_KEYS,
                            context = "${candidate.name}.${cards.key}[$index]",
                            jobId = jobId,
                        )
                    if (cardTransactions != null) {
                        transactionMaps += cardTransactions.values
                        invalidCount += cardTransactions.invalidCount
                    }
                }
            }
        }

        val rows = transactionMaps.mapNotNull(::toParsedTransaction)
        val parseFailures = transactionMaps.size - rows.size
        if (parseFailures > 0) {
            log.warn(
                "Dropped {} transaction rows after field parsing (missing/invalid required fields): jobId={}",
                parseFailures,
                jobId,
            )
        }
        return ParsedRowsResult(
            rows = rows,
            invalidCount = invalidCount + parseFailures,
            warnings = emptyList(),
        )
    }

    private fun extractFirstMapList(
        owner: Map<*, *>,
        keys: List<String>,
        context: String,
        jobId: UUID?,
    ): ExtractedMapList? {
        keys.forEach { key ->
            if (!owner.containsKey(key)) {
                return@forEach
            }
            val raw = owner[key]
            val list = raw as? List<*>
            if (list == null) {
                log.warn(
                    "Ignoring {}.{} because value is not an array: type={}, jobId={}",
                    context,
                    key,
                    raw?.javaClass?.name ?: "null",
                    jobId,
                )
                return ExtractedMapList(key = key, values = emptyList(), invalidCount = 1)
            }
            val values = list.filterIsInstance<Map<*, *>>()
            val invalidElements = list.size - values.size
            if (invalidElements > 0) {
                log.warn(
                    "Ignoring {} non-object element(s) from {}.{} list: jobId={}",
                    invalidElements,
                    context,
                    key,
                    jobId,
                )
            }
            return ExtractedMapList(
                key = key,
                values = values,
                invalidCount = invalidElements,
            )
        }
        return null
    }

    private fun extractValidationQuality(
        event: Map<String, Any?>,
        llmOutput: Map<*, *>,
        parserWarnings: List<String> = emptyList(),
    ): ImportValidationQuality {
        val parseMetadata = event["parse_metadata"] as? Map<*, *>
        val processorType =
            parseMetadata?.get("processor_type")
                ?.toString()
                ?.let(::toCanonicalProcessorType)
                ?: CanonicalProcessorType.LLM
        val parseMetadataWarnings = parseWarnings(parseMetadata?.get("warnings"))
        val llmWarnings = parseWarnings(llmOutput["warnings"])
        val confidence =
            parseMetadata?.get("confidence")
                ?.let { raw ->
                    when (raw) {
                        is Number -> raw.toDouble()
                        else -> raw.toString().toDoubleOrNull()
                    }
                }
        return ImportValidationQuality(
            processorType = processorType,
            warnings = (parseMetadataWarnings + llmWarnings + parserWarnings).distinct(),
            confidence = confidence,
        )
    }

    private fun applyPreDecision(
        job: ImportJob,
        totalRows: Int,
        decision: ImportValidationPreDecision,
        assistantResponse: Any? = null,
        documentType: String? = null,
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null,
    ): ImportJob =
        when (decision.status) {
            ImportJobStatus.REVIEW_REQUIRED ->
                markReviewRequired(
                    job = job,
                    totalRows = totalRows,
                    decision = decision,
                    assistantResponse = assistantResponse,
                    documentType = documentType,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                )
            ImportJobStatus.FAILED ->
                markFailed(
                    job = job,
                    errorMessage = "validation_gate_failed: ${decision.reason}",
                    assistantResponse = assistantResponse,
                    documentType = documentType,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    details = decision.details,
                )
            else -> job
        }

    private fun markReviewRequired(
        job: ImportJob,
        totalRows: Int,
        decision: ImportValidationPreDecision,
        assistantResponse: Any? = null,
        documentType: String? = null,
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null,
    ): ImportJob {
        val now = Instant.now()
        val updated =
            job.copy(
                status = ImportJobStatus.REVIEW_REQUIRED,
                documentType = documentType ?: job.documentType,
                assistantResponse =
                    assistantResponse?.let { objectMapper.writeValueAsString(it) } ?: job.assistantResponse,
                dateFrom = dateFrom ?: job.dateFrom,
                dateTo = dateTo ?: job.dateTo,
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

    private fun extractDateRange(
        documentType: String?,
        llmOutput: Map<*, *>,
        rows: List<ParsedTransaction>,
    ): Pair<LocalDate?, LocalDate?> {
        if (rows.isNotEmpty()) {
            return Pair(rows.minOf { it.date }, rows.maxOf { it.date })
        }
        if (documentType != "BANK_STATEMENT") {
            return Pair(null, null)
        }
        val statementPeriod = llmOutput["statement_period"] as? Map<*, *> ?: return Pair(null, null)
        return Pair(
            parseDate(statementPeriod["from"]),
            parseDate(statementPeriod["to"]),
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
                errorDetails = job.errorDetails,
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

    private companion object {
        val WRAPPER_KEYS =
            listOf("bank_statement", "statement", "account_statement", "extract", "data", "result", "output")
        val TRANSACTION_LIST_KEYS =
            listOf(
                "transactions",
                "transaction_rows",
                "entries",
                "movements",
                "movimentacoes",
                "lancamentos",
                "transacoes",
            )
        val CARD_LIST_KEYS = listOf("cards", "card_statements", "cartoes")
        val CARD_TRANSACTION_LIST_KEYS =
            listOf("transactions", "entries", "movements", "movimentacoes", "lancamentos", "transacoes")
    }

    private data class NamedObject(
        val name: String,
        val value: Map<*, *>,
    )

    private data class ExtractedMapList(
        val key: String,
        val values: List<Map<*, *>>,
        val invalidCount: Int,
    )

    private data class ParsedRowsResult(
        val rows: List<ParsedTransaction>,
        val invalidCount: Int,
        val warnings: List<String> = emptyList(),
    )
}
