package io.github.salomax.neotool.security.service

import java.util.UUID

/**
 * Normalized authentication context containing user identity and authorization data.
 *
 * This context is provider-agnostic and always contains non-null lists for roles and permissions,
 * even when empty, to avoid null handling downstream.
 *
 * @param userId The user's unique identifier
 * @param email The user's email address
 * @param displayName The user's display name (may be null)
 * @param roles List of role names assigned to the user (direct and group-inherited). Always non-null, empty list if none.
 * @param permissions List of permission names granted to the user (via roles). Always non-null, empty list if none.
 */
data class AuthContext(
    val userId: UUID,
    val email: String,
    val displayName: String?,
    // Always non-null, empty list if none
    val roles: List<String>,
    // Always non-null, empty list if none
    val permissions: List<String>,
)
