package io.github.salomax.neotool.security.test.service.unit.oauth2

import io.github.salomax.neotool.security.config.OAuthConfig
import io.github.salomax.neotool.security.service.oauth2.OAuth2SessionService
import io.micronaut.http.cookie.SameSite
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

@DisplayName("OAuth2SessionService Unit Tests")
class OAuth2SessionServiceTest {
    private val service =
        OAuth2SessionService(
            OAuthConfig(
                authorizationServer =
                    OAuthConfig.AuthorizationServerConfig(
                        session =
                            OAuthConfig.AuthorizationServerConfig.SessionConfig(
                                signingSecret = "test-session-secret",
                                ttlSeconds = 900,
                                cookieName = "INVISTUS_AUTH_SESSION",
                                secure = true,
                            ),
                    ),
            ),
        )

    @Test
    fun `should issue secure host-only lax http-only cookie`() {
        val cookie = service.createSessionCookie(UUID.randomUUID())

        assertThat(cookie.isHttpOnly).isTrue()
        assertThat(cookie.isSecure).isTrue()
        assertThat(cookie.sameSite.get()).isEqualTo(SameSite.Lax)
        assertThat(cookie.domain).isNull()
        assertThat(cookie.path).isEqualTo("/")
    }

    @Test
    fun `should reject tampered signature`() {
        val userId = UUID.randomUUID()
        val cookie = service.createSessionCookie(userId)

        assertThat(service.parse(cookie.value)).isNotNull
        assertThat(service.parse(cookie.value + "tampered")).isNull()
    }

    @Test
    fun `should reject expired cookie`() {
        val expiredService =
            OAuth2SessionService(
                OAuthConfig(
                    authorizationServer =
                        OAuthConfig.AuthorizationServerConfig(
                            session =
                                OAuthConfig.AuthorizationServerConfig.SessionConfig(
                                    signingSecret = "test-session-secret",
                                    ttlSeconds = -1,
                                ),
                        ),
                ),
            )

        val cookie = expiredService.createSessionCookie(UUID.randomUUID())

        assertThat(expiredService.parse(cookie.value)).isNull()
    }

    @Test
    fun `should fail when signing secret is blank`() {
        val blankSecretService =
            OAuth2SessionService(
                OAuthConfig(
                    authorizationServer =
                        OAuthConfig.AuthorizationServerConfig(
                            session =
                                OAuthConfig.AuthorizationServerConfig.SessionConfig(
                                    signingSecret = "",
                                ),
                        ),
                ),
            )

        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            blankSecretService.createSessionCookie(UUID.randomUUID())
        }
    }
}
