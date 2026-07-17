package io.github.salomax.neotool.banking.model.dto.investment

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ComeCotasCheckpointRequestedEvent(
    val jobId: UUID,
    val accountId: UUID,
    val financialAccountId: UUID,
    val holdingId: UUID,
    val checkpointDate: LocalDate,
    val requestedAt: Instant,
)
