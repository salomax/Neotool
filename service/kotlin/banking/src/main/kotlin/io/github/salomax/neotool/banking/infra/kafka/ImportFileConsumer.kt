package io.github.salomax.neotool.banking.infra.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobRepository
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportProcessor
import io.github.salomax.neotool.banking.domain.import.ImportReviewPayloadMapper
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.domain.import.ImportValidationCounters
import io.github.salomax.neotool.banking.domain.import.ImportValidationGate
import io.github.salomax.neotool.banking.domain.import.excel.ExcelExtractionService
import io.github.salomax.neotool.banking.domain.import.excel.reader.ExcelDeterministicRoutingService
import io.github.salomax.neotool.banking.domain.import.ofx.OfxDestinationCompatibilityService
import io.github.salomax.neotool.banking.domain.import.parser.OfxStatementIntent
import io.github.salomax.neotool.banking.domain.import.port.ImportFileStoragePort
import io.github.salomax.neotool.banking.domain.importevent.ImportEventBus
import io.github.salomax.neotool.banking.domain.importevent.ImportJobStatusPayload
import io.github.salomax.neotool.banking.domain.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.error.BankingErrorCode
import io.github.salomax.neotool.banking.infra.extractor.pdf.PdfExtractorSubmittedEvent
import io.github.salomax.neotool.banking.infra.extractor.pdf.PdfExtractorSubmittedProducer
import io.github.salomax.neotool.banking.infra.kafka.parser.CsvImportParserStrategy
import io.github.salomax.neotool.banking.infra.kafka.parser.ExcelImportParserStrategy
import io.github.salomax.neotool.banking.infra.kafka.parser.ImportAssistantRequest
import io.github.salomax.neotool.banking.infra.kafka.parser.ImportParserRouter
import io.github.salomax.neotool.banking.infra.kafka.parser.OfxImportParserStrategy
import io.github.salomax.neotool.common.error.BusinessRuleException
import io.github.salomax.neotool.common.error.DomainException
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.KafkaPartition
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private const val PDF_BANK_STATEMENT_DOCUMENT_TYPE = "BANK_STATEMENT"
private const val DEFAULT_IMPORT_DOWNLOAD_TTL_SECONDS = 3600

