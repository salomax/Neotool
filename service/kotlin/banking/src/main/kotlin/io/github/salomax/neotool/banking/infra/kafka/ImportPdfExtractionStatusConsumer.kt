package io.github.salomax.neotool.banking.infra.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobRepository
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.port.ImportFileStoragePort
import io.github.salomax.neotool.banking.domain.importevent.ImportEventBus
import io.github.salomax.neotool.banking.domain.importevent.ImportJobStatusPayload
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

private const val IMPORT_PDF_DOCUMENT_TYPE = "BANK_STATEMENT"
private const val IMPORT_PDF_FILE_TYPE = "PDF"
private const val IMPORT_PDF_ACCOUNT_TYPE = "BANK"

@Singleton
@Requires(property = "kafka.enabled", value = "true")
@Requires(property = "kafka.consumers.import-pdf-status.enabled", notEquals = "false", defaultValue = "true")
@KafkaListener(
    groupId = "\${kafka.consumers.import-pdf-status.group-id:banking-service-import-pdf-status}",
    offsetReset = OffsetReset.EARLIEST,
)
class ImportPdfExtractionStatusConsumer(
    private val importJobRepository: ImportJobRepository,
    private val importEventBus: ImportEventBus,
    private val jobCompletedProducer: ImportJobCompletedProducer,
    private val fileStorage: ImportFileStoragePort,
    private val objectMapper: ObjectMapper,
    private val assistantBankingReaderRequestedProducer: AssistantBankingReaderRequestedProducer? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Topic("\${kafka.topics.pdf.extractor-extracted:pdf-extractor.extracted}")
    fun onExtractorExtracted(event: Map<String, Any?>) {
        handleEvent("pdf-extractor.extracted", event) { job, callbackContext ->
            if (job.status.isTerminal()) return@handleEvent job
            if (job.assistantRequestPublishedAt != null) {
                log.info("Assistant request already published for import jobId={}", job.id)
                deleteAssetBestEffort(event, callbackContext, job.accountId)
                return@handleEvent job
            }

            val extractedText = event["extracted_text"] as? String
            if (extractedText.isNullOrBlank()) {
                deleteAssetBestEffort(event, callbackContext, job.accountId)
                return@handleEvent markFailed(
                    job = job,
                    details =
                        mapOf(
                            "topic" to "pdf-extractor.extracted",
                            "reason" to "missing_extracted_text",
                            "document_type" to event["document_type"]?.toString(),
                        ),
                )
            }

            val producer = assistantBankingReaderRequestedProducer
            if (producer == null) {
                deleteAssetBestEffort(event, callbackContext, job.accountId)
                return@handleEvent markFailed(
                    job = job,
                    details =
                        mapOf(
                            "topic" to "pdf-extractor.extracted",
                            "reason" to "assistant_producer_unavailable",
                        ),
                )
            }

            val extractionMetadata = normalizeMetadata(event["extraction_metadata"])
            producer.publish(
                AssistantBankingReaderRequestedEvent(
                    job_id = requireNotNull(job.id),
                    document_type = IMPORT_PDF_DOCUMENT_TYPE,
                    file_type = IMPORT_PDF_FILE_TYPE,
                    account_type = IMPORT_PDF_ACCOUNT_TYPE,
                    source_topic = "pdf-extractor.extracted",
                    extracted_text = extractedText,
                    extraction_metadata = extractionMetadata,
                    callback_context =
                        PdfImportCallbackContext(
                            job_id = requireNotNull(job.id),
                            account_id = job.accountId,
                            user_id = job.createdByUserId,
                        ),
                ),
            )

            val marked = importJobRepository.markAssistantRequestPublished(requireNotNull(job.id))
            if (!marked) {
                log.info("Assistant request marker already set for import jobId={}", job.id)
            }

            deleteAssetBestEffort(event, callbackContext, job.accountId)
            job
        }
    }

    @Topic("\${kafka.topics.pdf.extractor-dlq:pdf-extractor.dlq}")
    fun onExtractorDlq(event: Map<String, Any?>) {
        handleEvent("pdf-extractor.dlq", event) { job, callbackContext ->
            if (job.status.isTerminal()) return@handleEvent job
            val failed =
                markFailed(
                    job = job,
                    details =
                        mapOf(
                            "topic" to "pdf-extractor.dlq",
                            "error" to event["error"]?.toString(),
                        ),
                )
            deleteAssetBestEffort(event, callbackContext, job.accountId)
            failed
        }
    }

    private fun handleEvent(
        topic: String,
        event: Map<String, Any?>,
        action: (job: ImportJob, callbackContext: Map<*, *>) -> ImportJob?,
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
            action(job, callbackContext)
        } catch (e: Exception) {
            log.error("Failed handling {} event", topic, e)
        }
    }

    private fun markFailed(
        job: ImportJob,
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
        return saved
    }

    private fun deleteAssetBestEffort(
        event: Map<String, Any?>,
        callbackContext: Map<*, *>,
        accountId: UUID,
    ) {
        val assetId = toUuid(event["asset_id"]) ?: toUuid(callbackContext["asset_id"]) ?: return
        runCatching { fileStorage.delete(accountId, assetId) }
            .onFailure { e ->
                log.warn("Best-effort delete asset failed after pdf extraction: assetId={}", assetId, e)
            }
    }

    private fun normalizeMetadata(metadata: Any?): Map<String, Any?> =
        when (metadata) {
            null -> emptyMap()
            is Map<*, *> -> metadata.entries.filter { it.key != null }.associate { it.key.toString() to it.value }
            else -> mapOf("raw_extraction_metadata" to metadata.toString())
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
}
