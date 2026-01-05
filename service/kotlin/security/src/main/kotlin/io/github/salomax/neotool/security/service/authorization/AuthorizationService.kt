package io.github.salomax.neotool.security.service.authorization

import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalPermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalPermissionRepositoryCustom
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID
import io.github.salomax.neotool.security.domain.audit.AuthorizationResult as AuditAuthorizationResult

/**
 * Service for authorization checks.
 * Supports both RBAC-only and hybrid RBAC+ABAC authorization.
 * Handles group-inherited role assignments.
 */
@Singleton
class AuthorizationService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val groupMembershipRepository: GroupMembershipRepository,
    private val groupRoleAssignmentRepository: GroupRoleAssignmentRepository,
    private val principalRepository: PrincipalRepository,
    private val principalPermissionRepository: PrincipalPermissionRepository,
    private val principalPermissionRepositoryCustom: PrincipalPermissionRepositoryCustom,
    private val abacEvaluationService: AbacEvaluationService,
    private val auditService: AuthorizationAuditService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Check if a user has a specific permission.
     * Checks group-inherited roles.
     * Also evaluates ABAC policies if they exist.
     *
     * @param userId The user ID
     * @param permission The permission name (e.g., "transaction:update")
     * @return AuthorizationResult with decision and reason
     */
    fun checkPermission(
        userId: UUID,
        permission: String,
    ): AuthorizationResult {
        // Delegate to the full checkPermission method which evaluates both RBAC and ABAC
        // Use explicit parameter names to call the overloaded method
        return checkPermission(
            userId,
            permission,
            // resourceType
            null,
            // resourceId
            null,
            // subjectAttributes
            null,
            // resourceAttributes
            null,
            // contextAttributes
            null,
        )
    }

    /**
     * Internal RBAC-only permission check.
     * This method performs the actual RBAC evaluation without ABAC.
     * Optimized to use lightweight permission check instead of loading all permissions.
     */
    private fun checkPermissionRbac(
        userId: UUID,
        permission: String,
        now: Instant = Instant.now(),
    ): AuthorizationResult {
        // Collect role IDs once
        val roleIds = collectUserRoleIds(userId, now)

        // Use optimized permission check
        return checkPermissionRbacWithRoleIds(permission, roleIds)
    }

    /**
     * Lightweight RBAC permission check using pre-fetched role IDs.
     * This avoids duplicate data fetching when roles are already loaded.
     *
     * @param permission The permission name to check
     * @param roleIds Pre-fetched role IDs for the user
     * @return AuthorizationResult with decision and reason
     */
    private fun checkPermissionRbacWithRoleIds(
        permission: String,
        roleIds: Set<UUID>,
    ): AuthorizationResult {
        if (roleIds.isEmpty()) {
            return AuthorizationResult(
                allowed = false,
                reason = "User does not have permission '$permission' (no roles assigned)",
            )
        }

        // Optimized check: directly query if permission exists for these roles
        val hasPermission = permissionRepository.existsPermissionForRoles(permission, roleIds.toList())

        return if (hasPermission) {
            AuthorizationResult(
                allowed = true,
                reason = "User has permission '$permission'",
            )
        } else {
            AuthorizationResult(
                allowed = false,
                reason = "User does not have permission '$permission'",
            )
        }
    }

    /**
     * Get all permissions for a user, including group-inherited permissions.
     * Optimized to use batch loading to avoid N+1 queries.
     *
     * @param userId The user ID
     * @param now Current timestamp for validity checks
     * @return List of permissions
     */
    fun getUserPermissions(
        userId: UUID,
        now: Instant = Instant.now(),
    ): List<Permission> {
        // Collect all role IDs (direct and group-inherited) using shared logic
        val roleIds = collectUserRoleIds(userId, now)

        // Batch load all permission IDs for all roles at once
        if (roleIds.isEmpty()) {
            return emptyList()
        }

        val allPermissionIds = roleRepository.findPermissionIdsByRoleIds(roleIds.toList())

        // Batch load all permissions at once
        if (allPermissionIds.isEmpty()) {
            return emptyList()
        }

        val permissions = permissionRepository.findByIdIn(allPermissionIds)
        return permissions.map { it.toDomain() }
    }

    /**
     * Batch get all permissions for multiple users, including group-inherited permissions.
     * Optimized to avoid N+1 queries.
     *
     * @param userIds List of user IDs
     * @param now Current timestamp for validity checks
     * @return Map of user ID to list of permissions
     */
    fun getUserPermissionsBatch(
        userIds: List<UUID>,
        now: Instant = Instant.now(),
    ): Map<UUID, List<Permission>> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        // First get all roles for all users (reuse batch method)
        val userRolesMap = getUserRolesBatch(userIds, now)

        // Collect all unique role IDs
        val allRoleIds =
            userRolesMap.values
                .flatten()
                .mapNotNull { it.id }
                .distinct()

        // Batch load all permission IDs for all roles at once
        val allPermissionIds =
            if (allRoleIds.isNotEmpty()) {
                roleRepository.findPermissionIdsByRoleIds(allRoleIds)
            } else {
                emptyList()
            }

        // Batch load all permissions at once
        val allPermissions =
            if (allPermissionIds.isNotEmpty()) {
                permissionRepository
                    .findByIdIn(allPermissionIds.distinct())
                    .map { it.toDomain() }
                    .associateBy { it.id!! }
            } else {
                emptyMap()
            }

        // For each role, get its permission IDs (we need this mapping)
        // Note: This is still per-role, but at least it's batched per role, not per user
        val rolePermissionIdsMap = mutableMapOf<UUID, Set<UUID>>()
        for (roleId in allRoleIds) {
            val permissionIds = roleRepository.findPermissionIdsByRoleId(roleId).toSet()
            rolePermissionIdsMap[roleId] = permissionIds
        }

        // Build result map: for each user, collect permissions from all their roles
        val result = mutableMapOf<UUID, List<Permission>>()
        for (userId in userIds) {
            val roles = userRolesMap[userId] ?: emptyList()
            val permissionIds =
                roles
                    .mapNotNull { role ->
                        role.id?.let { rolePermissionIdsMap[it] }
                    }.flatten()
                    .distinct()
            val permissions = permissionIds.mapNotNull { permissionId -> allPermissions[permissionId] }
            result[userId] = permissions
        }

        return result
    }

    /**
     * Get all roles for a user, including group-inherited roles.
     * Optimized to use batch loading and shared logic.
     *
     * @param userId The user ID
     * @param now Current timestamp for validity checks
     * @return List of roles
     */
    fun getUserRoles(
        userId: UUID,
        now: Instant = Instant.now(),
    ): List<Role> {
        // Collect all role IDs using shared logic
        val roleIds = collectUserRoleIds(userId, now)

        if (roleIds.isEmpty()) {
            return emptyList()
        }

        // Batch load all role entities at once
        val roles = roleRepository.findByIdIn(roleIds.toList())
        return roles.mapNotNull { it.toDomain() }
    }

    /**
     * Batch get all roles for multiple users, including group-inherited roles.
     * Optimized to avoid N+1 queries.
     *
     * @param userIds List of user IDs
     * @param now Current timestamp for validity checks
     * @return Map of user ID to list of roles
     */
    fun getUserRolesBatch(
        userIds: List<UUID>,
        now: Instant = Instant.now(),
    ): Map<UUID, List<Role>> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        // Batch load all group memberships for all users
        val allGroupMemberships = groupMembershipRepository.findActiveMembershipsByUserIds(userIds, now)

        // Collect all group IDs
        val allGroupIds = allGroupMemberships.map { it.groupId }.distinct()

        // Batch load all group role assignments
        val allGroupAssignments =
            if (allGroupIds.isNotEmpty()) {
                groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(allGroupIds, now)
            } else {
                emptyList()
            }

        // Create a map of group ID to role IDs
        val groupRoleIdsMap =
            allGroupAssignments
                .groupBy { it.groupId }
                .mapValues { (_, assignments) -> assignments.map { it.roleId }.toSet() }

        // Create a map of user ID to group IDs
        val userGroupIdsMap =
            allGroupMemberships
                .groupBy { it.userId }
                .mapValues { (_, memberships) -> memberships.map { it.groupId }.toSet() }

        // Collect all role IDs per user
        val userRoleIdsMap = mutableMapOf<UUID, MutableSet<UUID>>()
        for (userId in userIds) {
            userRoleIdsMap[userId] = mutableSetOf()
        }

        // Add group-inherited role assignments
        for ((userId, groupIds) in userGroupIdsMap) {
            for (groupId in groupIds) {
                val roleIds = groupRoleIdsMap[groupId] ?: emptySet()
                userRoleIdsMap[userId]?.addAll(roleIds)
            }
        }

        // Collect all unique role IDs
        val allRoleIds = userRoleIdsMap.values.flatten().distinct()

        // Batch load all roles
        val allRoles =
            if (allRoleIds.isNotEmpty()) {
                roleRepository
                    .findByIdIn(allRoleIds)
                    .mapNotNull { it.toDomain() }
                    .associateBy { it.id!! }
            } else {
                emptyMap()
            }

        // Build result map
        val result = mutableMapOf<UUID, List<Role>>()
        for (userId in userIds) {
            val roleIds = userRoleIdsMap[userId] ?: emptySet()
            val roles = roleIds.mapNotNull { roleId -> allRoles[roleId] }
            result[userId] = roles
        }

        return result
    }

    /**
     * Collect all role IDs for a user (group-inherited).
     * This shared method eliminates code duplication between getUserPermissions and getUserRoles.
     * Optimized to use batch loading for group role assignments to avoid N+1 queries.
     *
     * @param userId The user ID
     * @param now Current timestamp for validity checks
     * @return Set of unique role IDs
     */
    private fun collectUserRoleIds(
        userId: UUID,
        now: Instant,
    ): Set<UUID> {
        val roleIds = mutableSetOf<UUID>()

        // Get group memberships
        val groupMemberships = groupMembershipRepository.findActiveMembershipsByUserId(userId, now)

        // Batch load group role assignments to avoid N+1 queries
        if (groupMemberships.isNotEmpty()) {
            val groupIds = groupMemberships.map { it.groupId }
            val groupAssignments = groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(groupIds, now)
            roleIds.addAll(groupAssignments.map { it.roleId })
        }

        return roleIds
    }

    /**
     * Check if a user has permission to perform an action on a resource.
     * Evaluates RBAC first, then ABAC if RBAC allows.
     * This is an overloaded method that combines RBAC and ABAC evaluation.
     *
     * @param userId The user ID
     * @param permission The permission name (e.g., "transaction:update")
     * @param resourceType Optional resource type (e.g., "transaction")
     * @param resourceId Optional resource ID
     * @param subjectAttributes Optional additional subject attributes for ABAC
     * @param resourceAttributes Optional additional resource attributes for ABAC
     * @param contextAttributes Optional additional context attributes for ABAC
     * @return AuthorizationResult with decision and reason
     */
    fun checkPermission(
        userId: UUID,
        permission: String,
        resourceType: String? = null,
        resourceId: UUID? = null,
        subjectAttributes: Map<String, Any>? = null,
        resourceAttributes: Map<String, Any>? = null,
        contextAttributes: Map<String, Any>? = null,
    ): AuthorizationResult {
        val now = Instant.now()

        // Step 1: Fetch user context once (role IDs, roles, groups)
        // This eliminates duplicate data fetching between RBAC check and ABAC evaluation
        val userContext = fetchUserContext(userId, now)

        // Step 2: Evaluate RBAC using pre-fetched role IDs (lightweight check)
        val rbacResult = checkPermissionRbacWithRoleIds(permission, userContext.roleIds)
        val rbacAuditResult = toAuditAuthorizationResult(rbacResult.allowed)

        // Step 3: If RBAC denies, short-circuit (don't evaluate ABAC)
        if (!rbacResult.allowed) {
            // Log audit entry
            auditService.logAuthorizationDecision(
                userId = userId,
                groups = userContext.groups,
                roles = userContext.roles.mapNotNull { it.id },
                requestedAction = permission,
                resourceType = resourceType,
                resourceId = resourceId,
                rbacResult = rbacAuditResult,
                abacResult = null,
                finalDecision = AuditAuthorizationResult.DENIED,
                metadata = mapOf("reason" to rbacResult.reason),
            )

            return AuthorizationResult(
                allowed = false,
                reason = rbacResult.reason,
            )
        }

        // Step 4: RBAC allows, evaluate ABAC
        // Build subject attributes for ABAC using pre-fetched context
        val abacSubjectAttributes =
            buildSubjectAttributes(
                userId = userId,
                roles = userContext.roles,
                groups = userContext.groups,
                additionalAttributes = subjectAttributes,
            )

        // Build resource attributes for ABAC
        val abacResourceAttributes =
            buildResourceAttributes(
                resourceType = resourceType,
                resourceId = resourceId,
                additionalAttributes = resourceAttributes,
            )

        // Evaluate ABAC policies
        val abacResult =
            abacEvaluationService.evaluatePolicies(
                subjectAttributes = abacSubjectAttributes,
                resourceAttributes = abacResourceAttributes,
                contextAttributes = contextAttributes,
            )

        val abacAuditResult = toAuditAuthorizationResult(abacResult.decision)

        // Step 5: Determine final decision
        // Explicit deny from ABAC overrides allow
        val finalAllowed =
            when (abacResult.decision) {
                io.github.salomax.neotool.security.domain.abac.PolicyEffect.DENY -> false
                io.github.salomax.neotool.security.domain.abac.PolicyEffect.ALLOW -> true
                null -> true // No ABAC policies matched, default to allow (RBAC already allowed)
            }
        val finalAuditResult = toAuditAuthorizationResult(finalAllowed)

        // Log audit entry
        auditService.logAuthorizationDecision(
            userId = userId,
            groups = userContext.groups,
            roles = userContext.roles.mapNotNull { it.id },
            requestedAction = permission,
            resourceType = resourceType,
            resourceId = resourceId,
            rbacResult = rbacAuditResult,
            abacResult = abacAuditResult,
            finalDecision = finalAuditResult,
            metadata =
                mapOf(
                    "rbacReason" to rbacResult.reason,
                    "abacReason" to abacResult.reason,
                    "matchedPolicies" to abacResult.matchedPolicies.map { it.name },
                ),
        )

        return AuthorizationResult(
            allowed = finalAllowed,
            reason =
                if (finalAllowed) {
                    if (abacResult.decision != null) {
                        "Access granted: RBAC allowed, ABAC allowed"
                    } else {
                        // No ABAC policies matched, use simple RBAC message
                        "Access granted: RBAC allowed, no policies matched"
                    }
                } else {
                    "Access denied: ABAC policy explicitly denies access"
                },
        )
    }

    /**
     * Check if a service has a specific permission.
     * Queries the principal_permissions table for service principal permissions.
     *
     * @param serviceId The service ID
     * @param permission The permission name (e.g., "assets:upload")
     * @param resourcePattern Optional resource pattern for resource-specific permissions
     * @return AuthorizationResult with decision and reason
     */
    fun checkServicePermission(
        serviceId: UUID,
        permission: String,
        resourcePattern: String? = null,
    ): AuthorizationResult {
        // Find the service principal by external_id (service ID)
        val principal =
            principalRepository
                .findByPrincipalTypeAndExternalId(
                    PrincipalType.SERVICE,
                    serviceId.toString(),
                ).orElse(null)
                ?: return AuthorizationResult(
                    allowed = false,
                    reason = "Service principal not found for service ID: $serviceId",
                )

        // Check if principal is enabled
        if (!principal.enabled) {
            return AuthorizationResult(
                allowed = false,
                reason = "Service principal is disabled for service ID: $serviceId",
            )
        }

        // Find the permission by name
        val permissionEntity =
            permissionRepository.findByName(permission).orElse(null)
                ?: return AuthorizationResult(
                    allowed = false,
                    reason = "Permission not found: $permission",
                )

        // Check if principal has the permission
        val hasPermission =
            principalPermissionRepositoryCustom.existsByPrincipalIdAndPermissionIdAndResourcePattern(
                principal.id!!,
                permissionEntity.id!!,
                resourcePattern,
            )

        val result =
            if (hasPermission) {
                AuthorizationResult(
                    allowed = true,
                    reason = "Service has permission '$permission'",
                )
            } else {
                AuthorizationResult(
                    allowed = false,
                    reason = "Service does not have permission '$permission'",
                )
            }

        // Log audit entry for service permission check
        val auditResult = toAuditAuthorizationResult(result.allowed)
        auditService.logAuthorizationDecision(
            // Use serviceId as userId for audit log (audit table requires userId)
            userId = serviceId,
            groups = null,
            roles = null,
            requestedAction = permission,
            resourceType = null,
            resourceId = null,
            rbacResult = auditResult,
            abacResult = null,
            finalDecision = auditResult,
            metadata =
                mapOf(
                    "principalType" to "service",
                    "serviceId" to serviceId.toString(),
                    "resourcePattern" to (resourcePattern ?: ""),
                    "reason" to result.reason,
                ),
        )

        return result
    }

    /**
     * Check permission for a request principal.
     * Routes to service permission check or user permission check based on principal type.
     * When both service and user context are present, both must have the required permission.
     *
     * @param principal The request principal (user or service)
     * @param permission The permission name
     * @param resourceType Optional resource type
     * @param resourceId Optional resource ID
     * @param resourcePattern Optional resource pattern (for service permissions)
     * @param subjectAttributes Optional additional subject attributes for ABAC
     * @param resourceAttributes Optional additional resource attributes for ABAC
     * @param contextAttributes Optional additional context attributes for ABAC
     * @return AuthorizationResult with decision and reason
     */
    fun checkPermission(
        principal: RequestPrincipal,
        permission: String,
        resourceType: String? = null,
        resourceId: UUID? = null,
        resourcePattern: String? = null,
        subjectAttributes: Map<String, Any>? = null,
        resourceAttributes: Map<String, Any>? = null,
        contextAttributes: Map<String, Any>? = null,
    ): AuthorizationResult {
        return when (principal.principalType) {
            PrincipalType.SERVICE -> {
                // Check service permission
                val serviceResult =
                    checkServicePermission(
                        serviceId =
                            principal.serviceId ?: return AuthorizationResult(
                                allowed = false,
                                reason = "Service ID is required for service principal",
                            ),
                        permission = permission,
                        resourcePattern = resourcePattern,
                    )

                // If service token has user context, also check user permission
                if (principal.userId != null && principal.userPermissions != null) {
                    val userId =
                        principal.userId
                            ?: return AuthorizationResult(
                                allowed = false,
                                reason = "User ID is required for propagated user context",
                            )
                    val userResult =
                        checkPermission(
                            userId = userId,
                            permission = permission,
                            resourceType = resourceType,
                            resourceId = resourceId,
                            subjectAttributes = subjectAttributes,
                            resourceAttributes = resourceAttributes,
                            contextAttributes = contextAttributes,
                        )

                    // Both service and user must have permission
                    if (!serviceResult.allowed) {
                        return AuthorizationResult(
                            allowed = false,
                            reason = "Service permission denied: ${serviceResult.reason}",
                        )
                    }
                    if (!userResult.allowed) {
                        return AuthorizationResult(
                            allowed = false,
                            reason = "User permission denied: ${userResult.reason}",
                        )
                    }

                    return AuthorizationResult(
                        allowed = true,
                        reason = "Both service and user have permission '$permission'",
                    )
                } else {
                    serviceResult
                }
            }

            PrincipalType.USER -> {
                // Use existing user permission check
                checkPermission(
                    userId =
                        principal.userId ?: return AuthorizationResult(
                            allowed = false,
                            reason = "User ID is required for user principal",
                        ),
                    permission = permission,
                    resourceType = resourceType,
                    resourceId = resourceId,
                    subjectAttributes = subjectAttributes,
                    resourceAttributes = resourceAttributes,
                    contextAttributes = contextAttributes,
                )
            }
        }
    }

    /**
     * Get user groups for ABAC evaluation.
     */
    private fun getUserGroups(
        userId: UUID,
        now: Instant = Instant.now(),
    ): List<UUID> {
        val memberships = groupMembershipRepository.findActiveMembershipsByUserId(userId, now)
        return memberships.map { it.groupId }
    }

    /**
     * User context data structure containing roles and groups.
     * Used to avoid duplicate fetching in hybrid authorization checks.
     */
    private data class UserContext(
        val roleIds: Set<UUID>,
        val roles: List<Role>,
        val groups: List<UUID>,
    )

    /**
     * Fetch user context (role IDs, roles, and groups) in a single optimized call.
     * This method collects all necessary data once to avoid duplicate queries.
     *
     * @param userId The user ID
     * @param now Current timestamp for validity checks
     * @return UserContext with role IDs, roles, and groups
     */
    private fun fetchUserContext(
        userId: UUID,
        now: Instant,
    ): UserContext {
        // Fetch role IDs once (this is the expensive part)
        val roleIds = collectUserRoleIds(userId, now)

        // Batch load roles if we have any
        val roles =
            if (roleIds.isNotEmpty()) {
                roleRepository.findByIdIn(roleIds.toList()).mapNotNull { it.toDomain() }
            } else {
                emptyList()
            }

        // Fetch groups
        val groups = getUserGroups(userId, now)

        return UserContext(roleIds, roles, groups)
    }

    /**
     * Build subject attributes for ABAC evaluation.
     */
    private fun buildSubjectAttributes(
        userId: UUID,
        roles: List<Role>,
        groups: List<UUID>,
        additionalAttributes: Map<String, Any>?,
    ): Map<String, Any> {
        val attributes =
            mutableMapOf<String, Any>(
                "userId" to userId.toString(),
                "roles" to roles.map { it.name },
                "roleIds" to roles.mapNotNull { it.id },
                "groups" to groups.map { it.toString() },
            )

        additionalAttributes?.let { attributes.putAll(it) }

        return attributes
    }

    /**
     * Build resource attributes for ABAC evaluation.
     */
    private fun buildResourceAttributes(
        resourceType: String?,
        resourceId: UUID?,
        additionalAttributes: Map<String, Any>?,
    ): Map<String, Any>? {
        if (resourceType == null && resourceId == null && additionalAttributes == null) {
            return null
        }

        val attributes = mutableMapOf<String, Any>()
        resourceType?.let { attributes["type"] = it }
        resourceId?.let { attributes["id"] = it.toString() }

        additionalAttributes?.let { attributes.putAll(it) }

        return attributes
    }

    /**
     * Convert boolean allowed status to AuditAuthorizationResult.
     * Helper method to reduce code duplication.
     */
    private fun toAuditAuthorizationResult(allowed: Boolean): AuditAuthorizationResult =
        if (allowed) {
            AuditAuthorizationResult.ALLOWED
        } else {
            AuditAuthorizationResult.DENIED
        }

    /**
     * Convert ABAC PolicyEffect to AuditAuthorizationResult.
     * Helper method to reduce code duplication.
     */
    private fun toAuditAuthorizationResult(
        effect: io.github.salomax.neotool.security.domain.abac.PolicyEffect?,
    ): AuditAuthorizationResult =
        when (effect) {
            io.github.salomax.neotool.security.domain.abac.PolicyEffect.ALLOW -> AuditAuthorizationResult.ALLOWED
            io.github.salomax.neotool.security.domain.abac.PolicyEffect.DENY -> AuditAuthorizationResult.DENIED
            null -> AuditAuthorizationResult.NOT_EVALUATED
        }

    /**
     * Require a permission, throwing an exception if not allowed.
     * This is a convenience method that calls checkPermission and throws AuthorizationDeniedException
     * when access is denied. Used internally by AuthorizationManager.
     *
     * @param userId The user ID
     * @param permission The permission name (e.g., "security:user:view")
     * @param resourceType Optional resource type (e.g., "user")
     * @param resourceId Optional resource ID
     * @param subjectAttributes Optional additional subject attributes for ABAC
     * @param resourceAttributes Optional additional resource attributes for ABAC
     * @param contextAttributes Optional additional context attributes for ABAC
     * @throws AuthorizationDeniedException if permission is denied
     */
    fun requirePermission(
        userId: UUID,
        permission: String,
        resourceType: String? = null,
        resourceId: UUID? = null,
        subjectAttributes: Map<String, Any>? = null,
        resourceAttributes: Map<String, Any>? = null,
        contextAttributes: Map<String, Any>? = null,
    ) {
        val result =
            checkPermission(
                userId = userId,
                permission = permission,
                resourceType = resourceType,
                resourceId = resourceId,
                subjectAttributes = subjectAttributes,
                resourceAttributes = resourceAttributes,
                contextAttributes = contextAttributes,
            )

        if (!result.allowed) {
            throw AuthorizationDeniedException("User $userId lacks permission '$permission': ${result.reason}")
        }
    }
}

/**
 * Result of an authorization check.
 */
data class AuthorizationResult(
    val allowed: Boolean,
    val reason: String,
)
