package io.github.salomax.neotool.banking.model.dto.investment

import io.micronaut.serde.annotation.Serdeable
import java.time.Instant
import java.util.UUID

@Serdeable
data class ComeCotasInlineBackfillRequestedEvent(
    val accountId: UUID,
    val jobIds: List<UUID>,
    val requestedAt: Instant,
)
