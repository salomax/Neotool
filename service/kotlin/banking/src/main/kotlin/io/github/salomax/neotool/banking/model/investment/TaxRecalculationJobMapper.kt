package io.github.salomax.neotool.banking.model.investment

import io.github.salomax.neotool.banking.domain.investment.TaxRecalculationJob

object TaxRecalculationJobMapper {
    fun toDomain(entity: TaxRecalculationJobEntity): TaxRecalculationJob =
        TaxRecalculationJob(
            id = entity.id,
            holdingId = entity.holdingId,
            financialAccountId = entity.financialAccountId,
            accountId = entity.accountId,
            status = entity.status,
            recalculateFrom = entity.recalculateFrom,
            triggeredBy = entity.triggeredBy,
            daysProcessed = entity.daysProcessed,
            errorCode = entity.errorCode,
            errorDetails = entity.errorDetails,
            processingNodeId = entity.processingNodeId,
            lastHeartbeatAt = entity.lastHeartbeatAt,
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toEntity(domain: TaxRecalculationJob): TaxRecalculationJobEntity =
        TaxRecalculationJobEntity(
            id = null,
            holdingId = domain.holdingId,
            financialAccountId = domain.financialAccountId,
            accountId = domain.accountId,
            status = domain.status,
            recalculateFrom = domain.recalculateFrom,
            triggeredBy = domain.triggeredBy,
            daysProcessed = domain.daysProcessed,
            errorCode = domain.errorCode,
            errorDetails = domain.errorDetails,
            processingNodeId = domain.processingNodeId,
            lastHeartbeatAt = domain.lastHeartbeatAt,
            startedAt = domain.startedAt,
            completedAt = domain.completedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )

    fun updateEntity(
        entity: TaxRecalculationJobEntity,
        domain: TaxRecalculationJob,
    ) {
        entity.status = domain.status
        entity.recalculateFrom = domain.recalculateFrom
        entity.triggeredBy = domain.triggeredBy
        entity.daysProcessed = domain.daysProcessed
        entity.errorCode = domain.errorCode
        entity.errorDetails = domain.errorDetails
        entity.processingNodeId = domain.processingNodeId
        entity.lastHeartbeatAt = domain.lastHeartbeatAt
        entity.startedAt = domain.startedAt
        entity.completedAt = domain.completedAt
        entity.updatedAt = domain.updatedAt
    }
}
