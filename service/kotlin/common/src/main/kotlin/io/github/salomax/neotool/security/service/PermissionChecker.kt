package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.service.exception.AuthorizationDeniedException
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Lightweight permission checker that validates permissions from JWT token claims.
 * This checker does not require database access and is suitable for use across all modules.
 *
 * For advanced authorization (ABAC policies, dynamic role checks), use AuthorizationManager
 * from the security module which performs database-backed authorization checks.
 */
@Singleton
class PermissionChecker : AuthorizationChecker {
    private val logger = KotlinLogging.logger {}

    /**
     * Check if the principal has the required permission.
     * Uses permissions from JWT token (no database access).
     *
     * @param principal The request principal with permissions from token
     * @param permission The permission to check (e.g., "assets:asset:view")
     * @throws AuthorizationDeniedException if permission is not granted
     */
    override fun require(principal: RequestPrincipal, permission: String) {
        val permissions = when {
            principal.principalType == PrincipalType.SERVICE && principal.userId != null && principal.userPermissions != null -> {
                // Service token with user context: use user permissions
                // Only use user permissions if explicitly provided (aligns with AuthorizationService behavior)
                principal.userPermissions
            }
            else -> {
                // User token or service token (with or without user context): use principal permissions
                // For service tokens with user_id but no user_permissions, fall back to service permissions
                principal.permissionsFromToken
            }
        }

        if (!permissions.contains(permission)) {
            val identityDescription =
                when (principal.principalType) {
                    PrincipalType.USER -> "User ${principal.userId}"
                    PrincipalType.SERVICE -> {
                        val userDesc = if (principal.userId != null) " (with user ${principal.userId})" else ""
                        "Service ${principal.serviceId}$userDesc"
                    }
                }
            logger.debug {
                "Permission denied: $identityDescription lacks permission '$permission'. " +
                    "Available permissions: ${permissions.joinToString()}"
            }
            throw AuthorizationDeniedException(
                "$identityDescription lacks permission '$permission'",
            )
        }

        logger.debug {
            "Permission granted: ${principal.userId ?: principal.serviceId} has permission '$permission'"
        }
    }
}

