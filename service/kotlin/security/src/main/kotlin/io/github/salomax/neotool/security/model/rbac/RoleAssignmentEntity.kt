package io.github.salomax.neotool.security.model.rbac
import io.github.salomax.neotool.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID
import io.github.salomax.neotool.security.domain.rbac.RoleAssignment as RoleAssignmentDomain

@Entity
@Table(name = "role_assignments", schema = "security")
open class RoleAssignmentEntity(
    @Id
    @Column(columnDefinition = "uuid")
    override val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    open var userId: UUID,
    @Column(name = "role_id", nullable = false)
    open var roleId: Int,
    @Column(name = "valid_from")
    open var validFrom: Instant? = null,
    @Column(name = "valid_until")
    open var validUntil: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID>(id) {
    fun toDomain(): RoleAssignmentDomain {
        return RoleAssignmentDomain(
            id = this.id,
            userId = this.userId,
            roleId = this.roleId,
            validFrom = this.validFrom,
            validUntil = this.validUntil,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }
}
