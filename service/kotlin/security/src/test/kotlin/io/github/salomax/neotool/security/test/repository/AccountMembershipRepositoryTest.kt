package io.github.salomax.neotool.security.test.repository

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for AccountMembershipRepository.
 * Critical queries: findByUserIdAndMembershipStatus uses idx_memberships_user (ACTIVE);
 * findByAccountId uses idx_memberships_account; findOneByInvitationToken uses idx_memberships_token;
 * findOneByUserIdAndMembershipStatusAndIsDefaultTrue uses uq_memberships_default_per_user.
 */
@MicronautTest(startApplication = false)
@DisplayName("AccountMembershipRepository Integration Tests")
class AccountMembershipRepositoryTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {

    @Inject
    lateinit var accountMembershipRepository: AccountMembershipRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var userRepository: UserRepository

    private fun createTestUser(): UserEntity {
        val user = SecurityTestDataBuilders.user(
            id = null,
            email = SecurityTestDataBuilders.uniqueEmail("membership-repo"),
        )
        return userRepository.save(user)
    }

    private fun createTestAccount(ownerUserId: UUID): AccountEntity {
        return accountRepository.save(
            AccountEntity(
                accountName = "Test Account",
                accountType = AccountType.BUSINESS,
                accountStatus = io.github.salomax.neotool.security.model.account.AccountStatus.ACTIVE,
                ownerUserId = ownerUserId,
            ),
        )
    }

    @Test
    fun `should save membership and find by id`() {
        val user = createTestUser()
        val account = createTestAccount(user.id!!)
        val membership = AccountMembershipEntity(
            accountId = account.id!!,
            userId = user.id!!,
            accountRole = AccountRole.OWNER,
            membershipStatus = MembershipStatus.ACTIVE,
            isDefault = true,
        )
        val saved = accountMembershipRepository.save(membership)

        assertThat(saved.id).isNotNull()
        val found = accountMembershipRepository.findById(saved.id!!)
        assertThat(found).isPresent
        assertThat(found.get().accountId).isEqualTo(account.id)
        assertThat(found.get().userId).isEqualTo(user.id)
        assertThat(found.get().accountRole).isEqualTo(AccountRole.OWNER)
        assertThat(found.get().isDefault).isTrue()
    }

