package io.github.salomax.neotool.assistant.graphql.mapper

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.graphql.dto.FeatureUsageDTO
import io.github.salomax.neotool.assistant.graphql.dto.ModelUsageDTO
import io.github.salomax.neotool.assistant.graphql.dto.UsagePeriodDTO
import io.github.salomax.neotool.assistant.graphql.dto.UsageStatsDTO
import io.github.salomax.neotool.assistant.service.UsageSummary
import jakarta.inject.Singleton

@Singleton
class UsageMapper {
    fun toBudgetPeriod(period: String): BudgetPeriod {
        return try {
            BudgetPeriod.valueOf(period.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid usage period: $period", e)
        }
    }

    fun toUsageStatsDTO(summary: UsageSummary): UsageStatsDTO {
        return UsageStatsDTO(
            period = UsagePeriodDTO.valueOf(summary.period.name),
            startTime = summary.startTime.toString(),
            endTime = summary.endTime.toString(),
            requests = summary.requests,
            promptTokens = summary.promptTokens,
            completionTokens = summary.completionTokens,
            totalTokens = summary.totalTokens,
            cost = summary.cost.toDouble(),
            modelUsage =
                summary.byModel.map { model ->
                    ModelUsageDTO(
                        modelId = model.modelId?.toString(),
                        requests = model.requests,
                        promptTokens = model.promptTokens,
                        completionTokens = model.completionTokens,
                        totalTokens = model.totalTokens,
                        cost = model.cost.toDouble(),
                    )
                },
            featureUsage =
                summary.byFeature.map { feature ->
                    FeatureUsageDTO(
                        feature = feature.feature,
                        requests = feature.requests,
                        promptTokens = feature.promptTokens,
                        completionTokens = feature.completionTokens,
                        totalTokens = feature.totalTokens,
                        cost = feature.cost.toDouble(),
                    )
                },
        )
    }
}
