package io.github.salomax.neotool.assistant.test.unit.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingReaderDoneEvent
import io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingReaderDoneProducer
import io.github.salomax.neotool.assistant.infra.kafka.AssistantBankingReaderRequestedEvent
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderCallbackContext
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderOutputValidator
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderProcessingException
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderPromptResolver
import io.github.salomax.neotool.assistant.infra.kafka.BankingReaderPromptResolverConfig
import io.github.salomax.neotool.assistant.infra.kafka.BillExtractionMetaValidator
import io.github.salomax.neotool.assistant.infra.kafka.LlmBankingReaderRequestedRoutingService
import io.github.salomax.neotool.assistant.infra.kafka.StatementExtractionMetaValidator
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.provider.model.ProviderTokenUsage
import io.github.salomax.neotool.assistant.service.CompletionInput
import io.github.salomax.neotool.assistant.service.CompletionResult
import io.github.salomax.neotool.assistant.service.CompletionService
import kotlinx.coroutines.runBlocking
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

class LlmBankingReaderRequestedRoutingServiceTest {
    private lateinit var completionService: CompletionService
    private lateinit var doneProducer: AssistantBankingReaderDoneProducer
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
                metaValidator = BillExtractionMetaValidator(),
                statementMetaValidator = StatementExtractionMetaValidator(),
                doneProducer = doneProducer,
                objectMapper = jacksonObjectMapper(),
            )
    }

    @Test
    fun `onCreditCardBill calls completion service in JSON mode with meta prompt and publishes done`() {
        val request = validRequest(documentType = "CREDIT_CARD_BILL", accountType = "CREDIT")
        runBlocking {
            whenever(completionService.generateCompletion(any(), any())).thenReturn(
                completionResult(content = MINIMAL_BILL_META_JSON),
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
        assertThat(completionInput.purpose).isEqualTo("banking_bill_reader_credit_card_bill")
        assertThat(completionInput.messages).hasSize(1)
        assertThat(completionInput.messages.first().role).isEqualTo(ProviderMessageRole.USER)
        assertThat(completionInput.messages.first().content).contains(request.extracted_text)
        // v2 prompt instructs LLM to return structure only, not transactions
        assertThat(completionInput.messages.first().content).contains("Do NOT extract individual transactions")
        assertThat(completionInput.messages.first().content).contains("anchor_start")
        assertThat(completionInput.messages.first().content).contains("transaction_pattern")
        assertThat(completionInput.messages.first().content).contains("card_blocks must be []")

        val doneCaptor =
            argumentCaptor<AssistantBankingReaderDoneEvent>()
        verify(doneProducer).publish(doneCaptor.capture())
        val doneEvent = doneCaptor.firstValue
        assertThat(doneEvent.job_id).isEqualTo(request.job_id)
        assertThat(doneEvent.callback_context.account_id).isEqualTo(request.callback_context.account_id)
        assertThat(doneEvent.parse_metadata.reader_id).isEqualTo("assistant-banking-bill-reader-v2")
        assertThat(doneEvent.parse_metadata.processor_type).isEqualTo("LLM")
        assertThat(doneEvent.parse_metadata.model_name).isEqualTo("gpt-4o-mini")
        assertThat(doneEvent.parse_metadata.latency_ms).isEqualTo(1200)
        // llm_output is metadata-only for bill parsing; banking owns deterministic transaction extraction.
        val llmOutput = doneEvent.llm_output
        assertThat(llmOutput["bank_name"]).isEqualTo("Nubank")
        assertThat(llmOutput["closing_date"]).isEqualTo("2026-01-31")
        assertThat(llmOutput["due_date"]).isEqualTo("2026-02-10")
        assertThat(llmOutput["card_blocks"]).isInstanceOf(List::class.java)
        assertThat(llmOutput["transaction_pattern"]).isInstanceOf(Map::class.java)
    }

    @Test
    fun `onCreditCardBill converts markdown table rows for LLM input and publishes metadata only`() {
        val request =
            validRequest(
                documentType = "CREDIT_CARD_BILL",
                accountType = "CREDIT",
                extractedText =
                    """
                    | Data | Descricao | Valor |
                    | --- | --- | --- |
                    | 03 OUT | ****1769 Netflix.Com | R$ 44,90 |
                    03 OUT ****1769 Netflix.Com R$ 44,90
                    """.trimIndent(),
            )
        runBlocking {
            whenever(completionService.generateCompletion(any(), any())).thenReturn(
                completionResult(content = MINIMAL_BILL_META_JSON),
            )
        }

        service.onCreditCardBill(request)

        val inputCaptor = argumentCaptor<CompletionInput>()
        runBlocking {
            verify(completionService).generateCompletion(inputCaptor.capture(), eq(request.callback_context.account_id))
        }
        val llmPrompt = inputCaptor.firstValue.messages.first().content
        assertThat(llmPrompt).doesNotContain("| 03 OUT | ****1769 Netflix.Com | R$ 44,90 |")
        assertThat(llmPrompt).contains("03 OUT ****1769 Netflix.Com R$ 44,90")

        val doneCaptor =
            argumentCaptor<AssistantBankingReaderDoneEvent>()
        verify(doneProducer).publish(doneCaptor.capture())
        val output = doneCaptor.firstValue.llm_output
        assertThat(output["card_blocks"]).isInstanceOf(List::class.java)
        assertThat(output["transaction_pattern"]).isInstanceOf(Map::class.java)
        assertThat(output).doesNotContainKey("cards")
    }

    @Test
    fun `onCreditCardBill keeps converted table card block headers in LLM input`() {
        val request =
            validRequest(
                documentType = "CREDIT_CARD_BILL",
                accountType = "CREDIT",
                extractedText =
                    """
                    | --- | Orival Onishi (Cartão 1295) | --- | --- | --- | --- |
                    | --- | --- | --- | --- | --- | --- |
                    | --- | Data | Descrição | País | Valor | --- |
                    | 03/02 | KamilaRufinoDos SAO PAULO | BR | R$ 26,00 |
                    """.trimIndent(),
            )
        runBlocking {
            whenever(completionService.generateCompletion(any(), any())).thenReturn(
                completionResult(content = MINIMAL_BILL_META_JSON),
            )
        }

        service.onCreditCardBill(request)

        val inputCaptor = argumentCaptor<CompletionInput>()
        runBlocking {
            verify(completionService).generateCompletion(inputCaptor.capture(), eq(request.callback_context.account_id))
        }
        val llmPrompt = inputCaptor.firstValue.messages.first().content
        assertThat(llmPrompt).contains("Orival Onishi (Cartão 1295)")
        assertThat(llmPrompt).doesNotContain("| --- | Orival Onishi (Cartão 1295) | --- | --- | --- | --- |")
        assertThat(llmPrompt).doesNotContain("--- Orival Onishi (Cartão 1295) ---")
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
    fun `onCreditCardBill maps schema-invalid meta output to SCHEMA_VALIDATION_FAILED`() {
        val request = validRequest(documentType = "CREDIT_CARD_BILL", accountType = "CREDIT")
        runBlocking {
            // Deserializes successfully but fails validator: card_blocks empty without last_four_pattern, empty currency_symbols, bad decimal_separator
            whenever(completionService.generateCompletion(any(), any())).thenReturn(
                completionResult(
                    content =
                        """
                        {
                          "bank_name": "Nubank",
                          "closing_date": "2026-01-31",
                          "due_date": "2026-02-10",
                          "declared_total": null,
                          "card_blocks": [],
                          "transaction_pattern": {
                            "date_format": "DD_MM",
                            "last_four_pattern": null,
                            "currency_symbols": [],
                            "decimal_separator": "invalid",
                            "installment_pattern": null
                          }
                        }
                        """.trimIndent(),
                ),
            )
        }

        assertThatThrownBy { service.onCreditCardBill(request) }
            .isInstanceOfSatisfying(BankingReaderProcessingException::class.java) { ex ->
                assertThat(ex.code).isEqualTo("SCHEMA_VALIDATION_FAILED")
                assertThat(ex.stage).isEqualTo("OUTPUT_VALIDATION")
                assertThat(ex.retryable).isFalse()
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
                          "parser_config":{
                            "layout_type":"DATE_GROUPED_MULTILINE",
                            "date_format":"DD_MMM_YYYY",
                            "date_header_pattern":"^(\\d{2}\\s+[A-Z]{3}\\s+\\d{4})\\b",
                            "inline_transaction_date_pattern":null,
                            "amount_pattern":"([\\-−]?[\\d.]+,\\d{2})",
                            "decimal_separator":"comma",
                            "credit_patterns":["recebid"],
                            "debit_patterns":["enviad"],
                            "skip_line_patterns":["^Saldo.*$","^Total de entradas.*$","^Total de saídas.*$"],
                            "default_credit_debit_type":"DEBIT"
                          },
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
        assertThat(inputCaptor.firstValue.messages.first().content).contains("A single date heading may apply to multiple consecutive transaction blocks")
        assertThat(inputCaptor.firstValue.messages.first().content).contains("Do not merge adjacent PIX/TED/transfer transactions that share the same day")
        assertThat(inputCaptor.firstValue.messages.first().content).contains("Do NOT extract final transactions")
        assertThat(inputCaptor.firstValue.messages.first().content).contains("parser_config")

        val doneCaptor =
            argumentCaptor<AssistantBankingReaderDoneEvent>()
        verify(doneProducer).publish(doneCaptor.capture())
        assertThat(doneCaptor.firstValue.parse_metadata.reader_id).isEqualTo("assistant-banking-reader-v1")
        assertThat(doneCaptor.firstValue.llm_output).doesNotContainKeys("closing_date", "due_date")
        assertThat(doneCaptor.firstValue.llm_output["parser_config"]).isInstanceOf(Map::class.java)
        assertThat(doneCaptor.firstValue.llm_output).doesNotContainKey("transactions")
    }

    @Test
    fun `onBankStatement normalizes common date format and decimal separator aliases`() {
        val request = validRequest(documentType = "BANK_STATEMENT", accountType = "BANK", fileType = "PDF")
        runBlocking {
            whenever(completionService.generateCompletion(any(), any())).thenReturn(
                completionResult(
                    content =
                        """
                        {
                          "bank_name":"Itau",
                          "statement_period":{"from":"2026-02-01","to":"2026-02-28"},
                          "parser_config":{
                            "layout_type":"grouped by date",
                            "date_format":"DD MMM YYYY",
                            "date_header_pattern":"^(\\d{2}\\s+[A-Z]{3}\\s+\\d{4})\\b",
                            "inline_transaction_date_pattern":null,
                            "amount_pattern":"([\\-−]?[\\d.]+,\\d{2})",
                            "decimal_separator":",",
                            "credit_patterns":["recebid"],
                            "debit_patterns":["enviad"],
                            "skip_line_patterns":["^Saldo.*$"],
                            "default_credit_debit_type":"debit"
                          },
                          "warnings":[]
                        }
                        """.trimIndent(),
                ),
            )
        }

        service.onBankStatement(request)

        val doneCaptor = argumentCaptor<AssistantBankingReaderDoneEvent>()
        verify(doneProducer).publish(doneCaptor.capture())
        val parserConfig = doneCaptor.firstValue.llm_output["parser_config"] as Map<*, *>
        assertThat(parserConfig["layout_type"]).isEqualTo("DATE_GROUPED_MULTILINE")
        assertThat(parserConfig["date_format"]).isEqualTo("DD_MMM_YYYY")
        assertThat(parserConfig["decimal_separator"]).isEqualTo("comma")
        assertThat(parserConfig["default_credit_debit_type"]).isEqualTo("DEBIT")
    }

    @Test
    fun `onCreditCardBill accepts XLSX tuple and publishes done`() {
        val request = validRequest(documentType = "CREDIT_CARD_BILL", accountType = "CREDIT", fileType = "XLSX")
        runBlocking {
            whenever(completionService.generateCompletion(any(), any())).thenReturn(
                completionResult(content = MINIMAL_BILL_META_JSON),
            )
        }

        service.onCreditCardBill(request)

        val inputCaptor = argumentCaptor<CompletionInput>()
        runBlocking {
            verify(completionService).generateCompletion(inputCaptor.capture(), eq(request.callback_context.account_id))
        }
        assertThat(inputCaptor.firstValue.purpose).isEqualTo("banking_bill_reader_credit_card_bill")

        val doneCaptor =
            argumentCaptor<AssistantBankingReaderDoneEvent>()
        verify(doneProducer).publish(doneCaptor.capture())
        assertThat(doneCaptor.firstValue.parse_metadata.reader_id).isEqualTo("assistant-banking-bill-reader-v2")
    }

    private fun validRequest(
        documentType: String,
        accountType: String,
        fileType: String = "PDF",
        extractedText: String = "sample extracted text",
    ): AssistantBankingReaderRequestedEvent {
        val jobId = UUID.randomUUID()
        return AssistantBankingReaderRequestedEvent(
            schema_version = "1.0",
            job_id = jobId,
            document_type = documentType,
            file_type = fileType,
            account_type = accountType,
            source_topic = "pdf-extractor.extracted",
            extracted_text = extractedText,
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

    companion object {
        // Minimal valid BillExtractionMeta JSON for full-document inline-card parsing.
        // "sample extracted text" contains no matching rows, so the extractor returns empty cards (acceptable for this test).
        private val MINIMAL_BILL_META_JSON =
            """
            {
              "bank_name": "Nubank",
              "closing_date": "2026-01-31",
              "due_date": "2026-02-10",
              "declared_total": null,
              "card_blocks": [],
              "transaction_pattern": {
                "date_format": "DD_MMM",
                "last_four_pattern": "[^\\\\w\\\\s]+\\\\s*(\\\\d{4})",
                "currency_symbols": ["R${'$'}"],
                "decimal_separator": "comma",
                "installment_pattern": null
              }
            }
            """.trimIndent()
    }
}
