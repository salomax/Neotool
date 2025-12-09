package io.github.salomax.neotool.security.test.service.unit

import com.google.api.client.auth.openidconnect.IdToken
import com.google.api.client.json.gson.GsonFactory
import io.jsonwebtoken.Jwts
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Date

/**
 * Test helper for creating Google ID token-like JWTs for testing.
 *
 * This helper creates tokens that match Google's ID token format and can be parsed
 * by Google's IdToken.parse() method. However, these tokens won't pass signature
 * verification with Google's public keys, so they're only useful for testing
 * the parsing and claim extraction logic.
 *
 * For full verification testing, you would need actual Google ID tokens or
 * a way to mock the IdTokenVerifier.
 */
object GoogleIdTokenTestHelper {
    private val jsonFactory = GsonFactory.getDefaultInstance()

    // Generate a test RSA key pair for signing tokens
    private val testKeyPair: KeyPair by lazy {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        keyGen.generateKeyPair()
    }

    /**
     * Create a Google ID token-like JWT for testing.
     *
     * This creates a token that can be parsed by IdToken.parse() but won't
     * pass signature verification with Google's public keys.
     *
     * @param clientId The audience (client ID) for the token
     * @param issuer The issuer (default: https://accounts.google.com)
     * @param email The user's email (required)
     * @param emailVerified Whether the email is verified
     * @param name The user's name (optional)
     * @param picture The user's picture URL (optional)
     * @param expirationSeconds Token expiration time in seconds from now (default: 3600)
     * @param issuedAtSeconds Token issued at time in seconds from now (default: now)
     * @return A JWT token string that can be parsed by IdToken.parse()
     */
    fun createTestToken(
        clientId: String = "test-client-id",
        issuer: String = "https://accounts.google.com",
        email: String = "test@example.com",
        emailVerified: Boolean = true,
        name: String? = "Test User",
        picture: String? = "https://example.com/picture.jpg",
        expirationSeconds: Long = 3600,
        issuedAtSeconds: Long = 0,
    ): String {
        val now = Instant.now()
        val issuedAt = now.plusSeconds(issuedAtSeconds)
        val expiration = now.plusSeconds(expirationSeconds)

        // Create a JWT with Google ID token structure
        return Jwts.builder()
            .claim("iss", issuer)
            .claim("aud", clientId)
            .claim("sub", "123456789") // Google user ID
            .claim("email", email)
            .claim("email_verified", emailVerified)
            .apply {
                if (name != null) {
                    claim("name", name)
                }
                if (picture != null) {
                    claim("picture", picture)
                }
            }
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiration))
            .signWith(testKeyPair.private)
            .compact()
    }

    /**
     * Create an expired token for testing expiration logic.
     */
    fun createExpiredToken(
        clientId: String = "test-client-id",
        issuer: String = "https://accounts.google.com",
        email: String = "test@example.com",
    ): String {
        // Expired 1 hour ago
        return createTestToken(
            clientId = clientId,
            issuer = issuer,
            email = email,
            expirationSeconds = -3600,
        )
    }

    /**
     * Create a token with wrong audience for testing audience validation.
     */
    fun createTokenWithWrongAudience(
        expectedClientId: String = "test-client-id",
        actualClientId: String = "wrong-client-id",
        issuer: String = "https://accounts.google.com",
        email: String = "test@example.com",
    ): String {
        return createTestToken(
            clientId = actualClientId,
            issuer = issuer,
            email = email,
        )
    }

    /**
     * Create a token with wrong issuer for testing issuer validation.
     */
    fun createTokenWithWrongIssuer(
        clientId: String = "test-client-id",
        expectedIssuer: String = "https://accounts.google.com",
        actualIssuer: String = "https://wrong-issuer.com",
        email: String = "test@example.com",
    ): String {
        return createTestToken(
            clientId = clientId,
            issuer = actualIssuer,
            email = email,
        )
    }

    /**
     * Create a token without email for testing email requirement.
     */
    fun createTokenWithoutEmail(
        clientId: String = "test-client-id",
        issuer: String = "https://accounts.google.com",
    ): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(3600)

        return Jwts.builder()
            .claim("iss", issuer)
            .claim("aud", clientId)
            .claim("sub", "123456789")
            // Intentionally omit email
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(testKeyPair.private)
            .compact()
    }

    /**
     * Parse a token using Google's IdToken.parse() to verify it's in the correct format.
     * This is useful for validating that our test tokens can be parsed.
     */
    fun parseToken(token: String): IdToken? {
        return try {
            IdToken.parse(jsonFactory, token)
        } catch (e: Exception) {
            null
        }
    }
}
