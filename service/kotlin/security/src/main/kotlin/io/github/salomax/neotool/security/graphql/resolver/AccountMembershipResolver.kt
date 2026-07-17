package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.security.domain.AccountMembership
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.graphql.dto.AcceptInvitationPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.AccountMemberDTO
import io.github.salomax.neotool.security.graphql.mapper.AccountMembershipMapper
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.management.membership.AccountMembershipService
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class AccountMembershipResolver(
    private val accountMembershipService: AccountMembershipService,
    private val accountMembershipRepository: AccountMembershipRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val mapper: AccountMembershipMapper,
) {
    fun accountMembers(
        accountId: String,
        principal: RequestPrincipal,
    ): List<AccountMemberDTO> {
        val actorUserId = requireNotNull(principal.userId) { "User ID required" }
        val accountUuid = UUID.fromString(accountId)
        requireActiveMembership(accountUuid, actorUserId)

        val memberships = accountMembershipRepository.findByAccountId(accountUuid)
        val usersById =
            userRepository.findByIdIn(memberships.mapNotNull { it.userId }.distinct())
                .associateBy { requireNotNull(it.id) }

        return memberships
            .filter {
                it.membershipStatus == MembershipStatus.ACTIVE ||
                    it.membershipStatus == MembershipStatus.PENDING
            }
            .sortedWith(
                compareByDescending<io.github.salomax.neotool.security.model.account.AccountMembershipEntity> {
                    it.membershipStatus == MembershipStatus.ACTIVE
                }.thenByDescending { it.invitedAt ?: it.joinedAt ?: it.removedAt },
            ).map { membership ->
                val user = membership.userId?.let { usersById[it] }
                mapper.toDto(membership, user, actorUserId.toString())
            }
    }

    fun inviteMember(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): AccountMemberDTO {
        val actorUserId = requireNotNull(principal.userId) { "User ID required" }
        val accountId = UUID.fromString(input["accountId"] as? String ?: error("accountId is required"))
        val email = input["email"] as? String ?: error("email is required")
        val role = AccountRole.valueOf((input["role"] as? String ?: AccountRole.MEMBER.name).uppercase())

        val result =
            accountMembershipService.invite(
                AccountMembership.InviteMemberCommand(
                    accountId = accountId,
                    inviterUserId = actorUserId,
                    inviteeEmail = email,
                    role = role,
                ),
            )

        val membership =
            accountMembershipRepository.findById(result.membershipId)
                .orElseThrow { ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "email") }
        val user = membership.userId?.let { userRepository.findById(it).orElse(null) }

        return mapper.toDto(membership, user, actorUserId.toString())
    }

    fun cancelInvitation(
        accountId: String,
        membershipId: String,
        principal: RequestPrincipal,
    ): Boolean {
        val actorUserId = requireNotNull(principal.userId) { "User ID required" }
        accountMembershipService.cancelInvitation(
            AccountMembership.CancelInvitationCommand(
                accountId = UUID.fromString(accountId),
                actorUserId = actorUserId,
                membershipId = UUID.fromString(membershipId),
            ),
        )
        return true
    }

    fun acceptInvitation(
        invitationToken: String,
        principal: RequestPrincipal,
    ): AcceptInvitationPayloadDTO {
        val actorUserId = requireNotNull(principal.userId) { "User ID required" }
        val result =
            accountMembershipService.acceptInvitation(
                AccountMembership.AcceptInvitationCommand(
                    invitationToken = invitationToken,
                    actorUserId = actorUserId,
                ),
            )
        val account =
            accountRepository.findById(result.accountId)
                .orElseThrow { ValidationException(SecurityErrorCode.ACCOUNT_NOT_FOUND, field = "accountId") }

        return AcceptInvitationPayloadDTO(
            membershipId = result.membershipId.toString(),
            accountId = result.accountId.toString(),
            accountName = account.accountName,
        )
    }

    fun declineInvitation(
        invitationToken: String,
        principal: RequestPrincipal,
    ): Boolean {
        val actorUserId = requireNotNull(principal.userId) { "User ID required" }
        accountMembershipService.declineInvitation(
            AccountMembership.DeclineInvitationCommand(
                invitationToken = invitationToken,
                actorUserId = actorUserId,
            ),
        )
        return true
    }

    fun removeMember(
        accountId: String,
        membershipId: String,
        principal: RequestPrincipal,
    ): Boolean {
        val actorUserId = requireNotNull(principal.userId) { "User ID required" }
        accountMembershipService.removeMember(
            AccountMembership.RemoveMemberCommand(
                accountId = UUID.fromString(accountId),
                actorUserId = actorUserId,
                targetMembershipId = UUID.fromString(membershipId),
            ),
        )
        return true
    }

    fun changeMemberRole(
        accountId: String,
        membershipId: String,
        role: String,
        principal: RequestPrincipal,
    ): AccountMemberDTO {
        val actorUserId = requireNotNull(principal.userId) { "User ID required" }
        val accountUuid = UUID.fromString(accountId)
        val membershipUuid = UUID.fromString(membershipId)
        accountMembershipService.changeMemberRole(
            AccountMembership.ChangeMemberRoleCommand(
                accountId = accountUuid,
                actorUserId = actorUserId,
                targetMembershipId = membershipUuid,
                newRole = AccountRole.valueOf(role.uppercase()),
            ),
        )
        val membership =
            accountMembershipRepository.findById(membershipUuid)
                .orElseThrow {
                    ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "membershipId")
                }
        val userId =
            membership.userId
                ?: throw ValidationException(SecurityErrorCode.USER_NOT_FOUND, field = "membershipId")
        val user =
            userRepository.findById(userId)
                .orElseThrow { ValidationException(SecurityErrorCode.USER_NOT_FOUND, field = "membershipId") }
        return mapper.toDto(membership, user, actorUserId.toString())
    }

    fun leaveAccount(
        accountId: String,
        principal: RequestPrincipal,
    ): Boolean {
        val actorUserId = requireNotNull(principal.userId) { "User ID required" }
        accountMembershipService.leaveAccount(
            AccountMembership.LeaveAccountCommand(
                accountId = UUID.fromString(accountId),
                actorUserId = actorUserId,
            ),
        )
        return true
    }

    private fun requireActiveMembership(
        accountId: UUID,
        actorUserId: UUID,
    ) {
        val membership =
            accountMembershipRepository.findOneByAccountIdAndUserId(accountId, actorUserId)
                .orElseThrow { ValidationException(SecurityErrorCode.NOT_ACCOUNT_MEMBER, field = "accountId") }
        if (membership.membershipStatus != MembershipStatus.ACTIVE) {
            throw ValidationException(SecurityErrorCode.NOT_ACCOUNT_MEMBER, field = "accountId")
        }
    }
}
