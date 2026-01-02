package io.github.salomax.neotool.security.service.oauth

import com.google.api.client.auth.openidconnect.IdToken
import com.google.api.client.json.gson.GsonFactory
import io.github.salomax.neotool.security.config.OAuthConfig
import io.github.salomax.neotool.security.service.oauth.OAuthProvider
import io.github.salomax.neotool.security.service.oauth.OAuthUserClaims
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Google OAuth provider implementation.
 *
 * Validates Google ID tokens using Google's public keys and extracts user claims.
 *
 * @see https://developers.google.com/identity/sign-in/web/backend-auth
 */
@Singleton
class GoogleOAuthProvider(
    private val oauthConfig: OAuthConfig,
) : OAuthProvider {
    private val logger = KotlinLogging.logger {}
    private val jsonFactory = GsonFactory.getDefaultInstance()

    override fun getProviderName(): String = "google"

    override fun validateAndExtractClaims(idToken: String): OAuthUserClaims? {
        // Use resolve methods to check environment variables at runtime
        val clientId = oauthConfig.google.resolveClientId()
        val issuer = oauthConfig.google.resolveIssuer()

        if (clientId.isBlank()) {
            logger.warn { "Google OAuth is not configured. Cannot validate token. GOOGLE_CLIENT_ID is not set." }
            return null
        }

        return try {
            // Parse the token first to check if it's valid format
            val parsedToken = IdToken.parse(jsonFactory, idToken)
            val payload = parsedToken.payload

            // Get token details for validation
            // Handle audience as either string or list
            val tokenAudienceList =
                try {
                    payload.audienceAsList ?: emptyList()
                } catch (e: Exception) {
                    // If audienceAsList fails, try to get it as a string
                    val audienceString = payload.audience
                    if (audienceString != null) {
                        listOf(audienceString)
                    } else {
                        throw IllegalArgumentException("Token verification failed: audience not found")
                    }
                }
            val tokenIssuer = payload.issuer
            val tokenExpiration = payload.expirationTimeSeconds
            val currentTime = System.currentTimeMillis() / 1000

            // Validate required fields
            if (tokenAudienceList.isEmpty()) {
                throw IllegalArgumentException("Token verification failed: audience not found")
            }
            if (tokenIssuer == null) {
                throw IllegalArgumentException("Token verification failed: issuer not found")
            }

            // Manually verify audience (replaces deprecated verifier.verify())
            if (!tokenAudienceList.contains(clientId)) {
                logger.warn {
                    "Token verification failed - audience mismatch. " +
                        "Token audience: $tokenAudienceList, Expected: [$clientId]"
                }
                throw IllegalArgumentException("Token verification failed: audience mismatch")
            }

            // Manually verify issuer (replaces deprecated verifier.verify())
            if (tokenIssuer != issuer) {
                logger.warn {
                    "Token verification failed - issuer mismatch. " +
                        "Token issuer: '$tokenIssuer', Expected: '$issuer'"
                }
                throw IllegalArgumentException("Token verification failed: issuer mismatch")
            }

            // Check expiration
            if (tokenExpiration != null && tokenExpiration < currentTime) {
                logger.warn { "Token has expired. Expiration: $tokenExpiration, Current: $currentTime" }
                throw IllegalArgumentException("Token has expired")
            }

            // Extract user claims from the payload
            // Google ID tokens use "sub" as the user ID, and "email" as a separate claim
            // The payload is a GenericData object, so we access claims by key
            val email =
                payload.get("email") as? String
                    ?: throw IllegalArgumentException("Email not found in token")

            val emailVerified = (payload.get("email_verified") as? Boolean) ?: false
            val name = payload.get("name") as? String
            val picture = payload.get("picture") as? String

            OAuthUserClaims(
                email = email,
                name = name,
                picture = picture,
                emailVerified = emailVerified,
            )
        } catch (e: Exception) {
            logger.warn { "Failed to validate Google ID token: ${e.message}" }
            null
        }
    }
}
