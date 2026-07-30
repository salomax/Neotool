package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpoint

object ComeCotasCheckpointMapper {
    fun toDomain(entity: ComeCotasCheckpointEntity): ComeCotasCheckpoint =
        ComeCotasCheckpoint(
            id = entity.id,
            holdingId = entity.holdingId,
            checkpointDate = entity.checkpointDate,
            computedAmount = entity.computedAmount,
            withheldAmount = entity.withheldAmount,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: ComeCotasCheckpoint): ComeCotasCheckpointEntity =
        ComeCotasCheckpointEntity(
            id = domain.id,
            holdingId = domain.holdingId,
            checkpointDate = domain.checkpointDate,
            computedAmount = domain.computedAmount,
            withheldAmount = domain.withheldAmount,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
}
