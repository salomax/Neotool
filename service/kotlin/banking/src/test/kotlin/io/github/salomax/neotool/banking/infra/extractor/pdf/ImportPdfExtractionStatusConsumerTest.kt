package io.github.salomax.neotool.banking.infra.extractor.pdf

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.client.assets.ImportFileStoragePort
import io.github.salomax.neotool.banking.client.import.messaging.AssistantBankingReaderRequestedEvent
import io.github.salomax.neotool.banking.client.import.messaging.AssistantBankingReaderRequestedProducer
import io.github.salomax.neotool.banking.client.import.messaging.ImportJobCompletedProducer
import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.github.salomax.neotool.banking.job.import.ImportPdfExtractionStatusConsumer
import io.github.salomax.neotool.banking.repo.import.ImportJobRepository
import io.github.salomax.neotool.banking.service.import.event.ImportEventBus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@DisplayName("ImportPdfExtractionStatusConsumer")
class ImportPdfExtractionStatusConsumerTest {
    private lateinit var importJobRepository: ImportJobRepository
    private lateinit var importEventBus: ImportEventBus
    private lateinit var jobCompletedProducer: ImportJobCompletedProducer
    private lateinit var fileStorage: ImportFileStoragePort
    private lateinit var assistantProducer: AssistantBankingReaderRequestedProducer
    private lateinit var consumer: ImportPdfExtractionStatusConsumer

    @BeforeEach
    fun setUp() {
        importJobRepository = mock()
        importEventBus = mock()
        jobCompletedProducer = mock()
        fileStorage = mock()
        assistantProducer = mock()
        consumer =
            ImportPdfExtractionStatusConsumer(
                importJobRepository = importJobRepository,
                importEventBus = importEventBus,
                jobCompletedProducer = jobCompletedProducer,
                fileStorage = fileStorage,
                objectMapper = jacksonObjectMapper(),
                assistantBankingReaderRequestedProducer = assistantProducer,
            )
    }

    @Test
    fun `extractor extracted publishes assistant request and marks assistant marker`() {
        val assetId = UUID.randomUUID()
        val job = processingJob(fileName = "extrato.pdf")
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importJobRepository.markAssistantRequestPublished(eq(jobId))).thenReturn(true)
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument<ImportJob>(0) }

        consumer.onExtractorExtracted(
            mapOf(
                "job_id" to jobId.toString(),
                "asset_id" to assetId.toString(),
                "document_type" to "BANK_STATEMENT",
                "extracted_text" to "linha 1\nlinha 2",
                "extraction_metadata" to mapOf("page_count" to 2),
                "callback_context" to
                    mapOf(
                        "job_id" to jobId.toString(),
                        "account_id" to job.accountId.toString(),
                        "user_id" to job.createdByUserId.toString(),
                    ),
            ),
        )

        val eventCaptor = argumentCaptor<AssistantBankingReaderRequestedEvent>()
        verify(assistantProducer).publish(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.job_id).isEqualTo(jobId)
        assertThat(eventCaptor.firstValue.document_type).isEqualTo("BANK_STATEMENT")
        assertThat(eventCaptor.firstValue.file_type).isEqualTo("PDF")
        assertThat(eventCaptor.firstValue.account_type).isEqualTo("BANK")
        assertThat(eventCaptor.firstValue.extracted_text).contains("linha 1")
        verify(importJobRepository).markAssistantRequestPublished(jobId)
        verify(fileStorage).delete(job.accountId, assetId)
        verify(importJobRepository).update(any())
    }

    @Test
    fun `extractor dlq marks job as failed and emits terminal events`() {
        val assetId = UUID.randomUUID()
        val job = processingJob(fileName = "extrato.pdf")
        val jobId = requireNotNull(job.id)
        whenever(importJobRepository.findById(eq(jobId), eq(job.accountId))).thenReturn(job)
        whenever(importJobRepository.update(any())).thenAnswer { it.getArgument(0) }

        consumer.onExtractorDlq(
            mapOf(
                "job_id" to jobId.toString(),
                "asset_id" to assetId.toString(),
                "error" to "extractor_timeout",
                "callback_context" to
                    mapOf(
                        "job_id" to jobId.toString(),
                        "account_id" to job.accountId.toString(),
                        "user_id" to job.createdByUserId.toString(),
                    ),
            ),
        )

        val jobCaptor = argumentCaptor<ImportJob>()
        verify(importJobRepository).update(jobCaptor.capture())
        assertThat(jobCaptor.firstValue.status).isEqualTo(ImportJobStatus.FAILED)
        assertThat(jobCaptor.firstValue.errorDetails).contains("pdf-extractor.dlq")
        verify(importEventBus).emit(any())
        verify(jobCompletedProducer).publish(any())
        verify(fileStorage).delete(job.accountId, assetId)
    }

    private fun processingJob(fileName: String): ImportJob {
        val now = Instant.now()
        return ImportJob(
            id = UUID.randomUUID(),
            financialAccountId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            createdByUserId = UUID.randomUUID(),
            source = ImportSource.AUTO,
            fileName = fileName,
            fileHash = "hash",
            status = ImportJobStatus.PROCESSING,
            createdAt = now,
            updatedAt = now,
        )
    }
}
