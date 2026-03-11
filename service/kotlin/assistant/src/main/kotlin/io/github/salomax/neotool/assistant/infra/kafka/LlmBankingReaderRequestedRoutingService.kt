package io.github.salomax.neotool.assistant.infra.kafka

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.assistant.domain.enums.OutputFormat
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.service.CompletionInput
import io.github.salomax.neotool.assistant.service.CompletionMessageInput
import io.github.salomax.neotool.assistant.service.CompletionService
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class LlmBankingReaderRequestedRoutingService(
    private val completionService: CompletionService,
    private val promptResolver: BankingReaderPromptResolver,
    private val outputValidator: BankingReaderOutputValidator,
    private val doneProducer: AssistantBankingPdfReaderDoneProducer,
    private val objectMapper: ObjectMapper,
) : BankingReaderRequestedRoutingService {
    override fun onCreditCardBill(request: AssistantBankingReaderRequestedEvent) {
        process(request)
    }

    override fun onBankStatement(request: AssistantBankingReaderRequestedEvent) {
        process(request)
    }

    override fun onInvestmentIrReport(request: AssistantBankingReaderRequestedEvent) {
        process(request)
    }

    private fun process(request: AssistantBankingReaderRequestedEvent) {
        val promptSelection = promptResolver.resolve(request)

        val completionInput =
            CompletionInput(
                promptId = promptSelection.promptId,
                promptVariables =
                    mapOf(
                        "documentText" to request.extracted_text,
                        "documentType" to request.document_type,
                        "accountType" to request.account_type,
                        "fileType" to request.file_type,
                        "extractionMetadata" to request.extraction_metadata,
                    ),
                messages = buildInputMessages(request, promptSelection),
                requiredCapabilities = listOf("JSON_MODE"),
                outputFormat = OutputFormat.JSON,
                purpose = promptSelection.purpose,
                requestedByService = REQUESTED_BY_SERVICE,
                userId = request.callback_context.user_id,
                metadata =
                    mapOf(
                        "jobId" to request.job_id.toString(),
                        "documentType" to request.document_type,
                        "accountType" to request.account_type,
                        "fileType" to request.file_type,
                        "readerId" to promptSelection.readerId,
                    ),
            )

        val completionResult =
            try {
                runBlocking {
                    completionService.generateCompletion(
                        input = completionInput,
                        accountId = request.callback_context.account_id,
                    )
                }
            } catch (ex: ProviderClientException) {
                throw mapProviderException(ex)
            } catch (ex: IllegalArgumentException) {
                throw BankingReaderProcessingException(
                    code = "PROMPT_RESOLUTION_ERROR",
                    message = ex.message ?: "Prompt resolution failed",
                    retryable = false,
                    stage = "PROMPT_SELECTION",
                    cause = ex,
                )
            }

        val llmOutputNode =
            try {
                objectMapper.readTree(completionResult.content)
            } catch (ex: Exception) {
                throw BankingReaderProcessingException(
                    code = "LLM_NON_JSON",
                    message = "Model returned non-JSON output",
                    retryable = false,
                    stage = "OUTPUT_VALIDATION",
                    cause = ex,
                )
            }

        val validationErrors =
            outputValidator.validate(
                documentType = request.document_type,
                output = llmOutputNode,
            )
        if (validationErrors.isNotEmpty()) {
            throw BankingReaderProcessingException(
                code = "SCHEMA_VALIDATION_FAILED",
                message = "Output did not match contract: ${validationErrors.take(10).joinToString("; ")}",
                retryable = false,
                stage = "OUTPUT_VALIDATION",
            )
        }

        val llmOutput =
            objectMapper.convertValue(
                llmOutputNode,
                object : TypeReference<Map<String, Any?>>() {},
            )
        val warnings = extractWarnings(llmOutput)
        val confidence = extractConfidence(llmOutput)
        val doneEvent =
            AssistantBankingPdfReaderDoneEvent(
                schema_version = "1.0",
                job_id = request.job_id,
                document_type = request.document_type,
                llm_output = llmOutput,
                parse_metadata =
                    AssistantBankingPdfReaderParseMetadata(
                        processor_type = "LLM",
                        reader_id = promptSelection.readerId,
                        confidence = confidence,
                        model_provider = inferProviderName(completionResult.model),
                        model_name = completionResult.model,
                        latency_ms = completionResult.latencyMs,
                        warnings = warnings,
                    ),
                callback_context = request.callback_context,
            )
        doneProducer.publish(doneEvent)
    }

    private fun buildInputMessages(
        request: AssistantBankingReaderRequestedEvent,
        promptSelection: BankingReaderPromptSelection,
    ): List<CompletionMessageInput> {
        val userContent =
            if (promptSelection.promptId == null) {
                // CompletionService blocks SYSTEM role in client input messages.
                // When no prompt version is configured, inject fallback instructions into USER content.
                "${promptSelection.fallbackSystemPrompt.trim()}\n\n${request.extracted_text}"
            } else {
                request.extracted_text
            }

        return listOf(
            CompletionMessageInput(
                role = ProviderMessageRole.USER,
                content = userContent,
            ),
        )
    }

    private fun mapProviderException(ex: ProviderClientException): BankingReaderProcessingException {
        val mappedCode =
            when (ex.errorType) {
                ErrorType.TIMEOUT -> "LLM_TIMEOUT"
                ErrorType.PROVIDER_RESPONSE_INVALID ->
                    if (ex.message.contains("empty content", ignoreCase = true)) "EMPTY_OUTPUT" else "LLM_NON_JSON"
                else -> "LLM_PROVIDER_ERROR"
            }

        val retryable =
            when (ex.errorType) {
                ErrorType.TIMEOUT, ErrorType.RATE_LIMIT, ErrorType.SERVER_ERROR, ErrorType.PROVIDER_UNAVAILABLE -> true
                else -> false
            }

        return BankingReaderProcessingException(
            code = mappedCode,
            message = ex.message,
            retryable = retryable,
            stage = "LLM_CALL",
            cause = ex,
        )
    }

    private fun extractWarnings(llmOutput: Map<String, Any?>): List<String> {
        val raw = llmOutput["warnings"] as? List<*> ?: return emptyList()
        return raw.mapNotNull { value ->
            val text = value?.toString()?.trim()
            if (text.isNullOrEmpty()) null else text
        }
    }

    private fun extractConfidence(llmOutput: Map<String, Any?>): Double {
        val raw = llmOutput["extraction_confidence"] as? Number ?: return 0.0
        return raw.toDouble().coerceIn(0.0, 1.0)
    }

    private fun inferProviderName(modelName: String?): String? {
        if (modelName.isNullOrBlank()) return null
        val normalized = modelName.lowercase()
        return when {
            normalized.contains("gpt") || normalized.contains("o1") || normalized.contains("o3") -> "openai"
            normalized.contains("gemini") -> "gemini"
            else -> null
        }
    }

    private companion object {
        private const val REQUESTED_BY_SERVICE = "assistant-banking-reader"
    }
}
