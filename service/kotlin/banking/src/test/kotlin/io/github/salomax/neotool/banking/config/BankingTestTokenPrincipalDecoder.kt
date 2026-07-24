package io.github.salomax.neotool.banking.config

import io.github.salomax.neotool.banking.shared.BankingPermissions
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.security.principal.TokenPrincipalDecoder
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Test decoder for banking REST/SSE ITs. Active only when micronaut.env=test.
 * Returns a principal with [currentAccountId] and [BankingPermissions.BANKING_ACCOUNT_VIEW].
 */
@Singleton
@Primary
@Requires(env = ["test"])
class BankingTestTokenPrincipalDecoder : TokenPrincipalDecoder {
    companion object {
        val TEST_ACCOUNT_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val TEST_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        const val TEST_TOKEN = "test-token"
        const val MCP_SERVICE_TOKEN = "mcp-service-token"
        const val OTHER_SERVICE_TOKEN = "other-service-token"
    }

    override fun fromToken(token: String): RequestPrincipal {
        if (token.isBlank()) throw AuthenticationRequiredException("Access token is required")
        return when (token) {
            MCP_SERVICE_TOKEN ->
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = TEST_USER_ID,
                    serviceId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
                    token = token,
                    tokenId = "mcp-service-token-id",
                    permissionsFromToken = listOf("service:call"),
                    userPermissions =
                        listOf(
                            BankingPermissions.BANKING_ACCOUNT_VIEW,
                            BankingPermissions.BANKING_TRANSACTION_CREATE,
                        ),
                    currentAccountId = TEST_ACCOUNT_ID,
                    delegatedBy = "mcp-service",
                )
            OTHER_SERVICE_TOKEN ->
                RequestPrincipal(
                    principalType = PrincipalType.SERVICE,
                    userId = TEST_USER_ID,
                    serviceId = UUID.fromString("20000000-0000-0000-0000-000000000002"),
                    token = token,
                    tokenId = "other-service-token-id",
                    permissionsFromToken = listOf("service:call"),
                    userPermissions =
                        listOf(
                            BankingPermissions.BANKING_ACCOUNT_VIEW,
                            BankingPermissions.BANKING_TRANSACTION_CREATE,
                        ),
                    currentAccountId = TEST_ACCOUNT_ID,
                    delegatedBy = "other-service",
                )
            else ->
                RequestPrincipal(
                    principalType = PrincipalType.USER,
                    userId = TEST_USER_ID,
                    serviceId = null,
                    token = token,
                    tokenId = "test-token-id",
                    permissionsFromToken =
                        listOf(
                            BankingPermissions.BANKING_ACCOUNT_VIEW,
                            BankingPermissions.BANKING_TRANSACTION_CREATE,
                        ),
                    currentAccountId = TEST_ACCOUNT_ID,
                )
        }
    }
}
