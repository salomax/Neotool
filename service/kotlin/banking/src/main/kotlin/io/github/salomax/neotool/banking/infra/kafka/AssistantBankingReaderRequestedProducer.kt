package io.github.salomax.neotool.banking.infra.kafka

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
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
    val callback_context: PdfImportCallbackContext,
)

@KafkaClient
@Requires(property = "kafka.enabled", value = "true")
interface AssistantBankingReaderRequestedProducerKafkaClient {
    @Topic("\${kafka.topics.pdf.banking-reader-requested:assistant.banking-reader.requested}")
    fun send(
        @KafkaKey jobId: String,
        event: AssistantBankingReaderRequestedEvent,
    )
}

@Singleton
@Requires(property = "kafka.enabled", value = "true")
class AssistantBankingReaderRequestedProducer(
    private val client: AssistantBankingReaderRequestedProducerKafkaClient,
) {
    fun publish(event: AssistantBankingReaderRequestedEvent) {
        client.send(event.job_id.toString(), event)
    }
}
