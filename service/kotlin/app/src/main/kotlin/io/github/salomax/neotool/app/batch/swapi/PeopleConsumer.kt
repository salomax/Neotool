package io.github.salomax.neotool.app.batch.swapi

import io.github.salomax.neotool.app.batch.swapi.metrics.PeopleMetrics
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

/**
 * Kafka consumer for SWAPI people messages.
 *
 * Features (inherited from AbstractKafkaConsumer):
 * - Manual commit after successful processing
 * - Retry logic with exponential backoff using virtual threads
 * - DLQ publishing for poison messages with retry
 * - Metrics and observability
 * - Proper handling of ValidationException vs ProcessingException
 * - Separate handling of CommitFailedException
 * - Sequential processing per partition with virtual threads
 */
@Singleton
@KafkaListener(
    groupId = "swapi-people-consumer-group",
    offsetReset = OffsetReset.EARLIEST,
    properties = [
        Property(name = "enable.auto.commit", value = "false"),
        Property(name = "max.poll.records", value = "100"),
        Property(name = "session.timeout.ms", value = "30000"),
        Property(name = "heartbeat.interval.ms", value = "10000"),
    ],
)
class PeopleConsumer(
    processor: PeopleProcessor,
    dlqPublisher: PeopleDlqPublisher,
    metrics: PeopleMetrics,
    config: ConsumerConfig,
    processingExecutor: ProcessingExecutor,
) : AbstractKafkaConsumer<PeopleMessage, PeopleProcessor, PeopleDlqPublisher, PeopleMetrics>(
        processor,
        dlqPublisher,
        metrics,
        config,
        processingExecutor,
    ) {
    /**
     * Get the topic name this consumer listens to.
     */
    override fun getTopicName(): String {
        return "swapi.people.v1"
    }

    /**
     * Process a people message from Kafka.
     * Delegates to AbstractKafkaConsumer.receive() which handles all retry, DLQ, and commit logic.
     *
     * @param key The record key (record_id)
     * @param message The people message
     * @param consumer The Kafka consumer for manual commit
     * @param partition The partition number
     * @param offset The message offset
     */
    @Topic("swapi.people.v1")
    override fun receive(
        @KafkaKey key: String,
        message: PeopleMessage,
        consumer: Consumer<*, *>,
        @KafkaPartition partition: Int,
        offset: Long,
    ) {
        super.receive(key, message, consumer, partition, offset)
    }
}
