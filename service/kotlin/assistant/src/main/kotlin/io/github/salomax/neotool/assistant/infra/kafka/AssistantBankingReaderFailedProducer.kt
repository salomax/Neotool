package io.github.salomax.neotool.assistant.infra.kafka

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

@KafkaClient
@Requires(property = "kafka.enabled", value = "true")
interface AssistantBankingReaderFailedProducerKafkaClient {
    @Topic("\${kafka.topics.pdf.banking-reader-failed:assistant.banking-pdf-reader.failed}")
    fun send(
        @KafkaKey jobId: String,
        event: AssistantBankingReaderFailedEvent,
    )
}

@Singleton
@Requires(property = "kafka.enabled", value = "true")
class AssistantBankingReaderFailedProducer(
    private val client: AssistantBankingReaderFailedProducerKafkaClient,
) {
    fun publish(event: AssistantBankingReaderFailedEvent) {
        client.send(event.job_id.toString(), event)
    }
}
