package io.github.salomax.neotool.security.service.management.membership

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.security.domain.AccountMembership
import io.github.salomax.neotool.security.domain.preferences.Language
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.email.EmailService
import io.github.salomax.neotool.security.service.preferences.UserPreferencesService
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
private const val DEFAULT_INVITATION_LOCALE = "pt"

/**
 * Service for account membership and invitation operations (FR-4).
 * Issues and cancels invitations; uses MembershipPolicyEngine for guards.
 */
@TraceExecution
@Singleton
open class AccountMembershipService(
    private val accountRepository: AccountRepository,
    private val accountMembershipRepository: AccountMembershipRepository,
    private val userRepository: UserRepository,
    private val membershipPolicyEngine: MembershipPolicyEngine,
    private val emailService: EmailService,
    private val userPreferencesService: UserPreferencesService,
) {
    private val logger = KotlinLogging.logger {}
    private val secureRandom = SecureRandom()

    /**
     * Issue an invitation by email (FR-4.1).
     * Creates a PENDING membership with secure token and 7-day expiry.
     * The invitee does not need an existing platform user — invite-before-signup stores
     * the email and binds the user later at registration / acceptance.
     *
     * @throws ValidationException with NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT,
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
                    parameters =
                        policyResult.message?.let { mapOf("reason" to it) }
                            ?: emptyMap(),
                )
        }

        val normalizedEmail = command.inviteeEmail.trim().lowercase()

        val invitee =
            userRepository.findByEmail(normalizedEmail)
                ?: userRepository.findByEmail(command.inviteeEmail.trim())

        if (invitee != null) {
            val activeElsewhere =
                accountMembershipRepository
                    .findByUserIdAndMembershipStatus(invitee.id!!, MembershipStatus.ACTIVE)
                    .any { it.accountId != command.accountId }
            if (activeElsewhere) {
                throw ValidationException(SecurityErrorCode.ACCOUNT_ALREADY_MEMBER, field = "inviteeEmail")
            }

            val existing =
                accountMembershipRepository
                    .findOneByAccountIdAndUserId(command.accountId, invitee.id!!)
                    .orElse(null)
            if (existing != null &&
                (
                    existing.membershipStatus == MembershipStatus.ACTIVE ||
                        existing.membershipStatus == MembershipStatus.PENDING
                )
            ) {
                throw ValidationException(SecurityErrorCode.ALREADY_MEMBER, field = "inviteeEmail")
            }
            if (existing != null && existing.membershipStatus == MembershipStatus.REMOVED) {
                return reactivateInvitation(
                    existing = existing,
                    inviteeUserId = invitee.id,
                    inviteeEmail = normalizedEmail,
                    role = command.role,
                    inviterUserId = command.inviterUserId,
                    accountName = account.accountName,
                )
            }
        }

        val existingByEmail =
            accountMembershipRepository
                .findOneByAccountIdAndInviteeEmail(command.accountId, normalizedEmail)
                .orElse(null)
        if (existingByEmail != null &&
            (
                existingByEmail.membershipStatus == MembershipStatus.ACTIVE ||
                    existingByEmail.membershipStatus == MembershipStatus.PENDING
            )
        ) {
            throw ValidationException(SecurityErrorCode.ALREADY_MEMBER, field = "inviteeEmail")
        }
        if (existingByEmail != null && existingByEmail.membershipStatus == MembershipStatus.REMOVED) {
            return reactivateInvitation(
                existing = existingByEmail,
                inviteeUserId = invitee?.id ?: existingByEmail.userId,
                inviteeEmail = normalizedEmail,
                role = command.role,
                inviterUserId = command.inviterUserId,
                accountName = account.accountName,
            )
        }

        if (account.accountType == AccountType.PERSONAL) {
            throw ValidationException(SecurityErrorCode.MEMBER_LIMIT_REACHED, field = "accountId")
        }
        val statuses = listOf(MembershipStatus.ACTIVE, MembershipStatus.PENDING)
        val currentCount =
            accountMembershipRepository
                .countByAccountIdAndMembershipStatusIn(command.accountId, statuses)
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
                userId = invitee?.id,
                accountRole = command.role,
                membershipStatus = MembershipStatus.PENDING,
                joinedAt = null,
                isDefault = false,
                invitedBy = command.inviterUserId,
                invitedAt = now,
                invitationToken = token,
                invitationExpiresAt = expiresAt,
                inviteeEmail = normalizedEmail,
            )
        val saved = accountMembershipRepository.save(membership)

        sendInvitationEmail(
            inviteeEmail = normalizedEmail,
            inviterUserId = command.inviterUserId,
            accountName = account.accountName,
            token = token,
            role = command.role,
        )

        logger.info {
            "Invitation issued: account=${command.accountId}, invitee=$normalizedEmail, role=${command.role}"
        }
        return AccountMembership.InvitationResult(
            membershipId = saved.id!!,
            invitationToken = token,
            expiresAt = expiresAt,
            inviteeEmail = normalizedEmail,
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
                .orElseThrow {
                    ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "membershipId")
                }
        if (targetMembership.accountId != command.accountId) {
            throw ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "membershipId")
        }

        if (targetMembership.membershipStatus != MembershipStatus.PENDING) {
            throw ValidationException(SecurityErrorCode.NOT_PENDING_INVITATION, field = "membershipId")
        }

        val actorMembership =
            accountMembershipRepository.findOneByAccountIdAndUserId(command.accountId, command.actorUserId)
                .orElse(null)
        val actor =
            actorMembership?.let { ActorMembershipContext(it.accountRole, it.membershipStatus) }
        val target =
            TargetMemberContext(
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
                    parameters =
                        policyResult.message?.let { mapOf("reason" to it) }
                            ?: emptyMap(),
                )
        }

        accountMembershipRepository.deleteById(targetMembership.id!!)
        logger.info { "Invitation cancelled: membershipId=${command.membershipId}, account=${command.accountId}" }
    }

    /**
     * Accept an invitation as the invited user (FR-4.2).
     * Sets membership to ACTIVE and joined_at; only the invitee may accept.
     *
     * @throws ValidationException with INVITATION_INVALID (token not found or wrong user),
     *   INVITATION_EXPIRED, or NOT_PENDING_INVITATION
     */
    @Transactional
    open fun acceptInvitation(
        command: AccountMembership.AcceptInvitationCommand,
    ): AccountMembership.AcceptInvitationResult {
        val membership =
            accountMembershipRepository
                .findOneByInvitationToken(command.invitationToken.trim())
                .orElseThrow {
                    ValidationException(SecurityErrorCode.INVITATION_INVALID, field = "invitationToken")
                }

        val actor =
            userRepository.findById(command.actorUserId).orElseThrow {
                ValidationException(SecurityErrorCode.USER_NOT_FOUND, field = "actorUserId")
            }
        val actorEmail = actor.email.trim().lowercase()
        val inviteeEmail = membership.inviteeEmail?.trim()?.lowercase()
        val isInvitee =
            membership.userId == command.actorUserId ||
                (membership.userId == null && inviteeEmail != null && inviteeEmail == actorEmail)
        if (!isInvitee) {
            throw ValidationException(SecurityErrorCode.INVITATION_INVALID, field = "actorUserId")
        }
        if (membership.invitationExpiresAt == null || membership.invitationExpiresAt!! < Instant.now()) {
            throw ValidationException(SecurityErrorCode.INVITATION_EXPIRED, field = "invitationToken")
        }
        if (membership.membershipStatus != MembershipStatus.PENDING) {
            throw ValidationException(SecurityErrorCode.NOT_PENDING_INVITATION, field = "invitationToken")
        }

        val activeMemberships =
            accountMembershipRepository.findByUserIdAndMembershipStatus(command.actorUserId, MembershipStatus.ACTIVE)
        if (activeMemberships.any { it.accountId != membership.accountId }) {
            throw ValidationException(SecurityErrorCode.ACCOUNT_ALREADY_MEMBER, field = "actorUserId")
        }

        membership.userId = command.actorUserId
        membership.inviteeEmail = inviteeEmail ?: actorEmail
        membership.membershipStatus = MembershipStatus.ACTIVE
        membership.joinedAt = Instant.now()
        membership.invitationToken = null
        membership.invitationExpiresAt = null
        if (activeMemberships.isEmpty()) {
            membership.isDefault = true
        }
        accountMembershipRepository.save(membership)

        logger.info {
            "Invitation accepted: membershipId=${membership.id}, accountId=${membership.accountId}, " +
                "userId=${command.actorUserId}"
        }
        return AccountMembership.AcceptInvitationResult(
            membershipId = membership.id!!,
            accountId = membership.accountId,
        )
    }

    /**
     * Decline an invitation as the invited user (FR-4.3).
     * Deletes the PENDING membership; only the invitee may decline.
     *
     * @throws ValidationException with INVITATION_INVALID (token not found or wrong user)
     *   or NOT_PENDING_INVITATION
     */
    @Transactional
    open fun declineInvitation(command: AccountMembership.DeclineInvitationCommand) {
        val membership =
            accountMembershipRepository.findOneByInvitationToken(command.invitationToken.trim())
                .orElseThrow {
                    ValidationException(SecurityErrorCode.INVITATION_INVALID, field = "invitationToken")
                }
        val actor =
            userRepository.findById(command.actorUserId).orElseThrow {
                ValidationException(SecurityErrorCode.USER_NOT_FOUND, field = "actorUserId")
            }
        val actorEmail = actor.email.trim().lowercase()
        val inviteeEmail = membership.inviteeEmail?.trim()?.lowercase()
        val isInvitee =
            membership.userId == command.actorUserId ||
                (membership.userId == null && inviteeEmail != null && inviteeEmail == actorEmail)
        if (!isInvitee) {
            throw ValidationException(SecurityErrorCode.INVITATION_INVALID, field = "actorUserId")
        }
        if (membership.membershipStatus != MembershipStatus.PENDING) {
            throw ValidationException(SecurityErrorCode.NOT_PENDING_INVITATION, field = "invitationToken")
        }

        accountMembershipRepository.deleteById(membership.id!!)
        logger.info {
            "Invitation declined: membershipId=${membership.id}, accountId=${membership.accountId}, " +
                "userId=${command.actorUserId}"
        }
    }

    /**
     * Change a member's role (FR-6.2). Only OWNER can change roles; target must be ACTIVE.
     *
     * @throws ValidationException with NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT,
     *   SELF_OPERATION_FORBIDDEN, TARGET_NOT_ELIGIBLE
     */
    @Transactional
    open fun changeMemberRole(command: AccountMembership.ChangeMemberRoleCommand) {
        val account =
            accountRepository.findById(command.accountId)
                .orElseThrow {
                    ValidationException(SecurityErrorCode.ACCOUNT_NOT_FOUND, field = "accountId")
                }
        if (account.accountStatus != AccountStatus.ACTIVE) {
            throw ValidationException(SecurityErrorCode.ACCOUNT_INACTIVE, field = "accountId")
        }

        val actorMembership =
            accountMembershipRepository.findOneByAccountIdAndUserId(command.accountId, command.actorUserId)
                .orElse(null)
        val actor =
            actorMembership?.let { ActorMembershipContext(it.accountRole, it.membershipStatus) }

        val targetMembership =
            accountMembershipRepository.findById(command.targetMembershipId)
                .orElseThrow {
                    ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "targetMembershipId")
                }
        if (targetMembership.accountId != command.accountId) {
            throw ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "targetMembershipId")
        }
        if (targetMembership.membershipStatus != MembershipStatus.ACTIVE) {
            throw ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "targetMembershipId")
        }

        val target =
            TargetMemberContext(
                role = targetMembership.accountRole,
                status = targetMembership.membershipStatus,
                isSelf = targetMembership.userId == command.actorUserId,
            )
        val policyResult =
            membershipPolicyEngine.evaluate(
                MembershipOperation.CHANGE_ROLE,
                actor,
                target = target,
                newRole = command.newRole,
            )
        when (policyResult) {
            is MembershipPolicyResult.Allowed -> { /* proceed */ }
            is MembershipPolicyResult.Denied ->
                throw ValidationException(
                    errorCode = policyResult.errorCode,
                    field = null,
                    parameters =
                        policyResult.message?.let { mapOf("reason" to it) }
                            ?: emptyMap(),
                )
        }

        targetMembership.accountRole = command.newRole
        accountMembershipRepository.save(targetMembership)
        logger.info {
            "Member role changed: accountId=${command.accountId}, targetMembershipId=${command.targetMembershipId}, " +
                "newRole=${command.newRole}"
        }
    }

    /**
     * Remove a member from the account (FR-6.3). OWNER or ADMIN; sets status to REMOVED.
     *
     * @throws ValidationException with NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT,
     *   SELF_OPERATION_FORBIDDEN, TARGET_ROLE_TOO_HIGH, TARGET_NOT_ELIGIBLE
     */
    @Transactional
    open fun removeMember(command: AccountMembership.RemoveMemberCommand) {
        val account =
            accountRepository.findById(command.accountId)
                .orElseThrow {
                    ValidationException(SecurityErrorCode.ACCOUNT_NOT_FOUND, field = "accountId")
                }
        if (account.accountStatus != AccountStatus.ACTIVE) {
            throw ValidationException(SecurityErrorCode.ACCOUNT_INACTIVE, field = "accountId")
        }

        val actorMembership =
            accountMembershipRepository.findOneByAccountIdAndUserId(command.accountId, command.actorUserId)
                .orElse(null)
        val actor =
            actorMembership?.let { ActorMembershipContext(it.accountRole, it.membershipStatus) }

        val targetMembership =
            accountMembershipRepository.findById(command.targetMembershipId)
                .orElseThrow {
                    ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "targetMembershipId")
                }
        if (targetMembership.accountId != command.accountId) {
            throw ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "targetMembershipId")
        }
        if (targetMembership.membershipStatus != MembershipStatus.ACTIVE) {
            throw ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "targetMembershipId")
        }

        val target =
            TargetMemberContext(
                role = targetMembership.accountRole,
                status = targetMembership.membershipStatus,
                isSelf = targetMembership.userId == command.actorUserId,
            )
        val policyResult =
            membershipPolicyEngine.evaluate(
                MembershipOperation.REMOVE_MEMBER,
                actor,
                target = target,
            )
        when (policyResult) {
            is MembershipPolicyResult.Allowed -> { /* proceed */ }
            is MembershipPolicyResult.Denied ->
                throw ValidationException(
                    errorCode = policyResult.errorCode,
                    field = null,
                    parameters =
                        policyResult.message?.let { mapOf("reason" to it) }
                            ?: emptyMap(),
                )
        }

        val now = Instant.now()
        targetMembership.membershipStatus = MembershipStatus.REMOVED
        targetMembership.isDefault = false
        targetMembership.removedAt = now
        targetMembership.removedBy = command.actorUserId
        accountMembershipRepository.save(targetMembership)
        logger.info {
            "Member removed: accountId=${command.accountId}, " +
                "targetMembershipId=${command.targetMembershipId}"
        }
    }

    /**
     * Leave the account (FR-6.4). Sole OWNER cannot leave without transferring ownership first.
     *
     * @throws ValidationException with NOT_ACCOUNT_MEMBER, SOLE_OWNER_CANNOT_LEAVE
     */
    @Transactional
    open fun leaveAccount(command: AccountMembership.LeaveAccountCommand) {
        val account =
            accountRepository.findById(command.accountId)
                .orElseThrow {
                    ValidationException(SecurityErrorCode.ACCOUNT_NOT_FOUND, field = "accountId")
                }
        if (account.accountStatus != AccountStatus.ACTIVE) {
            throw ValidationException(SecurityErrorCode.ACCOUNT_INACTIVE, field = "accountId")
        }

        val actorMembership =
            accountMembershipRepository.findOneByAccountIdAndUserId(command.accountId, command.actorUserId)
                .orElseThrow {
                    ValidationException(SecurityErrorCode.NOT_ACCOUNT_MEMBER, field = "actorUserId")
                }
        if (actorMembership.membershipStatus != MembershipStatus.ACTIVE) {
            throw ValidationException(SecurityErrorCode.NOT_ACCOUNT_MEMBER, field = "actorUserId")
        }

        val actor =
            ActorMembershipContext(actorMembership.accountRole, actorMembership.membershipStatus)
        val activeOwners =
            accountMembershipRepository
                .findByAccountId(command.accountId)
                .count { it.membershipStatus == MembershipStatus.ACTIVE && it.accountRole == AccountRole.OWNER }
        val isSoleOwner = actor.role == AccountRole.OWNER && activeOwners == 1

        val policyResult =
            membershipPolicyEngine.evaluate(
                MembershipOperation.LEAVE,
                actor,
                isSoleOwner = isSoleOwner,
            )
        when (policyResult) {
            is MembershipPolicyResult.Allowed -> { /* proceed */ }
            is MembershipPolicyResult.Denied ->
                throw ValidationException(
                    errorCode = policyResult.errorCode,
                    field = null,
                    parameters =
                        policyResult.message?.let { mapOf("reason" to it) }
                            ?: emptyMap(),
                )
        }

        val now = Instant.now()
        actorMembership.membershipStatus = MembershipStatus.REMOVED
        actorMembership.isDefault = false
        actorMembership.removedAt = now
        actorMembership.removedBy = null
        accountMembershipRepository.save(actorMembership)
        logger.info { "Member left account: accountId=${command.accountId}, userId=${command.actorUserId}" }
    }

    /**
     * Transfer account ownership to another member (FR-6.5). Current OWNER becomes ADMIN.
     *
     * @throws ValidationException with NOT_ACCOUNT_MEMBER, ACCOUNT_ROLE_INSUFFICIENT, TARGET_NOT_ELIGIBLE
     */
    @Transactional
    open fun transferOwnership(command: AccountMembership.TransferOwnershipCommand) {
        val account =
            accountRepository.findById(command.accountId)
                .orElseThrow { ValidationException(SecurityErrorCode.ACCOUNT_NOT_FOUND, field = "accountId") }
        if (account.accountStatus != AccountStatus.ACTIVE) {
            throw ValidationException(SecurityErrorCode.ACCOUNT_INACTIVE, field = "accountId")
        }

        val actorMembership =
            accountMembershipRepository.findOneByAccountIdAndUserId(command.accountId, command.actorUserId)
                .orElse(null)
        val actor = actorMembership?.let { ActorMembershipContext(it.accountRole, it.membershipStatus) }

        val newOwnerMembership =
            accountMembershipRepository.findOneByAccountIdAndUserId(command.accountId, command.newOwnerUserId)
                .orElseThrow { ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "newOwnerUserId") }
        if (newOwnerMembership.membershipStatus != MembershipStatus.ACTIVE) {
            throw ValidationException(SecurityErrorCode.TARGET_NOT_ELIGIBLE, field = "newOwnerUserId")
        }

        val newOwner = NewOwnerContext(newOwnerMembership.accountRole, newOwnerMembership.membershipStatus)
        val policyResult =
            membershipPolicyEngine.evaluate(
                MembershipOperation.TRANSFER_OWNERSHIP,
                actor,
                newOwner = newOwner,
            )
        when (policyResult) {
            is MembershipPolicyResult.Allowed -> { /* proceed */ }
            is MembershipPolicyResult.Denied ->
                throw ValidationException(
                    errorCode = policyResult.errorCode,
                    field = null,
                    parameters =
                        policyResult.message?.let { mapOf("reason" to it) }
                            ?: emptyMap(),
                )
        }

        account.ownerUserId = command.newOwnerUserId
        accountRepository.update(account)
        actorMembership!!.accountRole = AccountRole.ADMIN
        accountMembershipRepository.save(actorMembership)
        newOwnerMembership.accountRole = AccountRole.OWNER
        accountMembershipRepository.save(newOwnerMembership)
        logger.info {
            "Ownership transferred: accountId=${command.accountId}, newOwnerUserId=${command.newOwnerUserId}"
        }
    }

    private fun reactivateInvitation(
        existing: AccountMembershipEntity,
        inviteeUserId: UUID?,
        inviteeEmail: String,
        role: AccountRole,
        inviterUserId: UUID,
        accountName: String,
    ): AccountMembership.InvitationResult {
        val statuses = listOf(MembershipStatus.ACTIVE, MembershipStatus.PENDING)
        val currentCount =
            accountMembershipRepository
                .countByAccountIdAndMembershipStatusIn(existing.accountId, statuses)
        val account =
            accountRepository.findById(existing.accountId)
                .orElseThrow { ValidationException(SecurityErrorCode.ACCOUNT_NOT_FOUND, field = "accountId") }
        if (account.accountType == AccountType.PERSONAL) {
            throw ValidationException(SecurityErrorCode.MEMBER_LIMIT_REACHED, field = "accountId")
        }
        val maxMembers = maxMembersForAccountType(account.accountType)
        if (currentCount >= maxMembers) {
            throw ValidationException(SecurityErrorCode.MEMBER_LIMIT_REACHED, field = "accountId")
        }

        val now = Instant.now()
        val expiresAt = now.plusSeconds(INVITATION_EXPIRY_DAYS * 24 * 60 * 60)
        val token = generateUniqueInvitationToken()

        existing.userId = inviteeUserId
        existing.accountRole = role
        existing.membershipStatus = MembershipStatus.PENDING
        existing.joinedAt = null
        existing.isDefault = false
        existing.invitedBy = inviterUserId
        existing.invitedAt = now
        existing.invitationToken = token
        existing.invitationExpiresAt = expiresAt
        existing.inviteeEmail = inviteeEmail
        existing.removedAt = null
        existing.removedBy = null
        val saved = accountMembershipRepository.save(existing)

        sendInvitationEmail(
            inviteeEmail = inviteeEmail,
            inviterUserId = inviterUserId,
            accountName = accountName,
            token = token,
            role = role,
        )

        logger.info {
            "Invitation reissued for removed membership: " +
                "membershipId=${saved.id}, account=${existing.accountId}, invitee=$inviteeEmail"
        }
        return AccountMembership.InvitationResult(
            membershipId = saved.id!!,
            invitationToken = token,
            expiresAt = expiresAt,
            inviteeEmail = inviteeEmail,
            role = role,
        )
    }

    private fun sendInvitationEmail(
        inviteeEmail: String,
        inviterUserId: UUID,
        accountName: String,
        token: String,
        role: AccountRole,
    ) {
        try {
            val inviter = userRepository.findById(inviterUserId).orElse(null)
            val inviterDisplayName = inviter?.displayName?.takeIf { it.isNotBlank() } ?: inviter?.email
            val locale = resolveInvitationLocale(inviterUserId)
            val acceptLink = emailService.buildInvitationAcceptUrl(token)
            emailService.sendAccountInvitationEmail(
                to = inviteeEmail,
                inviterDisplayName = inviterDisplayName,
                accountName = accountName,
                acceptLink = acceptLink,
                expiryDays = INVITATION_EXPIRY_DAYS,
                role = localizedRoleLabel(role, locale),
                locale = locale,
            )
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to send invitation email to $inviteeEmail; membership already created"
            }
        }
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

    /**
     * Invitation emails use the inviter's language preference (app default is Portuguese).
     * The invitee typically has no preferences yet.
     */
    private fun resolveInvitationLocale(inviterUserId: UUID): String =
        when (userPreferencesService.get(inviterUserId).language) {
            Language.ENGLISH -> "en"
            Language.PORTUGUESE -> DEFAULT_INVITATION_LOCALE
        }

    private fun localizedRoleLabel(
        role: AccountRole,
        locale: String,
    ): String {
        val isPortuguese = locale.lowercase().startsWith("pt")
        return when (role) {
            AccountRole.OWNER -> if (isPortuguese) "Proprietário" else "Owner"
            AccountRole.ADMIN -> if (isPortuguese) "Administrador" else "Admin"
            AccountRole.MEMBER -> if (isPortuguese) "Membro" else "Member"
            AccountRole.VIEWER -> if (isPortuguese) "Visualizador" else "Viewer"
        }
    }
}
