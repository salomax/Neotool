package io.github.salomax.neotool.security.test.graphql

import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.security.domain.AccountManagement
import io.github.salomax.neotool.security.domain.account.Account
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.graphql.resolver.AccountResolver
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.service.management.AccountService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@DisplayName("AccountResolver Unit Tests")
class AccountResolverTest {
    private lateinit var accountService: AccountService
    private lateinit var accountMembershipRepository: AccountMembershipRepository
    private lateinit var resolver: AccountResolver

    @BeforeEach
    fun setUp() {
        accountService = mock()
        accountMembershipRepository = mock()
        resolver = AccountResolver(accountService, accountMembershipRepository)
    }

    @Test
    fun `myAccounts should return memberships with owner flag`() {
        val userId = UUID.randomUUID()
        val ownedAccountId = UUID.randomUUID()
        val memberAccountId = UUID.randomUUID()
        val principal =
            RequestPrincipal(
                principalType = PrincipalType.USER,
                userId = userId,
                serviceId = null,
                token = "token",
                tokenId = "test-token-id",
                permissionsFromToken = emptyList(),
            )

        whenever(accountMembershipRepository.findByUserIdAndMembershipStatus(userId, MembershipStatus.ACTIVE))
            .thenReturn(
                listOf(
                    AccountMembershipEntity(
                        accountId = ownedAccountId,
                        userId = userId,
                        accountRole = AccountRole.OWNER,
                        membershipStatus = MembershipStatus.ACTIVE,
                    ),
                    AccountMembershipEntity(
                        accountId = memberAccountId,
                        userId = userId,
                        accountRole = AccountRole.MEMBER,
                        membershipStatus = MembershipStatus.ACTIVE,
                    ),
                ),
            )
        whenever(accountService.getById(ownedAccountId))
            .thenReturn(
                Account(
                    id = ownedAccountId,
                    accountName = "Owned Account",
                    accountType = AccountType.PERSONAL,
                    accountStatus = AccountStatus.ACTIVE,
                ),
            )
        whenever(accountService.getById(memberAccountId))
            .thenReturn(
                Account(
                    id = memberAccountId,
                    accountName = "Shared Account",
                    accountType = AccountType.BUSINESS,
                    accountStatus = AccountStatus.ACTIVE,
                ),
            )

        val result = resolver.myAccounts(principal)

        assertThat(result).hasSize(2)
        assertThat(result.first { it.id == ownedAccountId.toString() }.isOwner).isTrue()
        assertThat(result.first { it.id == memberAccountId.toString() }.isOwner).isFalse()
    }

    @Test
    fun `updateAccount should return updated account for owner`() {
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val principal =
            RequestPrincipal(
                principalType = PrincipalType.USER,
                userId = userId,
                serviceId = null,
                token = "token",
                tokenId = "test-token-id",
                permissionsFromToken = emptyList(),
            )
        whenever(accountService.update(any<AccountManagement.UpdateAccountCommand>()))
            .thenReturn(
                Account(
                    id = accountId,
                    accountName = "Renamed Account",
                    accountType = AccountType.FAMILY,
                    accountStatus = AccountStatus.ACTIVE,
                ),
            )
        whenever(accountMembershipRepository.findOneByAccountIdAndUserId(accountId, userId))
            .thenReturn(
                Optional.of(
                    AccountMembershipEntity(
                        accountId = accountId,
                        userId = userId,
                        accountRole = AccountRole.OWNER,
                        membershipStatus = MembershipStatus.ACTIVE,
                    ),
                ),
            )

        val result =
            resolver.updateAccount(
                accountId = accountId.toString(),
                input = mapOf("accountName" to "Renamed Account"),
                principal = principal,
            )

        assertThat(result.id).isEqualTo(accountId.toString())
        assertThat(result.accountName).isEqualTo("Renamed Account")
        assertThat(result.isOwner).isTrue()
    }

    @Test
    fun `updateAccount should reject missing accountName`() {
        val principal =
            RequestPrincipal(
                principalType = PrincipalType.USER,
                userId = UUID.randomUUID(),
                serviceId = null,
                token = "token",
                tokenId = "test-token-id",
                permissionsFromToken = emptyList(),
            )

        assertThrows<ValidationException> {
            resolver.updateAccount(
                accountId = UUID.randomUUID().toString(),
                input = emptyMap(),
                principal = principal,
            )
        }.also { ex ->
            assertThat(ex.errorCode).isEqualTo(SecurityErrorCode.ACCOUNT_NAME_REQUIRED)
        }
    }
}
