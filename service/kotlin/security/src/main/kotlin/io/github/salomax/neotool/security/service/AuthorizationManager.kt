package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.service.exception.AuthorizationDeniedException
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID

/**
 * Generic authorization manager for request-level permission checks.
 * This manager provides a unified interface for authorization that can be used
 * across GraphQL resolvers, REST controllers, and future gRPC services.
 *
 * The manager enriches subject attributes with permissions from the token
 * to support ABAC evaluation while maintaining RBAC as the primary authorization mechanism.
 *
 * Implements [AuthorizationChecker] for compatibility with [AuthenticatedGraphQLWiringFactory].
 * The basic [require] method (from the interface) delegates to the full [require] method
 * with default parameters for simple permission checks.
 */
@Singleton
class AuthorizationManager(
    private val authorizationService: AuthorizationService,
) : AuthorizationChecker {
    private val logger = KotlinLogging.logger {}

    /**
     * Require a permission for the given principal (implements [AuthorizationChecker]).
     * This is the basic interface method that delegates to the full [require] method
     * with default parameters for simple permission checks.
     *
     * @param principal The authenticated request principal
     * @param permission The permission/action to check (e.g., "security:user:view")
     * @throws AuthorizationDeniedException if permission is denied
     */
    override fun require(principal: RequestPrincipal, permission: String) {
        require(principal, permission, null, null, null, null)
    }

    /**
     * Require a permission for the given principal with optional ABAC parameters.
     * Throws AuthorizationDeniedException if the permission is not granted.
     *
     * @param principal The authenticated request principal
     * @param action The permission/action to check (e.g., "security:user:view")
     * @param resourceType Optional resource type (e.g., "user")
     * @param resourceId Optional resource ID
     * @param resourceAttributes Optional additional resource attributes for ABAC
     * @param contextAttributes Optional additional context attributes for ABAC
     * @throws AuthorizationDeniedException if permission is denied
     */
    fun require(
        principal: RequestPrincipal,
        action: String,
        resourceType: String? = null,
        resourceId: UUID? = null,
        resourceAttributes: Map<String, Any>? = null,
        contextAttributes: Map<String, Any>? = null,
    ) {
        // Enrich subject attributes with permissions from token for ABAC evaluation
        val subjectAttributes =
            mapOf(
                "principalPermissions" to principal.permissionsFromToken,
            )

        // Call authorization service with principal-based routing
        val result =
            authorizationService.checkPermission(
                principal = principal,
                permission = action,
                resourceType = resourceType,
                resourceId = resourceId,
                // Can be extracted from resourceAttributes if needed
                resourcePattern = null,
                subjectAttributes = subjectAttributes,
                resourceAttributes = resourceAttributes,
                contextAttributes = contextAttributes,
            )

        // Log authorization decision for observability
        val identityInfo =
            when (principal.principalType) {
                PrincipalType.USER -> "userId=${principal.userId}"
                PrincipalType.SERVICE -> {
                    val userInfo = if (principal.userId != null) ", userId=${principal.userId}" else ""
                    "serviceId=${principal.serviceId}$userInfo"
                }
            }
        logger.debug {
            "Authorization check: $identityInfo, action=$action, " +
                "resourceType=$resourceType, resourceId=$resourceId, allowed=${result.allowed}"
        }

        // Throw exception if denied
        if (!result.allowed) {
            val identityDescription =
                when (principal.principalType) {
                    PrincipalType.USER -> "User ${principal.userId}"
                    PrincipalType.SERVICE -> {
                        val userDesc = if (principal.userId != null) " (with user ${principal.userId})" else ""
                        "Service ${principal.serviceId}$userDesc"
                    }
                }
            throw AuthorizationDeniedException("$identityDescription lacks permission '$action': ${result.reason}")
        }
    }
}
