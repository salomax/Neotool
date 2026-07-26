package io.github.salomax.neotool.security.service.oauth2

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("oauth.authorization-server.token.rate-limit")
data class OAuth2TokenRateLimitConfig(
    val limit: Long = 120,
    val windowMs: Long = 3_600_000,
)
