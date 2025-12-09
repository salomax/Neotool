package io.github.salomax.neotool.security.domain.rbac

import io.github.salomax.neotool.security.model.PermissionEntity
import java.time.Instant

data class Permission(
    val id: Int? = null,
    val name: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
) {
    fun toEntity(): PermissionEntity {
        return PermissionEntity(
            id = this.id,
            name = this.name,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }
}
