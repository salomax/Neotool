package io.github.salomax.neotool.mcp.security

import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Repository
interface ConnectionRevocationRepository : JpaRepository<ConnectionRevocationEntity, ConnectionRevocationId> {
    @Query(
        value = """
            INSERT INTO mcp.connection_revocations (
                user_id,
                client_id,
                account_id,
                revoked_at,
                revoked_until,
                reason
            )
            VALUES (
                :userId,
                :clientId,
                :accountId,
                :revokedAt,
                :revokedUntil,
                :reason
            )
            ON CONFLICT (user_id, client_id, account_id)
            DO UPDATE SET
                revoked_at = EXCLUDED.revoked_at,
                revoked_until = EXCLUDED.revoked_until,
                reason = EXCLUDED.reason
        """,
        nativeQuery = true,
    )
    fun upsertRevocation(
        userId: UUID,
        clientId: String,
        accountId: UUID,
        revokedAt: Instant,
        revokedUntil: Instant?,
        reason: String?,
    )

    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1
                FROM mcp.connection_revocations
                WHERE user_id = :userId
                  AND client_id = :clientId
                  AND account_id = :accountId
            )
        """,
        nativeQuery = true,
    )
    fun existsRevocation(
        userId: UUID,
        clientId: String,
        accountId: UUID,
    ): Boolean

    @Query(
        value = """
            DELETE FROM mcp.connection_revocations
            WHERE revoked_until IS NOT NULL
              AND revoked_until < :now
        """,
        nativeQuery = true,
    )
    fun deleteExpired(now: Instant): Long
}
