package io.github.salomax.neotool.security.domain.rbac

import io.github.salomax.neotool.security.model.rbac.RoleAssignmentEntity
import java.time.Instant
import java.util.UUID

enum class ScopeType {
    PROFILE,
    PROJECT,
    RESOURCE,
    ;

    /**
     * Check if this scope type requires a scopeId.
     * PROJECT and RESOURCE require scopeId, PROFILE does not.
     */
    fun requiresScopeId(): Boolean {
        return this == PROJECT || this == RESOURCE
    }
}

data class RoleAssignment(
    val id: UUID? = null,
    val userId: UUID,
    val roleId: Int,
    val scopeType: ScopeType,
    val scopeId: UUID? = null,
    val validFrom: Instant? = null,
    val validUntil: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
) {
    fun toEntity(): RoleAssignmentEntity {
        return RoleAssignmentEntity(
            id = this.id ?: UUID.randomUUID(),
            userId = this.userId,
            roleId = this.roleId,
            scopeType = this.scopeType,
            scopeId = this.scopeId,
            validFrom = this.validFrom,
            validUntil = this.validUntil,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }

    /**
     * Check if this role assignment is active at the given time.
     * An assignment is active if:
     * - validFrom is null or now >= validFrom
     * - validUntil is null or now <= validUntil
     *
     * @param now The time to check against (defaults to current time)
     * @return true if the assignment is active, false otherwise
     */
    fun isActive(now: Instant = Instant.now()): Boolean {
        val afterValidFrom = validFrom == null || now >= validFrom
        val beforeValidUntil = validUntil == null || now <= validUntil
        return afterValidFrom && beforeValidUntil
    }

    /**
     * Check if this role assignment has expired.
     *
     * @param now The time to check against (defaults to current time)
     * @return true if the assignment has expired, false otherwise
     */
    fun isExpired(now: Instant = Instant.now()): Boolean {
        return !isActive(now)
    }

    /**
     * Check if this role assignment has time limits (temporary).
     *
     * @return true if the assignment has validFrom or validUntil set, false otherwise
     */
    fun isTemporary(): Boolean {
        return validFrom != null || validUntil != null
    }

    /**
     * Check if this role assignment has no time limits (permanent).
     *
     * @return true if the assignment has no validFrom or validUntil, false otherwise
     */
    fun isPermanent(): Boolean {
        return validFrom == null && validUntil == null
    }
}
