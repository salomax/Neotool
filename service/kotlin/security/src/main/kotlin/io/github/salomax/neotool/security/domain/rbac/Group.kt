package io.github.salomax.neotool.security.domain.rbac

import io.github.salomax.neotool.security.model.rbac.GroupEntity
import java.time.Instant
import java.util.UUID

data class Group(
    val id: UUID? = null,
    val name: String,
    val description: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
) {
    fun toEntity(): GroupEntity {
        return GroupEntity(
            id = this.id ?: UUID.randomUUID(),
            name = this.name,
            description = this.description,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }
}
