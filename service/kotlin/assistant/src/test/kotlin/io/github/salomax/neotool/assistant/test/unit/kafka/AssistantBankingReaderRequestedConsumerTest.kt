package io.github.salomax.neotool.assistant.test.unit.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingPdfReaderFailedProducer
import io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingReaderRequestedConsumer
import io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingReaderRequestedEvent
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderProcessingException
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderRequestedRoutingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.util.UUID

class AssistantBankingReaderRequestedConsumerTest {
    private lateinit var objectMapper: ObjectMapper
    private lateinit var routingService: BankingReaderRequestedRoutingService
    private lateinit var failedProducer: AssistantBankingPdfReaderFailedProducer
    private lateinit var consumer: AssistantBankingReaderRequestedConsumer

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper()
        routingService = mock()
        failedProducer = mock()
        consumer =
            AssistantBankingReaderRequestedConsumer(
                objectMapper = objectMapper,
                routingService = routingService,
                failedProducer = failedProducer,
            )
    }

    @Test
    fun `onRequested routes CREDIT_CARD_BILL to credit card handler`() {
        val payload = validPayload(documentType = "CREDIT_CARD_BILL")

        consumer.onRequested(UUID.randomUUID().toString(), payload)

        val requestCaptor = argumentCaptor<AssistantBankingReaderRequestedEvent>()
        verify(routingService).onCreditCardBill(requestCaptor.capture())
        assertThat(requestCaptor.firstValue.document_type).isEqualTo("CREDIT_CARD_BILL")
        verify(failedProducer, never()).publish(any())
    }

    @Test
    fun `onRequested routes BANK_STATEMENT to bank statement handler`() {
        val payload = validPayload(documentType = "BANK_STATEMENT")

        consumer.onRequested(UUID.randomUUID().toString(), payload)

        verify(routingService).onBankStatement(any())
        verify(failedProducer, never()).publish(any())
    }

    @Test
    fun `onRequested publishes failed event when payload is structurally invalid`() {
        val payload = validPayload(documentType = "CREDIT_CARD_BILL", extractedText = "   ")

        consumer.onRequested(UUID.randomUUID().toString(), payload)

        val failedCaptor = argumentCaptor<io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingPdfReaderFailedEvent>()
        verify(failedProducer).publish(failedCaptor.capture())
        val failedEvent = failedCaptor.firstValue
        assertThat(failedEvent.error.code).isEqualTo("INVALID_PAYLOAD")
        assertThat(failedEvent.error.message).contains("extracted_text")
        verify(routingService, never()).onCreditCardBill(any())
        verify(routingService, never()).onBankStatement(any())
        verify(routingService, never()).onInvestmentIrReport(any())
    }

    @Test
    fun `onRequested publishes failed event with key fallback when JSON is malformed`() {
        val key = UUID.randomUUID()

        consumer.onRequested(key.toString(), """{"schema_version":"1.0", "job_id": }""")

        val failedCaptor = argumentCaptor<io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingPdfReaderFailedEvent>()
        verify(failedProducer).publish(failedCaptor.capture())
        val failedEvent = failedCaptor.firstValue
        assertThat(failedEvent.job_id).isEqualTo(key)
        assertThat(failedEvent.error.code).isEqualTo("INVALID_PAYLOAD")
        assertThat(failedEvent.error.message).contains("Invalid JSON payload")
        verify(routingService, never()).onCreditCardBill(any())
    }

    @Test
    fun `onRequested publishes unsupported document type failure`() {
        val payload = validPayload(documentType = "CUSTOM_DOC")

        consumer.onRequested(UUID.randomUUID().toString(), payload)

        val failedCaptor = argumentCaptor<io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingPdfReaderFailedEvent>()
        verify(failedProducer).publish(failedCaptor.capture())
        val failedEvent = failedCaptor.firstValue
        assertThat(failedEvent.error.code).isEqualTo("UNSUPPORTED_DOCUMENT_TYPE")
        assertThat(failedEvent.document_type).isEqualTo("CUSTOM_DOC")
        verify(routingService, never()).onCreditCardBill(any())
        verify(routingService, never()).onBankStatement(any())
        verify(routingService, never()).onInvestmentIrReport(any())
    }

    @Test
    fun `onRequested maps typed routing failure to failed event code and stage`() {
        val payload = validPayload(documentType = "CREDIT_CARD_BILL")
        doThrow(
            BankingReaderProcessingException(
                code = "SCHEMA_VALIDATION_FAILED",
                message = "output did not match schema",
                retryable = false,
                stage = "OUTPUT_VALIDATION",
            ),
        ).`when`(routingService).onCreditCardBill(any())

        consumer.onRequested(UUID.randomUUID().toString(), payload)

        val failedCaptor = argumentCaptor<io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingPdfReaderFailedEvent>()
        verify(failedProducer).publish(failedCaptor.capture())
        val failedEvent = failedCaptor.firstValue
        assertThat(failedEvent.error.code).isEqualTo("SCHEMA_VALIDATION_FAILED")
        assertThat(failedEvent.error.message).contains("output did not match schema")
        assertThat(failedEvent.error_metadata.stage).isEqualTo("OUTPUT_VALIDATION")
    }

    private fun validPayload(
        documentType: String,
        extractedText: String = "Sample extracted text",
    ): String {
        val jobId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val payload =
            mapOf(
                "schema_version" to "1.0",
                "job_id" to jobId.toString(),
                "document_type" to documentType,
                "file_type" to "PDF",
                "account_type" to "CREDIT",
                "source_topic" to "pdf-extractor.extracted",
                "extracted_text" to extractedText,
                "extraction_metadata" to mapOf("page_count" to 1),
                "callback_context" to
                    mapOf(
                        "job_id" to jobId.toString(),
                        "account_id" to accountId.toString(),
                        "user_id" to userId.toString(),
                    ),
            )
        return objectMapper.writeValueAsString(payload)
    }
}
