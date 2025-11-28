package io.github.salomax.neotool.security.domain.rbac

import io.github.salomax.neotool.security.model.rbac.GroupMembershipEntity
import java.time.Instant
import java.util.UUID

enum class MembershipType {
    MEMBER,
    ADMIN,
    OWNER,
}

data class GroupMembership(
    val id: UUID? = null,
    val userId: UUID,
    val groupId: UUID,
    val membershipType: MembershipType = MembershipType.MEMBER,
    val validUntil: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
) {
    fun toEntity(): GroupMembershipEntity {
        return GroupMembershipEntity(
            id = this.id ?: UUID.randomUUID(),
            userId = this.userId,
            groupId = this.groupId,
            membershipType = this.membershipType,
            validUntil = this.validUntil,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }

    /**
     * Check if this group membership is active at the given time.
     * A membership is active if validUntil is null or now <= validUntil.
     *
     * @param now The time to check against (defaults to current time)
     * @return true if the membership is active, false otherwise
     */
    fun isActive(now: Instant = Instant.now()): Boolean {
        return validUntil == null || now <= validUntil
    }

    /**
     * Check if this group membership has expired.
     *
     * @param now The time to check against (defaults to current time)
     * @return true if the membership has expired, false otherwise
     */
    fun isExpired(now: Instant = Instant.now()): Boolean {
        return validUntil != null && now > validUntil
    }

    /**
     * Check if this group membership has no expiry (permanent).
     *
     * @return true if the membership has no validUntil, false otherwise
     */
    fun isPermanent(): Boolean {
        return validUntil == null
    }
}
