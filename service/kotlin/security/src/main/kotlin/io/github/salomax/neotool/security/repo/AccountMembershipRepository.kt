package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

/**
 * JPA repository for security.account_memberships.
 * Lookups use idx_memberships_user (user_id, ACTIVE), idx_memberships_account (account_id, ACTIVE),
 * idx_memberships_token (invitation_token), uq_memberships_default_per_user (user_id, ACTIVE, is_default).
 */
@Repository
interface AccountMembershipRepository : JpaRepository<AccountMembershipEntity, UUID> {
    /**
     * Find all memberships for an account.
     * Uses index idx_memberships_account when combined with status filter in service.
     */
    fun findByAccountId(accountId: UUID): List<AccountMembershipEntity>

    /**
     * Find all memberships for a user.
     * Uses index idx_memberships_user when filtering by ACTIVE in service.
     */
    fun findByUserId(userId: UUID): List<AccountMembershipEntity>

    /**
     * Find the unique membership for (account, user). At most one by uq_account_user.
     * Used for role checks and active-membership resolution.
     */
    fun findOneByAccountIdAndUserId(
        accountId: UUID,
        userId: UUID,
    ): Optional<AccountMembershipEntity>

    /**
     * Resolve invitation by token. Service layer must enforce invitation_expires_at.
     * Uses index idx_memberships_token.
     */
    fun findOneByInvitationToken(invitationToken: String): Optional<AccountMembershipEntity>

    /**
     * Find active memberships for a user (membership_status = ACTIVE).
     * Uses index idx_memberships_user.
     */
    fun findByUserIdAndMembershipStatus(
        userId: UUID,
        membershipStatus: MembershipStatus,
    ): List<AccountMembershipEntity>

    /**
     * Find the default active membership for a user (at most one by uq_memberships_default_per_user).
     */
    fun findOneByUserIdAndMembershipStatusAndIsDefaultTrue(
        userId: UUID,
        membershipStatus: MembershipStatus,
    ): Optional<AccountMembershipEntity>

    /**
     * Count members with a given role in an account (e.g. for owner uniqueness: count OWNER must be 1).
     * Used for role transitions and owner-uniqueness enforcement.
     */
    fun countByAccountIdAndAccountRole(
        accountId: UUID,
        accountRole: AccountRole,
    ): Long
}
