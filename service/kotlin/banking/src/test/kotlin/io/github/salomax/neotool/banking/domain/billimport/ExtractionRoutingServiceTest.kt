package io.github.salomax.neotool.banking.domain.billimport

import io.github.salomax.neotool.banking.service.billimport.*
import io.github.salomax.neotool.banking.domain.import.CanonicalProcessorType
import io.github.salomax.neotool.banking.domain.import.CanonicalReaderOutput
import io.github.salomax.neotool.banking.service.import.CanonicalReaderOutputMapper
import io.github.salomax.neotool.banking.service.import.fingerprint.FingerprintClassification
import io.github.salomax.neotool.banking.service.import.fingerprint.FingerprintClassificationInput
import io.github.salomax.neotool.banking.service.import.fingerprint.FingerprintClassifierRegistry
import io.github.salomax.neotool.banking.service.billimport.pdf.DeterministicPdfReaderRouter
import io.github.salomax.neotool.banking.client.import.messaging.AssistantBankingReaderRequestedEvent
import io.github.salomax.neotool.banking.client.import.messaging.AssistantBankingReaderRequestedProducer
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

@DisplayName("ExtractionRoutingService")
class ExtractionRoutingServiceTest {
    private lateinit var billImportService: BillImportService
    private lateinit var classifierRegistry: FingerprintClassifierRegistry
    private lateinit var deterministicPdfReaderRouter: DeterministicPdfReaderRouter
    private lateinit var assistantRequestProducer: AssistantBankingReaderRequestedProducer
    private lateinit var service: ExtractionRoutingService

