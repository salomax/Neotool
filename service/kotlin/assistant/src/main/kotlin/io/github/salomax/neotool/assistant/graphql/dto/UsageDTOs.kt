package io.github.salomax.neotool.assistant.graphql.dto

enum class UsagePeriodDTO {
    DAILY,
    WEEKLY,
    MONTHLY,
}

data class UsageStatsDTO(
    val period: UsagePeriodDTO,
    val startTime: String,
    val endTime: String,
    val requests: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cost: Double,
    val modelUsage: List<ModelUsageDTO>,
    val featureUsage: List<FeatureUsageDTO>,
)

data class ModelUsageDTO(
    val modelId: String?,
    val requests: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cost: Double,
)

data class FeatureUsageDTO(
    val feature: String,
    val requests: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cost: Double,
)
