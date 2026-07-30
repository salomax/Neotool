package io.github.salomax.neotool.banking.domain.investment

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ComeCotasCheckpoint(
    val id: UUID? = null,
    val holdingId: UUID,
    val checkpointDate: LocalDate,
    val computedAmount: BigDecimal,
    val withheldAmount: BigDecimal,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
