package io.github.salomax.neotool.banking.job.investment

import io.github.salomax.neotool.banking.client.investment.messaging.ComeCotasCheckpointRequestedEvent
import io.github.salomax.neotool.banking.service.investment.ComeCotasCheckpointService
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.KafkaPartition
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition

@Singleton
@Requires(property = "kafka.enabled", value = "true")
@Requires(property = "kafka.consumers.come-cotas-checkpoint.enabled", notEquals = "false", defaultValue = "true")
@KafkaListener(
    groupId = "\${kafka.consumers.come-cotas-checkpoint.group-id:banking-come-cotas-checkpoint}",
    offsetReset = OffsetReset.EARLIEST,
    properties = [
        Property(name = "enable.auto.commit", value = "false"),
        Property(name = "max.poll.records", value = "1"),
        Property(name = "max.poll.interval.ms", value = "7200000"),
    ],
)
class ComeCotasCheckpointConsumer(
    private val service: ComeCotasCheckpointService,
    @param:Property(name = "kafka.topics.come-cotas-checkpoint-requested")
    private val topicName: String,
) {
    @Topic("\${kafka.topics.come-cotas-checkpoint-requested:banking.come-cotas-checkpoint.requested}")
    fun receive(
        @KafkaKey key: String,
        event: ComeCotasCheckpointRequestedEvent,
        consumer: Consumer<*, *>,
        @KafkaPartition partition: Int,
        offset: Long,
    ) {
        service.process(event.jobId)
        consumer.commitSync(
            mapOf(
                TopicPartition(topicName, partition) to OffsetAndMetadata(offset + 1),
            ),
        )
    }
}
