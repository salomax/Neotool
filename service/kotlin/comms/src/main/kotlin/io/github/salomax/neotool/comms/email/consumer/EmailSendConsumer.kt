package io.github.salomax.neotool.comms.email.consumer

import io.github.salomax.neotool.comms.email.events.EmailSendRequestedEvent
import io.github.salomax.neotool.comms.email.kafka.EmailTopics
import io.github.salomax.neotool.comms.email.kafka.EmailDlqPublisher
import io.github.salomax.neotool.comms.email.metrics.EmailSendMetrics
import io.github.salomax.neotool.comms.email.processor.EmailSendProcessor
import io.github.salomax.neotool.common.batch.AbstractKafkaConsumer
import io.github.salomax.neotool.common.batch.ConsumerConfig
import io.github.salomax.neotool.common.batch.ProcessingExecutor
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.KafkaPartition
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton
import org.apache.kafka.clients.consumer.Consumer

@Singleton
@KafkaListener(
    groupId = "comms-email-send-consumer-group",
    offsetReset = OffsetReset.EARLIEST,
    properties = [
        Property(name = "enable.auto.commit", value = "false"),
        Property(name = "max.poll.records", value = "100"),
        Property(name = "session.timeout.ms", value = "30000"),
        Property(name = "heartbeat.interval.ms", value = "10000"),
    ],
)
class EmailSendConsumer(
    processor: EmailSendProcessor,
    dlqPublisher: EmailDlqPublisher,
    metrics: EmailSendMetrics,
    config: ConsumerConfig,
    processingExecutor: ProcessingExecutor,
) : AbstractKafkaConsumer<EmailSendRequestedEvent, EmailSendProcessor, EmailDlqPublisher, EmailSendMetrics>(
        processor,
        dlqPublisher,
        metrics,
        config,
        processingExecutor,
    ) {
    override fun getTopicName(): String {
        return EmailTopics.SEND_REQUESTED
    }

    @Topic(EmailTopics.SEND_REQUESTED)
    override fun receive(
        @KafkaKey key: String,
        message: EmailSendRequestedEvent,
        consumer: Consumer<*, *>,
        @KafkaPartition partition: Int,
        offset: Long,
    ) {
        super.receive(key, message, consumer, partition, offset)
    }
}
