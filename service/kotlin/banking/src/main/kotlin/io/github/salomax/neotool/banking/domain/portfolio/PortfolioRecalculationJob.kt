package io.github.salomax.neotool.banking.domain.portfolio

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PortfolioRecalculationJob(
    val id: UUID? = null,
    val financialAccountId: UUID? = null,
    val accountId: UUID,
    val status: PortfolioRecalculationStatus,
    val recalculateFrom: LocalDate,
    val triggeredBy: String,
    val daysProcessed: Int? = null,
    val errorDetails: String? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