    @Test
    fun `findByAccountId returns all memberships for account - uses idx_memberships_account`() {
        val owner = createTestUser()
        val member = createTestUser()
        val account = createTestAccount(owner.id!!)
        val ownerMembership = accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = account.id!!,
                userId = owner.id!!,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = true,
            ),
        )
        val memberMembership = accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = account.id!!,
                userId = member.id!!,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = false,
            ),
        )

        val byAccount = accountMembershipRepository.findByAccountId(account.id!!)

        assertThat(byAccount).hasSize(2)
        assertThat(byAccount.map { it.id }).containsExactlyInAnyOrder(ownerMembership.id, memberMembership.id)
    }

    @Test
    fun `findByUserId returns all memberships for user - uses idx_memberships_user for ACTIVE`() {
        val user = createTestUser()
        val account1 = createTestAccount(user.id!!)
        val account2 = createTestAccount(user.id!!)
        accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = account1.id!!,
                userId = user.id!!,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = true,
            ),
        )
        accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = account2.id!!,
                userId = user.id!!,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = false,
            ),
        )

        val byUser = accountMembershipRepository.findByUserId(user.id!!)

        assertThat(byUser).hasSize(2)
    }

    @Test
    fun `findOneByAccountIdAndUserId returns unique membership - uq_account_user`() {
        val user = createTestUser()
        val account = createTestAccount(user.id!!)
        accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = account.id!!,
                userId = user.id!!,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = true,
            ),
        )

        val found = accountMembershipRepository.findOneByAccountIdAndUserId(account.id!!, user.id!!)

        assertThat(found).isPresent
        assertThat(found.get().accountRole).isEqualTo(AccountRole.OWNER)
    }

    @Test
    fun `findOneByAccountIdAndUserId returns empty when no membership`() {
        val user = createTestUser()
        val account = createTestAccount(user.id!!)

        val found = accountMembershipRepository.findOneByAccountIdAndUserId(account.id!!, user.id!!)

        assertThat(found).isEmpty
    }

    @Test
    fun `findOneByInvitationToken resolves membership by token - uses idx_memberships_token`() {
        val owner = createTestUser()
        val invitee = createTestUser()
        val account = createTestAccount(owner.id!!)
        val token = "invite-${UUID.randomUUID()}"
        val expiresAt = Instant.now().plusSeconds(7 * 24 * 3600)
        val membership = accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = account.id!!,
                userId = invitee.id!!,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.PENDING,
                isDefault = false,
                invitationToken = token,
                invitationExpiresAt = expiresAt,
                invitedBy = owner.id,
            ),
        )

        val found = accountMembershipRepository.findOneByInvitationToken(token)

        assertThat(found).isPresent
        assertThat(found.get().id).isEqualTo(membership.id)
        assertThat(found.get().invitationToken).isEqualTo(token)
    }

    @Test
    fun `findOneByInvitationToken returns empty for unknown token`() {
        val found = accountMembershipRepository.findOneByInvitationToken("no-such-token")
        assertThat(found).isEmpty
    }

    @Test
    fun `findByUserIdAndMembershipStatus returns active memberships - uses idx_memberships_user`() {
        val user = createTestUser()
        val account1 = createTestAccount(user.id!!)
        val account2 = createTestAccount(user.id!!)
        accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = account1.id!!,
                userId = user.id!!,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = true,
            ),
        )
        accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = account2.id!!,
                userId = user.id!!,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = false,
            ),
        )
        val otherUser = createTestUser()
        val otherAccount = createTestAccount(otherUser.id!!)
        accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = otherAccount.id!!,
                userId = user.id!!,
                accountRole = AccountRole.VIEWER,
                membershipStatus = MembershipStatus.PENDING,
                isDefault = false,
            ),
        )

        val active = accountMembershipRepository.findByUserIdAndMembershipStatus(user.id!!, MembershipStatus.ACTIVE)

        assertThat(active).hasSize(2)
        assertThat(active).allMatch { it.membershipStatus == MembershipStatus.ACTIVE }
    }

    @Test
    fun `findOneByUserIdAndMembershipStatusAndIsDefaultTrue returns at most one - uq_memberships_default_per_user`() {
        val user = createTestUser()
        val account = createTestAccount(user.id!!)
        accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = account.id!!,
                userId = user.id!!,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = true,
            ),
        )

        val defaultMembership =
            accountMembershipRepository.findOneByUserIdAndMembershipStatusAndIsDefaultTrue(
                user.id!!,
                MembershipStatus.ACTIVE,
            )

        assertThat(defaultMembership).isPresent
        assertThat(defaultMembership.get().isDefault).isTrue()
        assertThat(defaultMembership.get().accountId).isEqualTo(account.id)
    }

    @Test
    fun `findOneByUserIdAndMembershipStatusAndIsDefaultTrue returns empty when no default`() {
        val user = createTestUser()
        val defaultMembership =
            accountMembershipRepository.findOneByUserIdAndMembershipStatusAndIsDefaultTrue(
                user.id!!,
                MembershipStatus.ACTIVE,
            )
        assertThat(defaultMembership).isEmpty
    }

    @Test
    fun `countByAccountIdAndAccountRole supports owner uniqueness enforcement`() {
        val owner = createTestUser()
        val account = createTestAccount(owner.id!!)
        accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = account.id!!,
                userId = owner.id!!,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = true,
            ),
        )
        val member = createTestUser()
        accountMembershipRepository.save(
            AccountMembershipEntity(
                accountId = account.id!!,
                userId = member.id!!,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
                isDefault = false,
            ),
        )

        val ownerCount = accountMembershipRepository.countByAccountIdAndAccountRole(account.id!!, AccountRole.OWNER)
        val memberCount = accountMembershipRepository.countByAccountIdAndAccountRole(account.id!!, AccountRole.MEMBER)

        assertThat(ownerCount).isEqualTo(1L)
        assertThat(memberCount).isEqualTo(1L)
    }
}
