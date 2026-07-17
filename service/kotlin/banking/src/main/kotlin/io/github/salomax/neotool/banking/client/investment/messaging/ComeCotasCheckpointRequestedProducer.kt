package io.github.salomax.neotool.banking.client.investment.messaging

import io.micronaut.configuration.kafka.annotation.KafkaClient
import io.micronaut.configuration.kafka.annotation.KafkaKey
import io.micronaut.configuration.kafka.annotation.Topic
import io.micronaut.context.annotation.Requires
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Serdeable
data class ComeCotasCheckpointRequestedEvent(
    val jobId: UUID,
    val accountId: UUID,
    val financialAccountId: UUID,
    val holdingId: UUID,
    val checkpointDate: LocalDate,
    val requestedAt: Instant,
)

@KafkaClient
@Requires(property = "kafka.enabled", value = "true")
interface ComeCotasCheckpointRequestedProducerKafkaClient {
    @Topic("\${kafka.topics.come-cotas-checkpoint-requested:banking.come-cotas-checkpoint.requested}")
    fun send(
        @KafkaKey accountId: String,
        event: ComeCotasCheckpointRequestedEvent,
    )
}

@Singleton
@Requires(property = "kafka.enabled", value = "true")
class ComeCotasCheckpointRequestedProducer(
    private val client: ComeCotasCheckpointRequestedProducerKafkaClient,
) {
    fun publish(event: ComeCotasCheckpointRequestedEvent) {
        client.send(event.accountId.toString(), event)
    }
}
