package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.messaging.ComeCotasCheckpointRequestedEvent
import io.github.salomax.neotool.banking.job.investment.ComeCotasCheckpointConsumer
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

class ComeCotasCheckpointConsumerTest {
    private val service: ComeCotasCheckpointService = mock()
    private val consumer: Consumer<Any, Any> = mock()

    private val job =
        ComeCotasCheckpointConsumer(
            service = service,
            topicName = "banking.come-cotas-checkpoint.requested",
        )

    @Test
    fun `processes job and commits offset manually`() {
        val event =
            ComeCotasCheckpointRequestedEvent(
                jobId = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                financialAccountId = UUID.randomUUID(),
                holdingId = UUID.randomUUID(),
                checkpointDate = LocalDate.of(2026, 5, 29),
                requestedAt = Instant.parse("2026-05-30T10:00:00Z"),
            )

        job.receive(
            key = event.accountId.toString(),
            event = event,
            consumer = consumer,
            partition = 1,
            offset = 10L,
        )

        verify(service).process(event.jobId)
        val commitCaptor = argumentCaptor<Map<TopicPartition, OffsetAndMetadata>>()
        verify(consumer).commitSync(commitCaptor.capture())
        assertThat(
            commitCaptor.firstValue.keys.single(),
        ).isEqualTo(TopicPartition("banking.come-cotas-checkpoint.requested", 1))
        assertThat(commitCaptor.firstValue.values.single().offset()).isEqualTo(11L)
    }
}
