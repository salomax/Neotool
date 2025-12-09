package io.github.salomax.neotool.security.service

import java.util.UUID

/**
 * Represents the authenticated principal for a request.
 * Contains user identity and permissions extracted from the JWT token.
 *
 * @param userId The user ID from the token subject claim
 * @param token The raw JWT token string
 * @param permissionsFromToken List of permissions extracted from the token claims
 */
data class RequestPrincipal(
    val userId: UUID,
    val token: String,
    val permissionsFromToken: List<String>,
)
