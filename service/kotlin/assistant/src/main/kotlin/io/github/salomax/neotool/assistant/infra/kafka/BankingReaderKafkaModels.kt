package io.github.salomax.neotool.assistant.infra.kafka

import io.micronaut.serde.annotation.Serdeable
import java.util.UUID

@Serdeable
data class AssistantBankingReaderRequestedEvent(
    val schema_version: String = "1.0",
    val job_id: UUID,
    val document_type: String,
    val file_type: String,
    val account_type: String,
    val source_topic: String? = null,
    val extracted_text: String,
    val extraction_metadata: Map<String, Any?>,
    val callback_context: BankingReaderCallbackContext,
)

@Serdeable
data class BankingReaderCallbackContext(
    val job_id: UUID,
    val account_id: UUID,
    val user_id: UUID,
)

@Serdeable
data class AssistantBankingReaderDoneEvent(
    val schema_version: String = "1.0",
    val job_id: UUID,
    val document_type: String,
    val llm_output: Map<String, Any?>,
    val parse_metadata: AssistantBankingReaderParseMetadata,
    val callback_context: BankingReaderCallbackContext,
)

@Serdeable
data class AssistantBankingReaderParseMetadata(
    val processor_type: String = "LLM",
    val reader_id: String,
    val model_provider: String? = null,
    val model_name: String? = null,
    val latency_ms: Int? = null,
    val warnings: List<String> = emptyList(),
)

@Serdeable
data class AssistantBankingReaderFailedEvent(
    val schema_version: String = "1.0",
    val job_id: UUID,
    val document_type: String,
    val error: AssistantBankingReaderError,
    val error_metadata: AssistantBankingReaderErrorMetadata,
    val callback_context: BankingReaderCallbackContext,
)

@Serdeable
data class AssistantBankingReaderError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)

@Serdeable
data class AssistantBankingReaderErrorMetadata(
    val processor_type: String = "LLM",
    val stage: String,
)
