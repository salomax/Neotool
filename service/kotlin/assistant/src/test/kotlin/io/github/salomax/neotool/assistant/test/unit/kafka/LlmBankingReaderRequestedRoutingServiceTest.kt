package io.github.salomax.neotool.assistant.test.unit.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingReaderRequestedEvent
import io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingPdfReaderDoneProducer
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderCallbackContext
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderOutputValidator
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderProcessingException
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderPromptResolver
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderPromptResolverConfig
import io.github.salomax.neotool.assistant.infra.kafka.LlmBankingReaderRequestedRoutingService
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import io.github.salomax.neotool.assistant.service.CompletionInput
import io.github.salomax.neotool.assistant.service.CompletionResult
import io.github.salomax.neotool.assistant.service.CompletionService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.runBlocking

class LlmBankingReaderRequestedRoutingServiceTest {
    private lateinit var completionService: CompletionService
    private lateinit var doneProducer: AssistantBankingPdfReaderDoneProducer
    private lateinit var promptResolver: BankingReaderPromptResolver
    private lateinit var outputValidator: BankingReaderOutputValidator
    private lateinit var service: LlmBankingReaderRequestedRoutingService

    @BeforeEach
    fun setUp() {
        completionService = mock()
        doneProducer = mock()
        promptResolver =
            BankingReaderPromptResolver(
                BankingReaderPromptResolverConfig().apply {
                    creditCardBillPromptId = null
                    bankStatementPromptId = null
                },
            )
        outputValidator = BankingReaderOutputValidator()
        service =
            LlmBankingReaderRequestedRoutingService(
                completionService = completionService,
                promptResolver = promptResolver,
                outputValidator = outputValidator,
                doneProducer = doneProducer,
                objectMapper = jacksonObjectMapper(),
            )
    }

    @Test
    fun `onCreditCardBill calls completion service in JSON mode and validates output`() {
        val request = validRequest(documentType = "CREDIT_CARD_BILL", accountType = "CREDIT")
        runBlocking {
            whenever(completionService.generateCompletion(any(), any())).thenReturn(
                completionResult(
                    content =
                        """
                        {
                          "bank_name":"Nubank",
                          "statement_period":{"from":"2026-01-01","to":"2026-01-31"},
                          "cards":[],
                          "extraction_confidence":0.94,
                          "needs_review":false,
                          "warnings":["low quality logo text"]
                        }
                        """.trimIndent(),
                ),
            )
        }

        service.onCreditCardBill(request)

        val inputCaptor = argumentCaptor<CompletionInput>()
        runBlocking {
            verify(completionService).generateCompletion(inputCaptor.capture(), eq(request.callback_context.account_id))
        }
        val completionInput = inputCaptor.firstValue
        assertThat(completionInput.outputFormat?.name).isEqualTo("JSON")
        assertThat(completionInput.requiredCapabilities).contains("JSON_MODE")
        assertThat(completionInput.purpose).isEqualTo("banking_pdf_reader_credit_card_bill")
        assertThat(completionInput.messages).hasSize(1)
        assertThat(completionInput.messages.first().role).isEqualTo(ProviderMessageRole.USER)
        assertThat(completionInput.messages.first().content).contains(request.extracted_text)

        val doneCaptor =
            argumentCaptor<io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingPdfReaderDoneEvent>()
        verify(doneProducer).publish(doneCaptor.capture())
        val doneEvent = doneCaptor.firstValue
        assertThat(doneEvent.job_id).isEqualTo(request.job_id)
        assertThat(doneEvent.callback_context.account_id).isEqualTo(request.callback_context.account_id)
        assertThat(doneEvent.parse_metadata.reader_id).isEqualTo("assistant-banking-pdf-reader-v1")
        assertThat(doneEvent.parse_metadata.processor_type).isEqualTo("LLM")
        assertThat(doneEvent.parse_metadata.confidence).isEqualTo(0.94)
        assertThat(doneEvent.parse_metadata.model_name).isEqualTo("gpt-4o-mini")
        assertThat(doneEvent.parse_metadata.latency_ms).isEqualTo(1200)
        assertThat(doneEvent.parse_metadata.warnings).containsExactly("low quality logo text")
    }

    @Test
    fun `onCreditCardBill maps provider invalid-json failure to LLM_NON_JSON`() {
        val request = validRequest(documentType = "CREDIT_CARD_BILL", accountType = "CREDIT")
        runBlocking {
            whenever(completionService.generateCompletion(any(), any())).thenThrow(
                ProviderClientException(
                    errorType = ErrorType.PROVIDER_RESPONSE_INVALID,
                    message = "Provider returned invalid JSON content for output format JSON",
                ),
            )
        }

        assertThatThrownBy { service.onCreditCardBill(request) }
            .isInstanceOfSatisfying(BankingReaderProcessingException::class.java) { ex ->
                assertThat(ex.code).isEqualTo("LLM_NON_JSON")
                assertThat(ex.stage).isEqualTo("LLM_CALL")
                assertThat(ex.retryable).isFalse()
            }
        verify(doneProducer, never()).publish(any())
    }

