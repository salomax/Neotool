package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * JPA repository for security.accounts.
 * Lookups use idx_accounts_owner (owner_user_id) and idx_accounts_status (account_status).
 */
@Repository
interface AccountRepository : JpaRepository<AccountEntity, UUID> {

    /**
     * Find all accounts owned by the given user.
     * Uses index idx_accounts_owner.
     */
    fun findByOwnerUserId(ownerUserId: UUID): List<AccountEntity>

    /**
     * Find all accounts with the given status.
     * Uses index idx_accounts_status when filtering by ACTIVE.
     */
    fun findByAccountStatus(accountStatus: AccountStatus): List<AccountEntity>

    /**
     * Check whether the user owns any account (e.g. for "one personal account per user" rules).
     * Uses index idx_accounts_owner.
     */
    fun existsByOwnerUserId(ownerUserId: UUID): Boolean
}
