package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.audit.AuthorizationAuditLogEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface AuthorizationAuditLogRepository : JpaRepository<AuthorizationAuditLogEntity, UUID> {
    /**
     * Find audit log by ID.
     * Explicitly overridden to help KSP generate correct implementation.
     */
    override fun findById(id: UUID): Optional<AuthorizationAuditLogEntity>

    /**
     * Find all audit logs for a user.
     */
    fun findByUserId(userId: UUID): List<AuthorizationAuditLogEntity>

    /**
     * Find audit logs for a user within a time range.
     */
    @Query(
        """
        SELECT a FROM AuthorizationAuditLogEntity a
        WHERE a.userId = :userId
        AND a.timestamp >= :startTime
        AND a.timestamp <= :endTime
        ORDER BY a.timestamp DESC
        """,
    )
    fun findByUserIdAndTimestampBetween(
        userId: UUID,
        startTime: Instant,
        endTime: Instant,
    ): List<AuthorizationAuditLogEntity>

    /**
     * Find audit logs by resource type and ID.
     */
    @Query(
        """
        SELECT a FROM AuthorizationAuditLogEntity a
        WHERE a.resourceType = :resourceType
        AND a.resourceId = :resourceId
        ORDER BY a.timestamp DESC
        """,
    )
    fun findByResourceTypeAndResourceId(
        resourceType: String,
        resourceId: UUID,
    ): List<AuthorizationAuditLogEntity>

    /**
     * Find audit logs within a time range.
     */
    @Query(
        """
        SELECT a FROM AuthorizationAuditLogEntity a
        WHERE a.timestamp >= :startTime
        AND a.timestamp <= :endTime
        ORDER BY a.timestamp DESC
        """,
    )
    fun findByTimestampBetween(
        startTime: Instant,
        endTime: Instant,
    ): List<AuthorizationAuditLogEntity>
}
