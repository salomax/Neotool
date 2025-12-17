package io.github.salomax.neotool.security.domain.rbac

import io.github.salomax.neotool.security.model.UserEntity
import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID? = null,
    val email: String,
    val displayName: String? = null,
    val enabled: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val avatarUrl: String? = null,
    val version: Long = 0,
) {
    fun toEntity(): UserEntity {
        return UserEntity(
            id = this.id,
            email = this.email,
            displayName = this.displayName,
            enabled = this.enabled,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            avatarUrl = this.avatarUrl,
            version = this.version,
        )
    }
}
