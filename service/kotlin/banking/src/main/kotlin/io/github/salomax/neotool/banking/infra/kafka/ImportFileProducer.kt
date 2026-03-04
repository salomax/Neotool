package io.github.salomax.neotool.banking.infra.kafka

import io.github.salomax.neotool.banking.domain.import.ImportSource
import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Event requesting async processing of an import file.
 * Published to [ImportTopics.FILE_REQUESTED]; consumer uses [assetId] to fetch bytes from assets service.
 */
@Serdeable
data class ImportFileRequestedEvent(
    val jobId: UUID,
    val assetId: UUID,
    val source: ImportSource,
    val financialAccountId: UUID,
    val accountId: UUID,
)

@KafkaClient
@Requires(property = "kafka.enabled", value = "true")
interface ImportFileProducerKafkaClient {
    @Topic("\${kafka.topics.import-file-requested:banking.import.file-requested}")
    fun send(
        @KafkaKey jobId: String,
        event: ImportFileRequestedEvent,
    )
}

/**
 * Publishes [ImportFileRequestedEvent] to the import file requested topic.
 * Message key is [ImportFileRequestedEvent.jobId] for ordering per job within a partition.
 */
@Singleton
@Requires(property = "kafka.enabled", value = "true")
class ImportFileProducer(
    private val client: ImportFileProducerKafkaClient,
) {
    fun publish(event: ImportFileRequestedEvent) {
        client.send(event.jobId.toString(), event)
    }
}
