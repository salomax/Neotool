package io.github.salomax.neotool.security.domain.abac

import io.github.salomax.neotool.security.model.abac.AbacPolicyEntity
import java.time.Instant
import java.util.UUID

enum class PolicyEffect {
    ALLOW,
    DENY,
}

data class AbacPolicy(
    val id: UUID? = null,
    val name: String,
    val description: String? = null,
    val effect: PolicyEffect,
    // JSON or expression string
    val condition: String,
    val version: Int = 1,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toEntity(): AbacPolicyEntity {
        return AbacPolicyEntity(
            id = this.id ?: UUID.randomUUID(),
            name = this.name,
            description = this.description,
            effect = this.effect,
            condition = this.condition,
            version = this.version,
            isActive = this.isActive,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
