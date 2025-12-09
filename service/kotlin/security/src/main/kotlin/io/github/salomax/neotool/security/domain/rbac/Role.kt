package io.github.salomax.neotool.security.domain.rbac

import io.github.salomax.neotool.security.model.RoleEntity
import java.time.Instant

data class Role(
    val id: Int? = null,
    val name: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
) {
    fun toEntity(): RoleEntity {
        return RoleEntity(
            id = this.id,
            name = this.name,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }
}
