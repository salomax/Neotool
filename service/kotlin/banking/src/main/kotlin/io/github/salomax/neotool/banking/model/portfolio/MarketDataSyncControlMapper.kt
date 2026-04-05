package io.github.salomax.neotool.banking.model.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncControl
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncJobName

object MarketDataSyncControlMapper {
    fun toDomain(entity: MarketDataSyncControlEntity): MarketDataSyncControl =
        MarketDataSyncControl(
            jobName = MarketDataSyncJobName.from(entity.jobName),
            sourceLastDataDate = entity.sourceLastDataDate,
            sourceLastRunAt = entity.sourceLastRunAt,
            lastSyncedDate = entity.lastSyncedDate,
            lastSyncStartedAt = entity.lastSyncStartedAt,
            lastSyncCompletedAt = entity.lastSyncCompletedAt,
            status = entity.status,
            lastEventId = entity.lastEventId,
            lastError = entity.lastError,
            updatedAt = entity.updatedAt,
        )
}
