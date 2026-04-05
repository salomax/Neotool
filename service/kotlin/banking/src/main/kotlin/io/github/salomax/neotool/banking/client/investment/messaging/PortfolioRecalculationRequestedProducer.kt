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
data class PortfolioRecalculationRequestedEvent(
    val jobId: UUID,
    val accountId: UUID,
    val financialAccountId: UUID?,
    val recalculateFrom: LocalDate,
    val triggeredBy: String,
    val requestedAt: Instant,
)

@KafkaClient
@Requires(property = "kafka.enabled", value = "true")
interface PortfolioRecalculationRequestedProducerKafkaClient {
    @Topic("\${kafka.topics.portfolio-recalculation-requested:banking.portfolio-recalculation.requested}")
    fun send(
        @KafkaKey accountId: String,
        event: PortfolioRecalculationRequestedEvent,
    )
}

@Singleton
@Requires(property = "kafka.enabled", value = "true")
class PortfolioRecalculationRequestedProducer(
    private val client: PortfolioRecalculationRequestedProducerKafkaClient,
) {
    fun publish(event: PortfolioRecalculationRequestedEvent) {
        client.send(event.accountId.toString(), event)
    }
}
