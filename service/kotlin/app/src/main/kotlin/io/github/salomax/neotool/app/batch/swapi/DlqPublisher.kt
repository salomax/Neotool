package io.github.salomax.neotool.app.batch.swapi

import io.github.salomax.neotool.common.batch.DlqPublisherService
import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant

/**
 * Kafka client interface for publishing DLQ messages.
 */
@Singleton
@KafkaClient
interface DlqPublisher {
    @Topic("swapi.people.dlq")
    fun send(
        @KafkaKey key: String,
        message: DlqMessage,
    )
}

/**
 * DLQ message structure containing original message and error metadata.
 */
@Serdeable
data class DlqMessage(
    val originalMessage: PeopleMessage,
    val errorMessage: String,
    val errorType: String,
    // ISO 8601
    val failedAt: String,
    val retryCount: Int = 0,
    val stackTrace: String? = null,
) {
    companion object {
        fun from(
            originalMessage: PeopleMessage,
            error: Throwable,
            retryCount: Int = 0,
        ): DlqMessage {
            return DlqMessage(
                originalMessage = originalMessage,
                errorMessage = error.message ?: "Unknown error",
                errorType = error.javaClass.simpleName,
                failedAt = Instant.now().toString(),
                retryCount = retryCount,
                stackTrace = error.stackTraceToString(),
            )
        }
    }
}

/**
 * Implementation of DLQ publisher service for People messages.
 * Implements DlqPublisherService interface with retry logic.
 */
@Singleton
class PeopleDlqPublisher(
    private val dlqPublisher: DlqPublisher,
) : DlqPublisherService<PeopleMessage> {
    private val logger = KotlinLogging.logger {}

    override fun getDlqTopic(): String {
        return "swapi.people.dlq"
    }

    /**
     * Publish a failed message to DLQ.
     *
     * @param message The original message that failed
     * @param error The error that occurred
     * @param retryCount Number of retries attempted
     * @return true if successfully published, false otherwise
     */
    override fun publishToDlq(
        message: PeopleMessage,
        error: Throwable,
        retryCount: Int,
    ): Boolean {
        try {
            val dlqMessage = DlqMessage.from(message, error, retryCount)

            logger.warn {
                "Publishing message to DLQ: recordId=${message.recordId}, " +
                    "error=${error.message}, retryCount=$retryCount"
            }

            dlqPublisher.send(message.recordId, dlqMessage)

            logger.info {
                "Successfully published message to DLQ: recordId=${message.recordId}"
            }

            return true
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to publish message to DLQ: recordId=${message.recordId}. " +
                    "Message will be retried by Kafka."
            }
            return false
        }
    }
}
