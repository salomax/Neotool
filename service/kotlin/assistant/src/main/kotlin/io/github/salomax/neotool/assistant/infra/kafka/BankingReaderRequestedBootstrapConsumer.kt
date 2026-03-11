package io.github.salomax.neotool.assistant.infra.kafka

import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.KafkaListener
import io.micronaut.configuration.kafka.annotation.OffsetReset
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicInteger

/**
 * BKL-008 probe listener: validates assistant Kafka wiring and topic subscription bootstrap.
 * This stays opt-in and is not part of the production reader workflow.
 */
@Singleton
@Requires(property = "kafka.enabled", value = "true")
@Requires(property = "assistant.banking-reader.bootstrap-consumer.enabled", value = "true")
@KafkaListener(
    groupId = "\${kafka.consumers.banking-reader.group-id:assistant-banking-reader}",
    offsetReset = OffsetReset.EARLIEST,
)
class BankingReaderRequestedBootstrapConsumer {
    private val logger = KotlinLogging.logger {}
    private val consumedCounter = AtomicInteger(0)

    @Topic("\${kafka.topics.pdf.banking-reader-requested:assistant.banking-reader.requested}")
    fun receive(
        @KafkaKey key: String?,
        payload: String,
    ) {
        val current = consumedCounter.incrementAndGet()
        logger.debug { "Received bootstrap banking-reader message key=$key size=${payload.length} count=$current" }
    }

    fun consumedCount(): Int = consumedCounter.get()

    fun resetCount() {
        consumedCounter.set(0)
    }
}
