package io.github.salomax.neotool.assistant.repository

import io.github.salomax.neotool.assistant.domain.enums.BudgetScope
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.time.Instant
import java.util.UUID

@TraceExecution(layer = "repository")
@Singleton
@Requires(beans = [EntityManager::class])
open class RateLimitCounterRepository(
    private val entityManager: EntityManager,
) {
    open fun ensureCounterExists(
        scope: BudgetScope,
        scopeId: UUID,
        windowStart: Instant,
        windowSizeMs: Long,
    ) {
        entityManager
            .createNativeQuery(
                """
                INSERT INTO assistant.rate_limit_counters (
                    scope,
                    scope_id,
                    window_start,
                    window_size_ms,
                    request_count,
                    token_count,
                    created_at,
                    updated_at
                )
                VALUES (
                    :scope,
                    :scopeId,
                    :windowStart,
                    :windowSizeMs,
                    0,
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
        scope: BudgetScope,
        scopeId: UUID,
        windowStart: Instant,
        windowSizeMs: Long,
        requestLimit: Long,
        tokenIncrement: Int,
    ): RateLimitCounterSnapshot? {
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    UPDATE assistant.rate_limit_counters
                    SET
                        request_count = request_count + 1,
                        token_count = token_count + :tokenIncrement,
                        updated_at = NOW()
                    WHERE scope = :scope
                      AND scope_id = :scopeId
                      AND window_start = :windowStart
                      AND window_size_ms = :windowSizeMs
                      AND request_count + 1 <= :requestLimit
                    RETURNING request_count, token_count
                    """.trimIndent(),
                ).setParameter("scope", scope.name)
                .setParameter("scopeId", scopeId)
                .setParameter("windowStart", windowStart)
                .setParameter("windowSizeMs", windowSizeMs)
                .setParameter("requestLimit", requestLimit)
                .setParameter("tokenIncrement", tokenIncrement.toLong().coerceAtLeast(0L))
                .resultList

        if (rows.isEmpty()) {
            return null
        }

        val row = rows.first() as Array<*>
        return RateLimitCounterSnapshot(
            requestCount = (row[0] as Number).toLong(),
            tokenCount = (row[1] as Number).toLong(),
        )
    }

    open fun findCounter(
        scope: BudgetScope,
        scopeId: UUID,
        windowStart: Instant,
        windowSizeMs: Long,
    ): RateLimitCounterSnapshot? {
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    SELECT request_count, token_count
                    FROM assistant.rate_limit_counters
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

        val row = rows.first() as Array<*>
        return RateLimitCounterSnapshot(
            requestCount = (row[0] as Number).toLong(),
            tokenCount = (row[1] as Number).toLong(),
        )
    }
}

data class RateLimitCounterSnapshot(
    val requestCount: Long,
    val tokenCount: Long,
)
