package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.messaging.PortfolioRecalculationRequestedEvent
import io.github.salomax.neotool.banking.job.investment.PortfolioRecalculationConsumer
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class PortfolioRecalculationConsumerTest {
    private val portfolioRecalculationService: PortfolioRecalculationService = mock()
    private val consumer: Consumer<Any, Any> = mock()

    private val job =
        PortfolioRecalculationConsumer(
            portfolioRecalculationService = portfolioRecalculationService,
            topicName = "banking.portfolio-recalculation.requested",
        )

    @Test
    fun `processes job and commits offset manually`() {
        val event =
            PortfolioRecalculationRequestedEvent(
                jobId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                recalculateFrom = LocalDate.of(2026, 4, 1),
                triggeredBy = "test",
                requestedAt = Instant.parse("2026-04-03T10:00:00Z"),
            )

        job.receive(
            key = event.accountId.toString(),
            event = event,
            consumer = consumer,
            partition = 2,
            offset = 41L,
        )

        verify(portfolioRecalculationService).process(event.jobId)
        val commitCaptor = argumentCaptor<Map<TopicPartition, OffsetAndMetadata>>()
        verify(consumer).commitSync(commitCaptor.capture())
        val committed = commitCaptor.firstValue
        assertThat(committed.keys.single()).isEqualTo(TopicPartition("banking.portfolio-recalculation.requested", 2))
        assertThat(committed.values.single().offset()).isEqualTo(42L)
    }
}