@Singleton
@Requires(property = "kafka.enabled", value = "true")
@Requires(property = "kafka.consumers.import-file.enabled", notEquals = "false", defaultValue = "true")
@KafkaListener(
    groupId = "banking-import-consumer",
    offsetReset = OffsetReset.EARLIEST,
    properties = [
        Property(name = "enable.auto.commit", value = "false"),
        Property(name = "max.poll.records", value = "1"),
    ],
)
class ImportFileConsumer(
    private val fileStorage: ImportFileStoragePort,
    private val importProcessor: ImportProcessor,
    private val importJobRepository: ImportJobRepository,
    private val importEventBus: ImportEventBus,
    private val jobCompletedProducer: ImportJobCompletedProducer,
    private val csvImportMetrics: CsvImportMetrics,
    private val objectMapper: ObjectMapper,
    private val snapshotComputationService: SnapshotComputationService,
    private val importValidationGate: ImportValidationGate,
    private val ofxDestinationCompatibilityService: OfxDestinationCompatibilityService,
    private val assistantBankingReaderRequestedProducer: AssistantBankingReaderRequestedProducer? = null,
    private val pdfExtractorSubmittedProducer: PdfExtractorSubmittedProducer? = null,
    @Property(name = "kafka.topics.import-file-requested")
    private val topicName: String,
) {
    private class RetryableAssistantPublishException(
        message: String,
        cause: Throwable,
    ) : RuntimeException(message, cause)

    private val log = LoggerFactory.getLogger(javaClass)
    private val parserRouter =
        ImportParserRouter(
            strategies =
                listOf(
                    OfxImportParserStrategy(),
                    ExcelImportParserStrategy(
                        excelExtractionService = ExcelExtractionService(),
                        excelDeterministicRoutingService = ExcelDeterministicRoutingService(),
                    ),
                    CsvImportParserStrategy(csvImportMetrics),
                ),
        )

    @Topic("\${kafka.topics.import-file-requested:banking.import.file-requested}")
    fun receive(
        @KafkaKey key: String,
        event: ImportFileRequestedEvent,
        consumer: Consumer<*, *>,
        @KafkaPartition partition: Int,
        offset: Long,
    ) {
        val jobId = event.jobId
        val accountId = event.accountId
        val assetId = event.assetId

        try {
            // 1. Load job; idempotent if already COMPLETED (crash recovery)
            val job =
                importJobRepository.findById(jobId, accountId)
                    ?: run {
                        log.warn("Import job not found: jobId={}, accountId={}; committing offset", jobId, accountId)
                        commitOffset(consumer, partition, offset)
                        return
                    }
            if (job.status == ImportJobStatus.COMPLETED) {
                log.info("Import job already COMPLETED (idempotent skip): jobId={}", jobId)
                commitOffset(consumer, partition, offset)
                return
            }
            if (job.assistantRequestPublishedAt != null && job.status == ImportJobStatus.PROCESSING) {
                log.info("Import job already dispatched to assistant (idempotent skip): jobId={}", jobId)
                commitOffset(consumer, partition, offset)
                return
            }

            // 2. Update → PROCESSING; emit PROCESSING
            val now = Instant.now()
            var currentJob =
                job.copy(
                    status = ImportJobStatus.PROCESSING,
                    updatedAt = now,
                )
            importJobRepository.update(currentJob)
            emitStatus(currentJob)

            if (event.source == ImportSource.AUTO && isPdfFile(currentJob.fileName)) {
                publishPdfExtractorRequest(
                    event = event,
                    job = currentJob,
                )
                commitOffset(consumer, partition, offset)
                return
            }

            // 3. Read file bytes
            val bytes = fileStorage.read(accountId, assetId)

            // 4. Discover best parser strategy and parse
            val parseOutcome =
                parserRouter.parse(
                    bytes = bytes,
                    fileName = currentJob.fileName,
                    requestedSource = event.source,
                )
            val rows = parseOutcome.rows
            if (parseOutcome.resolvedSource != null && currentJob.source != parseOutcome.resolvedSource) {
                currentJob =
                    currentJob.copy(
                        source = parseOutcome.resolvedSource,
                        updatedAt = now,
                    )
                importJobRepository.update(currentJob)
            }

            if (parseOutcome.resolvedSource == ImportSource.OFX) {
                val statementIntent =
                    parseOutcome.canonicalOutput
                        ?.rawMetadata
                        ?.get("ofx_message_set_intent")
                        ?.toString()
                        ?.let(::toOfxStatementIntent)
                        ?: OfxStatementIntent.UNKNOWN
                ofxDestinationCompatibilityService.validateOrThrow(
                    financialAccountId = currentJob.financialAccountId,
                    accountId = accountId,
                    statementIntent = statementIntent,
                )
            }

            parseOutcome.assistantRequest?.let { assistantRequest ->
                publishAssistantRequest(
                    event = event,
                    job = currentJob,
                    assistantRequest = assistantRequest,
                )
                deleteAssetBestEffort(accountId, assetId)
                commitOffset(consumer, partition, offset)
                return
            }

            if (rows.isEmpty()) {
                currentJob =
                    currentJob.copy(
                        status = ImportJobStatus.COMPLETED,
                        totalRows = 0,
                        importedCount = 0,
                        skippedCount = 0,
                        duplicateReviewCount = 0,
                        errorCount = 0,
                        errorDetails = null,
                        reviewPayload = null,
                        completedAt = now,
                        updatedAt = now,
                    )
                importJobRepository.update(currentJob)
                emitStatus(currentJob)
                jobCompletedProducer.publish(
                    ImportJobCompletedEvent(
                        jobId = jobId,
                        accountId = accountId,
                        status = ImportJobStatus.COMPLETED,
                        importedCount = 0,
                        skippedCount = 0,
                        duplicateReviewCount = 0,
                        errorCount = 0,
                    ),
                )
                deleteAssetBestEffort(accountId, assetId)
                commitOffset(consumer, partition, offset)
                return
            }

            // 5. Process
            val result = importProcessor.process(currentJob, rows)

            // 5b. Recompute snapshots for all affected months (single chain from the earliest imported date)
            if (result.imported.isNotEmpty()) {
                val earliestDate = result.imported.minOf { it.date }
                try {
                    snapshotComputationService.recomputeChain(
                        financialAccountId = event.financialAccountId,
                        accountId = accountId,
                        fromYear = earliestDate.year,
                        fromMonth = earliestDate.monthValue,
                    )
                } catch (e: Exception) {
                    // recomputeChain already called markDirtyFromMonth; sweeper will heal
                    log.warn("Snapshot recompute failed after import; sweeper will heal: jobId={}", jobId, e)
                }
            }

            // 6. Build updated job with counts
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
                if (errorCount > 0) {
                    objectMapper.writeValueAsString(result.errors.take(50))
                } else {
                    null
                }

            currentJob =
                currentJob.copy(
                    status = finalStatus,
                    totalRows = rows.size,
                    importedCount = importedCount,
                    skippedCount = skippedCount,
                    duplicateReviewCount = duplicateReviewCount,
                    errorCount = errorCount,
                    errorDetails = if (finalStatus == ImportJobStatus.FAILED) errorDetailsJson else null,
                    reviewPayload = reviewPayloadJson,
                    completedAt = now,
                    updatedAt = now,
                )
            importJobRepository.update(currentJob)

            // 7. Emit terminal status
            emitStatus(currentJob)

            // 8. Publish job completed event
            jobCompletedProducer.publish(
                ImportJobCompletedEvent(
                    jobId = jobId,
                    accountId = accountId,
                    status = finalStatus,
                    importedCount = importedCount,
                    skippedCount = skippedCount,
                    duplicateReviewCount = duplicateReviewCount,
                    errorCount = errorCount,
                ),
            )

            // 9. Delete asset (best-effort)
            deleteAssetBestEffort(accountId, assetId)

            commitOffset(consumer, partition, offset)
        } catch (e: Exception) {
            if (e is RetryableAssistantPublishException) {
                log.warn(
                    "Retryable assistant publish failure for import jobId={}, assetId={}; leaving offset uncommitted",
                    jobId,
                    assetId,
                    e,
                )
                throw e
            }
            log.error("Import processing failed: jobId={}, assetId={}", jobId, assetId, e)
            try {
                val failedJob = importJobRepository.findById(jobId, accountId)
                if (failedJob != null) {
                    val now = Instant.now()
                    val errorDetails =
                        objectMapper.writeValueAsString(buildErrorDetails(e))
                    val updated =
                        failedJob.copy(
                            status = ImportJobStatus.FAILED,
                            errorCount = (failedJob.errorCount ?: 0) + 1,
                            errorDetails = errorDetails,
                            completedAt = now,
                            updatedAt = now,
                        )
                    importJobRepository.update(updated)
                    emitStatus(
                        ImportJobStatusPayload(
                            jobId = jobId,
                            accountId = accountId,
                            status = ImportJobStatus.FAILED,
                            importedCount = updated.importedCount,
                            skippedCount = updated.skippedCount,
                            duplicateReviewCount = updated.duplicateReviewCount,
                            errorCount = updated.errorCount,
                        ),
                    )
                    jobCompletedProducer.publish(
                        ImportJobCompletedEvent(
                            jobId = jobId,
                            accountId = accountId,
                            status = ImportJobStatus.FAILED,
                            importedCount = updated.importedCount,
                            skippedCount = updated.skippedCount,
                            duplicateReviewCount = updated.duplicateReviewCount,
                            errorCount = updated.errorCount,
                        ),
                    )
                }
                deleteAssetBestEffort(accountId, assetId)
                commitOffset(consumer, partition, offset)
            } catch (inner: Exception) {
                log.error("Failed to update job to FAILED: jobId={}", jobId, inner)
                throw inner
            }
        }
    }

    private fun emitStatus(job: ImportJob) {
        emitStatus(
            ImportJobStatusPayload(
                jobId = job.id!!,
                accountId = job.accountId,
                status = job.status,
                importedCount = job.importedCount,
                skippedCount = job.skippedCount,
                duplicateReviewCount = job.duplicateReviewCount,
                errorCount = job.errorCount,
            ),
        )
    }

    private fun emitStatus(payload: ImportJobStatusPayload) {
        importEventBus.emit(payload)
    }

    private fun deleteAssetBestEffort(
        accountId: UUID,
        assetId: UUID,
    ) {
        try {
            fileStorage.delete(accountId, assetId)
        } catch (e: Exception) {
            log.warn("Best-effort delete asset failed (TTL will cleanup): assetId={}", assetId, e)
        }
    }

    private fun publishAssistantRequest(
        event: ImportFileRequestedEvent,
        job: ImportJob,
        assistantRequest: ImportAssistantRequest,
    ) {
        val producer = assistantBankingReaderRequestedProducer
        if (producer == null) {
            log.warn("Assistant request producer unavailable for import jobId={}; skipping publish", event.jobId)
            return
        }
        val jobId = requireNotNull(job.id) { "ImportJob id is required for assistant request publish" }
        if (job.assistantRequestPublishedAt != null) {
            log.info("Assistant request already published for import jobId={}; skipping duplicate publish", jobId)
            return
        }
        try {
            producer.publish(
                AssistantBankingReaderRequestedEvent(
                    job_id = jobId,
                    document_type = assistantRequest.documentType,
                    file_type = assistantRequest.fileType,
                    account_type = assistantRequest.accountType,
                    source_topic = assistantRequest.sourceTopic,
                    extracted_text = assistantRequest.extractedText,
                    extraction_metadata = assistantRequest.extractionMetadata,
                    callback_context =
                        ImportCallbackContext(
                            job_id = jobId,
                            account_id = event.accountId,
                            user_id = job.createdByUserId,
                        ),
                ),
            )
        } catch (e: Exception) {
            throw RetryableAssistantPublishException("assistant_request_publish_failed", e)
        }

        val marked = importJobRepository.markAssistantRequestPublished(jobId)
        if (!marked) {
            log.info("Assistant request marker already set for import jobId={}", jobId)
        }
    }

    private fun publishPdfExtractorRequest(
        event: ImportFileRequestedEvent,
        job: ImportJob,
    ) {
        val producer =
            pdfExtractorSubmittedProducer
                ?: throw BusinessRuleException(
                    errorCode = BankingErrorCode.IMPORT_ASYNC_PIPELINE_UNAVAILABLE,
                    parameters = mapOf("kafkaEnabled" to false, "pipeline" to "pdf-extractor"),
                )
        val downloadUrl =
            fileStorage.resolveDownloadUrl(
                accountId = event.accountId,
                assetId = event.assetId,
                ttlSeconds = DEFAULT_IMPORT_DOWNLOAD_TTL_SECONDS,
            )
        producer.publish(
            PdfExtractorSubmittedEvent(
                job_id = event.jobId,
                asset_id = event.assetId,
                download_url = downloadUrl,
                document_type = PDF_BANK_STATEMENT_DOCUMENT_TYPE,
                callback_context =
                    ImportCallbackContext(
                        job_id = event.jobId,
                        account_id = event.accountId,
                        user_id = job.createdByUserId,
                    ),
            ),
        )
    }

    private fun isPdfFile(fileName: String?): Boolean = fileName?.lowercase()?.endsWith(".pdf") == true

    private fun stackSummary(e: Throwable): String {
        return e.stackTraceToString().lines().take(15).joinToString("\n")
    }

    private fun buildErrorDetails(error: Exception): Map<String, Any?> {
        val details =
            linkedMapOf<String, Any?>(
                "message" to (error.message ?: "Import processing failed"),
                "stackSummary" to stackSummary(error),
            )
        val domain = error as? DomainException
        if (domain != null) {
            details["errorCode"] = domain.errorCode.code
            details["errorField"] = domain.field
            details["parameters"] = domain.parameters
        }
        return details
    }

    private fun toOfxStatementIntent(raw: String): OfxStatementIntent =
        runCatching { OfxStatementIntent.valueOf(raw.trim().uppercase()) }
            .getOrDefault(OfxStatementIntent.UNKNOWN)

    private fun commitOffset(
        consumer: Consumer<*, *>,
        partition: Int,
        offset: Long,
    ) {
        val tp = TopicPartition(topicName, partition)
        consumer.commitSync(mapOf(tp to OffsetAndMetadata(offset + 1)))
    }
}
