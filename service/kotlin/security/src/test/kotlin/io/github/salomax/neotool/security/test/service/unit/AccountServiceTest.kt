package io.github.salomax.neotool.security.test.service.unit

import io.github.salomax.neotool.security.domain.AccountManagement
import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.service.management.AccountService
import io.github.salomax.neotool.security.service.management.membership.MembershipPolicyEngine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var accountMembershipRepository: AccountMembershipRepository
    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        accountRepository = mock()
        accountMembershipRepository = mock()
        accountService = AccountService(accountRepository, accountMembershipRepository, MembershipPolicyEngine())
    }

    @Nested
    @DisplayName("getById")
    inner class GetByIdTests {
        @Test
        fun `should return account when found`() {
            val accountId = UUID.randomUUID()
            val entity = AccountEntity(
                id = accountId,
                accountName = "Test Account",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = UUID.randomUUID(),
            )
            whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(entity))

            val result = accountService.getById(accountId)

            assertThat(result).isNotNull
            assertThat(result!!.accountName).isEqualTo("Test Account")
            assertThat(result.accountType).isEqualTo(AccountType.BUSINESS)
        }

        @Test
        fun `should return null when not found`() {
            val accountId = UUID.randomUUID()
            whenever(accountRepository.findById(accountId)).thenReturn(Optional.empty())

            val result = accountService.getById(accountId)

            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("Create Account")
    inner class CreateAccountTests {
        @Test
        fun `should create FAMILY account and OWNER membership`() {
            val ownerId = UUID.randomUUID()
            val command = AccountManagement.CreateAccountCommand(
                accountName = "Family Account",
                accountType = AccountType.FAMILY,
                ownerUserId = ownerId,
            )
            val savedAccount = AccountEntity(
                id = UUID.randomUUID(),
                accountName = command.accountName,
                accountType = command.accountType,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            whenever(accountRepository.save(any())).thenReturn(savedAccount)
            whenever(accountMembershipRepository.save(any())).thenAnswer { it.arguments[0] as AccountMembershipEntity }

            val result = accountService.create(command)

            assertThat(result).isNotNull
            assertThat(result.accountName).isEqualTo("Family Account")
            assertThat(result.accountType).isEqualTo(AccountType.FAMILY)
            assertThat(result.ownerUserId).isEqualTo(ownerId)
            val membershipCaptor = argumentCaptor<AccountMembershipEntity>()
            verify(accountMembershipRepository).save(membershipCaptor.capture())
            assertThat(membershipCaptor.firstValue.accountRole).isEqualTo(AccountRole.OWNER)
            assertThat(membershipCaptor.firstValue.membershipStatus).isEqualTo(MembershipStatus.ACTIVE)
            assertThat(membershipCaptor.firstValue.isDefault).isFalse()
        }

        @Test
        fun `should create BUSINESS account and OWNER membership`() {
            val ownerId = UUID.randomUUID()
            val command = AccountManagement.CreateAccountCommand(
                accountName = "Biz Account",
                accountType = AccountType.BUSINESS,
                ownerUserId = ownerId,
            )
            val savedAccount = AccountEntity(
                id = UUID.randomUUID(),
                accountName = command.accountName,
                accountType = command.accountType,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            whenever(accountRepository.save(any())).thenReturn(savedAccount)
            whenever(accountMembershipRepository.save(any())).thenAnswer { it.arguments[0] as AccountMembershipEntity }

            val result = accountService.create(command)

            assertThat(result).isNotNull
            assertThat(result.accountType).isEqualTo(AccountType.BUSINESS)
        }

        @Test
        fun `should throw when account type is PERSONAL`() {
            assertThrows<ValidationException> {
                AccountManagement.CreateAccountCommand(
                    accountName = "Personal",
                    accountType = AccountType.PERSONAL,
                    ownerUserId = UUID.randomUUID(),
                )
            }.also { ex ->
                assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ACCOUNT_TYPE_MUST_BE_FAMILY_OR_BUSINESS)
            }
            verify(accountRepository, never()).save(any())
            verify(accountMembershipRepository, never()).save(any())
        }

        @Test
        fun `should throw when account name is blank`() {
            assertThrows<ValidationException> {
                AccountManagement.CreateAccountCommand(
                    accountName = "   ",
                    accountType = AccountType.BUSINESS,
                    ownerUserId = UUID.randomUUID(),
                )
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ACCOUNT_NAME_REQUIRED) }
        }

        @Test
        fun `should throw when account name exceeds 100 characters`() {
            assertThrows<ValidationException> {
                AccountManagement.CreateAccountCommand(
                    accountName = "a".repeat(101),
                    accountType = AccountType.BUSINESS,
                    ownerUserId = UUID.randomUUID(),
                )
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ACCOUNT_NAME_TOO_LONG) }
        }
    }

    @Nested
    @DisplayName("Update Account")
    inner class UpdateAccountTests {
        @Test
        fun `should update account name when actor is owner`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val existing = AccountEntity(
                id = accountId,
                accountName = "Old Name",
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
            whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(existing))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(accountId, ownerId))
                .thenReturn(Optional.of(ownerMembership))
            whenever(accountRepository.update(any())).thenAnswer { it.arguments[0] as AccountEntity }
            val command = AccountManagement.UpdateAccountCommand(
                accountId = accountId,
                accountName = "New Name",
                actorUserId = ownerId,
            )

            val result = accountService.update(command)

            assertThat(result.accountName).isEqualTo("New Name")
            assertThat(result.accountType).isEqualTo(AccountType.BUSINESS)
            verify(accountRepository).update(any())
        }

        @Test
        fun `should throw when account not found`() {
            val accountId = UUID.randomUUID()
            whenever(accountRepository.findById(accountId)).thenReturn(Optional.empty())
            val command = AccountManagement.UpdateAccountCommand(
                accountId = accountId,
                accountName = "New Name",
                actorUserId = UUID.randomUUID(),
            )

            assertThrows<IllegalArgumentException> {
                accountService.update(command)
            }.also { ex -> assertThat(ex.message).contains("not found") }
            verify(accountRepository, never()).update(any())
        }

        @Test
        fun `should throw when actor is not owner`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val otherUserId = UUID.randomUUID()
            val existing = AccountEntity(
                id = accountId,
                accountName = "Name",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val memberMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = otherUserId,
                accountRole = AccountRole.MEMBER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(existing))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(accountId, otherUserId))
                .thenReturn(Optional.of(memberMembership))
            val command = AccountManagement.UpdateAccountCommand(
                accountId = accountId,
                accountName = "New Name",
                actorUserId = otherUserId,
            )

            assertThrows<ValidationException> {
                accountService.update(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ACCOUNT_ROLE_INSUFFICIENT) }
            verify(accountRepository, never()).update(any())
        }

        @Test
        fun `should throw when account is already deleted`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val existing = AccountEntity(
                id = accountId,
                accountName = "Name",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.DELETED,
                ownerUserId = ownerId,
                deletedAt = Instant.now(),
            )
            whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(existing))
            val command = AccountManagement.UpdateAccountCommand(
                accountId = accountId,
                accountName = "New Name",
                actorUserId = ownerId,
            )

            assertThrows<IllegalArgumentException> {
                accountService.update(command)
            }.also { ex -> assertThat(ex.message).contains("deleted") }
            verify(accountRepository, never()).update(any())
        }
    }

    @Nested
    @DisplayName("Delete Account")
    inner class DeleteAccountTests {
        @Test
        fun `should soft delete account and mark memberships REMOVED when owner`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "To Delete",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val membership = AccountMembershipEntity(
                accountId = accountId,
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(accountId, ownerId))
                .thenReturn(Optional.of(membership))
            whenever(accountMembershipRepository.findByAccountId(accountId)).thenReturn(listOf(membership))
            whenever(accountRepository.update(any())).thenAnswer { it.arguments[0] as AccountEntity }
            whenever(accountMembershipRepository.update(any())).thenAnswer { it.arguments[0] as AccountMembershipEntity }
            val command = AccountManagement.DeleteAccountCommand(accountId = accountId, actorUserId = ownerId)

            accountService.delete(command)

            val accountCaptor = argumentCaptor<AccountEntity>()
            verify(accountRepository).update(accountCaptor.capture())
            assertThat(accountCaptor.firstValue.accountStatus).isEqualTo(AccountStatus.DELETED)
            assertThat(accountCaptor.firstValue.deletedAt).isNotNull()
            verify(accountMembershipRepository).update(any())
        }

        @Test
        fun `should throw when deleting and actor is not owner`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val otherUserId = UUID.randomUUID()
            val account = AccountEntity(
                id = accountId,
                accountName = "Name",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(accountId, otherUserId))
                .thenReturn(Optional.empty())
            val command = AccountManagement.DeleteAccountCommand(accountId = accountId, actorUserId = otherUserId)

            assertThrows<ValidationException> {
                accountService.delete(command)
            }.also { ex -> assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.NOT_ACCOUNT_MEMBER) }
            verify(accountRepository, never()).update(any())
        }

        @Test
        fun `should allow delete personal account when user has another ACTIVE membership`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val personalAccount = AccountEntity(
                id = accountId,
                accountName = "Personal",
                accountType = AccountType.PERSONAL,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val ownerMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            val otherMembership = AccountMembershipEntity(
                accountId = UUID.randomUUID(),
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(personalAccount))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(accountId, ownerId))
                .thenReturn(Optional.of(ownerMembership))
            whenever(accountMembershipRepository.findByUserIdAndMembershipStatus(ownerId, MembershipStatus.ACTIVE))
                .thenReturn(listOf(ownerMembership, otherMembership))
            whenever(accountMembershipRepository.findByAccountId(accountId)).thenReturn(listOf(ownerMembership))
            whenever(accountRepository.update(any())).thenAnswer { it.arguments[0] as AccountEntity }
            whenever(accountMembershipRepository.update(any())).thenAnswer { it.arguments[0] as AccountMembershipEntity }
            val command = AccountManagement.DeleteAccountCommand(accountId = accountId, actorUserId = ownerId)

            accountService.delete(command)

            verify(accountRepository).update(any())
        }

        @Test
        fun `should throw when deleting personal account and user has no other account`() {
            val accountId = UUID.randomUUID()
            val ownerId = UUID.randomUUID()
            val personalAccount = AccountEntity(
                id = accountId,
                accountName = "Personal",
                accountType = AccountType.PERSONAL,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerId,
            )
            val ownerMembership = AccountMembershipEntity(
                accountId = accountId,
                userId = ownerId,
                accountRole = AccountRole.OWNER,
                membershipStatus = MembershipStatus.ACTIVE,
            )
            whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(personalAccount))
            whenever(accountMembershipRepository.findOneByAccountIdAndUserId(accountId, ownerId))
                .thenReturn(Optional.of(ownerMembership))
            whenever(accountMembershipRepository.findByUserIdAndMembershipStatus(ownerId, MembershipStatus.ACTIVE))
                .thenReturn(listOf(ownerMembership))
            val command = AccountManagement.DeleteAccountCommand(accountId = accountId, actorUserId = ownerId)

            assertThrows<IllegalArgumentException> {
                accountService.delete(command)
            }.also { ex ->
                assertThat(ex.message).isNotNull
                assertThat(ex.message!!).contains("another")
            }
            verify(accountRepository, never()).update(any())
        }
    }
}
