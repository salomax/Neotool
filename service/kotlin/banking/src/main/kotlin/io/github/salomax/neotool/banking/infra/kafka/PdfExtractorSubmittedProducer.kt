package io.github.salomax.neotool.banking.infra.kafka

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import java.util.UUID

@Serdeable
data class PdfImportCallbackContext(
    val job_id: UUID,
    val account_id: UUID,
    val user_id: UUID,
)

@Serdeable
data class PdfExtractorSubmittedEvent(
    val job_id: UUID,
    val asset_id: UUID,
    val download_url: String,
    val document_type: String,
    val callback_context: PdfImportCallbackContext,
)

@KafkaClient
@Requires(property = "kafka.enabled", value = "true")
interface PdfExtractorSubmittedProducerKafkaClient {
    @Topic("\${kafka.topics.pdf.extractor-submitted:pdf-extractor.submitted}")
    fun send(
        @KafkaKey jobId: String,
        event: PdfExtractorSubmittedEvent,
    )
}

@Singleton
@Requires(property = "kafka.enabled", value = "true")
class PdfExtractorSubmittedProducer(
    private val client: PdfExtractorSubmittedProducerKafkaClient,
) {
    fun publish(event: PdfExtractorSubmittedEvent) {
        client.send(event.job_id.toString(), event)
    }
}
