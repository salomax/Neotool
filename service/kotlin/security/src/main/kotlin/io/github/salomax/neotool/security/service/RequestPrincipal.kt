package io.github.salomax.neotool.security.service

import java.util.UUID

/**
 * Represents the authenticated principal for a request.
 * Supports both user and service principals with optional user context propagation.
 *
 * @param principalType The type of principal (USER or SERVICE)
 * @param userId The user ID (nullable for service-only tokens)
 * @param serviceId The service ID (nullable, only for service principals)
 * @param token The raw JWT token string
 * @param permissionsFromToken List of permissions extracted from the token claims (service permissions for service principals, user permissions for user principals)
 * @param userPermissions Optional user permissions when user context is propagated in a service token
 */
data class RequestPrincipal(
    val principalType: PrincipalType,
    val userId: UUID?,
    val serviceId: UUID?,
    val token: String,
    val permissionsFromToken: List<String>,
    val userPermissions: List<String>? = null,
) {
    /**
     * Convenience constructor for user principals (backward compatibility).
     */
    constructor(
        userId: UUID,
        token: String,
        permissionsFromToken: List<String>,
    ) : this(
        principalType = PrincipalType.USER,
        userId = userId,
        serviceId = null,
        token = token,
        permissionsFromToken = permissionsFromToken,
        userPermissions = null,
    )
}
