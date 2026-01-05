package io.github.salomax.neotool.security.model

import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Entity representing a permission assignment to a principal.
 * Supports both user and service principals with optional resource pattern matching.
 */
@Entity
@Table(name = "principal_permissions", schema = "security")
open class PrincipalPermissionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "uuid")
    override val id: UUID? = null,
    @Column(name = "principal_id", nullable = false, columnDefinition = "uuid")
    open var principalId: UUID,
    @Column(name = "permission_id", nullable = false, columnDefinition = "uuid")
    open var permissionId: UUID,
    @Column(name = "resource_pattern")
    open var resourcePattern: String? = null,
) : BaseEntity<UUID?>(id) {
    fun toDomain(): PrincipalPermission {
        return PrincipalPermission(
            principalId = this.principalId,
            permissionId = this.permissionId,
            resourcePattern = this.resourcePattern,
        )
    }
}

/**
 * Domain model for PrincipalPermission.
 */
data class PrincipalPermission(
    val principalId: UUID,
    val permissionId: UUID,
    val resourcePattern: String?,
)
