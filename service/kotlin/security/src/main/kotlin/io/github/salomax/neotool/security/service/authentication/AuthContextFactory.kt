package io.github.salomax.neotool.security.service.authentication

import io.github.salomax.neotool.common.security.principal.AuthContext
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.service.authorization.AuthorizationService
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Factory for creating normalized AuthContext from a resolved user entity.
 *
 * This factory's single responsibility is to:
 * 1. Load roles and groups for a user
 * 2. Call the AuthorizationService to get permissions
 * 3. Return a normalized AuthContext object with explicit empty lists (never null)
 *
 * The factory hides provider-specific differences - all authentication entry points
 * work the same way regardless of how the user authenticated.
 */
@Singleton
class AuthContextFactory(
    private val authorizationService: AuthorizationService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Build an AuthContext from a resolved user entity.
     *
     * Loads roles and permissions via AuthorizationService, handling exceptions gracefully.
     * Always returns non-null empty lists for roles and permissions when none exist.
     *
     * @param user The resolved user entity (from any authentication method)
     * @return Normalized AuthContext with userId, email, displayName, roles, and permissions
     * @throws IllegalStateException if user.id is null
     */
    fun build(user: UserEntity): AuthContext {
        val userId = user.id ?: throw IllegalStateException("User ID is required for AuthContext creation")

        // Load roles (direct and group-inherited)
        val roles =
            try {
                authorizationService.getUserRoles(userId).map { it.name }
            } catch (e: Exception) {
                logger.warn(e) {
                    "Failed to fetch roles for user ${user.email}, continuing with empty roles"
                }
                emptyList()
            }

        // Load permissions (from roles)
        val permissions =
            try {
                authorizationService.getUserPermissions(userId).map { it.name }
            } catch (e: Exception) {
                logger.warn(e) {
                    "Failed to fetch permissions for user ${user.email}, continuing with empty permissions"
                }
                emptyList()
            }

        return AuthContext(
            userId = userId,
            email = user.email,
            displayName = user.displayName,
            // Always non-null, empty list if none
            roles = roles,
            // Always non-null, empty list if none
            permissions = permissions,
        )
    }
}
