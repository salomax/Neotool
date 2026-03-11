package io.github.salomax.neotool.assistant.infra.kafka

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

@KafkaClient
@Requires(property = "kafka.enabled", value = "true")
interface AssistantBankingPdfReaderFailedProducerKafkaClient {
    @Topic("\${kafka.topics.pdf.banking-reader-failed:assistant.banking-pdf-reader.failed}")
    fun send(
        @KafkaKey jobId: String,
        event: AssistantBankingPdfReaderFailedEvent,
    )
}

@Singleton
@Requires(property = "kafka.enabled", value = "true")
class AssistantBankingPdfReaderFailedProducer(
    private val client: AssistantBankingPdfReaderFailedProducerKafkaClient,
) {
    fun publish(event: AssistantBankingPdfReaderFailedEvent) {
        client.send(event.job_id.toString(), event)
    }
}
