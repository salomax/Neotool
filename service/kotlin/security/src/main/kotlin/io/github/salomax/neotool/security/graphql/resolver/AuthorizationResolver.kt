package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.security.graphql.dto.AuthorizationResultDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.mapper.AuthorizationMapper
import io.github.salomax.neotool.security.service.authorization.AuthorizationService
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.util.UUID
import java.util.regex.Pattern

/**
 * GraphQL resolver for authorization operations.
 * Provides queries for checking permissions and retrieving user authorization data.
 */
@Singleton
class AuthorizationResolver(
    private val authorizationService: AuthorizationService,
    private val mapper: AuthorizationMapper,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val MAX_PERMISSION_LENGTH = 255
        private const val MAX_INPUT_LENGTH = 1000
        private val PERMISSION_PATTERN = Pattern.compile("^[a-z0-9_-]+:[a-z0-9_-]+$")
    }

    /**
     * Check if a user has a specific permission.
     *
     * @param userId The user ID
     * @param permission The permission name (e.g., "transaction:update")
     * @param resourceId Optional resource ID
     * @return AuthorizationResultDTO with decision and reason
     */
    fun checkPermission(
        userId: String,
        permission: String,
        resourceId: String? = null,
    ): AuthorizationResultDTO {
        return try {
            // Sanitize and validate inputs - throw IllegalArgumentException for invalid input
            val sanitizedUserId = sanitizeInput(userId, "userId")
            val sanitizedPermission = sanitizeAndValidatePermission(permission)
            val sanitizedResourceId = resourceId?.let { sanitizeInput(it, "resourceId") }

            // Validate UUIDs - throw IllegalArgumentException for malformed UUIDs
            val userIdUuid =
                try {
                    UUID.fromString(sanitizedUserId)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid user ID format: $userId", e)
                }
            val resourceIdUuid =
                sanitizedResourceId?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("Invalid resource ID format: $resourceId", e)
                    }
                }

            logger.debug {
                "Checking permission: user=$userIdUuid, " +
                    "permission=$sanitizedPermission, " +
                    "resourceId=$resourceIdUuid"
            }

            // Call service - catch exceptions and return sanitized error messages
            val result =
                authorizationService.checkPermission(
                    userId = userIdUuid,
                    permission = sanitizedPermission,
                    resourceType = extractResourceType(sanitizedPermission),
                    resourceId = resourceIdUuid,
                )

            mapper.toAuthorizationResultDTO(result)
        } catch (e: IllegalArgumentException) {
            // Sanitize validation errors - don't expose internal details
            logger.warn(e) { "Invalid input in checkPermission" }
            AuthorizationResultDTO(
                allowed = false,
                reason = "Invalid input provided",
            )
        } catch (e: Exception) {
            // Sanitize all other errors - don't expose internal exception details
            logger.error(e) { "Error checking permission" }
            AuthorizationResultDTO(
                allowed = false,
                reason = "An error occurred while checking permission",
            )
        }
    }

    /**
     * Get all permissions for a user.
     *
     * @param userId The user ID
     * @return List of PermissionDTO (empty list for invalid input)
     */
    fun getUserPermissions(userId: String): List<PermissionDTO> {
        return try {
            // Sanitize and validate input - throw IllegalArgumentException for invalid input
            val sanitizedUserId = sanitizeInput(userId, "userId")
            val userIdUuid =
                try {
                    UUID.fromString(sanitizedUserId)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid user ID format: $userId", e)
                }

            logger.debug { "Getting permissions for user: $userIdUuid" }

            // Call service - catch exceptions and return empty list
            val permissions = authorizationService.getUserPermissions(userIdUuid)
            permissions.map { mapper.toPermissionDTO(it) }
        } catch (e: IllegalArgumentException) {
            // Invalid input - return empty list
            logger.warn(e) { "Invalid user ID in getUserPermissions: $userId" }
            emptyList()
        } catch (e: Exception) {
            // Service error - return empty list
            logger.error(e) { "Error getting user permissions for: $userId" }
            emptyList()
        }
    }

    /**
     * Get all roles for a user.
     *
     * @param userId The user ID
     * @return List of RoleDTO (empty list for invalid input)
     */
    fun getUserRoles(userId: String): List<RoleDTO> {
        return try {
            // Sanitize and validate input - throw IllegalArgumentException for invalid input
            val sanitizedUserId = sanitizeInput(userId, "userId")
            val userIdUuid =
                try {
                    UUID.fromString(sanitizedUserId)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid user ID format: $userId", e)
                }

            logger.debug { "Getting roles for user: $userIdUuid" }

            // Call service - catch exceptions and return empty list
            val roles = authorizationService.getUserRoles(userIdUuid)
            roles.map { mapper.toRoleDTO(it) }
        } catch (e: IllegalArgumentException) {
            // Invalid input - return empty list
            logger.warn(e) { "Invalid user ID in getUserRoles: $userId" }
            emptyList()
        } catch (e: Exception) {
            // Service error - return empty list
            logger.error(e) { "Error getting user roles for: $userId" }
            emptyList()
        }
    }

    /**
     * Extract resource type from permission string (e.g., "transaction:update" -> "transaction").
     */
    private fun extractResourceType(permission: String): String? {
        return permission.split(":").firstOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * Sanitize and validate permission name.
     * Validates format: resource:action pattern (e.g., "transaction:read")
     * Validates max length: 255 characters
     * Validates pattern: ^[a-z0-9_-]+:[a-z0-9_-]+$
     *
     * @param permission The permission name to validate
     * @return Sanitized (lowercase, trimmed) permission name
     * @throws IllegalArgumentException if permission is invalid
     */
    private fun sanitizeAndValidatePermission(permission: String): String {
        val trimmed = permission.trim()

        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Permission name cannot be empty")
        }

        if (trimmed.length > MAX_PERMISSION_LENGTH) {
            throw IllegalArgumentException(
                "Permission name exceeds maximum length of $MAX_PERMISSION_LENGTH characters",
            )
        }

        val normalized = trimmed.lowercase()

        if (!PERMISSION_PATTERN.matcher(normalized).matches()) {
            throw IllegalArgumentException(
                "Permission name must follow the format 'resource:action' (e.g., 'transaction:read')",
            )
        }

        return normalized
    }

    /**
     * Sanitize input string by trimming whitespace and validating length.
     *
     * @param input The input string to sanitize
     * @param fieldName The name of the field (for error messages)
     * @return Sanitized input string
     * @throws IllegalArgumentException if input is invalid
     */
    private fun sanitizeInput(
        input: String,
        fieldName: String,
    ): String {
        val trimmed = input.trim()

        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("$fieldName cannot be empty")
        }

        if (trimmed.length > MAX_INPUT_LENGTH) {
            throw IllegalArgumentException("$fieldName exceeds maximum length of $MAX_INPUT_LENGTH characters")
        }

        return trimmed
    }
}
