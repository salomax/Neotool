package io.github.salomax.neotool.security.test.repository

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.security.model.OAuth2ConsentEntity
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.model.account.AccountEntity
import io.github.salomax.neotool.security.model.account.AccountStatus
import io.github.salomax.neotool.security.model.account.AccountType
import io.github.salomax.neotool.security.repo.AccountRepository
import io.github.salomax.neotool.security.repo.OAuth2ConsentRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.test.SecurityTestDataBuilders
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@MicronautTest(startApplication = false)
@DisplayName("OAuth2ConsentRepository Integration Tests")
@Tag("integration")
class OAuth2ConsentRepositoryTest :
    BaseIntegrationTest(),
    PostgresIntegrationTest {
    @Inject
    lateinit var consentRepository: OAuth2ConsentRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Test
    fun `findByAccountIdAndRevokedAtIsNull scopes rows to the requested account`() {
        val user = createUser()
        val firstAccount = createAccount(user.id!!)
        val secondAccount = createAccount(user.id!!)
        val sharedClientId = "agent-shared"
        val firstConsent =
            consentRepository.save(
                OAuth2ConsentEntity(
                    userId = user.id!!,
                    clientId = sharedClientId,
                    accountId = firstAccount.id!!,
                    grantedScopes = "finance:transactions:read",
                    firstGrantedAt = Instant.parse("2026-07-30T12:00:00Z"),
                    lastGrantedAt = Instant.parse("2026-07-30T13:00:00Z"),
                ),
            )
        consentRepository.save(
            OAuth2ConsentEntity(
                userId = user.id!!,
                clientId = sharedClientId,
                accountId = secondAccount.id!!,
                grantedScopes = "finance:transactions:read",
                firstGrantedAt = Instant.parse("2026-07-30T12:00:00Z"),
                lastGrantedAt = Instant.parse("2026-07-30T14:00:00Z"),
            ),
        )
        consentRepository.save(
            OAuth2ConsentEntity(
                userId = user.id!!,
                clientId = "agent-revoked",
                accountId = firstAccount.id!!,
                grantedScopes = "finance:cashflow:read",
                revokedAt = Instant.parse("2026-07-30T15:00:00Z"),
                firstGrantedAt = Instant.parse("2026-07-30T11:00:00Z"),
                lastGrantedAt = Instant.parse("2026-07-30T11:00:00Z"),
            ),
        )

        val result = consentRepository.findByAccountIdAndRevokedAtIsNullOrderByLastGrantedAtDesc(firstAccount.id!!)

        assertThat(result.map { it.id }).containsExactly(firstConsent.id)
        assertThat(result.map { it.accountId }).containsOnly(firstAccount.id)
        assertThat(result.map { it.clientId }).containsOnly(sharedClientId)
    }

    private fun createUser(): UserEntity =
        userRepository.save(
            SecurityTestDataBuilders.user(
                id = null,
                email = SecurityTestDataBuilders.uniqueEmail("consent-repo"),
            ),
        )

    private fun createAccount(ownerUserId: UUID): AccountEntity =
        accountRepository.save(
            AccountEntity(
                accountName = "Connected Agents Account",
                accountType = AccountType.BUSINESS,
                accountStatus = AccountStatus.ACTIVE,
                ownerUserId = ownerUserId,
            ),
        )
}
