package io.github.salomax.neotool.security.service.management.membership

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.security.domain.AccountMembership
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.UserRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/** Max members per account type (FR-1.1). */
private fun maxMembersForAccountType(accountType: AccountType): Int =
    when (accountType) {
        AccountType.PERSONAL -> 1
        AccountType.FAMILY -> 10
        AccountType.BUSINESS -> 50
    }

private const val INVITATION_EXPIRY_DAYS = 7L
private const val TOKEN_BYTES = 32
private const val TOKEN_UNIQUE_RETRIES = 5

/**
 * Service for account membership and invitation operations (FR-4).
 * Issues and cancels invitations; uses MembershipPolicyEngine for guards.
 */
@Singleton
open class AccountMembershipService(
    private val accountRepository: AccountRepository,
    private val accountMembershipRepository: AccountMembershipRepository,
    private val userRepository: UserRepository,
    private val membershipPolicyEngine: MembershipPolicyEngine,
) {
    private val logger = KotlinLogging.logger {}
    private val secureRandom = SecureRandom()

    /**
     * Issue an invitation to an existing user by email (FR-4.1).
     * Creates a PENDING membership with secure token and 7-day expiry.
     *
     * @throws ValidationException with NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT, USER_NOT_FOUND,
     *   ALREADY_MEMBER, MEMBER_LIMIT_REACHED
     */
    @Transactional
    open fun invite(command: AccountMembership.InviteMemberCommand): AccountMembership.InvitationResult {
        val account =
            accountRepository.findById(command.accountId)
                .orElseThrow { ValidationException(SecurityErrorCode.ACCOUNT_NOT_FOUND, field = "accountId") }
        if (account.accountStatus != AccountStatus.ACTIVE) {
            throw ValidationException(SecurityErrorCode.ACCOUNT_INACTIVE, field = "accountId")
        }

        val inviterMembership =
            accountMembershipRepository.findOneByAccountIdAndUserId(command.accountId, command.inviterUserId)
                .orElse(null)
        val actor =
            inviterMembership?.let { ActorMembershipContext(it.accountRole, it.membershipStatus) }
        val policyResult = membershipPolicyEngine.evaluate(MembershipOperation.INVITE, actor)
        when (policyResult) {
            is MembershipPolicyResult.Allowed -> { /* proceed */ }
            is MembershipPolicyResult.Denied ->
                throw ValidationException(
                    errorCode = policyResult.errorCode,
                    field = null,
                    parameters = policyResult.message?.let { mapOf("reason" to it) } ?: emptyMap(),
                )
        }

        val invitee =
            userRepository.findByEmail(command.inviteeEmail.trim())
                ?: throw ValidationException(SecurityErrorCode.USER_NOT_FOUND, field = "inviteeEmail")

        val existing =
            accountMembershipRepository.findOneByAccountIdAndUserId(command.accountId, invitee.id!!).orElse(null)
        if (existing != null &&
            (existing.membershipStatus == MembershipStatus.ACTIVE || existing.membershipStatus == MembershipStatus.PENDING)
        ) {
            throw ValidationException(SecurityErrorCode.ALREADY_MEMBER, field = "inviteeEmail")
        }

        if (account.accountType == AccountType.PERSONAL) {
            throw ValidationException(SecurityErrorCode.MEMBER_LIMIT_REACHED, field = "accountId")
        }
        val statuses = listOf(MembershipStatus.ACTIVE, MembershipStatus.PENDING)
        val currentCount =
            accountMembershipRepository.countByAccountIdAndMembershipStatusIn(command.accountId, statuses)
        val maxMembers = maxMembersForAccountType(account.accountType)
        if (currentCount >= maxMembers) {
            throw ValidationException(SecurityErrorCode.MEMBER_LIMIT_REACHED, field = "accountId")
        }

        val now = Instant.now()
        val expiresAt = now.plusSeconds(INVITATION_EXPIRY_DAYS * 24 * 60 * 60)
        val token = generateUniqueInvitationToken()

        val membership =
            AccountMembershipEntity(
                accountId = command.accountId,
                userId = invitee.id!!,
                accountRole = command.role,
                membershipStatus = MembershipStatus.PENDING,
                joinedAt = null,
                isDefault = false,
                invitedBy = command.inviterUserId,
                invitedAt = now,
                invitationToken = token,
                invitationExpiresAt = expiresAt,
            )
        val saved = accountMembershipRepository.save(membership)

        logger.info { "Invitation issued: account=${command.accountId}, invitee=${command.inviteeEmail}, role=${command.role}" }
        return AccountMembership.InvitationResult(
            membershipId = saved.id!!,
            invitationToken = token,
            expiresAt = expiresAt,
            inviteeEmail = command.inviteeEmail.trim(),
            role = command.role,
        )
    }

    /**
     * Cancel a pending invitation (FR-4.4).
     * Deletes the PENDING membership; only OWNER/ADMIN can cancel.
     *
     * @throws ValidationException with NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT,
     *   NOT_PENDING_INVITATION, or ACCOUNT_NOT_FOUND
     */
    @Transactional
    open fun cancelInvitation(command: AccountMembership.CancelInvitationCommand) {
        val targetMembership =
            accountMembershipRepository.findById(command.membershipId)
                .orElseThrow { ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "membershipId") }
        if (targetMembership.accountId != command.accountId) {
            throw ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "membershipId")
        }

        if (targetMembership.membershipStatus != MembershipStatus.PENDING) {
            throw ValidationException(SecurityErrorCode.NOT_PENDING_INVITATION, field = "membershipId")
        }

        val actorMembership =
            accountMembershipRepository.findOneByAccountIdAndUserId(command.accountId, command.actorUserId)
                .orElse(null)
        val actor = actorMembership?.let { ActorMembershipContext(it.accountRole, it.membershipStatus) }
        val target = TargetMemberContext(
            role = targetMembership.accountRole,
            status = targetMembership.membershipStatus,
            isSelf = false,
        )
        val policyResult =
            membershipPolicyEngine.evaluate(
                MembershipOperation.CANCEL_INVITATION,
                actor,
                target = target,
            )
        when (policyResult) {
            is MembershipPolicyResult.Allowed -> { /* proceed */ }
            is MembershipPolicyResult.Denied ->
                throw ValidationException(
                    errorCode = policyResult.errorCode,
                    field = null,
                    parameters = policyResult.message?.let { mapOf("reason" to it) } ?: emptyMap(),
                )
        }

        accountMembershipRepository.deleteById(targetMembership.id!!)
        logger.info { "Invitation cancelled: membershipId=${command.membershipId}, account=${command.accountId}" }
    }

    private fun generateSecureToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateUniqueInvitationToken(): String {
        repeat(TOKEN_UNIQUE_RETRIES) {
            val token = generateSecureToken()
            if (accountMembershipRepository.findOneByInvitationToken(token).isEmpty) {
                return token
            }
        }
        throw IllegalStateException("Could not generate unique invitation token after $TOKEN_UNIQUE_RETRIES attempts")
    }
}