    private val accountId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val jobId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        billImportService = mock()
        classifierRegistry = mock()
        deterministicPdfReaderRouter = mock()
        assistantRequestProducer = mock()
        service =
            ExtractionRoutingService(
                billImportService = billImportService,
                fingerprintClassifierRegistry = classifierRegistry,
                deterministicPdfReaderRouter = deterministicPdfReaderRouter,
                assistantRequestProducer = assistantRequestProducer,
            )
    }

    @Test
    fun `unknown fingerprint publishes assistant request and marks LLM processing`() {
        val metadata = mapOf("page_count" to 2, "tables_found" to 1)
        val extracted = extractedJob()
        val llmProcessing = extracted.copy(status = BillImportJobStatus.LLM_PROCESSING)
        whenever(
            billImportService.markExtracted(
                eq(jobId),
                eq(accountId),
                eq("extracted text"),
                eq(metadata),
            ),
        ).thenReturn(extracted)
        whenever(classifierRegistry.classify(any<FingerprintClassificationInput>())).thenReturn(
            FingerprintClassification.unknown("no_known_pdf_signature_match"),
        )
        whenever(billImportService.markLlmProcessing(eq(jobId), eq(accountId))).thenReturn(llmProcessing)

        val result =
            service.routeBillImportExtraction(
                sourceTopic = "pdf-extractor.extracted",
                jobId = jobId,
                accountId = accountId,
                callbackContext = callbackContext(),
                documentType = "CREDIT_CARD_BILL",
                extractedText = "extracted text",
                extractionMetadata = metadata,
            )

        assertThat(result?.status).isEqualTo(BillImportJobStatus.LLM_PROCESSING)
        val eventCaptor = argumentCaptor<AssistantBankingReaderRequestedEvent>()
        verify(assistantRequestProducer).publish(eventCaptor.capture())
        val event = eventCaptor.firstValue
        assertThat(event.job_id).isEqualTo(jobId)
        assertThat(event.document_type).isEqualTo("CREDIT_CARD_BILL")
        assertThat(event.file_type).isEqualTo("PDF")
        assertThat(event.account_type).isEqualTo("CREDIT")
        assertThat(event.source_topic).isEqualTo("pdf-extractor.extracted")
        assertThat(event.extracted_text).isEqualTo("extracted text")
        val fingerprint = event.extraction_metadata["fingerprint"] as? Map<*, *>
        assertThat(fingerprint).isNotNull()
        assertThat(fingerprint?.get("status")).isEqualTo("unknown_profile")
        assertThat(fingerprint?.get("reason")).isEqualTo("no_known_pdf_signature_match")
        assertThat(event.extraction_metadata["page_count"]).isEqualTo(2)
        assertThat(event.extraction_metadata["tables_found"]).isEqualTo(1)
        assertThat(event.callback_context.job_id).isEqualTo(jobId)
        assertThat(event.callback_context.account_id).isEqualTo(accountId)
        assertThat(event.callback_context.user_id).isEqualTo(userId)
        verify(billImportService).markLlmProcessing(jobId, accountId)
        verify(deterministicPdfReaderRouter, never()).parse(any(), any(), any(), any())
    }

    @Test
    fun `known fingerprint parses deterministic output and marks awaiting review`() {
        val metadata = mapOf("profile_id" to "pdf-itau-v1")
        val extracted = extractedJob()
        val deterministicOutput =
            CanonicalReaderOutput(
                schemaId = CanonicalReaderOutputMapper.SCHEMA_ID,
                schemaVersion = CanonicalReaderOutputMapper.SCHEMA_VERSION,
                processorType = CanonicalProcessorType.DETERMINISTIC,
                readerId = "pdf-reader-itau-credit-card-v1",
                confidence = 1.0,
                normalizedRecords = listOf(mapOf("record_type" to "PDF_CARD_SUMMARY")),
            )
        val awaiting = extracted.copy(status = BillImportJobStatus.AWAITING_REVIEW)
        whenever(
            billImportService.markExtracted(
                eq(jobId),
                eq(accountId),
                eq("text"),
                eq(metadata),
            ),
        ).thenReturn(extracted)
        whenever(classifierRegistry.classify(any<FingerprintClassificationInput>())).thenReturn(
            FingerprintClassification.known(
                profileId = "pdf-itau-credit-card-bill-v1",
                reason = "metadata_profile_id_match",
            ),
        )
        whenever(
            deterministicPdfReaderRouter.parse(
                eq("pdf-itau-credit-card-bill-v1"),
                eq("CREDIT_CARD_BILL"),
                eq("text"),
                eq(metadata),
            ),
        ).thenReturn(deterministicOutput)
        whenever(
            billImportService.markAwaitingReviewFromCanonical(
                eq(jobId),
                eq(accountId),
                eq(deterministicOutput),
            ),
        ).thenReturn(awaiting)

        val result =
            service.routeBillImportExtraction(
                sourceTopic = "pdf-extractor.extracted",
                jobId = jobId,
                accountId = accountId,
                callbackContext = callbackContext(),
                documentType = "CREDIT_CARD_BILL",
                extractedText = "text",
                extractionMetadata = metadata,
            )

        assertThat(result?.status).isEqualTo(BillImportJobStatus.AWAITING_REVIEW)
        verify(assistantRequestProducer, never()).publish(any())
        verify(billImportService, never()).markLlmProcessing(any(), any())
        verify(deterministicPdfReaderRouter).parse(
            "pdf-itau-credit-card-bill-v1",
            "CREDIT_CARD_BILL",
            "text",
            metadata,
        )
        verify(billImportService).markAwaitingReviewFromCanonical(
            jobId,
            accountId,
            deterministicOutput,
        )
    }

    @Test
    fun `known fingerprint routes to LLM when deterministic path is disabled`() {
        val serviceWithDeterministicDisabled =
            ExtractionRoutingService(
                billImportService = billImportService,
                fingerprintClassifierRegistry = classifierRegistry,
                deterministicPdfReaderRouter = deterministicPdfReaderRouter,
                assistantRequestProducer = assistantRequestProducer,
                deterministicEnabled = false,
            )
        val metadata = mapOf("profile_id" to "pdf-nubank-credit-card-bill-v1")
        val extracted = extractedJob()
        val llmProcessing = extracted.copy(status = BillImportJobStatus.LLM_PROCESSING)

        whenever(
            billImportService.markExtracted(
                eq(jobId),
                eq(accountId),
                eq("text"),
                eq(metadata),
            ),
        ).thenReturn(extracted)
        whenever(classifierRegistry.classify(any<FingerprintClassificationInput>())).thenReturn(
            FingerprintClassification.known(
                profileId = "pdf-nubank-credit-card-bill-v1",
                reason = "metadata_profile_id_match",
            ),
        )
        whenever(billImportService.markLlmProcessing(eq(jobId), eq(accountId))).thenReturn(llmProcessing)

        val result =
            serviceWithDeterministicDisabled.routeBillImportExtraction(
                sourceTopic = "pdf-extractor.extracted",
                jobId = jobId,
                accountId = accountId,
                callbackContext = callbackContext(),
                documentType = "CREDIT_CARD_BILL",
                extractedText = "text",
                extractionMetadata = metadata,
            )

        assertThat(result?.status).isEqualTo(BillImportJobStatus.LLM_PROCESSING)
        verify(deterministicPdfReaderRouter, never()).parse(any(), any(), any(), any())
        verify(billImportService, never()).markAwaitingReviewFromCanonical(any(), any(), any())
        verify(assistantRequestProducer).publish(any())
        verify(billImportService).markLlmProcessing(jobId, accountId)
    }

    @Test
    fun `uses createdByUserId as callback fallback when user_id is absent`() {
        val metadata = mapOf<String, Any?>()
        val extracted = extractedJob(createdByUserId = userId)
        whenever(
            billImportService.markExtracted(
                eq(jobId),
                eq(accountId),
                eq("text"),
                eq(metadata),
            ),
        ).thenReturn(extracted)
        whenever(classifierRegistry.classify(any<FingerprintClassificationInput>())).thenReturn(
            FingerprintClassification.unknown("no_known_pdf_signature_match"),
        )
        whenever(billImportService.markLlmProcessing(eq(jobId), eq(accountId))).thenReturn(
            extracted.copy(status = BillImportJobStatus.LLM_PROCESSING),
        )

        service.routeBillImportExtraction(
            sourceTopic = "pdf-extractor.extracted",
            jobId = jobId,
            accountId = accountId,
            callbackContext =
                mapOf(
                    "job_id" to jobId.toString(),
                    "account_id" to accountId.toString(),
                ),
            documentType = "CREDIT_CARD_BILL",
            extractedText = "text",
            extractionMetadata = metadata,
        )

        val eventCaptor = argumentCaptor<AssistantBankingReaderRequestedEvent>()
        verify(assistantRequestProducer).publish(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.callback_context.user_id).isEqualTo(userId)
    }

    @Test
    fun `bank statement uses BANK account_type and importType fallback for document_type`() {
        val metadata = mapOf("page_count" to 1)
        val extracted = extractedJob(importType = ImportDocumentType.BANK_STATEMENT)
        whenever(
            billImportService.markExtracted(
                eq(jobId),
                eq(accountId),
                eq("text"),
                eq(metadata),
            ),
        ).thenReturn(extracted)
        whenever(classifierRegistry.classify(any<FingerprintClassificationInput>())).thenReturn(
            FingerprintClassification.unknown("no_known_pdf_signature_match"),
        )
        whenever(billImportService.markLlmProcessing(eq(jobId), eq(accountId))).thenReturn(
            extracted.copy(status = BillImportJobStatus.LLM_PROCESSING),
        )

        service.routeBillImportExtraction(
            sourceTopic = "pdf-extractor.extracted",
            jobId = jobId,
            accountId = accountId,
            callbackContext = callbackContext(),
            documentType = null,
            extractedText = "text",
            extractionMetadata = metadata,
        )

        val eventCaptor = argumentCaptor<AssistantBankingReaderRequestedEvent>()
        verify(assistantRequestProducer).publish(eventCaptor.capture())
        assertThat(eventCaptor.firstValue.document_type).isEqualTo("BANK_STATEMENT")
        assertThat(eventCaptor.firstValue.account_type).isEqualTo("BANK")
    }

    private fun extractedJob(
        createdByUserId: UUID = userId,
        importType: ImportDocumentType = ImportDocumentType.CREDIT_CARD_BILL,
    ): BillImportJob =
        BillImportJob(
            id = jobId,
            importType = importType,
            financialAccountId = null,
            accountId = accountId,
            createdByUserId = createdByUserId,
            status = BillImportJobStatus.EXTRACTED,
            assetId = UUID.randomUUID(),
            fileName = "fatura.pdf",
            fileHash = "hash",
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    private fun callbackContext(): Map<String, String> =
        mapOf(
            "job_id" to jobId.toString(),
            "account_id" to accountId.toString(),
            "user_id" to userId.toString(),
        )
}
