package io.github.salomax.neotool.security.test.service.integration

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.domain.AccountManagement
import io.github.salomax.neotool.security.domain.AccountMembership
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RefreshTokenRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.authentication.AuthenticationService
import io.github.salomax.neotool.security.service.management.AccountService
import io.github.salomax.neotool.security.service.management.membership.AccountMembershipService
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows

@MicronautTest(startApplication = true)
@DisplayName("AccountMembershipService Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("security")
class AccountMembershipServiceIntegrationTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var principalRepository: PrincipalRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var accountMembershipRepository: AccountMembershipRepository

    @Inject
    lateinit var refreshTokenRepository: RefreshTokenRepository

    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var authenticationService: AuthenticationService

    @Inject
    lateinit var accountService: AccountService

    @Inject
    lateinit var accountMembershipService: AccountMembershipService

    private fun uniqueEmail(prefix: String) = SecurityTestDataBuilders.uniqueEmail(prefix)

    @BeforeEach
    fun cleanupBefore() {
        cleanupTestData()
    }

    @AfterEach
    fun cleanupAfter() {
        cleanupTestData()
    }

    private fun cleanupTestData() {
        try {
            entityManager.runTransaction {
                refreshTokenRepository.deleteAll()
                accountMembershipRepository.deleteAll()
                accountRepository.deleteAll()
                principalRepository.deleteAll()
                userRepository.deleteAll()
                entityManager.flush()
            }
            entityManager.clear()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun `issue invitation then cancel`() {
        val ownerEmail = uniqueEmail("invite-owner")
        val inviteeEmail = uniqueEmail("invitee")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        authenticationService.registerUser("Invitee", inviteeEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!
        val invitee = userRepository.findByEmail(inviteeEmail)!!

        val createCmd = AccountManagement.CreateAccountCommand(
            accountName = "Biz Account",
            accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
            ownerUserId = owner.id!!,
        )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd = AccountMembership.InviteMemberCommand(
            accountId = accountId,
            inviterUserId = owner.id!!,
            inviteeEmail = inviteeEmail,
            role = AccountRole.MEMBER,
        )
        val invitation = accountMembershipService.invite(inviteCmd)

        assertThat(invitation.membershipId).isNotNull()
        assertThat(invitation.invitationToken).isNotBlank
        assertThat(invitation.inviteeEmail).isEqualTo(inviteeEmail)
        assertThat(invitation.role).isEqualTo(AccountRole.MEMBER)
        assertThat(invitation.expiresAt).isAfter(java.time.Instant.now())

        val pending = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, invitee.id!!)
        assertThat(pending).isPresent
        assertThat(pending.get().membershipStatus).isEqualTo(MembershipStatus.PENDING)
        assertThat(pending.get().invitationToken).isEqualTo(invitation.invitationToken)

        val cancelCmd = AccountMembership.CancelInvitationCommand(
            accountId = accountId,
            actorUserId = owner.id!!,
            membershipId = invitation.membershipId,
        )
        accountMembershipService.cancelInvitation(cancelCmd)

        val afterCancel = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, invitee.id!!)
        assertThat(afterCancel).isEmpty
    }

    @Test
    fun `invite throws ALREADY_MEMBER when invitee already in account`() {
        val ownerEmail = uniqueEmail("dup-owner")
        val memberEmail = uniqueEmail("dup-member")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        authenticationService.registerUser("Member", memberEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!
        val member = userRepository.findByEmail(memberEmail)!!

        val createCmd = AccountManagement.CreateAccountCommand(
            accountName = "Biz",
            accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
            ownerUserId = owner.id!!,
        )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd = AccountMembership.InviteMemberCommand(
            accountId = accountId,
            inviterUserId = owner.id!!,
            inviteeEmail = memberEmail,
            role = AccountRole.MEMBER,
        )
        accountMembershipService.invite(inviteCmd)

        assertThrows<ValidationException> {
            accountMembershipService.invite(inviteCmd)
        }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ALREADY_MEMBER) }
    }

    @Test
    fun `accept invitation flow invitee accepts and membership becomes ACTIVE`() {
        val ownerEmail = uniqueEmail("accept-owner")
        val inviteeEmail = uniqueEmail("accept-invitee")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        authenticationService.registerUser("Invitee", inviteeEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!
        val invitee = userRepository.findByEmail(inviteeEmail)!!

        val createCmd = AccountManagement.CreateAccountCommand(
            accountName = "Accept Test Account",
            accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
            ownerUserId = owner.id!!,
        )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd = AccountMembership.InviteMemberCommand(
            accountId = accountId,
            inviterUserId = owner.id!!,
            inviteeEmail = inviteeEmail,
            role = AccountRole.MEMBER,
        )
        val invitation = accountMembershipService.invite(inviteCmd)
        val token = invitation.invitationToken

        val acceptCmd = AccountMembership.AcceptInvitationCommand(
            invitationToken = token,
            actorUserId = invitee.id!!,
        )
        val result = accountMembershipService.acceptInvitation(acceptCmd)

        assertThat(result.accountId).isEqualTo(accountId)
        assertThat(result.membershipId).isNotNull()
        val membership = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, invitee.id!!)
        assertThat(membership).isPresent
        assertThat(membership.get().membershipStatus).isEqualTo(MembershipStatus.ACTIVE)
        assertThat(membership.get().joinedAt).isNotNull()
        assertThat(membership.get().invitationToken).isNull()
    }

    @Test
    fun `decline invitation flow invitee declines and PENDING membership removed`() {
        val ownerEmail = uniqueEmail("decline-owner")
        val inviteeEmail = uniqueEmail("decline-invitee")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        authenticationService.registerUser("Invitee", inviteeEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!
        val invitee = userRepository.findByEmail(inviteeEmail)!!

        val createCmd = AccountManagement.CreateAccountCommand(
            accountName = "Decline Test Account",
            accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
            ownerUserId = owner.id!!,
        )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd = AccountMembership.InviteMemberCommand(
            accountId = accountId,
            inviterUserId = owner.id!!,
            inviteeEmail = inviteeEmail,
            role = AccountRole.MEMBER,
        )
        val invitation = accountMembershipService.invite(inviteCmd)
        val token = invitation.invitationToken

        val declineCmd = AccountMembership.DeclineInvitationCommand(
            invitationToken = token,
            actorUserId = invitee.id!!,
        )
        accountMembershipService.declineInvitation(declineCmd)

        val afterDecline = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, invitee.id!!)
        assertThat(afterDecline).isEmpty
    }

    @Test
    fun `accept invitation throws INVITATION_INVALID when actor is not the invitee`() {
        val ownerEmail = uniqueEmail("wrong-owner")
        val inviteeEmail = uniqueEmail("wrong-invitee")
        val otherEmail = uniqueEmail("wrong-other")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        authenticationService.registerUser("Invitee", inviteeEmail, "TestPassword123!")
        authenticationService.registerUser("Other", otherEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!
        val invitee = userRepository.findByEmail(inviteeEmail)!!
        val other = userRepository.findByEmail(otherEmail)!!

        val createCmd = AccountManagement.CreateAccountCommand(
            accountName = "Wrong Actor Account",
            accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
            ownerUserId = owner.id!!,
        )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd = AccountMembership.InviteMemberCommand(
            accountId = accountId,
            inviterUserId = owner.id!!,
            inviteeEmail = inviteeEmail,
            role = AccountRole.MEMBER,
        )
        val invitation = accountMembershipService.invite(inviteCmd)

        val acceptCmd = AccountMembership.AcceptInvitationCommand(
            invitationToken = invitation.invitationToken,
            actorUserId = other.id!!, // not the invitee
        )
        assertThrows<ValidationException> {
            accountMembershipService.acceptInvitation(acceptCmd)
        }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.INVITATION_INVALID) }
        val pending = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, invitee.id!!)
        assertThat(pending).isPresent
        assertThat(pending.get().membershipStatus).isEqualTo(MembershipStatus.PENDING)
    }
}
