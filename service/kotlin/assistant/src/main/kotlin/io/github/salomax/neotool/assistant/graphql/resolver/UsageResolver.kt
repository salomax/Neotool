package io.github.salomax.neotool.assistant.graphql.resolver

import io.github.salomax.neotool.assistant.graphql.dto.UsageStatsDTO
import io.github.salomax.neotool.assistant.graphql.mapper.UsageMapper
import io.github.salomax.neotool.assistant.service.UsageTrackingService
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class UsageResolver(
    private val usageTrackingService: UsageTrackingService,
    private val usageMapper: UsageMapper,
) {
    @ExecuteOn(TaskExecutors.BLOCKING)
    fun myUsage(
        accountId: UUID,
        period: String,
    ): UsageStatsDTO {
        val periodEnum = usageMapper.toBudgetPeriod(period)
        return usageMapper.toUsageStatsDTO(usageTrackingService.getUsageSummary(accountId, periodEnum))
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    fun serviceUsage(
        serviceId: String,
        period: String,
    ): UsageStatsDTO {
        val periodEnum = usageMapper.toBudgetPeriod(period)
        return usageMapper.toUsageStatsDTO(usageTrackingService.getServiceUsage(serviceId, periodEnum))
    }
}
