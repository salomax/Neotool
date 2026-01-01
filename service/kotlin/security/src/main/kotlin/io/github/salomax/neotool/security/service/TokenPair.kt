package io.github.salomax.neotool.security.service

/**
 * Data class representing a pair of access and refresh tokens.
 *
 * Used when refreshing tokens to return both new tokens to the client.
 */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

