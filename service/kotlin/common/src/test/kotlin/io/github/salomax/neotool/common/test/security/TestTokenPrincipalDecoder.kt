package io.github.salomax.neotool.common.test.security

import io.github.salomax.neotool.security.service.RequestPrincipal
import io.github.salomax.neotool.security.service.TokenPrincipalDecoder
import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Test implementation of TokenPrincipalDecoder for integration tests.
 * Accepts any token and returns a mock principal with test user ID.
 * Used as primary bean in test environments.
 */
@Singleton
@Primary
@Requires(env = ["test"])
class TestTokenPrincipalDecoder : TokenPrincipalDecoder {
    companion object {
        val TEST_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        const val TEST_TOKEN = "test-token"
    }

    override fun fromToken(token: String): RequestPrincipal {
        if (token.isBlank()) {
            throw AuthenticationRequiredException("Access token is required")
        }

        // Accept any token in tests - return a mock principal
        // In real tests, you might want to parse claims from the token
        return RequestPrincipal(
            userId = TEST_USER_ID,
            token = token,
            permissionsFromToken = listOf(
                "assets:read",
                "assets:write",
                "assets:delete",
            ),
        )
    }
}
