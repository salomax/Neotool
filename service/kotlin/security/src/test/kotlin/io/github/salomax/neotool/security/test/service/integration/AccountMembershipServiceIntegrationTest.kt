package io.github.salomax.neotool.security.test.service.integration

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.security.domain.AccountManagement
import io.github.salomax.neotool.security.domain.AccountMembership
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.model.UserEntity
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

    private fun createUserWithoutAccount(
        name: String,
        email: String,
    ): UserEntity {
        val saved =
            authenticationService.saveUser(
                UserEntity(
                    email = email,
                    displayName = name,
                    passwordHash = authenticationService.hashPassword("TestPassword123!"),
                    emailVerified = true,
                ),
            )
        authenticationService.ensureUserPrincipalExists(requireNotNull(saved.id))
        return saved
    }

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
        val owner = userRepository.findByEmail(ownerEmail)!!
        val invitee = createUserWithoutAccount("Invitee", inviteeEmail)

        val createCmd =
            AccountManagement.CreateAccountCommand(
                accountName = "Biz Account",
                accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
                ownerUserId = owner.id!!,
            )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd =
            AccountMembership.InviteMemberCommand(
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

        val cancelCmd =
            AccountMembership.CancelInvitationCommand(
                accountId = accountId,
                actorUserId = owner.id!!,
                membershipId = invitation.membershipId,
            )
        accountMembershipService.cancelInvitation(cancelCmd)

        val afterCancel = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, invitee.id!!)
        assertThat(afterCancel).isEmpty
    }

    @Test
    fun `invite succeeds for email without platform user then register and accept`() {
        val ownerEmail = uniqueEmail("invite-new-owner")
        val inviteeEmail = uniqueEmail("invite-new-invitee")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!

        val createCmd =
            AccountManagement.CreateAccountCommand(
                accountName = "Family Account",
                accountType = io.github.salomax.neotool.security.model.account.AccountType.FAMILY,
                ownerUserId = owner.id!!,
            )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd =
            AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = owner.id!!,
                inviteeEmail = inviteeEmail,
                role = AccountRole.MEMBER,
            )
        val invitation = accountMembershipService.invite(inviteCmd)

        assertThat(invitation.membershipId).isNotNull()
        assertThat(invitation.inviteeEmail).isEqualTo(inviteeEmail.lowercase())
        val pendingBeforeSignup =
            accountMembershipRepository.findOneByAccountIdAndInviteeEmail(accountId, inviteeEmail.lowercase())
        assertThat(pendingBeforeSignup).isPresent
        assertThat(pendingBeforeSignup.get().userId).isNull()
        assertThat(pendingBeforeSignup.get().membershipStatus).isEqualTo(MembershipStatus.PENDING)

        authenticationService.registerUser("Invitee", inviteeEmail, "TestPassword123!")
        val invitee = userRepository.findByEmail(inviteeEmail)!!
        assertThat(
            accountMembershipRepository.findByUserIdAndMembershipStatus(invitee.id!!, MembershipStatus.ACTIVE),
        ).isEmpty()

        val acceptCmd =
            AccountMembership.AcceptInvitationCommand(
                invitationToken = invitation.invitationToken,
                actorUserId = invitee.id!!,
            )
        val accepted = accountMembershipService.acceptInvitation(acceptCmd)
        assertThat(accepted.accountId).isEqualTo(accountId)

        val active = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, invitee.id!!)
        assertThat(active).isPresent
        assertThat(active.get().membershipStatus).isEqualTo(MembershipStatus.ACTIVE)
        assertThat(active.get().accountRole).isEqualTo(AccountRole.MEMBER)
    }

    @Test
    fun `invite throws ALREADY_MEMBER when invitee already in account`() {
        val ownerEmail = uniqueEmail("dup-owner")
        val memberEmail = uniqueEmail("dup-member")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!
        val member = createUserWithoutAccount("Member", memberEmail)

        val createCmd =
            AccountManagement.CreateAccountCommand(
                accountName = "Biz",
                accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
                ownerUserId = owner.id!!,
            )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd =
            AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = owner.id!!,
                inviteeEmail = memberEmail,
                role = AccountRole.MEMBER,
            )
        val invitation = accountMembershipService.invite(inviteCmd)
        accountMembershipService.acceptInvitation(
            AccountMembership.AcceptInvitationCommand(
                invitationToken = invitation.invitationToken,
                actorUserId = member.id!!,
            ),
        )

        assertThrows<ValidationException> {
            accountMembershipService.invite(inviteCmd)
        }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ALREADY_MEMBER) }
    }

    @Test
    fun `accept invitation flow invitee accepts and membership becomes ACTIVE`() {
        val ownerEmail = uniqueEmail("accept-owner")
        val inviteeEmail = uniqueEmail("accept-invitee")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!
        val invitee = createUserWithoutAccount("Invitee", inviteeEmail)

        val createCmd =
            AccountManagement.CreateAccountCommand(
                accountName = "Accept Test Account",
                accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
                ownerUserId = owner.id!!,
            )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd =
            AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = owner.id!!,
                inviteeEmail = inviteeEmail,
                role = AccountRole.MEMBER,
            )
        val invitation = accountMembershipService.invite(inviteCmd)
        val token = invitation.invitationToken

        val acceptCmd =
            AccountMembership.AcceptInvitationCommand(
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
        val owner = userRepository.findByEmail(ownerEmail)!!
        val invitee = createUserWithoutAccount("Invitee", inviteeEmail)

        val createCmd =
            AccountManagement.CreateAccountCommand(
                accountName = "Decline Test Account",
                accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
                ownerUserId = owner.id!!,
            )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd =
            AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = owner.id!!,
                inviteeEmail = inviteeEmail,
                role = AccountRole.MEMBER,
            )
        val invitation = accountMembershipService.invite(inviteCmd)
        val token = invitation.invitationToken

        val declineCmd =
            AccountMembership.DeclineInvitationCommand(
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
        val owner = userRepository.findByEmail(ownerEmail)!!
        val invitee = createUserWithoutAccount("Invitee", inviteeEmail)
        val other = createUserWithoutAccount("Other", otherEmail)

        val createCmd =
            AccountManagement.CreateAccountCommand(
                accountName = "Wrong Actor Account",
                accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
                ownerUserId = owner.id!!,
            )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd =
            AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = owner.id!!,
                inviteeEmail = inviteeEmail,
                role = AccountRole.MEMBER,
            )
        val invitation = accountMembershipService.invite(inviteCmd)

        val acceptCmd =
            AccountMembership.AcceptInvitationCommand(
                invitationToken = invitation.invitationToken,
                // not the invitee
                actorUserId = other.id!!,
            )
        assertThrows<ValidationException> {
            accountMembershipService.acceptInvitation(acceptCmd)
        }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.INVITATION_INVALID) }
        val pending = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, invitee.id!!)
        assertThat(pending).isPresent
        assertThat(pending.get().membershipStatus).isEqualTo(MembershipStatus.PENDING)
    }

    @Test
    fun `change member role flow OWNER changes member to VIEWER`() {
        val ownerEmail = uniqueEmail("chrole-owner")
        val memberEmail = uniqueEmail("chrole-member")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!
        val member = createUserWithoutAccount("Member", memberEmail)

        val createCmd =
            AccountManagement.CreateAccountCommand(
                accountName = "Change Role Account",
                accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
                ownerUserId = owner.id!!,
            )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd =
            AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = owner.id!!,
                inviteeEmail = memberEmail,
                role = AccountRole.MEMBER,
            )
        val invitation = accountMembershipService.invite(inviteCmd)
        val acceptCmd =
            AccountMembership.AcceptInvitationCommand(
                invitationToken = invitation.invitationToken,
                actorUserId = member.id!!,
            )
        accountMembershipService.acceptInvitation(acceptCmd)

        val membership = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, member.id!!).get()
        val changeCmd =
            AccountMembership.ChangeMemberRoleCommand(
                accountId = accountId,
                actorUserId = owner.id!!,
                targetMembershipId = membership.id!!,
                newRole = AccountRole.VIEWER,
            )
        accountMembershipService.changeMemberRole(changeCmd)

        val updated = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, member.id!!)
        assertThat(updated).isPresent
        assertThat(updated.get().accountRole).isEqualTo(AccountRole.VIEWER)
        assertThat(updated.get().membershipStatus).isEqualTo(MembershipStatus.ACTIVE)
    }

    @Test
    fun `remove member flow OWNER removes member membership becomes REMOVED`() {
        val ownerEmail = uniqueEmail("rem-owner")
        val memberEmail = uniqueEmail("rem-member")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!
        val member = createUserWithoutAccount("Member", memberEmail)

        val createCmd =
            AccountManagement.CreateAccountCommand(
                accountName = "Remove Member Account",
                accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
                ownerUserId = owner.id!!,
            )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd =
            AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = owner.id!!,
                inviteeEmail = memberEmail,
                role = AccountRole.MEMBER,
            )
        val invitation = accountMembershipService.invite(inviteCmd)
        accountMembershipService.acceptInvitation(
            AccountMembership.AcceptInvitationCommand(invitation.invitationToken, member.id!!),
        )

        val membership = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, member.id!!).get()
        val removeCmd =
            AccountMembership.RemoveMemberCommand(
                accountId = accountId,
                actorUserId = owner.id!!,
                targetMembershipId = membership.id!!,
            )
        accountMembershipService.removeMember(removeCmd)

        val afterRemove = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, member.id!!)
        assertThat(afterRemove).isPresent
        assertThat(afterRemove.get().membershipStatus).isEqualTo(MembershipStatus.REMOVED)
        assertThat(afterRemove.get().isDefault).isFalse()
        assertThat(afterRemove.get().removedAt).isNotNull()
        assertThat(afterRemove.get().removedBy).isEqualTo(owner.id)
    }

    @Test
    fun `leave account flow member leaves membership becomes REMOVED`() {
        val ownerEmail = uniqueEmail("leave-owner")
        val memberEmail = uniqueEmail("leave-member")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!
        val member = createUserWithoutAccount("Member", memberEmail)

        val createCmd =
            AccountManagement.CreateAccountCommand(
                accountName = "Leave Account",
                accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
                ownerUserId = owner.id!!,
            )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd =
            AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = owner.id!!,
                inviteeEmail = memberEmail,
                role = AccountRole.MEMBER,
            )
        val invitation = accountMembershipService.invite(inviteCmd)
        accountMembershipService.acceptInvitation(
            AccountMembership.AcceptInvitationCommand(invitation.invitationToken, member.id!!),
        )

        val leaveCmd =
            AccountMembership.LeaveAccountCommand(
                accountId = accountId,
                actorUserId = member.id!!,
            )
        accountMembershipService.leaveAccount(leaveCmd)

        val afterLeave = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, member.id!!)
        assertThat(afterLeave).isPresent
        assertThat(afterLeave.get().membershipStatus).isEqualTo(MembershipStatus.REMOVED)
        assertThat(afterLeave.get().isDefault).isFalse()
        assertThat(afterLeave.get().removedAt).isNotNull()
    }

    @Test
    fun `transfer ownership flow OWNER transfers to ADMIN account and roles updated`() {
        val ownerEmail = uniqueEmail("xfer-owner")
        val adminEmail = uniqueEmail("xfer-admin")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!
        val admin = createUserWithoutAccount("Admin", adminEmail)

        val createCmd =
            AccountManagement.CreateAccountCommand(
                accountName = "Transfer Account",
                accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
                ownerUserId = owner.id!!,
            )
        val account = accountService.create(createCmd)
        val accountId = account.id!!

        val inviteCmd =
            AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = owner.id!!,
                inviteeEmail = adminEmail,
                role = AccountRole.MEMBER,
            )
        val invitation = accountMembershipService.invite(inviteCmd)
        accountMembershipService.acceptInvitation(
            AccountMembership.AcceptInvitationCommand(invitation.invitationToken, admin.id!!),
        )

        val adminMembership =
            accountMembershipRepository.findOneByAccountIdAndUserId(accountId, admin.id!!).get()
        accountMembershipService.changeMemberRole(
            AccountMembership.ChangeMemberRoleCommand(
                accountId = accountId,
                actorUserId = owner.id!!,
                targetMembershipId = adminMembership.id!!,
                newRole = AccountRole.ADMIN,
            ),
        )

        val transferCmd =
            AccountMembership.TransferOwnershipCommand(
                accountId = accountId,
                actorUserId = owner.id!!,
                newOwnerUserId = admin.id!!,
            )
        accountMembershipService.transferOwnership(transferCmd)

        val updatedAccount = accountRepository.findById(accountId).get()
        assertThat(updatedAccount.ownerUserId).isEqualTo(admin.id)

        val formerOwnerMembership = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, owner.id!!).get()
        assertThat(formerOwnerMembership.accountRole).isEqualTo(AccountRole.ADMIN)

        val newOwnerMembership = accountMembershipRepository.findOneByAccountIdAndUserId(accountId, admin.id!!).get()
        assertThat(newOwnerMembership.accountRole).isEqualTo(AccountRole.OWNER)
    }

    @Test
    fun `invite throws ACCOUNT_ALREADY_MEMBER when invitee already has an active account`() {
        val ownerEmail = uniqueEmail("already-owner")
        val inviteeEmail = uniqueEmail("already-invitee")
        authenticationService.registerUser("Owner", ownerEmail, "TestPassword123!")
        authenticationService.registerUser("Invitee", inviteeEmail, "TestPassword123!")
        val owner = userRepository.findByEmail(ownerEmail)!!

        val account =
            accountService.create(
                AccountManagement.CreateAccountCommand(
                    accountName = "Already Member Account",
                    accountType = io.github.salomax.neotool.security.model.account.AccountType.BUSINESS,
                    ownerUserId = owner.id!!,
                ),
            )

        assertThrows<ValidationException> {
            accountMembershipService.invite(
                AccountMembership.InviteMemberCommand(
                    accountId = account.id!!,
                    inviterUserId = owner.id!!,
                    inviteeEmail = inviteeEmail,
                    role = AccountRole.MEMBER,
                ),
            )
        }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ACCOUNT_ALREADY_MEMBER) }
    }
}
