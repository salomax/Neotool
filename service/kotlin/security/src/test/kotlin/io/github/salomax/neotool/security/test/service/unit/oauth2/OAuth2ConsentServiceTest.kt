package io.github.salomax.neotool.security.test.service.unit.oauth2

import io.github.salomax.neotool.security.model.OAuth2ConsentEntity
import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountMembershipEntity
import io.github.salomax.neotool.security.model.account.AccountRole
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.OAuth2ConsentRepository
import io.github.salomax.neotool.security.service.oauth2.OAuth2ConsentService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class OAuth2ConsentServiceTest {
    private val consentRepository: OAuth2ConsentRepository = mock()
    private val membershipRepository: AccountMembershipRepository = mock()
    private val accountRepository: AccountRepository = mock()
    private val service = OAuth2ConsentService(consentRepository, membershipRepository, accountRepository)

    @Test
    fun `subset of existing consent does not require prompt and superset only prompts for delta`() {
        val userId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        stubDefaultAccount(userId, accountId)
        whenever(
            consentRepository.findOneByUserIdAndClientIdAndAccountIdAndRevokedAtIsNull(
                userId,
                "agent",
                accountId,
            ),
        ).thenReturn(
            Optional.of(
                OAuth2ConsentEntity(
                    userId = userId,
                    clientId = "agent",
                    accountId = accountId,
                    grantedScopes = "finance:cashflow:read finance:transactions:read",
                ),
            ),
        )

        val subset =
            service.consentRequirement(
                userId = userId,
                clientId = "agent",
                requestedScopes = listOf("finance:cashflow:read"),
            )
        val superset =
            service.consentRequirement(
                userId = userId,
                clientId = "agent",
                requestedScopes =
                    listOf(
                        "finance:cashflow:read",
                        "finance:transactions:read",
                        "finance:rules:write",
                    ),
            )

        assertThat(subset.requiresPrompt).isFalse()
        assertThat(superset.scopesNeedingConsent).containsExactly("finance:rules:write")
    }

    @Test
    fun `grants are independent per current account`() {
        val userId = UUID.randomUUID()
        val firstAccountId = UUID.randomUUID()
        val secondAccountId = UUID.randomUUID()
        stubDefaultAccount(userId, secondAccountId)
        whenever(
            consentRepository.findOneByUserIdAndClientIdAndAccountIdAndRevokedAtIsNull(
                userId,
                "agent",
                secondAccountId,
            ),
        ).thenReturn(Optional.empty())
        whenever(
            consentRepository.findOneByUserIdAndClientIdAndAccountIdAndRevokedAtIsNull(
                userId,
                "agent",
                firstAccountId,
            ),
        ).thenReturn(
            Optional.of(
                OAuth2ConsentEntity(
                    userId = userId,
                    clientId = "agent",
                    accountId = firstAccountId,
                    grantedScopes = "finance:cashflow:read",
                ),
            ),
        )

        val requirement =
            service.consentRequirement(
                userId = userId,
                clientId = "agent",
                requestedScopes = listOf("finance:cashflow:read"),
            )

        assertThat(requirement.accountId).isEqualTo(secondAccountId)
        assertThat(requirement.requiresPrompt).isTrue()
    }

    private fun stubDefaultAccount(
        userId: UUID,
        accountId: UUID,
    ) {
        whenever(
            membershipRepository.findOneByUserIdAndMembershipStatusAndIsDefaultTrue(
                userId,
                MembershipStatus.ACTIVE,
            ),
        ).thenReturn(
            Optional.of(
                AccountMembershipEntity(
                    accountId = accountId,
                    userId = userId,
                    accountRole = AccountRole.OWNER,
                    membershipStatus = MembershipStatus.ACTIVE,
                    isDefault = true,
                ),
            ),
        )
        whenever(accountRepository.findById(any())).thenReturn(
            Optional.of(
                AccountEntity(
                    id = accountId,
                    accountName = "Personal",
                    accountType = AccountType.PERSONAL,
                ),
            ),
        )
    }
}
