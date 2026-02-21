package io.github.salomax.neotool.security.service.management.membership

import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.MembershipStatus
import jakarta.inject.Singleton

/**
 * Result of a membership policy evaluation.
 * Used by account/member services to enforce FR-3 and FR-6 rules.
 */
sealed class MembershipPolicyResult {
    data object Allowed : MembershipPolicyResult()

    data class Denied(
        val errorCode: SecurityErrorCode,
        val message: String? = null,
    ) : MembershipPolicyResult()
}

/**
 * Actor's membership context (or absent if not a member).
 * Callers load from DB and pass in; engine is stateless.
 */
data class ActorMembershipContext(
    val role: AccountRole,
    val status: MembershipStatus,
)

/**
 * Target member context for binary operations (change role, remove).
 */
data class TargetMemberContext(
    val role: AccountRole,
    val status: MembershipStatus,
    val isSelf: Boolean,
)

/**
 * New owner context for transfer ownership (must be ADMIN or MEMBER, ACTIVE).
 */
data class NewOwnerContext(
    val role: AccountRole,
    val status: MembershipStatus,
)

/**
 * Membership operations from FR-3 / FR-6 and permission matrix.
 */
enum class MembershipOperation {
    UPDATE_ACCOUNT,
    DELETE_ACCOUNT,
    INVITE,
    CANCEL_INVITATION,
    CHANGE_ROLE,
    REMOVE_MEMBER,
    LEAVE,
    TRANSFER_OWNERSHIP,
}

/**
 * Stateless policy engine for account membership rules (FR-3, FR-6).
 * Evaluates who can invite/remove/change roles, self-demotion and owner transfer constraints.
 * No DB access; callers supply membership context.
 */
@Singleton
class MembershipPolicyEngine {

    /**
     * Evaluate whether the actor can perform the operation.
     *
     * @param operation The operation to check
     * @param actor Nullable actor membership (null = not a member)
     * @param target Required for CHANGE_ROLE, REMOVE_MEMBER, CANCEL_INVITATION
     * @param newRole Required for CHANGE_ROLE (ADMIN, MEMBER, or VIEWER)
     * @param newOwner Required for TRANSFER_OWNERSHIP (candidate's membership)
     * @param isSoleOwner Required for LEAVE when actor is OWNER (true = cannot leave without transfer)
     */
    fun evaluate(
        operation: MembershipOperation,
        actor: ActorMembershipContext?,
        target: TargetMemberContext? = null,
        newRole: AccountRole? = null,
        newOwner: NewOwnerContext? = null,
        isSoleOwner: Boolean? = null,
    ): MembershipPolicyResult {
        return when (operation) {
            MembershipOperation.UPDATE_ACCOUNT,
            MembershipOperation.DELETE_ACCOUNT -> evaluateOwnerOnly(actor)
            MembershipOperation.INVITE -> evaluateInvite(actor)
            MembershipOperation.CANCEL_INVITATION -> evaluateCancelInvitation(actor, target)
            MembershipOperation.CHANGE_ROLE -> evaluateChangeRole(actor, target, newRole)
            MembershipOperation.REMOVE_MEMBER -> evaluateRemoveMember(actor, target)
            MembershipOperation.LEAVE -> evaluateLeave(actor, isSoleOwner)
            MembershipOperation.TRANSFER_OWNERSHIP -> evaluateTransferOwnership(actor, newOwner)
        }
    }

    private fun evaluateOwnerOnly(actor: ActorMembershipContext?): MembershipPolicyResult {
        if (actor == null || actor.status != MembershipStatus.ACTIVE) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER)
        }
        return if (actor.role == AccountRole.OWNER) {
            MembershipPolicyResult.Allowed
        } else {
            MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT)
        }
    }

    private fun evaluateInvite(actor: ActorMembershipContext?): MembershipPolicyResult {
        if (actor == null || actor.status != MembershipStatus.ACTIVE) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER)
        }
        return if (actor.role == AccountRole.OWNER || actor.role == AccountRole.ADMIN) {
            MembershipPolicyResult.Allowed
        } else {
            MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT)
        }
    }

    private fun evaluateCancelInvitation(
        actor: ActorMembershipContext?,
        target: TargetMemberContext?,
    ): MembershipPolicyResult {
        if (actor == null || actor.status != MembershipStatus.ACTIVE) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER)
        }
        if (actor.role != AccountRole.OWNER && actor.role != AccountRole.ADMIN) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT)
        }
        if (target == null || target.status != MembershipStatus.PENDING) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.NOT_PENDING_INVITATION)
        }
        return MembershipPolicyResult.Allowed
    }

    private fun evaluateChangeRole(
        actor: ActorMembershipContext?,
        target: TargetMemberContext?,
        newRole: AccountRole?,
    ): MembershipPolicyResult {
        if (actor == null || actor.status != MembershipStatus.ACTIVE) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER)
        }
        if (actor.role != AccountRole.OWNER) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT)
        }
        if (target == null) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_NOT_ELIGIBLE)
        }
        if (target.isSelf) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.SELF_OPERATION_FORBIDDEN)
        }
        if (newRole != AccountRole.ADMIN && newRole != AccountRole.MEMBER && newRole != AccountRole.VIEWER) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_NOT_ELIGIBLE)
        }
        return MembershipPolicyResult.Allowed
    }

    private fun evaluateRemoveMember(
        actor: ActorMembershipContext?,
        target: TargetMemberContext?,
    ): MembershipPolicyResult {
        if (actor == null || actor.status != MembershipStatus.ACTIVE) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER)
        }
        if (actor.role != AccountRole.OWNER && actor.role != AccountRole.ADMIN) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT)
        }
        if (target == null) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_NOT_ELIGIBLE)
        }
        if (target.isSelf) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.SELF_OPERATION_FORBIDDEN)
        }
        if (actor.role == AccountRole.ADMIN && (target.role == AccountRole.OWNER || target.role == AccountRole.ADMIN)) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_ROLE_TOO_HIGH)
        }
        return MembershipPolicyResult.Allowed
    }

    private fun evaluateLeave(
        actor: ActorMembershipContext?,
        isSoleOwner: Boolean?,
    ): MembershipPolicyResult {
        if (actor == null || actor.status != MembershipStatus.ACTIVE) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER)
        }
        if (actor.role == AccountRole.OWNER && isSoleOwner == true) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.SOLE_OWNER_CANNOT_LEAVE)
        }
        return MembershipPolicyResult.Allowed
    }

    private fun evaluateTransferOwnership(
        actor: ActorMembershipContext?,
        newOwner: NewOwnerContext?,
    ): MembershipPolicyResult {
        if (actor == null || actor.status != MembershipStatus.ACTIVE) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.NOT_ACCOUNT_MEMBER)
        }
        if (actor.role != AccountRole.OWNER) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT)
        }
        if (newOwner == null || newOwner.status != MembershipStatus.ACTIVE) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_NOT_ELIGIBLE)
        }
        if (newOwner.role != AccountRole.ADMIN && newOwner.role != AccountRole.MEMBER) {
            return MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_NOT_ELIGIBLE)
        }
        return MembershipPolicyResult.Allowed
    }
}
