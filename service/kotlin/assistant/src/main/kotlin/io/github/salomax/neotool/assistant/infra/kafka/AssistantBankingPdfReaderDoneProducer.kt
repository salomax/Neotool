package io.github.salomax.neotool.assistant.infra.kafka

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

@KafkaClient
@Requires(property = "kafka.enabled", value = "true")
interface AssistantBankingPdfReaderDoneProducerKafkaClient {
    @Topic("\${kafka.topics.pdf.banking-reader-done:assistant.banking-pdf-reader.done}")
    fun send(
        @KafkaKey jobId: String,
        event: AssistantBankingPdfReaderDoneEvent,
    )
}

@Singleton
@Requires(property = "kafka.enabled", value = "true")
class AssistantBankingPdfReaderDoneProducer(
    private val client: AssistantBankingPdfReaderDoneProducerKafkaClient,
) {
    fun publish(event: AssistantBankingPdfReaderDoneEvent) {
        client.send(event.job_id.toString(), event)
    }
}
