package io.github.salomax.neotool.security.model.rbac
import io.github.salomax.neotool.common.entity.BaseEntity
import io.github.salomax.neotool.security.domain.rbac.MembershipType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID
import io.github.salomax.neotool.security.domain.rbac.GroupMembership as GroupMembershipDomain

@Entity
@Table(name = "group_memberships", schema = "security")
open class GroupMembershipEntity(
    @Id
    @Column(columnDefinition = "uuid")
    override val id: UUID,
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    open var userId: UUID,
    @Column(name = "group_id", nullable = false, columnDefinition = "uuid")
    open var groupId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "membership_type", nullable = false, length = 32)
    open var membershipType: MembershipType = MembershipType.MEMBER,
    @Column(name = "valid_until")
    open var validUntil: Instant? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.now(),
    @Version
    open var version: Long = 0,
) : BaseEntity<UUID>(id) {
    fun toDomain(): GroupMembershipDomain {
        return GroupMembershipDomain(
            id = this.id,
            userId = this.userId,
            groupId = this.groupId,
            membershipType = this.membershipType,
            validUntil = this.validUntil,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }
}
