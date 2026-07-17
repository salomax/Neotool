package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJob

object ComeCotasCheckpointJobMapper {
    fun toDomain(entity: ComeCotasCheckpointJobEntity): ComeCotasCheckpointJob =
        ComeCotasCheckpointJob(
            id = entity.id,
            holdingId = entity.holdingId,
            financialAccountId = entity.financialAccountId,
            accountId = entity.accountId,
            checkpointDate = entity.checkpointDate,
            status = entity.status,
            processingNodeId = entity.processingNodeId,
            lastHeartbeatAt = entity.lastHeartbeatAt,
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            nextAttemptAt = entity.nextAttemptAt,
            attemptCount = entity.attemptCount,
            errorCode = entity.errorCode,
            errorDetails = entity.errorDetails,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: ComeCotasCheckpointJob): ComeCotasCheckpointJobEntity =
        ComeCotasCheckpointJobEntity(
            id = null,
            holdingId = domain.holdingId,
            financialAccountId = domain.financialAccountId,
            accountId = domain.accountId,
            checkpointDate = domain.checkpointDate,
            status = domain.status,
            processingNodeId = domain.processingNodeId,
            lastHeartbeatAt = domain.lastHeartbeatAt,
            startedAt = domain.startedAt,
            completedAt = domain.completedAt,
            nextAttemptAt = domain.nextAttemptAt,
            attemptCount = domain.attemptCount,
            errorCode = domain.errorCode,
            errorDetails = domain.errorDetails,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )

    fun updateEntity(
        entity: ComeCotasCheckpointJobEntity,
        domain: ComeCotasCheckpointJob,
    ) {
        entity.status = domain.status
        entity.processingNodeId = domain.processingNodeId
        entity.lastHeartbeatAt = domain.lastHeartbeatAt
        entity.startedAt = domain.startedAt
        entity.completedAt = domain.completedAt
        entity.nextAttemptAt = domain.nextAttemptAt
        entity.attemptCount = domain.attemptCount
        entity.errorCode = domain.errorCode
        entity.errorDetails = domain.errorDetails
        entity.updatedAt = domain.updatedAt
    }
}
