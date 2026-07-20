package io.github.salomax.neotool.security.service.oauth2

import io.github.salomax.neotool.security.config.OAuthConfig
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.SameSite
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class OAuth2Session(
    val userId: UUID,
    val expiresAt: Instant,
)

@Singleton
class OAuth2SessionService(
    private val oauthConfig: OAuthConfig,
) {
    @PostConstruct
    fun validateConfig() {
        signingSecret()
    }

    fun createSessionCookie(userId: UUID): Cookie {
        val expiresAt = Instant.now().plusSeconds(config().ttlSeconds)
        val value = signPayload("$userId:${expiresAt.epochSecond}")
        return Cookie.of(config().cookieName, value)
            .httpOnly(true)
            .secure(config().secure)
            // OAuth starts with a cross-site top-level navigation. SameSite=Strict drops
            // this cookie on the return to /oauth2/authorize and creates a silent login loop.
            .sameSite(SameSite.Lax)
            .path("/")
            .maxAge(config().ttlSeconds)
    }

    fun parse(cookieValue: String?): OAuth2Session? {
        if (cookieValue.isNullOrBlank()) {
            return null
        }
        val parts = cookieValue.split(".")
        if (parts.size != 2) {
            return null
        }
        val payload = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
        val expected = hmac(parts[0])
        if (!MessageDigest.isEqual(expected.toByteArray(), parts[1].toByteArray())) {
            return null
        }
        val payloadParts = payload.split(":")
        if (payloadParts.size != 2) {
            return null
        }
        val expiresAt = Instant.ofEpochSecond(payloadParts[1].toLongOrNull() ?: return null)
        if (!expiresAt.isAfter(Instant.now())) {
            return null
        }
        return OAuth2Session(
            userId = runCatching { UUID.fromString(payloadParts[0]) }.getOrNull() ?: return null,
            expiresAt = expiresAt,
        )
    }

    fun cookieName(): String = config().cookieName

    private fun signPayload(payload: String): String {
        val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        return "$encodedPayload.${hmac(encodedPayload)}"
    }

    private fun hmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingSecret().toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun signingSecret(): String =
        config().signingSecret.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("oauth.authorization-server.session.signing-secret is required")

    private fun config(): OAuthConfig.AuthorizationServerConfig.SessionConfig = oauthConfig.authorizationServer.session
}
