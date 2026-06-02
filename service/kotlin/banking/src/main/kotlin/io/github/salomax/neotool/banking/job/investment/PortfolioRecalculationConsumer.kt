package io.github.salomax.neotool.banking.job.investment

import io.github.salomax.neotool.banking.client.investment.messaging.PortfolioRecalculationRequestedEvent
import io.github.salomax.neotool.banking.service.investment.PortfolioRecalculationService
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
@Requires(property = "kafka.consumers.portfolio-recalculation.enabled", notEquals = "false", defaultValue = "true")
@KafkaListener(
    groupId = "\${kafka.consumers.portfolio-recalculation.group-id:banking-portfolio-recalculation}",
    offsetReset = OffsetReset.EARLIEST,
    properties = [
        Property(name = "enable.auto.commit", value = "false"),
        Property(name = "max.poll.records", value = "1"),
        Property(name = "max.poll.interval.ms", value = "7200000"),
    ],
)
class PortfolioRecalculationConsumer(
    private val portfolioRecalculationService: PortfolioRecalculationService,
    @param:Property(name = "kafka.topics.portfolio-recalculation-requested")
    private val topicName: String,
) {
    @Topic("\${kafka.topics.portfolio-recalculation-requested:banking.portfolio-recalculation.requested}")
    fun receive(
        @KafkaKey key: String,
        event: PortfolioRecalculationRequestedEvent,
        consumer: Consumer<*, *>,
        @KafkaPartition partition: Int,
        offset: Long,
    ) {
        portfolioRecalculationService.process(event.jobId)
        consumer.commitSync(
            mapOf(
                TopicPartition(topicName, partition) to OffsetAndMetadata(offset + 1),
            ),
        )
    }
}
