package io.github.salomax.neotool.security.graphql.resolver

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.security.graphql.dto.AuthorizationResultDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.mapper.AuthorizationMapper
import io.github.salomax.neotool.security.model.account.MembershipStatus
import io.github.salomax.neotool.security.repo.AccountMembershipRepository
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
    private val accountMembershipRepository: AccountMembershipRepository,
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
        // Sanitize and validate inputs
        val sanitizedUserId = sanitizeInput(userId, "userId")
        val sanitizedPermission = sanitizeAndValidatePermission(permission)
        val sanitizedResourceId = resourceId?.let { sanitizeInput(it, "resourceId") }

        val userIdUuid = parseUuid(sanitizedUserId, "userId")
        val resourceIdUuid = sanitizedResourceId?.let { parseUuid(it, "resourceId") }

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

        return mapper.toAuthorizationResultDTO(result)
    }

    /**
     * Get all permissions for a user.
     *
     * @param userId The user ID
     * @return List of PermissionDTO (empty list for invalid input)
     */
    fun getUserPermissions(userId: String): List<PermissionDTO> {
        val sanitizedUserId = sanitizeInput(userId, "userId")
        val userIdUuid = parseUuid(sanitizedUserId, "userId")

        logger.debug { "Getting permissions for user: $userIdUuid" }

        val permissions = authorizationService.getUserPermissions(userIdUuid)
        return permissions.map { mapper.toPermissionDTO(it) }
    }

    /**
     * Get all roles for a user.
     *
     * @param userId The user ID
     * @return List of RoleDTO (empty list for invalid input)
     */
    fun getUserRoles(userId: String): List<RoleDTO> {
        val sanitizedUserId = sanitizeInput(userId, "userId")
        val userIdUuid = parseUuid(sanitizedUserId, "userId")

        logger.debug { "Getting roles for user: $userIdUuid" }

        val roles = authorizationService.getUserRoles(userIdUuid)
        return roles.map { mapper.toRoleDTO(it) }
    }

    /**
     * Returns whether [userId] has an ACTIVE membership in [accountId].
     *
     * Intended for service-to-service account boundary validation.
     */
    fun isUserActiveMemberOfAccount(
        userId: String,
        accountId: String,
    ): Boolean {
        return try {
            val sanitizedUserId = sanitizeInput(userId, "userId")
            val sanitizedAccountId = sanitizeInput(accountId, "accountId")
            val userUuid = UUID.fromString(sanitizedUserId)
            val accountUuid = UUID.fromString(sanitizedAccountId)

            val membership =
                accountMembershipRepository
                    .findOneByAccountIdAndUserId(accountUuid, userUuid)
                    .orElse(null)
                    ?: return false

            membership.membershipStatus == MembershipStatus.ACTIVE
        } catch (e: IllegalArgumentException) {
            logger.warn(e) { "Invalid input in isUserActiveMemberOfAccount" }
            false
        } catch (e: Exception) {
            logger.error(e) { "Error in isUserActiveMemberOfAccount" }
            false
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
     * @throws ValidationException if permission is invalid
     */
    private fun sanitizeAndValidatePermission(permission: String): String {
        val trimmed = permission.trim()

        if (trimmed.isEmpty()) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "permission",
            )
        }

        if (trimmed.length > MAX_PERMISSION_LENGTH) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "permission",
                parameters = mapOf("maxLength" to MAX_PERMISSION_LENGTH),
            )
        }

        val normalized = trimmed.lowercase()

        if (!PERMISSION_PATTERN.matcher(normalized).matches()) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = "permission",
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
     * @throws ValidationException if input is invalid
     */
    private fun sanitizeInput(
        input: String,
        fieldName: String,
    ): String {
        val trimmed = input.trim()

        if (trimmed.isEmpty()) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = fieldName,
            )
        }

        if (trimmed.length > MAX_INPUT_LENGTH) {
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = fieldName,
                parameters = mapOf("maxLength" to MAX_INPUT_LENGTH),
            )
        }

        return trimmed
    }

    private fun parseUuid(
        value: String,
        fieldName: String,
    ): UUID {
        return try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            logger.warn(e) { "Invalid UUID format for field: $fieldName" }
            throw ValidationException(
                errorCode = CommonErrorCode.INVALID_INPUT,
                field = fieldName,
            )
        }
    }
}
