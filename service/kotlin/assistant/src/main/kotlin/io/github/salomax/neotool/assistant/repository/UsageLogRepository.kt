package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.entity.UsageLogEntity
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface UsageLogRepository : JpaRepository<UsageLogEntity, UUID> {
    @Query(
        """
        SELECT COUNT(u) AS requests,
               COALESCE(SUM(u.promptTokens), 0) AS promptTokens,
               COALESCE(SUM(u.completionTokens), 0) AS completionTokens,
               COALESCE(SUM(u.totalTokens), 0) AS totalTokens,
               COALESCE(SUM(u.cost), 0) AS cost
        FROM UsageLogEntity u
        WHERE u.accountId = :accountId
        AND u.timestamp >= :startTime
        AND u.timestamp <= :endTime
        """,
    )
    fun summarizeByAccountIdAndTimestampBetween(
        accountId: UUID,
        startTime: Instant,
        endTime: Instant,
    ): UsageTotalsProjection?

    @Query(
        """
        SELECT u.modelId AS modelId,
               COUNT(u) AS requests,
               COALESCE(SUM(u.promptTokens), 0) AS promptTokens,
               COALESCE(SUM(u.completionTokens), 0) AS completionTokens,
               COALESCE(SUM(u.totalTokens), 0) AS totalTokens,
               COALESCE(SUM(u.cost), 0) AS cost
        FROM UsageLogEntity u
        WHERE u.accountId = :accountId
          AND u.timestamp >= :startTime
          AND u.timestamp <= :endTime
        GROUP BY u.modelId
        ORDER BY COALESCE(SUM(u.totalTokens), 0) DESC
        """,
    )
    fun summarizeByAccountIdGroupedByModel(
        accountId: UUID,
        startTime: Instant,
        endTime: Instant,
    ): List<ModelUsageProjection>

    @Query(
        """
        SELECT u.feature AS feature,
               COUNT(u) AS requests,
               COALESCE(SUM(u.promptTokens), 0) AS promptTokens,
               COALESCE(SUM(u.completionTokens), 0) AS completionTokens,
               COALESCE(SUM(u.totalTokens), 0) AS totalTokens,
               COALESCE(SUM(u.cost), 0) AS cost
        FROM UsageLogEntity u
        WHERE u.accountId = :accountId
          AND u.timestamp >= :startTime
          AND u.timestamp <= :endTime
        GROUP BY u.feature
        ORDER BY COALESCE(SUM(u.totalTokens), 0) DESC
        """,
    )
    fun summarizeByAccountIdGroupedByFeature(
        accountId: UUID,
        startTime: Instant,
        endTime: Instant,
    ): List<FeatureUsageProjection>

    @Query(
        """
        SELECT COUNT(u) AS requests,
               COALESCE(SUM(u.promptTokens), 0) AS promptTokens,
               COALESCE(SUM(u.completionTokens), 0) AS completionTokens,
               COALESCE(SUM(u.totalTokens), 0) AS totalTokens,
               COALESCE(SUM(u.cost), 0) AS cost
        FROM UsageLogEntity u
        WHERE u.service = :service
        AND u.timestamp >= :startTime
        AND u.timestamp <= :endTime
        """,
    )
    fun summarizeByServiceAndTimestampBetween(
        service: String,
        startTime: Instant,
        endTime: Instant,
    ): UsageTotalsProjection?

    @Query(
        """
        SELECT u.modelId AS modelId,
               COUNT(u) AS requests,
               COALESCE(SUM(u.promptTokens), 0) AS promptTokens,
               COALESCE(SUM(u.completionTokens), 0) AS completionTokens,
               COALESCE(SUM(u.totalTokens), 0) AS totalTokens,
               COALESCE(SUM(u.cost), 0) AS cost
        FROM UsageLogEntity u
        WHERE u.service = :service
          AND u.timestamp >= :startTime
          AND u.timestamp <= :endTime
        GROUP BY u.modelId
        ORDER BY COALESCE(SUM(u.totalTokens), 0) DESC
        """,
    )
    fun summarizeByServiceGroupedByModel(
        service: String,
        startTime: Instant,
        endTime: Instant,
    ): List<ModelUsageProjection>

    @Query(
        """
        SELECT u.feature AS feature,
               COUNT(u) AS requests,
               COALESCE(SUM(u.promptTokens), 0) AS promptTokens,
               COALESCE(SUM(u.completionTokens), 0) AS completionTokens,
               COALESCE(SUM(u.totalTokens), 0) AS totalTokens,
               COALESCE(SUM(u.cost), 0) AS cost
        FROM UsageLogEntity u
        WHERE u.service = :service
          AND u.timestamp >= :startTime
          AND u.timestamp <= :endTime
        GROUP BY u.feature
        ORDER BY COALESCE(SUM(u.totalTokens), 0) DESC
        """,
    )
    fun summarizeByServiceGroupedByFeature(
        service: String,
        startTime: Instant,
        endTime: Instant,
    ): List<FeatureUsageProjection>
}

interface UsageTotalsProjection {
    val requests: Long
    val promptTokens: Long
    val completionTokens: Long
    val totalTokens: Long
    val cost: BigDecimal
}

interface ModelUsageProjection {
    val modelId: UUID?
    val requests: Long
    val promptTokens: Long
    val completionTokens: Long
    val totalTokens: Long
    val cost: BigDecimal
}

interface FeatureUsageProjection {
    val feature: String
    val requests: Long
    val promptTokens: Long
    val completionTokens: Long
    val totalTokens: Long
    val cost: BigDecimal
}
