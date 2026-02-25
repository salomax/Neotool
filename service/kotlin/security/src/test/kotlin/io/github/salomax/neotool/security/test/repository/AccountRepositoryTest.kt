package io.github.salomax.neotool.security.test.repository

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Integration tests for AccountRepository.
 * Critical queries: findByOwnerUserId / existsByOwnerUserId use idx_accounts_owner;
 * findByAccountStatus uses idx_accounts_status when filtering by ACTIVE.
 */
@MicronautTest(startApplication = false)
@DisplayName("AccountRepository Integration Tests")
class AccountRepositoryTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var userRepository: UserRepository

    private fun createTestUser(): UserEntity {
        val user = SecurityTestDataBuilders.user(
            id = null,
            email = SecurityTestDataBuilders.uniqueEmail("account-repo"),
        )
        return userRepository.save(user)
    }

    @Test
    fun `should save account and find by id`() {
        val user = createTestUser()
        val account = AccountEntity(
            accountName = "Test Account",
            accountType = AccountType.BUSINESS,
            accountStatus = AccountStatus.ACTIVE,
            ownerUserId = user.id,
        )
        val saved = accountRepository.save(account)

        assertThat(saved.id).isNotNull()
        val found = accountRepository.findById(saved.id!!)
        assertThat(found).isPresent
        assertThat(found.get().accountName).isEqualTo("Test Account")
        assertThat(found.get().accountType).isEqualTo(AccountType.BUSINESS)
        assertThat(found.get().ownerUserId).isEqualTo(user.id)
    }

    @Test
    fun `findByOwnerUserId returns accounts owned by user - uses idx_accounts_owner`() {
        val user = createTestUser()
        val account1 = accountRepository.save(
            AccountEntity(
                accountName = "Account 1",
                accountType = AccountType.FAMILY,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = user.id,
            ),
        )
        val account2 = accountRepository.save(
            AccountEntity(
                accountName = "Account 2",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = user.id,
            ),
        )
        val otherUser = createTestUser()
        accountRepository.save(
            AccountEntity(
                accountName = "Other Account",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = otherUser.id,
            ),
        )

        val byOwner = accountRepository.findByOwnerUserId(user.id!!)

        assertThat(byOwner).hasSize(2)
        assertThat(byOwner.map { it.id }).containsExactlyInAnyOrder(account1.id, account2.id)
    }

    @Test
    fun `findByAccountStatus returns accounts by status - uses idx_accounts_status for ACTIVE`() {
        val user = createTestUser()
        accountRepository.save(
            AccountEntity(
                accountName = "Active 1",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = user.id,
            ),
        )
        accountRepository.save(
            AccountEntity(
                accountName = "Suspended 1",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.SUSPENDED,
                ownerUserId = user.id,
            ),
        )
        accountRepository.save(
            AccountEntity(
                accountName = "Active 2",
                accountType = AccountType.FAMILY,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = user.id,
            ),
        )

        val active = accountRepository.findByAccountStatus(AccountStatus.ACTIVE)
        val suspended = accountRepository.findByAccountStatus(AccountStatus.SUSPENDED)

        assertThat(active).hasSizeGreaterThanOrEqualTo(2)
        assertThat(active.map { it.accountName }).contains("Active 1", "Active 2")
        assertThat(suspended).hasSizeGreaterThanOrEqualTo(1)
        assertThat(suspended.map { it.accountName }).contains("Suspended 1")
    }

    @Test
    fun `existsByOwnerUserId returns true when user owns at least one account - uses idx_accounts_owner`() {
        val user = createTestUser()
        assertThat(accountRepository.existsByOwnerUserId(user.id!!)).isFalse()

        accountRepository.save(
            AccountEntity(
                accountName = "My Account",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = user.id,
            ),
        )

        assertThat(accountRepository.existsByOwnerUserId(user.id!!)).isTrue()
    }

    @Test
    fun `existsByOwnerUserId returns false for user with no accounts`() {
        val user = createTestUser()
        assertThat(accountRepository.existsByOwnerUserId(user.id!!)).isFalse()
    }
}
