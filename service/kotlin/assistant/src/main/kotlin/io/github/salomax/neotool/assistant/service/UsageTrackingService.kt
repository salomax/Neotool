package io.github.salomax.neotool.assistant.service

import io.github.salomax.neotool.assistant.domain.enums.BudgetPeriod
import io.github.salomax.neotool.assistant.entity.UsageLogEntity
import io.github.salomax.neotool.assistant.repository.FeatureUsageProjection
import io.github.salomax.neotool.assistant.repository.ModelUsageProjection
import io.github.salomax.neotool.assistant.repository.UsageLogRepository
import io.github.salomax.neotool.assistant.repository.UsageTotalsProjection
import io.github.salomax.neotool.common.tracing.TraceExecution
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@TraceExecution
@Singleton
open class UsageTrackingService(
    private val usageLogRepository: UsageLogRepository,
) {
    @Transactional
    open fun recordUsage(
        completionResponse: CompletionResult,
        accountId: UUID,
        service: String,
        feature: String,
        purpose: String,
    ): UsageLogEntity {
        val normalizedService = service.trim()
        val normalizedFeature = feature.trim()
        val normalizedPurpose = purpose.trim()

        require(normalizedService.isNotEmpty()) { "service is required" }
        require(normalizedFeature.isNotEmpty()) { "feature is required" }
        require(normalizedPurpose.isNotEmpty()) { "purpose is required" }

        val promptTokens = completionResponse.usage.promptTokens.coerceAtLeast(0)
        val completionTokens = completionResponse.usage.completionTokens.coerceAtLeast(0)
        val totalTokens = completionResponse.usage.totalTokens.takeIf { it >= 0 } ?: (promptTokens + completionTokens)

        val cost = completionResponse.cost.max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP)

        return usageLogRepository.save(
            UsageLogEntity(
                accountId = accountId,
                requestId = completionResponse.requestId,
                service = normalizedService,
                userId = null,
                feature = normalizedFeature,
                purpose = normalizedPurpose,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                cost = cost,
                modelId = completionResponse.modelId,
            ),
        )
    }

    open fun getUsageSummary(
        accountId: UUID,
        period: BudgetPeriod,
    ): UsageSummary {
        val range = period.toRange(Instant.now())
        val totals =
            usageLogRepository.summarizeByAccountIdAndTimestampBetween(accountId, range.start, range.end)
                ?: emptyTotals()
        val byModel = usageLogRepository.summarizeByAccountIdGroupedByModel(accountId, range.start, range.end)
        val byFeature = usageLogRepository.summarizeByAccountIdGroupedByFeature(accountId, range.start, range.end)
        return aggregateUsage(period, range, totals, byModel, byFeature)
    }

    open fun getServiceUsage(
        serviceId: String,
        period: BudgetPeriod,
    ): UsageSummary {
        val normalizedService = serviceId.trim()
        require(normalizedService.isNotEmpty()) { "serviceId is required" }

        val range = period.toRange(Instant.now())
        val totals =
            usageLogRepository.summarizeByServiceAndTimestampBetween(normalizedService, range.start, range.end)
                ?: emptyTotals()
        val byModel = usageLogRepository.summarizeByServiceGroupedByModel(normalizedService, range.start, range.end)
        val byFeature = usageLogRepository.summarizeByServiceGroupedByFeature(normalizedService, range.start, range.end)
        return aggregateUsage(period, range, totals, byModel, byFeature)
    }

    private fun aggregateUsage(
        period: BudgetPeriod,
        range: UsageWindow,
        totals: UsageTotalsProjection,
        byModelRows: List<ModelUsageProjection>,
        byFeatureRows: List<FeatureUsageProjection>,
    ): UsageSummary {
        val byModel =
            byModelRows.map { row ->
                ModelUsageSummary(
                    modelId = row.modelId,
                    requests = toInt(row.requests),
                    promptTokens = toInt(row.promptTokens),
                    completionTokens = toInt(row.completionTokens),
                    totalTokens = toInt(row.totalTokens),
                    cost = normalizeCost(row.cost),
                )
            }

        val byFeature =
            byFeatureRows.map { row ->
                FeatureUsageSummary(
                    feature = row.feature,
                    requests = toInt(row.requests),
                    promptTokens = toInt(row.promptTokens),
                    completionTokens = toInt(row.completionTokens),
                    totalTokens = toInt(row.totalTokens),
                    cost = normalizeCost(row.cost),
                )
            }

        return UsageSummary(
            period = period,
            startTime = range.start,
            endTime = range.end,
            requests = toInt(totals.requests),
            promptTokens = toInt(totals.promptTokens),
            completionTokens = toInt(totals.completionTokens),
            totalTokens = toInt(totals.totalTokens),
            cost = normalizeCost(totals.cost),
            byModel = byModel,
            byFeature = byFeature,
        )
    }

    private fun emptyTotals(): UsageTotalsProjection {
        return object : UsageTotalsProjection {
            override val requests: Long = 0L
            override val promptTokens: Long = 0L
            override val completionTokens: Long = 0L
            override val totalTokens: Long = 0L
            override val cost: BigDecimal = BigDecimal.ZERO
        }
    }

    private fun toInt(value: Long): Int {
        return value.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun normalizeCost(cost: BigDecimal): BigDecimal {
        return cost.max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP)
    }

    private fun BudgetPeriod.toRange(now: Instant): UsageWindow {
        val start =
            when (this) {
                BudgetPeriod.DAILY -> now.minus(1, ChronoUnit.DAYS)
                BudgetPeriod.WEEKLY -> now.minus(7, ChronoUnit.DAYS)
                BudgetPeriod.MONTHLY -> now.minus(30, ChronoUnit.DAYS)
            }

        return UsageWindow(start = start, end = now)
    }
}

data class UsageSummary(
    val period: BudgetPeriod,
    val startTime: Instant,
    val endTime: Instant,
    val requests: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cost: BigDecimal,
    val byModel: List<ModelUsageSummary> = emptyList(),
    val byFeature: List<FeatureUsageSummary> = emptyList(),
)

data class ModelUsageSummary(
    val modelId: UUID?,
    val requests: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cost: BigDecimal,
)

data class FeatureUsageSummary(
    val feature: String,
    val requests: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cost: BigDecimal,
)

private data class UsageWindow(
    val start: Instant,
    val end: Instant,
)
