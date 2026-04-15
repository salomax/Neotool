package io.github.salomax.neotool.banking.job.investment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

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
    private val objectMapper: ObjectMapper,
    @param:Property(name = "kafka.topics.market-data-job-succeeded")
    private val topicName: String,
) {
    @Topic("\${kafka.topics.market-data-job-succeeded:financial-data.market-data.job-succeeded}")
    fun receive(
        @KafkaKey key: String?,
        payload: String,
        consumer: Consumer<*, *>,
        @KafkaPartition partition: Int,
        offset: Long,
    ) {
        val event = parseEvent(payload)
        marketDataSyncService.handleJobSucceeded(event)
        consumer.commitSync(
            mapOf(
                TopicPartition(topicName, partition) to OffsetAndMetadata(offset + 1),
            ),
        )
    }

    private fun parseEvent(payload: String): MarketDataJobSucceededEvent {
        val node = objectMapper.readTree(payload)
        return MarketDataJobSucceededEvent(
            eventId = UUID.fromString(node.requiredText("eventId")),
            jobName = node.requiredText("jobName"),
            runId = UUID.fromString(node.requiredText("runId")),
            fromDate = parseFlexibleLocalDate(node.requiredText("fromDate")),
            toDate = parseFlexibleLocalDate(node.requiredText("toDate")),
            lastDataDate = parseFlexibleLocalDate(node.requiredText("lastDataDate")),
            lastRunAt = Instant.parse(node.requiredText("lastRunAt")),
            status = node.requiredText("status"),
            isBackfill = node.requiredBoolean("isBackfill"),
            publishedAt = Instant.parse(node.requiredText("publishedAt")),
        )
    }

    private fun parseFlexibleLocalDate(raw: String): LocalDate {
        runCatching { return LocalDate.parse(raw) }
        runCatching { return Instant.parse(raw).atOffset(ZoneOffset.UTC).toLocalDate() }
        runCatching { return OffsetDateTime.parse(raw).toLocalDate() }
        runCatching { return LocalDateTime.parse(raw).toLocalDate() }
        throw IllegalArgumentException("Unsupported date format: $raw")
    }

    private fun JsonNode.requiredText(field: String): String {
        val value = get(field)?.asText()?.trim()
        require(!value.isNullOrBlank()) { "Missing or blank field '$field' in market data event payload" }
        return value
    }

    private fun JsonNode.requiredBoolean(field: String): Boolean {
        val node = get(field) ?: throw IllegalArgumentException("Missing field '$field' in market data event payload")
        if (node.isBoolean) return node.booleanValue()
        return node.asText().toBooleanStrictOrNull()
            ?: throw IllegalArgumentException("Invalid boolean field '$field' in market data event payload")
    }
}
