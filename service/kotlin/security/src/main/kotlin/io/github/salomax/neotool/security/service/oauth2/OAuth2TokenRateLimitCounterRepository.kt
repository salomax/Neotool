package io.github.salomax.neotool.security.service.oauth2

import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.time.Instant

@Singleton
open class OAuth2TokenRateLimitCounterRepository(
    private val entityManager: EntityManager,
) {
    open fun ensureCounterExists(
        scopeId: String,
        windowStart: Instant,
        windowSizeMs: Long,
    ) {
        entityManager
            .createNativeQuery(
                """
                INSERT INTO security.oauth2_token_rate_limit_counters (
                    scope_id,
                    window_start,
                    window_size_ms,
                    request_count,
                    created_at,
                    updated_at
                )
                VALUES (
                    :scopeId,
                    :windowStart,
                    :windowSizeMs,
                    0,
                    NOW(),
                    NOW()
                )
                ON CONFLICT (scope_id, window_start, window_size_ms)
                DO NOTHING
                """.trimIndent(),
            ).setParameter("scopeId", scopeId)
            .setParameter("windowStart", windowStart)
            .setParameter("windowSizeMs", windowSizeMs)
            .executeUpdate()
    }

    open fun incrementIfAllowed(
        scopeId: String,
        windowStart: Instant,
        windowSizeMs: Long,
        requestLimit: Long,
    ): OAuth2TokenRateLimitSnapshot? {
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    UPDATE security.oauth2_token_rate_limit_counters
                    SET
                        request_count = request_count + 1,
                        updated_at = NOW()
                    WHERE scope_id = :scopeId
                      AND window_start = :windowStart
                      AND window_size_ms = :windowSizeMs
                      AND request_count + 1 <= :requestLimit
                    RETURNING request_count
                    """.trimIndent(),
                ).setParameter("scopeId", scopeId)
                .setParameter("windowStart", windowStart)
                .setParameter("windowSizeMs", windowSizeMs)
                .setParameter("requestLimit", requestLimit)
                .resultList

        if (rows.isEmpty()) {
            return null
        }

        return OAuth2TokenRateLimitSnapshot(
            requestCount = (rows.first() as Number).toLong(),
        )
    }

    open fun findCounter(
        scopeId: String,
        windowStart: Instant,
        windowSizeMs: Long,
    ): OAuth2TokenRateLimitSnapshot? {
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    SELECT request_count
                    FROM security.oauth2_token_rate_limit_counters
                    WHERE scope_id = :scopeId
                      AND window_start = :windowStart
                      AND window_size_ms = :windowSizeMs
                    """.trimIndent(),
                ).setParameter("scopeId", scopeId)
                .setParameter("windowStart", windowStart)
                .setParameter("windowSizeMs", windowSizeMs)
                .resultList

        if (rows.isEmpty()) {
            return null
        }

        return OAuth2TokenRateLimitSnapshot(
            requestCount = (rows.first() as Number).toLong(),
        )
    }
}

data class OAuth2TokenRateLimitSnapshot(
    val requestCount: Long,
)
