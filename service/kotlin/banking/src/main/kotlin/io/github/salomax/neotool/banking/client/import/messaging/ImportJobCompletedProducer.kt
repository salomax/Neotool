package io.github.salomax.neotool.banking.client.import.messaging

import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Event published when an import job reaches a terminal status (COMPLETED, FAILED, REVIEW_REQUIRED).
 */
@Serdeable
data class ImportJobCompletedEvent(
    val jobId: UUID,
    val accountId: UUID,
    val status: ImportJobStatus,
    val importedCount: Int? = null,
    val skippedCount: Int? = null,
    val duplicateReviewCount: Int? = null,
    val errorCount: Int? = null,
)

@KafkaClient
@Requires(property = "kafka.enabled", value = "true")
interface ImportJobCompletedProducerKafkaClient {
    @Topic("\${kafka.topics.import-job-completed:banking.import.job-completed}")
    fun send(
        @KafkaKey jobId: String,
        event: ImportJobCompletedEvent,
    )
}

@Singleton
@Requires(property = "kafka.enabled", value = "true")
class ImportJobCompletedProducer(
    private val client: ImportJobCompletedProducerKafkaClient,
) {
    fun publish(event: ImportJobCompletedEvent) {
        client.send(event.jobId.toString(), event)
    }
}
