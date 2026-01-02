package io.github.salomax.neotool.security.service.oauth

/**
 * Data class representing OAuth user claims extracted from an ID token.
 */
data class OAuthUserClaims(
    val email: String,
    val name: String?,
    val picture: String?,
    val emailVerified: Boolean = false,
)

/**
 * Interface for OAuth providers.
 *
 * Each OAuth provider (Google, Microsoft, GitHub, etc.) implements this interface
 * to validate ID tokens and extract user claims.
 */
interface OAuthProvider {
    /**
     * Get the provider name (e.g., "google", "microsoft", "github")
     */
    fun getProviderName(): String

    /**
     * Validate an OAuth ID token and extract user claims.
     *
     * @param idToken The OAuth ID token (JWT) to validate
     * @return OAuthUserClaims if token is valid, null otherwise
     */
    fun validateAndExtractClaims(idToken: String): OAuthUserClaims?
}
