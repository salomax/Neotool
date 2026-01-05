/**
 * Kafka Consumer Template
 * 
 * This template provides a starting point for implementing Kafka consumers
 * following the Kafka Consumer Pattern using virtual threads.
 * 
 * Replace:
 * - <Domain> with your domain name (e.g., People, Order, Product)
 * - <domain> with lowercase domain (e.g., people, order, product)
 * - <Entity> with entity name (e.g., Person, Order, Product)
 * - <entity> with lowercase entity (e.g., person, order, product)
 * 
 * See: docs/04-patterns/backend-patterns/kafka-consumer-pattern.md
 * 
 * Note: Virtual threads require JDK 21+ and Micronaut 4+.
 * Configure in application.yml: micronaut.executors.consumer.type: virtual
 * 
 * Additional Features to Consider:
 * - Health Check: Implement health check endpoint (see example below)
 * - Idempotency: Ensure processing is idempotent using record_id (see processor example)
 */

package io.github.salomax.neotool.<module>.batch.<domain>

import io.github.salomax.neotool.common.batch.AbstractKafkaConsumer
import io.github.salomax.neotool.common.batch.ConsumerConfig
import io.github.salomax.neotool.<module>.batch.<domain>.metrics.<Domain>Metrics
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.KafkaPartition
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Property
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.kafka.clients.consumer.Consumer

/**
 * Kafka consumer for <domain> <entity> messages.
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
    groupId = "<domain>-<entity>-consumer-group",
    offsetReset = OffsetReset.EARLIEST,
    properties = [
        Property(name = "enable.auto.commit", value = "false"),
        Property(name = "max.poll.records", value = "100"),
        Property(name = "session.timeout.ms", value = "30000"),
        Property(name = "heartbeat.interval.ms", value = "10000")
    ]
)
class <Domain><Entity>Consumer(
    @Inject processor: <Domain><Entity>Processor,
    @Inject dlqPublisher: <Domain><Entity>DlqPublisher,
    @Inject metrics: <Domain>Metrics,
    @Inject config: ConsumerConfig
) : AbstractKafkaConsumer<<Domain><Entity>Message, <Domain><Entity>Processor, <Domain><Entity>DlqPublisher, <Domain>Metrics>(
    processor,
    dlqPublisher,
    metrics,
    config
) {
    /**
     * Get the topic name this consumer listens to.
     */
    override fun getTopicName(): String {
        return "<domain>.<entity>.v1"
    }
    
    /**
     * Process a <domain> <entity> message from Kafka.
     * Delegates to AbstractKafkaConsumer.receive() which handles all retry, DLQ, and commit logic.
     * 
     * @param key The record key (record_id)
     * @param message The <entity> message
     * @param consumer The Kafka consumer for manual commit
     * @param partition The partition number
     * @param offset The message offset
     */
    @Topic("<domain>.<entity>.v1")
    fun receive(
        @KafkaKey key: String,
        message: <Domain><Entity>Message,
        consumer: Consumer<*, *>,
        @KafkaPartition partition: Int,
        offset: Long
    ) {
        super.receive(key, message, consumer, partition, offset)
    }
}

/**
 * Health Check Example for Kafka Consumer
 * 
 * Add this to your consumer class or create a separate health check component.
 * This allows monitoring systems to verify consumer health.
 * 
 * Example implementation:
 * 
 * @Singleton
 * class <Domain><Entity>ConsumerHealth(
 *     @Inject private val consumer: <Domain><Entity>Consumer,
 *     @Inject private val meterRegistry: MeterRegistry
 * ) : HealthIndicator {
 *     override fun getResult(): HealthResult {
 *         // Check consumer group membership
 *         // Check partition assignments
 *         // Check for stuck partitions
 *         // Return HealthResult.up() or HealthResult.down()
 *     }
 * }
 * 
 * Register in application.yml:
 * endpoints:
 *   health:
 *     enabled: true
 *     sensitive: false
 */

/**
 * Processor with Idempotency Example
 * 
 * Ensure your processor implements idempotency to handle duplicate messages.
 * Use the record_id to check if message has already been processed.
 * 
 * Example implementation:
 * 
 * @Singleton
 * class <Domain><Entity>Processor(
 *     private val metrics: <Domain>Metrics,
 *     private val repository: <Entity>Repository  // Your data repository
 * ) : MessageProcessor<<Domain><Entity>Message> {
 *     
 *     override fun process(message: <Domain><Entity>Message) {
 *         val recordId = message.recordId
 *         
 *         // Idempotency check: verify if already processed
 *         if (repository.existsByRecordId(recordId)) {
 *             logger.info { "Message already processed: recordId=$recordId, skipping" }
 *             metrics.incrementProcessed()  // Count as processed (idempotent)
 *             return  // Skip processing
 *         }
 *         
 *         // Process message
 *         // ... your business logic ...
 *         
 *         // Save with record_id for future idempotency checks
 *         repository.save(entity.copy(recordId = recordId))
 *     }
 *     
 *     override fun getRecordId(message: <Domain><Entity>Message): String {
 *         return message.recordId
 *     }
 * }
 * 
 * Database schema example (for idempotency check):
 * CREATE TABLE <entity> (
 *     id UUID PRIMARY KEY,
 *     record_id VARCHAR(255) UNIQUE NOT NULL,  -- Unique constraint for idempotency
 *     -- ... other fields ...
 *     created_at TIMESTAMP NOT NULL
 * );
 * 
 * Repository method:
 * fun existsByRecordId(recordId: String): Boolean {
 *     return repository.existsByRecordId(recordId)
 * }
 */
