package io.github.salomax.neotool.security.graphql.dto

import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.MembershipStatus

data class AccountMemberDTO(
    val membershipId: String,
    val accountId: String,
    val userId: String?,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
    val role: AccountRole,
    val status: MembershipStatus,
    val joinedAt: String?,
    val invitedAt: String?,
    val removedAt: String?,
    val isDefault: Boolean,
    val isCurrentUser: Boolean,
)

data class AcceptInvitationPayloadDTO(
    val membershipId: String,
    val accountId: String,
    val accountName: String,
)
