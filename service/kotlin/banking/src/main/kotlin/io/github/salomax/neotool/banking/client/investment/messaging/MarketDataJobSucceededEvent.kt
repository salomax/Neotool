package io.github.salomax.neotool.banking.client.investment.messaging

import io.micronaut.serde.annotation.Serdeable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Serdeable
data class MarketDataJobSucceededEvent(
    val eventId: UUID,
    val jobName: String,
    val runId: UUID,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val lastDataDate: LocalDate,
    val lastRunAt: Instant,
    val status: String,
    val isBackfill: Boolean,
    val publishedAt: Instant,
)
