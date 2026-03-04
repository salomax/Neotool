package io.github.salomax.neotool.banking.infra.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobRepository
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportProcessor
import io.github.salomax.neotool.banking.domain.import.port.ImportFileStoragePort
import io.github.salomax.neotool.banking.domain.transaction.SnapshotComputationService
import io.github.salomax.neotool.banking.domain.import_.ImportEventBus
import io.github.salomax.neotool.banking.domain.import_.ImportJobStatusPayload
import io.github.salomax.neotool.banking.infra.kafka.parser.CsvImportParserStrategy
import io.github.salomax.neotool.banking.infra.kafka.parser.ImportParserRouter
import io.github.salomax.neotool.banking.infra.kafka.parser.OfxImportParserStrategy
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

@Singleton
@Requires(property = "kafka.enabled", value = "true")
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
    @Property(name = "kafka.topics.import-file-requested")
    private val topicName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val parserRouter = ImportParserRouter(
        strategies = listOf(
            OfxImportParserStrategy(),
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
            val job = importJobRepository.findById(jobId, accountId)
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

            // 2. Update → PROCESSING; emit PROCESSING
            val now = Instant.now()
            var currentJob = job.copy(
                status = ImportJobStatus.PROCESSING,
                updatedAt = now,
            )
            importJobRepository.update(currentJob)
            emitStatus(currentJob)

            // 3. Read file bytes
            val bytes = fileStorage.read(assetId)

            // 4. Discover best parser strategy and parse
            val parseOutcome = parserRouter.parse(
                bytes = bytes,
                fileName = currentJob.fileName,
                requestedSource = event.source,
            )
            val rows = parseOutcome.rows
            if (parseOutcome.resolvedSource != null && currentJob.source != parseOutcome.resolvedSource) {
                currentJob = currentJob.copy(
                    source = parseOutcome.resolvedSource,
                    updatedAt = now,
                )
                importJobRepository.update(currentJob)
            }

            if (rows.isEmpty()) {
                currentJob = currentJob.copy(
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
                deleteAssetBestEffort(assetId)
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
                if (result.duplicateCandidates.isEmpty()) null
                else objectMapper.writeValueAsString(result.duplicateCandidates)

            val finalStatus = when {
                duplicateReviewCount > 0 -> ImportJobStatus.REVIEW_REQUIRED
                importedCount == 0 && skippedCount == 0 && errorCount > 0 -> ImportJobStatus.FAILED
                else -> ImportJobStatus.COMPLETED
            }
            val errorDetailsJson: String? =
                if (errorCount > 0) objectMapper.writeValueAsString(result.errors.take(50))
                else null

            currentJob = currentJob.copy(
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
            deleteAssetBestEffort(assetId)

            commitOffset(consumer, partition, offset)
        } catch (e: Exception) {
            log.error("Import processing failed: jobId={}, assetId={}", jobId, assetId, e)
            try {
                val failedJob = importJobRepository.findById(jobId, accountId)
                if (failedJob != null) {
                    val now = Instant.now()
                    val errorDetails = objectMapper.writeValueAsString(
                        mapOf(
                            "message" to (e.message ?: "Import processing failed"),
                            "stackSummary" to stackSummary(e),
                        ),
                    )
                    val updated = failedJob.copy(
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
                deleteAssetBestEffort(assetId)
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

    private fun deleteAssetBestEffort(assetId: UUID) {
        try {
            fileStorage.delete(assetId)
        } catch (e: Exception) {
            log.warn("Best-effort delete asset failed (TTL will cleanup): assetId={}", assetId, e)
        }
    }

    private fun stackSummary(e: Throwable): String {
        return e.stackTraceToString().lines().take(15).joinToString("\n")
    }

    private fun commitOffset(consumer: Consumer<*, *>, partition: Int, offset: Long) {
        val tp = TopicPartition(topicName, partition)
        consumer.commitSync(mapOf(tp to OffsetAndMetadata(offset + 1)))
    }
}
