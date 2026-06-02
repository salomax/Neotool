package io.github.salomax.neotool.banking.model.dto.investment

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PortfolioRecalculationRequestedEvent(
    val jobId: UUID,
    val accountId: UUID,
    val financialAccountId: UUID?,
    val recalculateFrom: LocalDate,
    val triggeredBy: String,
    val requestedAt: Instant,
)
