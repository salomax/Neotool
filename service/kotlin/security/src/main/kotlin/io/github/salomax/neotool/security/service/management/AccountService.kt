package io.github.salomax.neotool.security.service.management

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.security.domain.AccountManagement
import io.github.salomax.neotool.security.domain.account.Account
import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.service.management.membership.ActorMembershipContext
import io.github.salomax.neotool.security.service.management.membership.MembershipOperation
import io.github.salomax.neotool.security.service.management.membership.MembershipPolicyEngine
import io.github.salomax.neotool.security.service.management.membership.MembershipPolicyResult
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

/**
 * Service for account management (FR-5).
 * Create (FAMILY/BUSINESS), update (owner only, name only), delete (soft delete).
 */
@Singleton
open class AccountService(
    private val accountRepository: AccountRepository,
    private val accountMembershipRepository: AccountMembershipRepository,
    private val membershipPolicyEngine: MembershipPolicyEngine,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get account by id. Returns null if not found.
     * Does not filter by status; callers may exclude DELETED if desired.
     */
    fun getById(accountId: UUID): Account? = accountRepository.findById(accountId).map { it.toDomain() }.orElse(null)

    /**
     * Create a new account (FAMILY or BUSINESS). Caller becomes OWNER.
     *
     * @throws IllegalArgumentException if account type is PERSONAL or validation fails
     */
    @Transactional
    open fun create(command: AccountManagement.CreateAccountCommand): Account {
        val account =
            AccountEntity(
                accountName = command.accountName,
                accountType = command.accountType,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = command.ownerUserId,
            )
        val savedAccount = accountRepository.save(account)

        val membership =
            AccountMembershipEntity(
                accountId = savedAccount.id!!,
                userId = command.ownerUserId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                joinedAt = Instant.now(),
                isDefault = false,
            )
        accountMembershipRepository.save(membership)

        logger.info { "Account created: ${savedAccount.accountName} (ID: ${savedAccount.id}), type=${command.accountType}" }
        return savedAccount.toDomain()
    }

    /**
     * Update account (owner only). Only account name is editable; type is immutable.
     *
     * @throws IllegalArgumentException if account not found, not owner, or already deleted
     */
    @Transactional
    open fun update(command: AccountManagement.UpdateAccountCommand): Account {
        val account =
            accountRepository.findById(command.accountId)
                .orElseThrow { IllegalArgumentException("Account not found with ID: ${command.accountId}") }
        require(account.accountStatus != AccountStatus.DELETED) {
            "Cannot update deleted account"
        }
        requirePolicyAllowed(
            MembershipOperation.UPDATE_ACCOUNT,
            command.accountId,
            command.actorUserId,
        )

        account.accountName = command.accountName
        account.updatedAt = Instant.now()
        val saved = accountRepository.update(account)
        logger.info { "Account updated: ${saved.accountName} (ID: ${saved.id})" }
        return saved.toDomain()
    }

    /**
     * Soft-delete account (owner only). Sets status=DELETED and deleted_at.
     * For PERSONAL accounts, user must have at least one other ACTIVE account (FR-5.4).
     * Marks all memberships as REMOVED.
     *
     * @throws IllegalArgumentException if account not found, not owner, or personal without other account
     */
    @Transactional
    open fun delete(command: AccountManagement.DeleteAccountCommand) {
        val account =
            accountRepository.findById(command.accountId)
                .orElseThrow { IllegalArgumentException("Account not found with ID: ${command.accountId}") }
        require(account.accountStatus != AccountStatus.DELETED) {
            "Account is already deleted"
        }
        requirePolicyAllowed(
            MembershipOperation.DELETE_ACCOUNT,
            command.accountId,
            command.actorUserId,
        )

        if (account.accountType == AccountType.PERSONAL) {
            val otherActiveCount =
                accountMembershipRepository
                    .findByUserIdAndMembershipStatus(command.actorUserId, MembershipStatus.ACTIVE)
                    .count { it.accountId != command.accountId }
            require(otherActiveCount > 0) {
                "Cannot delete personal account without another account (user must have at least one other ACTIVE membership)"
            }
        }

        val now = Instant.now()
        account.accountStatus = AccountStatus.DELETED
        account.deletedAt = now
        account.updatedAt = now
        accountRepository.update(account)

        val memberships = accountMembershipRepository.findByAccountId(command.accountId)
        memberships.forEach { m ->
            m.membershipStatus = MembershipStatus.REMOVED
            m.removedAt = now
            m.removedBy = command.actorUserId
            accountMembershipRepository.update(m)
        }

        logger.info { "Account soft-deleted (ID: ${command.accountId})" }
    }

    private fun requirePolicyAllowed(
        operation: MembershipOperation,
        accountId: UUID,
        actorUserId: UUID,
    ) {
        val actor =
            accountMembershipRepository
                .findOneByAccountIdAndUserId(accountId, actorUserId)
                .map { ActorMembershipContext(it.accountRole, it.membershipStatus) }
                .orElse(null)
        val result = membershipPolicyEngine.evaluate(operation, actor)
        when (result) {
            is MembershipPolicyResult.Allowed -> { /* proceed */ }
            is MembershipPolicyResult.Denied ->
                throw ValidationException(
                    errorCode = result.errorCode,
                    field = null,
                    parameters = result.message?.let { mapOf("reason" to it) } ?: emptyMap(),
                )
        }
    }
}
