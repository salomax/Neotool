package io.github.salomax.neotool.banking.infra.kafka

import io.github.salomax.neotool.banking.domain.pdfimport.ExtractionRoutingService
import io.github.salomax.neotool.banking.domain.pdfimport.PdfImportService
import io.github.salomax.neotool.common.error.CommonErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.UUID

class PdfImportStatusConsumerTest {
    private val pdfImportService: PdfImportService = mock()
    private val extractionRoutingService: ExtractionRoutingService = mock()
    private val consumer = PdfImportStatusConsumer(pdfImportService, extractionRoutingService)

    @Test
    fun `on banking reader done marks failed when llm_output is missing`() {
        val jobId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val event =
            mapOf(
                "schema_version" to "1.0",
                "job_id" to jobId.toString(),
                "document_type" to "CREDIT_CARD_BILL",
                "callback_context" to
                    mapOf(
                        "job_id" to jobId.toString(),
                        "account_id" to accountId.toString(),
                    ),
            )

        consumer.onBankingReaderDone(event)

        val detailsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(pdfImportService).markFailed(
            jobId = eq(jobId),
            accountId = eq(accountId),
            errorMessage = eq("assistant_pdf_reader_done_invalid_payload: missing_llm_output"),
            errorCode = eq(CommonErrorCode.INTERNAL_ERROR.code),
            errorParameters = anyOrNull(),
            errorDetails = detailsCaptor.capture(),
        )
        verify(pdfImportService, never()).markAwaitingReview(any(), any(), anyOrNull())

        val details = detailsCaptor.firstValue
        assertThat(details["reason"]).isEqualTo("missing_llm_output")
        assertThat(details["topic"]).isEqualTo("assistant.banking-pdf-reader.done")
    }
}
