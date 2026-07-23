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
