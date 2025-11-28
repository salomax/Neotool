package io.github.salomax.neotool.security.domain.abac

import io.github.salomax.neotool.security.model.abac.AbacPolicyVersionEntity
import java.time.Instant
import java.util.UUID

data class AbacPolicyVersion(
    val id: UUID? = null,
    val policyId: UUID,
    val version: Int,
    val effect: PolicyEffect,
    // JSON or expression string
    val condition: String,
    val isActive: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val createdBy: UUID? = null,
) {
    fun toEntity(): AbacPolicyVersionEntity {
        return AbacPolicyVersionEntity(
            id = this.id ?: UUID.randomUUID(),
            policyId = this.policyId,
            version = this.version,
            effect = this.effect,
            condition = this.condition,
            isActive = this.isActive,
            createdAt = this.createdAt,
            createdBy = this.createdBy,
        )
    }
}
