package io.github.salomax.neotool.comms.email.consumer

import io.github.salomax.neotool.comms.email.kafka.EmailDlqMessage
import io.github.salomax.neotool.comms.email.kafka.EmailTopics
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.KafkaPartition
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import mu.KotlinLogging

@Singleton
@KafkaListener(
    groupId = "comms-email-send-dlq-consumer-group",
    offsetReset = OffsetReset.EARLIEST,
    properties = [
        Property(name = "enable.auto.commit", value = "true"),
        Property(name = "max.poll.records", value = "100"),
        Property(name = "session.timeout.ms", value = "30000"),
        Property(name = "heartbeat.interval.ms", value = "10000"),
    ],
)
class EmailDlqConsumer {
    private val logger = KotlinLogging.logger {}

    @Topic(EmailTopics.SEND_DLQ)
    fun receive(
        @KafkaKey key: String,
        message: EmailDlqMessage,
        @KafkaPartition partition: Int,
        offset: Long,
    ) {
        logger.error {
            "Email DLQ message received: recordId=$key, partition=$partition, offset=$offset, " +
                "errorType=${message.errorType}, errorMessage=${message.errorMessage}"
        }
    }
}
