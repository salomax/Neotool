package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpoint
import java.time.LocalDate
import java.util.UUID

interface ComeCotasCheckpointRepository {
    fun upsert(checkpoint: ComeCotasCheckpoint): ComeCotasCheckpoint

    fun findByHoldingIds(holdingIds: List<UUID>): List<ComeCotasCheckpoint>

    fun findByHoldingAndDateRange(
        holdingId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<ComeCotasCheckpoint>
}
