package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.security.domain.AccountMembership
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.management.membership.AccountMembershipService
import io.github.salomax.neotool.security.service.management.membership.ActorMembershipContext
import io.github.salomax.neotool.security.service.management.membership.MembershipOperation
import io.github.salomax.neotool.security.service.management.membership.MembershipPolicyEngine
import io.github.salomax.neotool.security.service.management.membership.MembershipPolicyResult
import io.github.salomax.neotool.security.service.management.membership.TargetMemberContext
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

@DisplayName("AccountMembershipService Unit Tests")
class AccountMembershipServiceTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var accountMembershipRepository: AccountMembershipRepository
    private lateinit var userRepository: UserRepository
    private lateinit var membershipPolicyEngine: MembershipPolicyEngine
    private lateinit var accountMembershipService: AccountMembershipService

    @BeforeEach
    fun setUp() {
        accountRepository = mock()
        accountMembershipRepository = mock()
        userRepository = mock()
        membershipPolicyEngine = mock()
        accountMembershipService = AccountMembershipService(
            accountRepository,
            accountMembershipRepository,
            userRepository,
            membershipPolicyEngine,
        )
    }

    @Nested
    @DisplayName("Invite (FR-4.1)")
    inner class InviteTests {
        @Test
        fun `invite succeeds when OWNER invites existing user and under limit`() {
            val accountId = UUID.randomUUID()
            val inviterId = UUID.randomUUID()
            val inviteeId = UUID.randomUUID()
            val inviteeEmail = "invitee@example.com"
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = inviterId,
            )
            val invitee = SecurityTestDataBuilders.user(id = inviteeId, email = inviteeEmail)
            val inviterMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = inviterId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val savedMembershipId = UUID.randomUUID()
            val savedMembership = AccountMembershipEntity(
                id = savedMembershipId,
                accountId = accountId,
                userId = inviteeId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.PENDING,
                invitationToken = "token123",
                invitationExpiresAt = Instant.now().plusSeconds(604800),
                invitedBy = inviterId,
                invitedAt = Instant.now(),
            )

            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(inviterId)))
                .thenReturn(Optional.of(inviterMembership))
            whenever(membershipPolicyEngine.evaluate(eq(MembershipOperation.INVITE), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(MembershipPolicyResult.Allowed)
            whenever(userRepository.findByEmail(eq(inviteeEmail))).thenReturn(invitee)
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(inviteeId)))
                .thenReturn(Optional.empty())
            whenever(accountMembershipRepository.countByAccountIdAndMembershipStatusIn(eq(accountId), any()))
                .thenReturn(1L)
            whenever(accountMembershipRepository.findOneByInvitationToken(any())).thenReturn(Optional.empty())
            whenever(accountMembershipRepository.save(any())).thenReturn(savedMembership)

            val command = AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = inviterId,
                inviteeEmail = inviteeEmail,
                role = AccountRole.MEMBER,
            )
            val result = accountMembershipService.invite(command)

            assertThat(result.membershipId).isEqualTo(savedMembershipId)
            assertThat(result.inviteeEmail).isEqualTo(inviteeEmail)
            assertThat(result.role).isEqualTo(AccountRole.MEMBER)
            assertThat(result.invitationToken).isNotBlank()
            assertThat(result.expiresAt).isAfter(Instant.now())
            verify(accountMembershipRepository).save(any())
        }

        @Test
        fun `invite throws ACCOUNT_ROLE_INSUFFICIENT when policy denies`() {
            val accountId = UUID.randomUUID()
            val inviterId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = UUID.randomUUID(),
            )
            val memberMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = inviterId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(inviterId)))
                .thenReturn(Optional.of(memberMembership))
            whenever(membershipPolicyEngine.evaluate(eq(MembershipOperation.INVITE), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))

            val command = AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = inviterId,
                inviteeEmail = "other@example.com",
                role = AccountRole.MEMBER,
            )
            assertThrows<ValidationException> {
                accountMembershipService.invite(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT) }
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `invite throws USER_NOT_FOUND when invitee email not found`() {
            val accountId = UUID.randomUUID()
            val inviterId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = inviterId,
            )
            val inviterMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = inviterId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(inviterId)))
                .thenReturn(Optional.of(inviterMembership))
            whenever(membershipPolicyEngine.evaluate(eq(MembershipOperation.INVITE), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(MembershipPolicyResult.Allowed)
            whenever(userRepository.findByEmail(eq("nonexistent@example.com"))).thenReturn(null)

            val command = AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = inviterId,
                inviteeEmail = "nonexistent@example.com",
                role = AccountRole.MEMBER,
            )
            assertThrows<ValidationException> {
                accountMembershipService.invite(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.USER_NOT_FOUND) }
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `invite throws ALREADY_MEMBER when invitee already has membership`() {
            val accountId = UUID.randomUUID()
            val inviterId = UUID.randomUUID()
            val inviteeId = UUID.randomUUID()
            val inviteeEmail = "member@example.com"
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = inviterId,
            )
            val invitee = SecurityTestDataBuilders.user(id = inviteeId, email = inviteeEmail)
            val inviterMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = inviterId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val existingMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = inviteeId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(inviterId)))
                .thenReturn(Optional.of(inviterMembership))
            whenever(membershipPolicyEngine.evaluate(eq(MembershipOperation.INVITE), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(MembershipPolicyResult.Allowed)
            whenever(userRepository.findByEmail(eq(inviteeEmail))).thenReturn(invitee)
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(inviteeId)))
                .thenReturn(Optional.of(existingMembership))

            val command = AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = inviterId,
                inviteeEmail = inviteeEmail,
                role = AccountRole.MEMBER,
            )
            assertThrows<ValidationException> {
                accountMembershipService.invite(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ALREADY_MEMBER) }
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `invite throws MEMBER_LIMIT_REACHED for PERSONAL account`() {
            val accountId = UUID.randomUUID()
            val inviterId = UUID.randomUUID()
            val inviteeId = UUID.randomUUID()
            val inviteeEmail = "other@example.com"
            val account = AccountEntity(
                id = accountId,
                accountName = "Personal",
                accountType = AccountType.PERSONAL,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = inviterId,
            )
            val inviterMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = inviterId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val invitee = SecurityTestDataBuilders.user(id = inviteeId, email = inviteeEmail)
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(inviterId)))
                .thenReturn(Optional.of(inviterMembership))
            whenever(membershipPolicyEngine.evaluate(eq(MembershipOperation.INVITE), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(MembershipPolicyResult.Allowed)
            whenever(userRepository.findByEmail(eq(inviteeEmail))).thenReturn(invitee)
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(inviteeId)))
                .thenReturn(Optional.empty())

            val command = AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = inviterId,
                inviteeEmail = inviteeEmail,
                role = AccountRole.MEMBER,
            )
            assertThrows<ValidationException> {
                accountMembershipService.invite(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.MEMBER_LIMIT_REACHED) }
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `invite throws MEMBER_LIMIT_REACHED when FAMILY at 10 members`() {
            val accountId = UUID.randomUUID()
            val inviterId = UUID.randomUUID()
            val inviteeId = UUID.randomUUID()
            val inviteeEmail = "new@example.com"
            val account = AccountEntity(
                id = accountId,
                accountName = "Family",
                accountType = AccountType.FAMILY,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = inviterId,
            )
            val inviterMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = inviterId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val invitee = SecurityTestDataBuilders.user(id = inviteeId, email = inviteeEmail)
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(inviterId)))
                .thenReturn(Optional.of(inviterMembership))
            whenever(membershipPolicyEngine.evaluate(eq(MembershipOperation.INVITE), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(MembershipPolicyResult.Allowed)
            whenever(userRepository.findByEmail(eq(inviteeEmail))).thenReturn(invitee)
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(inviteeId)))
                .thenReturn(Optional.empty())
            whenever(accountMembershipRepository.countByAccountIdAndMembershipStatusIn(eq(accountId), any()))
                .thenReturn(10L)

            val command = AccountMembership.InviteMemberCommand(
                accountId = accountId,
                inviterUserId = inviterId,
                inviteeEmail = inviteeEmail,
                role = AccountRole.MEMBER,
            )
            assertThrows<ValidationException> {
                accountMembershipService.invite(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.MEMBER_LIMIT_REACHED) }
            verify(accountMembershipRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("Cancel invitation (FR-4.4)")
    inner class CancelInvitationTests {
        @Test
        fun `cancelInvitation succeeds when OWNER cancels PENDING`() {
            val accountId = UUID.randomUUID()
            val actorId = UUID.randomUUID()
            val membershipId = UUID.randomUUID()
            val targetMembership = AccountMembershipEntity(
                id = membershipId,
                accountId = accountId,
                userId = UUID.randomUUID(),
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.PENDING,
            )
            val actorMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = actorId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountMembershipRepository.findById(eq(membershipId))).thenReturn(Optional.of(targetMembership))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(actorId)))
                .thenReturn(Optional.of(actorMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.CANCEL_INVITATION),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                ),
            ).thenReturn(MembershipPolicyResult.Allowed)

            val command = AccountMembership.CancelInvitationCommand(
                accountId = accountId,
                actorUserId = actorId,
                membershipId = membershipId,
            )
            accountMembershipService.cancelInvitation(command)

            verify(accountMembershipRepository).deleteById(membershipId)
        }

        @Test
        fun `cancelInvitation throws ACCOUNT_ROLE_INSUFFICIENT when policy denies`() {
            val accountId = UUID.randomUUID()
            val actorId = UUID.randomUUID()
            val membershipId = UUID.randomUUID()
            val targetMembership = AccountMembershipEntity(
                id = membershipId,
                accountId = accountId,
                userId = UUID.randomUUID(),
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.PENDING,
            )
            val actorMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = actorId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountMembershipRepository.findById(eq(membershipId))).thenReturn(Optional.of(targetMembership))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(actorId)))
                .thenReturn(Optional.of(actorMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.CANCEL_INVITATION),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                ),
            ).thenReturn(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))

            val command = AccountMembership.CancelInvitationCommand(
                accountId = accountId,
                actorUserId = actorId,
                membershipId = membershipId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.cancelInvitation(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT) }
            verify(accountMembershipRepository, never()).deleteById(any())
        }

        @Test
        fun `cancelInvitation throws NOT_PENDING_INVITATION when target is ACTIVE`() {
            val accountId = UUID.randomUUID()
            val actorId = UUID.randomUUID()
            val membershipId = UUID.randomUUID()
            val targetMembership = AccountMembershipEntity(
                id = membershipId,
                accountId = accountId,
                userId = UUID.randomUUID(),
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountMembershipRepository.findById(eq(membershipId))).thenReturn(Optional.of(targetMembership))

            val command = AccountMembership.CancelInvitationCommand(
                accountId = accountId,
                actorUserId = actorId,
                membershipId = membershipId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.cancelInvitation(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.NOT_PENDING_INVITATION) }
            verify(accountMembershipRepository, never()).deleteById(any())
        }

        @Test
        fun `cancelInvitation throws TARGET_NOT_ELIGIBLE when membership not found`() {
            val membershipId = UUID.randomUUID()
            whenever(accountMembershipRepository.findById(eq(membershipId))).thenReturn(Optional.empty())

            val command = AccountMembership.CancelInvitationCommand(
                accountId = UUID.randomUUID(),
                actorUserId = UUID.randomUUID(),
                membershipId = membershipId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.cancelInvitation(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.TARGET_NOT_ELIGIBLE) }
            verify(accountMembershipRepository, never()).deleteById(any())
        }

        @Test
        fun `cancelInvitation throws TARGET_NOT_ELIGIBLE when membership accountId does not match`() {
            val accountId = UUID.randomUUID()
            val membershipId = UUID.randomUUID()
            val targetMembership = AccountMembershipEntity(
                id = membershipId,
                accountId = UUID.randomUUID(), // different account
                userId = UUID.randomUUID(),
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.PENDING,
            )
            whenever(accountMembershipRepository.findById(eq(membershipId))).thenReturn(Optional.of(targetMembership))

            val command = AccountMembership.CancelInvitationCommand(
                accountId = accountId,
                actorUserId = UUID.randomUUID(),
                membershipId = membershipId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.cancelInvitation(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.TARGET_NOT_ELIGIBLE) }
            verify(accountMembershipRepository, never()).deleteById(any())
        }
    }

    @Nested
    @DisplayName("Accept invitation (FR-4.2)")
    inner class AcceptInvitationTests {
        @Test
        fun `acceptInvitation succeeds when invitee accepts valid token`() {
            val accountId = UUID.randomUUID()
            val inviteeId = UUID.randomUUID()
            val token = "valid-token-abc"
            val membershipId = UUID.randomUUID()
            val membership = AccountMembershipEntity(
                id = membershipId,
                accountId = accountId,
                userId = inviteeId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.PENDING,
                invitationToken = token,
                invitationExpiresAt = Instant.now().plusSeconds(86400),
            )
            whenever(accountMembershipRepository.findOneByInvitationToken(eq(token)))
                .thenReturn(Optional.of(membership))
            whenever(accountMembershipRepository.save(any())).thenAnswer { it.getArgument(0) }

            val command = AccountMembership.AcceptInvitationCommand(
                invitationToken = token,
                actorUserId = inviteeId,
            )
            val result = accountMembershipService.acceptInvitation(command)

            assertThat(result.membershipId).isEqualTo(membershipId)
            assertThat(result.accountId).isEqualTo(accountId)
            assertThat(membership.membershipStatus).isEqualTo(MembershipStatus.ACTIVE)
            assertThat(membership.joinedAt).isNotNull()
            assertThat(membership.invitationToken).isNull()
            assertThat(membership.invitationExpiresAt).isNull()
            verify(accountMembershipRepository).save(membership)
        }

        @Test
        fun `acceptInvitation throws INVITATION_INVALID when token not found`() {
            whenever(accountMembershipRepository.findOneByInvitationToken(any())).thenReturn(Optional.empty())

            val command = AccountMembership.AcceptInvitationCommand(
                invitationToken = "unknown-token",
                actorUserId = UUID.randomUUID(),
            )
            assertThrows<ValidationException> {
                accountMembershipService.acceptInvitation(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.INVITATION_INVALID) }
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `acceptInvitation throws INVITATION_INVALID when actor is not the invitee`() {
            val inviteeId = UUID.randomUUID()
            val otherUserId = UUID.randomUUID()
            val token = "valid-token"
            val membership = AccountMembershipEntity(
                id = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                userId = inviteeId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.PENDING,
                invitationToken = token,
                invitationExpiresAt = Instant.now().plusSeconds(86400),
            )
            whenever(accountMembershipRepository.findOneByInvitationToken(eq(token)))
                .thenReturn(Optional.of(membership))

            val command = AccountMembership.AcceptInvitationCommand(
                invitationToken = token,
                actorUserId = otherUserId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.acceptInvitation(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.INVITATION_INVALID) }
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `acceptInvitation throws INVITATION_EXPIRED when token expired`() {
            val inviteeId = UUID.randomUUID()
            val token = "expired-token"
            val membership = AccountMembershipEntity(
                id = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                userId = inviteeId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.PENDING,
                invitationToken = token,
                invitationExpiresAt = Instant.now().minusSeconds(3600),
            )
            whenever(accountMembershipRepository.findOneByInvitationToken(eq(token)))
                .thenReturn(Optional.of(membership))

            val command = AccountMembership.AcceptInvitationCommand(
                invitationToken = token,
                actorUserId = inviteeId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.acceptInvitation(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.INVITATION_EXPIRED) }
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `acceptInvitation throws NOT_PENDING_INVITATION when membership already ACTIVE`() {
            val inviteeId = UUID.randomUUID()
            val token = "used-token"
            val membership = AccountMembershipEntity(
                id = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                userId = inviteeId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
                invitationToken = token,
                invitationExpiresAt = Instant.now().plusSeconds(86400),
            )
            whenever(accountMembershipRepository.findOneByInvitationToken(eq(token)))
                .thenReturn(Optional.of(membership))

            val command = AccountMembership.AcceptInvitationCommand(
                invitationToken = token,
                actorUserId = inviteeId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.acceptInvitation(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.NOT_PENDING_INVITATION) }
            verify(accountMembershipRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("Decline invitation (FR-4.3)")
    inner class DeclineInvitationTests {
        @Test
        fun `declineInvitation succeeds when invitee declines`() {
            val accountId = UUID.randomUUID()
            val inviteeId = UUID.randomUUID()
            val membershipId = UUID.randomUUID()
            val token = "valid-token"
            val membership = AccountMembershipEntity(
                id = membershipId,
                accountId = accountId,
                userId = inviteeId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.PENDING,
                invitationToken = token,
                invitationExpiresAt = Instant.now().plusSeconds(86400),
            )
            whenever(accountMembershipRepository.findOneByInvitationToken(eq(token)))
                .thenReturn(Optional.of(membership))

            val command = AccountMembership.DeclineInvitationCommand(
                invitationToken = token,
                actorUserId = inviteeId,
            )
            accountMembershipService.declineInvitation(command)

            verify(accountMembershipRepository).deleteById(membershipId)
        }

        @Test
        fun `declineInvitation throws INVITATION_INVALID when token not found`() {
            whenever(accountMembershipRepository.findOneByInvitationToken(any())).thenReturn(Optional.empty())

            val command = AccountMembership.DeclineInvitationCommand(
                invitationToken = "unknown-token",
                actorUserId = UUID.randomUUID(),
            )
            assertThrows<ValidationException> {
                accountMembershipService.declineInvitation(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.INVITATION_INVALID) }
            verify(accountMembershipRepository, never()).deleteById(any())
        }

        @Test
        fun `declineInvitation throws INVITATION_INVALID when actor is not the invitee`() {
            val inviteeId = UUID.randomUUID()
            val otherUserId = UUID.randomUUID()
            val token = "valid-token"
            val membership = AccountMembershipEntity(
                id = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                userId = inviteeId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.PENDING,
                invitationToken = token,
                invitationExpiresAt = Instant.now().plusSeconds(86400),
            )
            whenever(accountMembershipRepository.findOneByInvitationToken(eq(token)))
                .thenReturn(Optional.of(membership))

            val command = AccountMembership.DeclineInvitationCommand(
                invitationToken = token,
                actorUserId = otherUserId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.declineInvitation(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.INVITATION_INVALID) }
            verify(accountMembershipRepository, never()).deleteById(any())
        }

        @Test
        fun `declineInvitation throws NOT_PENDING_INVITATION when membership already ACTIVE`() {
            val inviteeId = UUID.randomUUID()
            val token = "used-token"
            val membership = AccountMembershipEntity(
                id = UUID.randomUUID(),
                accountId = UUID.randomUUID(),
                userId = inviteeId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
                invitationToken = token,
            )
            whenever(accountMembershipRepository.findOneByInvitationToken(eq(token)))
                .thenReturn(Optional.of(membership))

            val command = AccountMembership.DeclineInvitationCommand(
                invitationToken = token,
                actorUserId = inviteeId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.declineInvitation(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.NOT_PENDING_INVITATION) }
            verify(accountMembershipRepository, never()).deleteById(any())
        }
    }

    @Nested
    @DisplayName("Change member role (FR-6.2)")
    inner class ChangeMemberRoleTests {
        @Test
        fun `changeMemberRole succeeds when OWNER changes another member to VIEWER`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val targetMembershipId = UUID.randomUUID()
            val targetUserId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val ownerMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val targetMembership = AccountMembershipEntity(
                id = targetMembershipId,
                accountId = accountId,
                userId = targetUserId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(ownerId)))
                .thenReturn(Optional.of(ownerMembership))
            whenever(accountMembershipRepository.findById(eq(targetMembershipId)))
                .thenReturn(Optional.of(targetMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.CHANGE_ROLE),
                    any(),
                    any(),
                    eq(AccountRole.VIEWER),
                    anyOrNull(),
                    anyOrNull(),
                ),
            ).thenReturn(MembershipPolicyResult.Allowed)
            whenever(accountMembershipRepository.save(any())).thenAnswer { it.getArgument(0) }

            val command = AccountMembership.ChangeMemberRoleCommand(
                accountId = accountId,
                actorUserId = ownerId,
                targetMembershipId = targetMembershipId,
                newRole = AccountRole.VIEWER,
            )
            accountMembershipService.changeMemberRole(command)

            assertThat(targetMembership.accountRole).isEqualTo(AccountRole.VIEWER)
            verify(accountMembershipRepository).save(targetMembership)
        }

        @Test
        fun `changeMemberRole throws ACCOUNT_ROLE_INSUFFICIENT when MEMBER actor`() {
            val accountId = UUID.randomUUID()
            val actorId = UUID.randomUUID()
            val targetMembershipId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = UUID.randomUUID(),
            )
            val actorMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = actorId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val targetMembership = AccountMembershipEntity(
                id = targetMembershipId,
                accountId = accountId,
                userId = UUID.randomUUID(),
                accountRole = AccountRole.VIEWER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(actorId)))
                .thenReturn(Optional.of(actorMembership))
            whenever(accountMembershipRepository.findById(eq(targetMembershipId)))
                .thenReturn(Optional.of(targetMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.CHANGE_ROLE),
                    any(),
                    any(),
                    eq(AccountRole.ADMIN),
                    anyOrNull(),
                    anyOrNull(),
                ),
            ).thenReturn(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))

            val command = AccountMembership.ChangeMemberRoleCommand(
                accountId = accountId,
                actorUserId = actorId,
                targetMembershipId = targetMembershipId,
                newRole = AccountRole.ADMIN,
            )
            assertThrows<ValidationException> {
                accountMembershipService.changeMemberRole(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT) }
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `changeMemberRole throws SELF_OPERATION_FORBIDDEN when changing own role`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val targetMembershipId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val ownerMembership = AccountMembershipEntity(
                id = targetMembershipId,
                accountId = accountId,
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(ownerId)))
                .thenReturn(Optional.of(ownerMembership))
            whenever(accountMembershipRepository.findById(eq(targetMembershipId)))
                .thenReturn(Optional.of(ownerMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.CHANGE_ROLE),
                    any(),
                    any(),
                    eq(AccountRole.ADMIN),
                    anyOrNull(),
                    anyOrNull(),
                ),
            ).thenReturn(MembershipPolicyResult.Denied(SecurityErrorCode.SELF_OPERATION_FORBIDDEN))

            val command = AccountMembership.ChangeMemberRoleCommand(
                accountId = accountId,
                actorUserId = ownerId,
                targetMembershipId = targetMembershipId,
                newRole = AccountRole.ADMIN,
            )
            assertThrows<ValidationException> {
                accountMembershipService.changeMemberRole(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.SELF_OPERATION_FORBIDDEN) }
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `changeMemberRole throws TARGET_NOT_ELIGIBLE when target not ACTIVE`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val targetMembershipId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val ownerMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val targetMembership = AccountMembershipEntity(
                id = targetMembershipId,
                accountId = accountId,
                userId = UUID.randomUUID(),
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.REMOVED,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(ownerId)))
                .thenReturn(Optional.of(ownerMembership))
            whenever(accountMembershipRepository.findById(eq(targetMembershipId)))
                .thenReturn(Optional.of(targetMembership))

            val command = AccountMembership.ChangeMemberRoleCommand(
                accountId = accountId,
                actorUserId = ownerId,
                targetMembershipId = targetMembershipId,
                newRole = AccountRole.VIEWER,
            )
            assertThrows<ValidationException> {
                accountMembershipService.changeMemberRole(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.TARGET_NOT_ELIGIBLE) }
            verify(accountMembershipRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("Remove member (FR-6.3)")
    inner class RemoveMemberTests {
        @Test
        fun `removeMember succeeds when OWNER removes MEMBER`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val targetMembershipId = UUID.randomUUID()
            val targetUserId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val ownerMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val targetMembership = AccountMembershipEntity(
                id = targetMembershipId,
                accountId = accountId,
                userId = targetUserId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(ownerId)))
                .thenReturn(Optional.of(ownerMembership))
            whenever(accountMembershipRepository.findById(eq(targetMembershipId)))
                .thenReturn(Optional.of(targetMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.REMOVE_MEMBER),
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                ),
            ).thenReturn(MembershipPolicyResult.Allowed)
            whenever(accountMembershipRepository.save(any())).thenAnswer { it.getArgument(0) }

            val command = AccountMembership.RemoveMemberCommand(
                accountId = accountId,
                actorUserId = ownerId,
                targetMembershipId = targetMembershipId,
            )
            accountMembershipService.removeMember(command)

            assertThat(targetMembership.membershipStatus).isEqualTo(MembershipStatus.REMOVED)
            assertThat(targetMembership.removedAt).isNotNull()
            assertThat(targetMembership.removedBy).isEqualTo(ownerId)
            verify(accountMembershipRepository).save(targetMembership)
        }

        @Test
        fun `removeMember succeeds when ADMIN removes VIEWER`() {
            val accountId = UUID.randomUUID()
            val adminId = UUID.randomUUID()
            val targetMembershipId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = UUID.randomUUID(),
            )
            val adminMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = adminId,
                accountRole = AccountRole.ADMIN,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val targetMembership = AccountMembershipEntity(
                id = targetMembershipId,
                accountId = accountId,
                userId = UUID.randomUUID(),
                accountRole = AccountRole.VIEWER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(adminId)))
                .thenReturn(Optional.of(adminMembership))
            whenever(accountMembershipRepository.findById(eq(targetMembershipId)))
                .thenReturn(Optional.of(targetMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.REMOVE_MEMBER),
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                ),
            ).thenReturn(MembershipPolicyResult.Allowed)
            whenever(accountMembershipRepository.save(any())).thenAnswer { it.getArgument(0) }

            val command = AccountMembership.RemoveMemberCommand(
                accountId = accountId,
                actorUserId = adminId,
                targetMembershipId = targetMembershipId,
            )
            accountMembershipService.removeMember(command)

            assertThat(targetMembership.membershipStatus).isEqualTo(MembershipStatus.REMOVED)
            verify(accountMembershipRepository).save(targetMembership)
        }

        @Test
        fun `removeMember throws TARGET_ROLE_TOO_HIGH when ADMIN removes OWNER`() {
            val accountId = UUID.randomUUID()
            val adminId = UUID.randomUUID()
            val targetMembershipId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = UUID.randomUUID(),
            )
            val adminMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = adminId,
                accountRole = AccountRole.ADMIN,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val targetMembership = AccountMembershipEntity(
                id = targetMembershipId,
                accountId = accountId,
                userId = UUID.randomUUID(),
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(adminId)))
                .thenReturn(Optional.of(adminMembership))
            whenever(accountMembershipRepository.findById(eq(targetMembershipId)))
                .thenReturn(Optional.of(targetMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.REMOVE_MEMBER),
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                ),
            ).thenReturn(MembershipPolicyResult.Denied(SecurityErrorCode.TARGET_ROLE_TOO_HIGH))

            val command = AccountMembership.RemoveMemberCommand(
                accountId = accountId,
                actorUserId = adminId,
                targetMembershipId = targetMembershipId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.removeMember(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.TARGET_ROLE_TOO_HIGH) }
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `removeMember throws SELF_OPERATION_FORBIDDEN when removing self`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val targetMembershipId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val ownerMembership = AccountMembershipEntity(
                id = targetMembershipId,
                accountId = accountId,
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(ownerId)))
                .thenReturn(Optional.of(ownerMembership))
            whenever(accountMembershipRepository.findById(eq(targetMembershipId)))
                .thenReturn(Optional.of(ownerMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.REMOVE_MEMBER),
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                ),
            ).thenReturn(MembershipPolicyResult.Denied(SecurityErrorCode.SELF_OPERATION_FORBIDDEN))

            val command = AccountMembership.RemoveMemberCommand(
                accountId = accountId,
                actorUserId = ownerId,
                targetMembershipId = targetMembershipId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.removeMember(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.SELF_OPERATION_FORBIDDEN) }
            verify(accountMembershipRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("Leave account (FR-6.4)")
    inner class LeaveAccountTests {
        @Test
        fun `leaveAccount succeeds when MEMBER leaves`() {
            val accountId = UUID.randomUUID()
            val memberId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = UUID.randomUUID(),
            )
            val memberMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = memberId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(memberId)))
                .thenReturn(Optional.of(memberMembership))
            whenever(accountMembershipRepository.findByAccountId(eq(accountId)))
                .thenReturn(listOf(memberMembership, AccountMembershipEntity(
                    accountId = accountId,
                    userId = UUID.randomUUID(),
                    accountRole = AccountRole.OWNER,
                    membershipStatus = MembershipStatus.ACTIVE,
                )))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.LEAVE),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    eq(false),
                ),
            ).thenReturn(MembershipPolicyResult.Allowed)
            whenever(accountMembershipRepository.save(any())).thenAnswer { it.getArgument(0) }

            val command = AccountMembership.LeaveAccountCommand(
                accountId = accountId,
                actorUserId = memberId,
            )
            accountMembershipService.leaveAccount(command)

            assertThat(memberMembership.membershipStatus).isEqualTo(MembershipStatus.REMOVED)
            assertThat(memberMembership.removedAt).isNotNull()
            verify(accountMembershipRepository).save(memberMembership)
        }

        @Test
        fun `leaveAccount throws SOLE_OWNER_CANNOT_LEAVE when sole OWNER leaves`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val ownerMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(ownerId)))
                .thenReturn(Optional.of(ownerMembership))
            whenever(accountMembershipRepository.findByAccountId(eq(accountId)))
                .thenReturn(listOf(ownerMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.LEAVE),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    eq(true),
                ),
            ).thenReturn(MembershipPolicyResult.Denied(SecurityErrorCode.SOLE_OWNER_CANNOT_LEAVE))

            val command = AccountMembership.LeaveAccountCommand(
                accountId = accountId,
                actorUserId = ownerId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.leaveAccount(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.SOLE_OWNER_CANNOT_LEAVE) }
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `leaveAccount throws NOT_ACCOUNT_MEMBER when not a member`() {
            val accountId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = UUID.randomUUID(),
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(userId)))
                .thenReturn(Optional.empty())

            val command = AccountMembership.LeaveAccountCommand(
                accountId = accountId,
                actorUserId = userId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.leaveAccount(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.NOT_ACCOUNT_MEMBER) }
            verify(accountMembershipRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("Transfer ownership (FR-6.5)")
    inner class TransferOwnershipTests {
        @Test
        fun `transferOwnership succeeds when OWNER transfers to ADMIN`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val newOwnerId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val ownerMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val newOwnerMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = newOwnerId,
                accountRole = AccountRole.ADMIN,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(ownerId)))
                .thenReturn(Optional.of(ownerMembership))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(newOwnerId)))
                .thenReturn(Optional.of(newOwnerMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.TRANSFER_OWNERSHIP),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    any(),
                    anyOrNull(),
                ),
            ).thenReturn(MembershipPolicyResult.Allowed)
            whenever(accountRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(accountMembershipRepository.save(any())).thenAnswer { it.getArgument(0) }

            val command = AccountMembership.TransferOwnershipCommand(
                accountId = accountId,
                actorUserId = ownerId,
                newOwnerUserId = newOwnerId,
            )
            accountMembershipService.transferOwnership(command)

            assertThat(account.ownerUserId).isEqualTo(newOwnerId)
            assertThat(ownerMembership.accountRole).isEqualTo(AccountRole.ADMIN)
            assertThat(newOwnerMembership.accountRole).isEqualTo(AccountRole.OWNER)
            verify(accountRepository).update(account)
            verify(accountMembershipRepository, times(2)).save(any())
        }

        @Test
        fun `transferOwnership throws ACCOUNT_ROLE_INSUFFICIENT when actor not OWNER`() {
            val accountId = UUID.randomUUID()
            val adminId = UUID.randomUUID()
            val newOwnerId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = UUID.randomUUID(),
            )
            val adminMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = adminId,
                accountRole = AccountRole.ADMIN,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val newOwnerMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = newOwnerId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(adminId)))
                .thenReturn(Optional.of(adminMembership))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(newOwnerId)))
                .thenReturn(Optional.of(newOwnerMembership))
            whenever(
                membershipPolicyEngine.evaluate(
                    eq(MembershipOperation.TRANSFER_OWNERSHIP),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    any(),
                    anyOrNull(),
                ),
            ).thenReturn(MembershipPolicyResult.Denied(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT))

            val command = AccountMembership.TransferOwnershipCommand(
                accountId = accountId,
                actorUserId = adminId,
                newOwnerUserId = newOwnerId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.transferOwnership(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT) }
            verify(accountRepository, never()).update(any())
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `transferOwnership throws TARGET_NOT_ELIGIBLE when new owner not ACTIVE`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val newOwnerId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Biz",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val ownerMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val newOwnerMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = newOwnerId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.REMOVED,
            )
            whenever(accountRepository.findById(eq(accountId))).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(ownerId)))
                .thenReturn(Optional.of(ownerMembership))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(eq(accountId), eq(newOwnerId)))
                .thenReturn(Optional.of(newOwnerMembership))

            val command = AccountMembership.TransferOwnershipCommand(
                accountId = accountId,
                actorUserId = ownerId,
                newOwnerUserId = newOwnerId,
            )
            assertThrows<ValidationException> {
                accountMembershipService.transferOwnership(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.TARGET_NOT_ELIGIBLE) }
            verify(accountRepository, never()).update(any())
            verify(accountMembershipRepository, never()).save(any())
        }
    }
}
