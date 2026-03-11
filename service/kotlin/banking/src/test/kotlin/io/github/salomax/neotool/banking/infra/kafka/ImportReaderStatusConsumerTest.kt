package io.github.salomax.neotool.banking.infra.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobRepository
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportProcessingResult
import io.github.salomax.neotool.banking.domain.import.ImportProcessor
import io.github.salomax.neotool.banking.domain.import.ImportReviewPayloadMapper
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.domain.import.ImportValidationGate
import io.github.salomax.neotool.banking.domain.importevent.ImportEventBus
import io.github.salomax.neotool.banking.domain.transaction.SnapshotComputationService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("ImportReaderStatusConsumer")
class ImportReaderStatusConsumerTest {
    private lateinit var importJobRepository: ImportJobRepository
    private lateinit var importProcessor: ImportProcessor
    private lateinit var importEventBus: ImportEventBus
    private lateinit var jobCompletedProducer: ImportJobCompletedProducer
    private lateinit var snapshotComputationService: SnapshotComputationService
    private lateinit var consumer: ImportReaderStatusConsumer
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        importJobRepository = mock()
        importProcessor = mock()
        importEventBus = mock()
        jobCompletedProducer = mock()
        snapshotComputationService = mock()
        consumer =
            ImportReaderStatusConsumer(
                importJobRepository = importJobRepository,
                importProcessor = importProcessor,
                importEventBus = importEventBus,
                jobCompletedProducer = jobCompletedProducer,
                snapshotComputationService = snapshotComputationService,
                importValidationGate = ImportValidationGate(),
                objectMapper = objectMapper,
            )
    }

    @Test
    fun `done event processes rows and completes import job`() {
        val job = processingJob()
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importProcessor.process(eq(job), any(), eq(false))).thenReturn(
            ImportProcessingResult(
                imported = emptyList(),
                skipped = 1,
                duplicateCandidates = emptyList(),
                errors = emptyList(),
            ),
        )
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderDone(doneEvent(jobId, job.accountId))

        val updateCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(updateCaptor.capture())
        val updated = updateCaptor.firstValue
        assertThat(updated.status).isEqualTo(ImportJobStatus.COMPLETED)
        assertThat(updated.totalRows).isEqualTo(1)
        assertThat(updated.importedCount).isEqualTo(0)
        assertThat(updated.skippedCount).isEqualTo(1)
        verify(jobCompletedProducer).publish(any())
    }

    @Test
    fun `failed event marks import job as failed with details`() {
        val job = processingJob()
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderFailed(failedEvent(jobId, job.accountId))

        val updateCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(updateCaptor.capture())
        val updated = updateCaptor.firstValue
        assertThat(updated.status).isEqualTo(ImportJobStatus.FAILED)
        assertThat(updated.errorDetails).contains("SCHEMA_VALIDATION_FAILED")
        verify(jobCompletedProducer).publish(any())
    }

    @Test
    fun `done event with low llm confidence marks job as review required without processing`() {
        val job = processingJob()
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderDone(
            doneEvent(
                jobId = jobId,
                accountId = job.accountId,
                parseMetadata =
                    mapOf(
                        "processor_type" to "LLM",
                        "reader_id" to "assistant-banking-reader-v1",
                        "confidence" to 0.42,
                        "warnings" to emptyList<String>(),
                    ),
            ),
        )

        verify(importProcessor, never()).process(any(), any(), any())
        val updateCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(updateCaptor.capture())
        val updated = updateCaptor.firstValue
        assertThat(updated.status).isEqualTo(ImportJobStatus.REVIEW_REQUIRED)
        val reviewPayload = ImportReviewPayloadMapper.fromJson(objectMapper, updated.reviewPayload)
        assertThat(reviewPayload).isNotNull
        assertThat(reviewPayload!!.schemaId).isEqualTo(ImportReviewPayloadMapper.SCHEMA_ID)
        assertThat(reviewPayload.duplicateCandidates).isEmpty()
        assertThat(reviewPayload.gate).isNotNull
        assertThat(reviewPayload.gate!!["gate_reason"]).isEqualTo("llm_low_confidence")
        verify(jobCompletedProducer).publish(any())
    }

    @Test
    fun `done event with invalid llm rows marks job as failed without processing`() {
        val job = processingJob()
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onBankingReaderDone(
            doneEventWithInvalidRow(
                jobId = jobId,
                accountId = job.accountId,
            ),
        )

        verify(importProcessor, never()).process(any(), any(), any())
        val updateCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(updateCaptor.capture())
        val updated = updateCaptor.firstValue
        assertThat(updated.status).isEqualTo(ImportJobStatus.FAILED)
        assertThat(updated.errorDetails).contains("llm_invalid_rows")
        verify(jobCompletedProducer).publish(any())
    }

    private fun processingJob(): ImportJob {
        val now = Instant.now()
        return ImportJob(
            id = UUID.randomUUID(),
            financialAccountId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            createdByUserId = UUID.randomUUID(),
            source = ImportSource.AUTO,
            fileName = "planilha.xlsx",
            fileHash = "hash",
            status = ImportJobStatus.PROCESSING,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun doneEvent(
        jobId: UUID,
        accountId: UUID,
        parseMetadata: Map<String, Any?>? = null,
    ): Map<String, Any?> =
        buildMap {
            put("job_id", jobId.toString())
            put("document_type", "BANK_STATEMENT")
            put(
                "llm_output",
                mapOf(
                    "cards" to
                        listOf(
                            mapOf(
                                "transactions" to
                                    listOf(
                                        mapOf(
                                            "date" to "2026-02-03",
                                            "description" to "Salario",
                                            "amount" to 5600.00,
                                            "credit_debit_type" to "CREDIT",
                                        ),
                                    ),
                            ),
                        ),
                ),
            )
            put(
                "callback_context",
                mapOf(
                    "job_id" to jobId.toString(),
                    "account_id" to accountId.toString(),
                    "user_id" to UUID.randomUUID().toString(),
                ),
            )
            if (parseMetadata != null) {
                put("parse_metadata", parseMetadata)
            }
        }

    private fun doneEventWithInvalidRow(
        jobId: UUID,
        accountId: UUID,
    ): Map<String, Any?> =
        mapOf(
            "job_id" to jobId.toString(),
            "document_type" to "BANK_STATEMENT",
            "llm_output" to
                mapOf(
                    "cards" to
                        listOf(
                            mapOf(
                                "transactions" to
                                    listOf(
                                        mapOf(
                                            "date" to "2026-02-03",
                                            "description" to "Salario",
                                            "amount" to 5600.00,
                                            "credit_debit_type" to "CREDIT",
                                        ),
                                        mapOf(
                                            "date" to "",
                                            "description" to "",
                                            "amount" to "abc",
                                            "credit_debit_type" to "UNKNOWN",
                                        ),
                                    ),
                            ),
                        ),
                ),
            "parse_metadata" to
                mapOf(
                    "processor_type" to "LLM",
                    "reader_id" to "assistant-banking-reader-v1",
                    "confidence" to 0.95,
                    "warnings" to emptyList<String>(),
                ),
            "callback_context" to
                mapOf(
                    "job_id" to jobId.toString(),
                    "account_id" to accountId.toString(),
                    "user_id" to UUID.randomUUID().toString(),
                ),
        )

    private fun failedEvent(
        jobId: UUID,
        accountId: UUID,
    ): Map<String, Any?> =
        mapOf(
            "job_id" to jobId.toString(),
            "document_type" to "BANK_STATEMENT",
            "error" to
                mapOf(
                    "code" to "SCHEMA_VALIDATION_FAILED",
                    "message" to "output did not match contract",
                    "retryable" to false,
                ),
            "error_metadata" to mapOf("stage" to "OUTPUT_VALIDATION"),
            "callback_context" to
                mapOf(
                    "job_id" to jobId.toString(),
                    "account_id" to accountId.toString(),
                    "user_id" to UUID.randomUUID().toString(),
                ),
        )
}
