package io.github.salomax.neotool.banking.job.investment

import io.github.salomax.neotool.banking.client.investment.messaging.MarketDataJobSucceededEvent
import io.github.salomax.neotool.banking.service.investment.MarketDataSyncService
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
@Requires(property = "kafka.consumers.market-data-sync.enabled", notEquals = "false", defaultValue = "true")
@KafkaListener(
    groupId = "\${kafka.consumers.market-data-sync.group-id:banking-market-data-sync}",
    offsetReset = OffsetReset.EARLIEST,
    properties = [
        Property(name = "enable.auto.commit", value = "false"),
        Property(name = "max.poll.records", value = "1"),
    ],
)
class MarketDataSyncConsumer(
    private val marketDataSyncService: MarketDataSyncService,
    @param:Property(name = "kafka.topics.market-data-job-succeeded")
    private val topicName: String,
) {
    @Topic("\${kafka.topics.market-data-job-succeeded:financial-data.market-data.job-succeeded}")
    fun receive(
        @KafkaKey key: String,
        event: MarketDataJobSucceededEvent,
        consumer: Consumer<*, *>,
        @KafkaPartition partition: Int,
        offset: Long,
    ) {
        marketDataSyncService.handleJobSucceeded(event)
        consumer.commitSync(
            mapOf(
                TopicPartition(topicName, partition) to OffsetAndMetadata(offset + 1),
            ),
        )
    }
}
