package io.github.salomax.neotool.security.domain

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.model.account.AccountRole
import java.time.Instant
import java.util.UUID

/**
 * Command and result DTOs for account membership and invitation operations (FR-4).
 */
object AccountMembership {

    private val INVITABLE_ROLES = setOf(AccountRole.ADMIN, AccountRole.MEMBER, AccountRole.VIEWER)
    private val EMAIL_REGEX = Regex("^[^@]+@[^@]+\\.[^@]+$")

    /**
     * Command to invite an existing user to an account by email (FR-4.1).
     *
     * @throws ValidationException if role not in ADMIN/MEMBER/VIEWER or email blank/invalid
     */
    data class InviteMemberCommand(
        val accountId: UUID,
        val inviterUserId: UUID,
        val inviteeEmail: String,
        val role: AccountRole,
    ) {
        init {
            if (inviteeEmail.isBlank()) {
                throw ValidationException(
                    errorCode = SecurityErrorCode.USER_EMAIL_REQUIRED,
                    field = "inviteeEmail",
                )
            }
            if (!EMAIL_REGEX.matches(inviteeEmail.trim())) {
                throw ValidationException(
                    errorCode = SecurityErrorCode.USER_EMAIL_INVALID,
                    field = "inviteeEmail",
                )
            }
            if (role !in INVITABLE_ROLES) {
                throw ValidationException(
                    errorCode = SecurityErrorCode.TARGET_NOT_ELIGIBLE,
                    field = "role",
                    parameters = mapOf("reason" to "Invitation role must be ADMIN, MEMBER, or VIEWER"),
                )
            }
        }
    }

    /**
     * Command to cancel a pending invitation (FR-4.4).
     */
    data class CancelInvitationCommand(
        val accountId: UUID,
        val actorUserId: UUID,
        val membershipId: UUID,
    )

    /**
     * Command for the invited user to accept an invitation (FR-4.2).
     * Only the user identified by [actorUserId] (the invitee) may accept.
     */
    data class AcceptInvitationCommand(
        val invitationToken: String,
        val actorUserId: UUID,
    ) {
        init {
            if (invitationToken.isBlank()) {
                throw ValidationException(
                    errorCode = SecurityErrorCode.INVITATION_INVALID,
                    field = "invitationToken",
                )
            }
        }
    }

    /**
     * Command for the invited user to decline an invitation (FR-4.3).
     * Only the user identified by [actorUserId] (the invitee) may decline.
     */
    data class DeclineInvitationCommand(
        val invitationToken: String,
        val actorUserId: UUID,
    ) {
        init {
            if (invitationToken.isBlank()) {
                throw ValidationException(
                    errorCode = SecurityErrorCode.INVITATION_INVALID,
                    field = "invitationToken",
                )
            }
        }
    }

    /**
     * Result of a successful invitation acceptance (FR-4.2).
     */
    data class AcceptInvitationResult(
        val membershipId: UUID,
        val accountId: UUID,
    )

    /**
     * Result of a successful invitation issuance.
     */
    data class InvitationResult(
        val membershipId: UUID,
        val invitationToken: String,
        val expiresAt: Instant,
        val inviteeEmail: String,
        val role: AccountRole,
    )
}
