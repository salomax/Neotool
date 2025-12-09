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
 */
@Singleton
class AuthorizationManager(
    private val authorizationService: AuthorizationService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Require a permission for the given principal.
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

        // Call authorization service with enriched attributes
        val result =
            authorizationService.checkPermission(
                userId = principal.userId,
                permission = action,
                resourceType = resourceType,
                resourceId = resourceId,
                subjectAttributes = subjectAttributes,
                resourceAttributes = resourceAttributes,
                contextAttributes = contextAttributes,
            )

        // Log authorization decision for observability
        logger.debug {
            "Authorization check: userId=${principal.userId}, action=$action, " +
                "resourceType=$resourceType, resourceId=$resourceId, allowed=${result.allowed}"
        }

        // Throw exception if denied
        if (!result.allowed) {
            throw AuthorizationDeniedException("User ${principal.userId} lacks permission '$action': ${result.reason}")
        }
    }
}
