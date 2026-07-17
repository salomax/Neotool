package io.github.salomax.neotool.security.graphql.mapper

import io.github.salomax.neotool.security.graphql.dto.AccountMemberDTO
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import jakarta.inject.Singleton

@Singleton
class AccountMembershipMapper {
    fun toDto(
        membership: AccountMembershipEntity,
        user: UserEntity?,
        currentUserId: String,
    ): AccountMemberDTO {
        val email =
            user?.email
                ?: membership.inviteeEmail
                ?: error("Membership ${membership.id} has neither user nor inviteeEmail")
        return AccountMemberDTO(
            membershipId = membership.id.toString(),
            accountId = membership.accountId.toString(),
            userId = membership.userId?.toString(),
            email = email,
            displayName = user?.displayName?.trim()?.takeIf { it.isNotEmpty() },
            avatarUrl = user?.avatarUrl?.trim()?.takeIf { it.isNotEmpty() },
            role = membership.accountRole,
            status = membership.membershipStatus,
            joinedAt = membership.joinedAt?.toString(),
            invitedAt = membership.invitedAt?.toString(),
            removedAt = membership.removedAt?.toString(),
            isDefault = membership.isDefault,
            isCurrentUser = membership.userId?.toString() == currentUserId,
        )
    }
}
