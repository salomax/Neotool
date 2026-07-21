package io.github.salomax.neotool.security.service.oauth2

import jakarta.inject.Singleton

interface OAuth2AccessTokenRevocationChecker {
    fun isRevoked(jwtId: String): Boolean
}

@Singleton
class NoOpOAuth2AccessTokenRevocationChecker : OAuth2AccessTokenRevocationChecker {
    override fun isRevoked(jwtId: String): Boolean = false
}