    @Test
    fun `onCreditCardBill maps schema-invalid output to SCHEMA_VALIDATION_FAILED`() {
        val request = validRequest(documentType = "CREDIT_CARD_BILL", accountType = "CREDIT")
        runBlocking {
            whenever(completionService.generateCompletion(any(), any())).thenReturn(
                completionResult(content = """{"bank_name":"Nubank"}"""),
            )
        }

        assertThatThrownBy { service.onCreditCardBill(request) }
            .isInstanceOfSatisfying(BankingReaderProcessingException::class.java) { ex ->
                assertThat(ex.code).isEqualTo("SCHEMA_VALIDATION_FAILED")
                assertThat(ex.stage).isEqualTo("OUTPUT_VALIDATION")
                assertThat(ex.retryable).isFalse()
                assertThat(ex.message).contains("statement_period")
            }
        verify(doneProducer, never()).publish(any())
    }

    @Test
    fun `onInvestmentIrReport fails when tuple has no prompt mapping`() {
        val request = validRequest(documentType = "INVESTMENT_IR_REPORT", accountType = "INVESTMENT")

        assertThatThrownBy { service.onInvestmentIrReport(request) }
            .isInstanceOfSatisfying(BankingReaderProcessingException::class.java) { ex ->
                assertThat(ex.code).isEqualTo("PROMPT_RESOLUTION_ERROR")
                assertThat(ex.stage).isEqualTo("PROMPT_SELECTION")
                assertThat(ex.retryable).isFalse()
            }
        verify(doneProducer, never()).publish(any())
    }

    @Test
    fun `onBankStatement accepts XLSX tuple and publishes done`() {
        val request = validRequest(documentType = "BANK_STATEMENT", accountType = "BANK", fileType = "XLSX")
        runBlocking {
            whenever(completionService.generateCompletion(any(), any())).thenReturn(
                completionResult(
                    content =
                        """
                        {
                          "bank_name":"Itau",
                          "statement_period":{"from":"2026-02-01","to":"2026-02-28"},
                          "cards":[{"last_four_digits":"0000","closing_date":"2026-02-28","due_date":"2026-03-10","declared_total":0.0,"computed_total":0.0,"total_matches_declared":true,"transactions":[],"confidence":0.91}],
                          "extraction_confidence":0.91,
                          "needs_review":false,
                          "warnings":[]
                        }
                        """.trimIndent(),
                ),
            )
        }

        service.onBankStatement(request)

        val inputCaptor = argumentCaptor<CompletionInput>()
        runBlocking {
            verify(completionService).generateCompletion(inputCaptor.capture(), eq(request.callback_context.account_id))
        }
        assertThat(inputCaptor.firstValue.purpose).isEqualTo("banking_reader_bank_statement")

        val doneCaptor =
            argumentCaptor<io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingPdfReaderDoneEvent>()
        verify(doneProducer).publish(doneCaptor.capture())
        assertThat(doneCaptor.firstValue.parse_metadata.reader_id).isEqualTo("assistant-banking-reader-v1")
    }

    private fun validRequest(
        documentType: String,
        accountType: String,
        fileType: String = "PDF",
    ): AssistantBankingReaderRequestedEvent {
        val jobId = UUID.randomUUID()
        return AssistantBankingReaderRequestedEvent(
            schema_version = "1.0",
            job_id = jobId,
            document_type = documentType,
            file_type = fileType,
            account_type = accountType,
            source_topic = "pdf-extractor.extracted",
            extracted_text = "sample extracted text",
            extraction_metadata = mapOf("page_count" to 1),
            callback_context =
                BankingReaderCallbackContext(
                    job_id = jobId,
                    account_id = UUID.randomUUID(),
                    user_id = UUID.randomUUID(),
                ),
        )
    }

    private fun completionResult(content: String): CompletionResult =
        CompletionResult(
            id = UUID.randomUUID(),
            requestId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            providerId = UUID.randomUUID(),
            modelId = UUID.randomUUID(),
            model = "gpt-4o-mini",
            content = content,
            finishReason = "stop",
            usage = ProviderTokenUsage(promptTokens = 100, completionTokens = 100, totalTokens = 200),
            latencyMs = 1200,
            cost = BigDecimal("0.001"),
            escalate = false,
        )
}
