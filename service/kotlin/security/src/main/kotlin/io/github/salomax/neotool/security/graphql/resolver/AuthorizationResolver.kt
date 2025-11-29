package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.security.graphql.dto.AuthorizationResultDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.mapper.AuthorizationMapper
import io.github.salomax.neotool.security.service.AuthorizationService
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
            // Sanitize inputs
            val sanitizedUserId = sanitizeInput(userId, "userId")
            val sanitizedPermission = sanitizeAndValidatePermission(permission)
            val sanitizedResourceId = resourceId?.let { sanitizeInput(it, "resourceId") }

            // Validate UUIDs
            val userIdUuid = UUID.fromString(sanitizedUserId)
            val resourceIdUuid = sanitizedResourceId?.let { UUID.fromString(it) }

            logger.debug {
                "Checking permission: user=$userIdUuid, " +
                    "permission=$sanitizedPermission, " +
                    "resourceId=$resourceIdUuid"
            }

            val result =
                authorizationService.checkPermission(
                    userId = userIdUuid,
                    permission = sanitizedPermission,
                    resourceType = extractResourceType(sanitizedPermission),
                    resourceId = resourceIdUuid,
                )

            mapper.toAuthorizationResultDTO(result)
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid input for checkPermission: ${e.message}" }
            AuthorizationResultDTO(
                allowed = false,
                reason = "Invalid input provided",
            )
        } catch (e: Exception) {
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
     * @return List of PermissionDTO
     */
    fun getUserPermissions(userId: String): List<PermissionDTO> {
        return try {
            val sanitizedUserId = sanitizeInput(userId, "userId")
            val userIdUuid = UUID.fromString(sanitizedUserId)
            logger.debug { "Getting permissions for user: $userIdUuid" }

            val permissions = authorizationService.getUserPermissions(userIdUuid)
            permissions.map { mapper.toPermissionDTO(it) }
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID provided" }
            emptyList()
        } catch (e: Exception) {
            logger.error(e) { "Error getting user permissions" }
            emptyList()
        }
    }

    /**
     * Get all roles for a user.
     *
     * @param userId The user ID
     * @return List of RoleDTO
     */
    fun getUserRoles(userId: String): List<RoleDTO> {
        return try {
            val sanitizedUserId = sanitizeInput(userId, "userId")
            val userIdUuid = UUID.fromString(sanitizedUserId)
            logger.debug { "Getting roles for user: $userIdUuid" }

            val roles = authorizationService.getUserRoles(userIdUuid)
            roles.map { mapper.toRoleDTO(it) }
        } catch (e: IllegalArgumentException) {
            logger.warn { "Invalid user ID provided" }
            emptyList()
        } catch (e: Exception) {
            logger.error(e) { "Error getting user roles" }
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
