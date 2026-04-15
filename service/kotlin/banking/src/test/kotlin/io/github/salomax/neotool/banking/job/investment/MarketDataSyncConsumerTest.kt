package io.github.salomax.neotool.banking.job.investment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.salomax.neotool.banking.client.investment.messaging.MarketDataJobSucceededEvent
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncJobName
import io.github.salomax.neotool.banking.service.investment.MarketDataSyncService
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MarketDataSyncConsumerTest {
    private val marketDataSyncService: MarketDataSyncService = mock()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun `parses date-only and datetime payload fields and commits next offset`() {
        val eventId = UUID.randomUUID()
        val runId = UUID.randomUUID()
        val payload =
            """
            {
              "eventId":"$eventId",
              "jobName":"${MarketDataSyncJobName.CVM_NAV.jobName}",
              "runId":"$runId",
              "fromDate":"2021-02-01T00:00:00Z",
              "toDate":"2021-02-28",
              "lastDataDate":"2021-02-27T23:59:59Z",
              "lastRunAt":"2026-04-11T14:00:00Z",
              "status":"SUCCESS",
              "isBackfill":false,
              "publishedAt":"2026-04-11T14:00:05Z"
            }
            """.trimIndent()

        val consumer =
            MarketDataSyncConsumer(
                marketDataSyncService = marketDataSyncService,
                objectMapper = objectMapper,
                topicName = "financial-data.market-data.job-succeeded",
            )

        @Suppress("UNCHECKED_CAST")
        val kafkaConsumer = mock<Consumer<Any, Any>>()
        consumer.receive(
            key = "k",
            payload = payload,
            consumer = kafkaConsumer,
            partition = 0,
            offset = 8L,
        )

        val eventCaptor = argumentCaptor<MarketDataJobSucceededEvent>()
        verify(marketDataSyncService).handleJobSucceeded(eventCaptor.capture())
        val event = eventCaptor.firstValue
        assertThat(event.eventId).isEqualTo(eventId)
        assertThat(event.runId).isEqualTo(runId)
        assertThat(event.fromDate).isEqualTo(LocalDate.of(2021, 2, 1))
        assertThat(event.toDate).isEqualTo(LocalDate.of(2021, 2, 28))
        assertThat(event.lastDataDate).isEqualTo(LocalDate.of(2021, 2, 27))
        assertThat(event.lastRunAt).isEqualTo(Instant.parse("2026-04-11T14:00:00Z"))
        assertThat(event.publishedAt).isEqualTo(Instant.parse("2026-04-11T14:00:05Z"))

        verify(kafkaConsumer).commitSync(
            eq(
                mapOf(
                    TopicPartition("financial-data.market-data.job-succeeded", 0) to OffsetAndMetadata(9L),
                ),
            ),
        )
    }
}
