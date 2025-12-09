package io.github.salomax.neotool.security.domain.audit

import io.github.salomax.neotool.security.model.audit.AuthorizationAuditLogEntity
import java.time.Instant
import java.util.UUID

enum class AuthorizationResult {
    ALLOWED,
    DENIED,
    NOT_EVALUATED,
}

data class AuthorizationAuditLog(
    val id: UUID? = null,
    val userId: UUID,
    // list of group IDs
    val groups: List<UUID>? = null,
    // list of role IDs
    val roles: List<Int>? = null,
    val requestedAction: String,
    val resourceType: String? = null,
    val resourceId: UUID? = null,
    val rbacResult: AuthorizationResult,
    val abacResult: AuthorizationResult? = null,
    val finalDecision: AuthorizationResult,
    val timestamp: Instant = Instant.now(),
    // additional context
    val metadata: Map<String, Any>? = null,
    val createdAt: Instant = Instant.now(),
) {
    fun toEntity(): AuthorizationAuditLogEntity {
        return AuthorizationAuditLogEntity(
            id = this.id ?: UUID.randomUUID(),
            userId = this.userId,
            groups = this.groups,
            roles = this.roles,
            requestedAction = this.requestedAction,
            resourceType = this.resourceType,
            resourceId = this.resourceId,
            rbacResult = this.rbacResult,
            abacResult = this.abacResult,
            finalDecision = this.finalDecision,
            timestamp = this.timestamp,
            metadata = this.metadata,
            createdAt = this.createdAt,
        )
    }
}
