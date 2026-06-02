package io.github.salomax.neotool.assistant.infra.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.util.UUID

@Singleton
@Requires(property = "kafka.enabled", value = "true")
@Requires(property = "assistant.banking-reader.consumer.enabled", notEquals = "false", defaultValue = "true")
@KafkaListener(
    groupId = "\${kafka.consumers.banking-reader.group-id:assistant-banking-reader}",
    offsetReset = OffsetReset.EARLIEST,
)
class AssistantBankingReaderRequestedConsumer(
    private val objectMapper: ObjectMapper,
    private val routingService: BankingReaderRequestedRoutingService,
    private val failedProducer: AssistantBankingReaderFailedProducer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Topic("\${kafka.topics.pdf.banking-reader-requested:assistant.banking-reader.requested}")
    fun onRequested(
        @KafkaKey key: String?,
        payload: String,
    ) {
        val tree =
            try {
                objectMapper.readTree(payload)
            } catch (ex: Exception) {
                publishInvalidPayloadFailure(
                    context = extractFailureContext(null, key),
                    message = "Invalid JSON payload: ${ex.message}",
                )
                return
            }

        val request =
            try {
                objectMapper.treeToValue(tree, AssistantBankingReaderRequestedEvent::class.java)
            } catch (ex: Exception) {
                publishInvalidPayloadFailure(
                    context = extractFailureContext(tree, key),
                    message = "Invalid payload shape: ${ex.message}",
                )
                return
            }

        val validationError = validate(request)
        if (validationError != null) {
            publishInvalidPayloadFailure(
                context = extractFailureContext(tree, key, request),
                message = validationError,
            )
            return
        }

        try {
            when (request.document_type.trim().uppercase()) {
                "CREDIT_CARD_BILL" -> routingService.onCreditCardBill(request)
                "BANK_STATEMENT" -> routingService.onBankStatement(request)
                "INVESTMENT_IR_REPORT" -> routingService.onInvestmentIrReport(request)
                else ->
                    publishFailure(
                        context = extractFailureContext(tree, key, request),
                        code = "UNSUPPORTED_DOCUMENT_TYPE",
                        message = "Unsupported document_type: ${request.document_type}",
                        retryable = false,
                        stage = "CONSUME",
                    )
            }
        } catch (ex: BankingReaderProcessingException) {
            publishFailure(
                context = extractFailureContext(tree, key, request),
                code = ex.code,
                message = ex.message,
                retryable = ex.retryable,
                stage = ex.stage,
            )
        } catch (ex: Exception) {
            log.error("Failed processing assistant.banking-reader.requested job_id={}", request.job_id, ex)
            publishFailure(
                context = extractFailureContext(tree, key, request),
                code = "PROMPT_RESOLUTION_ERROR",
                message = "Failed to route request by document_type: ${ex.message ?: ex::class.java.simpleName}",
                retryable = true,
                stage = "CONSUME",
            )
        }
    }

    private fun validate(request: AssistantBankingReaderRequestedEvent): String? {
        if (request.schema_version != SCHEMA_VERSION) {
            return "Invalid schema_version: expected $SCHEMA_VERSION but got ${request.schema_version}"
        }
        if (request.extracted_text.isBlank()) {
            return "Invalid payload: extracted_text must not be blank"
        }
        if (!SUPPORTED_FILE_TYPES.contains(request.file_type)) {
            return "Invalid payload: unsupported file_type=${request.file_type}"
        }
        if (!SUPPORTED_ACCOUNT_TYPES.contains(request.account_type)) {
            return "Invalid payload: unsupported account_type=${request.account_type}"
        }
        if (request.job_id != request.callback_context.job_id) {
            return "Invalid payload: job_id and callback_context.job_id must match"
        }
        return null
    }

    private fun publishInvalidPayloadFailure(
        context: FailureContext,
        message: String,
    ) {
        publishFailure(
            context = context,
            code = "INVALID_PAYLOAD",
            message = message,
            retryable = false,
            stage = "CONSUME",
        )
    }

    private fun publishFailure(
        context: FailureContext,
        code: String,
        message: String,
        retryable: Boolean,
        stage: String,
    ) {
        val event =
            AssistantBankingReaderFailedEvent(
                schema_version = SCHEMA_VERSION,
                job_id = context.jobId,
                document_type = context.documentType,
                error =
                    AssistantBankingReaderError(
                        code = code,
                        message = message,
                        retryable = retryable,
                    ),
                error_metadata =
                    AssistantBankingReaderErrorMetadata(
                        processor_type = "LLM",
                        stage = stage,
                    ),
                callback_context = context.callbackContext,
            )
        failedProducer.publish(event)
    }

    private fun extractFailureContext(
        tree: JsonNode?,
        messageKey: String?,
        parsed: AssistantBankingReaderRequestedEvent? = null,
    ): FailureContext {
        val callbackContext =
            parsed?.callback_context
                ?: BankingReaderCallbackContext(
                    job_id =
                        parsed?.job_id
                            ?: uuidFromNode(tree?.path("callback_context")?.path("job_id"))
                            ?: uuidFromNode(tree?.path("job_id"))
                            ?: uuidFromKey(messageKey)
                            ?: NIL_UUID,
                    account_id = uuidFromNode(tree?.path("callback_context")?.path("account_id")) ?: NIL_UUID,
                    user_id = uuidFromNode(tree?.path("callback_context")?.path("user_id")) ?: NIL_UUID,
                )

        val documentType =
            parsed?.document_type
                ?: tree?.path("document_type")?.takeIf { !it.isMissingNode && !it.isNull }?.asText()
                ?: DEFAULT_DOCUMENT_TYPE

        return FailureContext(
            jobId = parsed?.job_id ?: callbackContext.job_id,
            documentType = documentType,
            callbackContext = callbackContext,
        )
    }

    private fun uuidFromNode(node: JsonNode?): UUID? {
        if (node == null || node.isMissingNode || node.isNull) {
            return null
        }
        return runCatching { UUID.fromString(node.asText()) }.getOrNull()
    }

    private fun uuidFromKey(key: String?): UUID? =
        if (key.isNullOrBlank()) {
            null
        } else {
            runCatching { UUID.fromString(key) }.getOrNull()
        }

    private data class FailureContext(
        val jobId: UUID,
        val documentType: String,
        val callbackContext: BankingReaderCallbackContext,
    )

    companion object {
        private const val SCHEMA_VERSION = "1.0"
        private const val DEFAULT_DOCUMENT_TYPE = "CREDIT_CARD_BILL"
        private val NIL_UUID: UUID = UUID(0L, 0L)
        private val SUPPORTED_FILE_TYPES = setOf("PDF", "XLSX", "XLS")
        private val SUPPORTED_ACCOUNT_TYPES = setOf("BANK", "CREDIT", "PAYMENT", "CASH", "INVESTMENT", "LOAN", "ASSET")
    }
}
