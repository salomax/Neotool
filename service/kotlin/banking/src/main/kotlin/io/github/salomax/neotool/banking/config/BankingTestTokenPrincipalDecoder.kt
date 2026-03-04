package io.github.salomax.neotool.banking.config

import io.github.salomax.neotool.banking.domain.rbac.BankingPermissions
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
    }

    override fun fromToken(token: String): RequestPrincipal {
        if (token.isBlank()) throw AuthenticationRequiredException("Access token is required")
        return RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = TEST_USER_ID,
            serviceId = null,
            token = token,
            permissionsFromToken = listOf(BankingPermissions.BANKING_ACCOUNT_VIEW),
            currentAccountId = TEST_ACCOUNT_ID,
        )
    }
}
