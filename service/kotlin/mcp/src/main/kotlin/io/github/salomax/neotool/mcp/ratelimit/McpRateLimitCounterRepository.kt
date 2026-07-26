package io.github.salomax.neotool.mcp.ratelimit

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

@TraceExecution(layer = "repository")
@Singleton
@Requires(beans = [EntityManager::class])
@Transactional
open class McpRateLimitCounterRepository(
    private val entityManager: EntityManager,
) {
    open fun ensureCounterExists(
        scope: McpRateLimitScope,
        scopeId: UUID,
        windowStart: Instant,
        windowSizeMs: Long,
    ) {
        entityManager
            .createNativeQuery(
                """
                INSERT INTO mcp.rate_limit_counters (
                    scope,
                    scope_id,
                    window_start,
                    window_size_ms,
                    request_count,
                    created_at,
                    updated_at
                )
                VALUES (
                    :scope,
                    :scopeId,
                    :windowStart,
                    :windowSizeMs,
                    0,
                    NOW(),
                    NOW()
                )
                ON CONFLICT (scope, scope_id, window_start, window_size_ms)
                DO NOTHING
                """.trimIndent(),
            ).setParameter("scope", scope.name)
            .setParameter("scopeId", scopeId)
            .setParameter("windowStart", windowStart)
            .setParameter("windowSizeMs", windowSizeMs)
            .executeUpdate()
    }

    open fun incrementIfAllowed(
        scope: McpRateLimitScope,
        scopeId: UUID,
        windowStart: Instant,
        windowSizeMs: Long,
        requestLimit: Long,
    ): McpRateLimitCounterSnapshot? {
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    UPDATE mcp.rate_limit_counters
                    SET
                        request_count = request_count + 1,
                        updated_at = NOW()
                    WHERE scope = :scope
                      AND scope_id = :scopeId
                      AND window_start = :windowStart
                      AND window_size_ms = :windowSizeMs
                      AND request_count + 1 <= :requestLimit
                    RETURNING request_count
                    """.trimIndent(),
                ).setParameter("scope", scope.name)
                .setParameter("scopeId", scopeId)
                .setParameter("windowStart", windowStart)
                .setParameter("windowSizeMs", windowSizeMs)
                .setParameter("requestLimit", requestLimit)
                .resultList

        if (rows.isEmpty()) {
            return null
        }

        return McpRateLimitCounterSnapshot(
            requestCount = (rows.first() as Number).toLong(),
        )
    }

    open fun findCounter(
        scope: McpRateLimitScope,
        scopeId: UUID,
        windowStart: Instant,
        windowSizeMs: Long,
    ): McpRateLimitCounterSnapshot? {
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    SELECT request_count
                    FROM mcp.rate_limit_counters
                    WHERE scope = :scope
                      AND scope_id = :scopeId
                      AND window_start = :windowStart
                      AND window_size_ms = :windowSizeMs
                    """.trimIndent(),
                ).setParameter("scope", scope.name)
                .setParameter("scopeId", scopeId)
                .setParameter("windowStart", windowStart)
                .setParameter("windowSizeMs", windowSizeMs)
                .resultList

        if (rows.isEmpty()) {
            return null
        }

        return McpRateLimitCounterSnapshot(
            requestCount = (rows.first() as Number).toLong(),
        )
    }

    open fun deleteExpired(now: Instant): Int =
        entityManager
            .createNativeQuery(
                """
                DELETE FROM mcp.rate_limit_counters
                WHERE window_start + (window_size_ms * INTERVAL '1 millisecond') < :now
                """.trimIndent(),
            ).setParameter("now", now)
            .executeUpdate()
}

data class McpRateLimitCounterSnapshot(
    val requestCount: Long,
)
