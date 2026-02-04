package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.EmailVerificationEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Repository
interface EmailVerificationRepository : JpaRepository<EmailVerificationEntity, UUID> {
    /**
     * Find active (non-invalidated) verification record for user.
     */
    @Query(
        value = """
        SELECT ev FROM EmailVerificationEntity ev
        WHERE ev.user.id = :userId
          AND ev.invalidatedAt IS NULL
        ORDER BY ev.createdAt DESC
        """,
    )
    fun findActiveByUserId(userId: UUID): EmailVerificationEntity?

    /**
     * Find recent verification records (for rate limiting).
     */
    @Query(
        value = """
        SELECT ev FROM EmailVerificationEntity ev
        WHERE ev.user.id = :userId
          AND ev.createdAt > :since
        ORDER BY ev.createdAt DESC
        """,
    )
    fun findRecentByUserId(
        userId: UUID,
        since: Instant,
    ): List<EmailVerificationEntity>

    /**
     * Find by token hash (for magic link verification).
     */
    @Query(
        value = """
        SELECT ev FROM EmailVerificationEntity ev
        WHERE ev.tokenHash = :tokenHash
          AND ev.invalidatedAt IS NULL
        """,
    )
    fun findByTokenHash(tokenHash: String): EmailVerificationEntity?
}
