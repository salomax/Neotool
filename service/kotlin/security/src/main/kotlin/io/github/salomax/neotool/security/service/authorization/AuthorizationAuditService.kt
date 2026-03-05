package io.github.salomax.neotool.security.service.authorization

import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.security.domain.audit.AuthorizationAuditLog
import io.github.salomax.neotool.security.domain.audit.AuthorizationResult
import io.github.salomax.neotool.security.repo.AuthorizationAuditLogRepository
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

/**
 * Service for logging authorization decisions.
 * Creates audit log entries with full context for compliance and debugging.
 * Disabled when [authorization.audit-logs.enabled] is false.
 */
@TraceExecution
@Singleton
open class AuthorizationAuditService(
    private val auditLogRepository: AuthorizationAuditLogRepository,
    @param:Value("\${authorization.audit-logs.enabled:false}") private val enabled: Boolean,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Log an authorization decision.
     * No-op when authorization audit logs are disabled.
     *
     * @param userId The user ID
     * @param groups List of group IDs the user belongs to
     * @param roles List of role IDs the user has
     * @param requestedAction The action that was requested (e.g., "transaction:update")
     * @param resourceType Optional resource type (e.g., "transaction")
     * @param resourceId Optional resource ID
     * @param rbacResult RBAC evaluation result
     * @param abacResult Optional ABAC evaluation result
     * @param finalDecision Final authorization decision
     * @param metadata Optional additional context metadata
     */
    @Transactional
    open fun logAuthorizationDecision(
        userId: UUID,
        groups: List<UUID>? = null,
        roles: List<UUID>? = null,
        requestedAction: String,
        resourceType: String? = null,
        resourceId: UUID? = null,
        rbacResult: AuthorizationResult,
        abacResult: AuthorizationResult? = null,
        finalDecision: AuthorizationResult,
        metadata: Map<String, Any>? = null,
    ) {
        if (!enabled) return

        val auditLog =
            AuthorizationAuditLog(
                userId = userId,
                groups = groups,
                roles = roles,
                requestedAction = requestedAction,
                resourceType = resourceType,
                resourceId = resourceId,
                rbacResult = rbacResult,
                abacResult = abacResult,
                finalDecision = finalDecision,
                timestamp = Instant.now(),
                metadata = metadata,
            )

        val entity = auditLog.toEntity()
        auditLogRepository.save(entity)

        logger.debug {
            "Authorization decision logged: user=$userId, action=$requestedAction, " +
                "rbac=${rbacResult.name}, abac=${abacResult?.name ?: "N/A"}, " +
                "final=${finalDecision.name}"
        }
    }

    /**
     * Find audit logs for a user.
     * Returns empty list when authorization audit logs are disabled.
     */
    fun findByUserId(userId: UUID): List<AuthorizationAuditLog> =
        if (!enabled) {
            emptyList()
        } else {
            auditLogRepository.findByUserId(userId).map { it.toDomain() }
        }

    /**
     * Find audit logs for a user within a time range.
     * Returns empty list when authorization audit logs are disabled.
     */
    fun findByUserIdAndTimestampBetween(
        userId: UUID,
        startTime: Instant,
        endTime: Instant,
    ): List<AuthorizationAuditLog> =
        if (!enabled) {
            emptyList()
        } else {
            auditLogRepository
                .findByUserIdAndTimestampBetween(userId, startTime, endTime)
                .map { it.toDomain() }
        }

    /**
     * Find audit logs by resource.
     * Returns empty list when authorization audit logs are disabled.
     */
    fun findByResource(
        resourceType: String,
        resourceId: UUID,
    ): List<AuthorizationAuditLog> =
        if (!enabled) {
            emptyList()
        } else {
            auditLogRepository
                .findByResourceTypeAndResourceId(resourceType, resourceId)
                .map { it.toDomain() }
        }
}
