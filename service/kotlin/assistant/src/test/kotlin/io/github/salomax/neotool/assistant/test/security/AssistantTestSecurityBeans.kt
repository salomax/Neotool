package io.github.salomax.neotool.assistant.test.security

import io.github.salomax.neotool.assistant.provider.LLMProviderClient
import io.github.salomax.neotool.assistant.test.mock.MockProviderClient
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.principal.AccountSummary
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.security.principal.TokenPrincipalDecoder
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.util.UUID

@Factory
@Requires(env = ["test"])
class AssistantTestSecurityBeans {
    private val sharedMockProviderClient = MockProviderClient()

    @Singleton
    @Primary
    @Replaces(LLMProviderClient::class)
    fun llmProviderClient(): LLMProviderClient = sharedMockProviderClient

    @Singleton
    fun mockProviderClient(): MockProviderClient = sharedMockProviderClient

    @Singleton
    @Primary
    fun tokenPrincipalDecoder(): TokenPrincipalDecoder {
        return TokenPrincipalDecoder { token ->
            if (token.isBlank()) {
                throw AuthenticationRequiredException("Access token is required")
            }

            val accountId = parseAccountId(token) ?: DEFAULT_ACCOUNT_ID

            RequestPrincipal(
                principalType = PrincipalType.USER,
                userId = TEST_USER_ID,
                serviceId = null,
                token = token,
                permissionsFromToken = listOf("assistant:completion:generate"),
                currentAccountId = accountId,
                accounts = listOf(AccountSummary(accountId = accountId, role = "OWNER")),
            )
        }
    }

    private fun parseAccountId(token: String): UUID? {
        if (!token.startsWith(TOKEN_PREFIX)) {
            return null
        }
        val rawAccountId = token.removePrefix(TOKEN_PREFIX)
        return runCatching { UUID.fromString(rawAccountId) }.getOrNull()
    }

    companion object {
        const val TOKEN_PREFIX = "test-account:"
        val TEST_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val DEFAULT_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000101")
    }
}
