package io.github.salomax.neotool.comms.email.kafka

import io.github.salomax.neotool.comms.email.events.EmailSendRequestedEvent
import io.github.salomax.neotool.common.batch.DlqPublisherService
import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant

@Singleton
@KafkaClient
interface EmailDlqProducer {
    @Topic(EmailTopics.SEND_DLQ)
    fun send(
        @KafkaKey key: String,
        message: EmailDlqMessage,
    )
}

@Serdeable
data class EmailDlqMessage(
    val originalMessage: EmailSendRequestedEvent,
    val errorMessage: String,
    val errorType: String,
    val failedAt: String,
    val retryCount: Int = 0,
    val stackTrace: String? = null,
) {
    companion object {
        fun from(
            originalMessage: EmailSendRequestedEvent,
            error: Throwable,
            retryCount: Int = 0,
        ): EmailDlqMessage {
            return EmailDlqMessage(
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

@Singleton
class EmailDlqPublisher(
    private val dlqProducer: EmailDlqProducer,
) : DlqPublisherService<EmailSendRequestedEvent> {
    private val logger = KotlinLogging.logger {}

    override fun getDlqTopic(): String {
        return EmailTopics.SEND_DLQ
    }

    override fun publishToDlq(
        message: EmailSendRequestedEvent,
        error: Throwable,
        retryCount: Int,
    ): Boolean {
        return try {
            val dlqMessage = EmailDlqMessage.from(message, error, retryCount)
            logger.warn {
                "Publishing email message to DLQ: recordId=${message.id}, " +
                    "error=${error.message}, retryCount=$retryCount"
            }
            dlqProducer.send(message.id, dlqMessage)
            logger.info { "Successfully published email message to DLQ: recordId=${message.id}" }
            true
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to publish email message to DLQ: recordId=${message.id}. " +
                    "Message will be retried by Kafka."
            }
            false
        }
    }
}
