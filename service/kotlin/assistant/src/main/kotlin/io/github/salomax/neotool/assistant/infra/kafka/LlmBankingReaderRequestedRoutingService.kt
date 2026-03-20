package io.github.salomax.neotool.assistant.infra.kafka

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.salomax.neotool.assistant.domain.enums.OutputFormat
import io.github.salomax.neotool.assistant.provider.error.ErrorType
import io.github.salomax.neotool.assistant.provider.error.ProviderClientException
import io.github.salomax.neotool.assistant.provider.model.ProviderMessageRole
import io.github.salomax.neotool.assistant.service.CompletionInput
import io.github.salomax.neotool.assistant.service.CompletionMessageInput
import io.github.salomax.neotool.assistant.service.CompletionResult
import io.github.salomax.neotool.assistant.service.CompletionService
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

@Singleton
class LlmBankingReaderRequestedRoutingService(
    private val completionService: CompletionService,
    private val promptResolver: BankingReaderPromptResolver,
    private val outputValidator: BankingReaderOutputValidator,
    private val metaValidator: BillExtractionMetaValidator,
    private val statementMetaValidator: StatementExtractionMetaValidator,
    private val doneProducer: AssistantBankingReaderDoneProducer,
    private val objectMapper: ObjectMapper,
) : BankingReaderRequestedRoutingService {
    override fun onCreditCardBill(request: AssistantBankingReaderRequestedEvent) {
        val promptSelection = promptResolver.resolve(request)
        val llmInputText = prepareTextForLlm(request.extracted_text)
        val completionResult = callLlm(request, promptSelection, llmInputText)

        // Stage 1: parse BillExtractionMeta from LLM response
        val meta =
            try {
                objectMapper.readValue(stripMarkdown(completionResult.content), BillExtractionMeta::class.java)
            } catch (ex: Exception) {
                log.warn("[banking-reader] job={} LLM_NON_JSON raw_content={}", request.job_id, completionResult.content)
                throw BankingReaderProcessingException(
                    code = "LLM_NON_JSON",
                    message = "Model returned non-JSON output",
                    retryable = false,
                    stage = "OUTPUT_VALIDATION",
                    cause = ex,
                )
            }

        val metaErrors = metaValidator.validate(meta)
        if (metaErrors.isNotEmpty()) {
            throw BankingReaderProcessingException(
                code = "SCHEMA_VALIDATION_FAILED",
                message = "Output did not match contract: ${metaErrors.take(10).joinToString("; ")}",
                retryable = false,
                stage = "OUTPUT_VALIDATION",
            )
        }

        val llmOutput =
            objectMapper.convertValue(meta, object : TypeReference<Map<String, Any?>>() {})

        publishDone(request, promptSelection, completionResult, llmOutput, emptyList())
    }

    override fun onBankStatement(request: AssistantBankingReaderRequestedEvent) {
        processStatementMeta(request)
    }

    override fun onInvestmentIrReport(request: AssistantBankingReaderRequestedEvent) {
        processLegacy(request)
    }

    private fun processLegacy(request: AssistantBankingReaderRequestedEvent) {
        val promptSelection = promptResolver.resolve(request)
        val completionResult = callLlm(request, promptSelection)

        val llmOutputNode =
            try {
                objectMapper.readTree(stripMarkdown(completionResult.content))
            } catch (ex: Exception) {
                log.warn("[banking-reader] job={} LLM_NON_JSON raw_content={}", request.job_id, completionResult.content)
                throw BankingReaderProcessingException(
                    code = "LLM_NON_JSON",
                    message = "Model returned non-JSON output",
                    retryable = false,
                    stage = "OUTPUT_VALIDATION",
                    cause = ex,
                )
            }

        val validationErrors = outputValidator.validate(documentType = request.document_type, output = llmOutputNode)
        if (validationErrors.isNotEmpty()) {
            throw BankingReaderProcessingException(
                code = "SCHEMA_VALIDATION_FAILED",
                message = "Output did not match contract: ${validationErrors.take(10).joinToString("; ")}",
                retryable = false,
                stage = "OUTPUT_VALIDATION",
            )
        }

        val llmOutput =
            objectMapper.convertValue(llmOutputNode, object : TypeReference<Map<String, Any?>>() {})
        val warnings = extractWarnings(llmOutput)

        publishDone(request, promptSelection, completionResult, llmOutput, warnings)
    }

    private fun processStatementMeta(request: AssistantBankingReaderRequestedEvent) {
        val promptSelection = promptResolver.resolve(request)
        val completionResult = callLlm(request, promptSelection)

        val meta =
            try {
                objectMapper.readValue(stripMarkdown(completionResult.content), StatementExtractionMeta::class.java)
            } catch (ex: Exception) {
                log.warn("[banking-reader] job={} LLM_NON_JSON raw_content={}", request.job_id, completionResult.content)
                throw BankingReaderProcessingException(
                    code = "LLM_NON_JSON",
                    message = "Model returned non-JSON output",
                    retryable = false,
                    stage = "OUTPUT_VALIDATION",
                    cause = ex,
                )
            }

        val normalizedMeta = meta.normalized()
        val metaErrors = statementMetaValidator.validate(normalizedMeta)
        if (metaErrors.isNotEmpty()) {
            throw BankingReaderProcessingException(
                code = "SCHEMA_VALIDATION_FAILED",
                message = "Output did not match contract: ${metaErrors.take(10).joinToString("; ")}",
                retryable = false,
                stage = "OUTPUT_VALIDATION",
            )
        }

        val llmOutput =
            objectMapper.convertValue(normalizedMeta, object : TypeReference<Map<String, Any?>>() {})

        publishDone(request, promptSelection, completionResult, llmOutput, normalizedMeta.warnings)
    }

    private fun callLlm(
        request: AssistantBankingReaderRequestedEvent,
        promptSelection: BankingReaderPromptSelection,
        extractedText: String = request.extracted_text,
    ): CompletionResult {
        val completionInput =
            CompletionInput(
                promptId = promptSelection.promptId,
                promptVariables =
                    mapOf(
                        "documentText" to extractedText,
                        "documentType" to request.document_type,
                        "accountType" to request.account_type,
                        "fileType" to request.file_type,
                        "extractionMetadata" to request.extraction_metadata,
                    ),
                messages = buildInputMessages(request, promptSelection, extractedText),
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

        return try {
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
        }.also { result ->
            log.info(
                "[banking-reader] job={} model={} llm_response={}",
                request.job_id,
                result.model,
                result.content,
            )
        }
    }

    private fun publishDone(
        request: AssistantBankingReaderRequestedEvent,
        promptSelection: BankingReaderPromptSelection,
        completionResult: CompletionResult,
        llmOutput: Map<String, Any?>,
        warnings: List<String>,
    ) {
        doneProducer.publish(
            AssistantBankingReaderDoneEvent(
                schema_version = "1.0",
                job_id = request.job_id,
                document_type = request.document_type,
                llm_output = llmOutput,
                parse_metadata =
                    AssistantBankingReaderParseMetadata(
                        processor_type = "LLM",
                        reader_id = promptSelection.readerId,
                        model_provider = inferProviderName(completionResult.model),
                        model_name = completionResult.model,
                        latency_ms = completionResult.latencyMs,
                        warnings = warnings,
                    ),
                callback_context = request.callback_context,
            ),
        )
    }

    private fun buildInputMessages(
        request: AssistantBankingReaderRequestedEvent,
        promptSelection: BankingReaderPromptSelection,
        extractedText: String = request.extracted_text,
    ): List<CompletionMessageInput> {
        val userContent =
            if (promptSelection.promptId == null) {
                // CompletionService blocks SYSTEM role in client input messages.
                // When no prompt version is configured, inject fallback instructions into USER content.
                "${promptSelection.fallbackSystemPrompt.trim()}\n\n$extractedText"
            } else {
                extractedText
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

    private fun inferProviderName(modelName: String?): String? {
        if (modelName.isNullOrBlank()) return null
        val normalized = modelName.lowercase()
        return when {
            normalized.contains("gpt") || normalized.contains("o1") || normalized.contains("o3") -> "openai"
            normalized.contains("gemini") -> "gemini"
            else -> null
        }
    }

    private fun prepareTextForLlm(text: String): String {
        val seen = mutableSetOf<String>()
        return text.lines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank()) return@mapNotNull null
                val normalized =
                    when {
                        isMarkdownTableSeparator(trimmed) -> null
                        trimmed.startsWith("|") -> convertMarkdownTableRowToPlainText(trimmed)
                        else -> trimmed
                    } ?: return@mapNotNull null
                normalized.takeIf { seen.add(it) }
            }
            .joinToString("\n")
    }

    private fun isMarkdownTableSeparator(line: String): Boolean {
        if (!line.startsWith("|")) return false
        val cells =
            line
                .trim('|')
                .split('|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        return cells.isNotEmpty() && cells.all { cell -> cell.matches(Regex(":?-{3,}:?")) }
    }

    private fun convertMarkdownTableRowToPlainText(line: String): String? {
        val cells =
            line
                .trim('|')
                .split('|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot { cell -> cell == "---" || cell.matches(Regex(":?-{3,}:?")) }

        if (cells.isEmpty()) return null
        if (cells.all { cell -> cell.matches(Regex(":?-{3,}:?")) }) return null
        return cells.joinToString(" ")
    }

    private fun stripMarkdown(content: String): String {
        val trimmed = content.trim()
        val match = MARKDOWN_JSON_BLOCK.find(trimmed) ?: return trimmed
        return match.groupValues[1].trim()
    }

    private companion object {
        private const val REQUESTED_BY_SERVICE = "assistant-banking-reader"
        private val log = LoggerFactory.getLogger(LlmBankingReaderRequestedRoutingService::class.java)
        private val MARKDOWN_JSON_BLOCK = Regex("^```(?:json)?\\s*([\\s\\S]*?)```\\s*$", RegexOption.MULTILINE)
    }
}
