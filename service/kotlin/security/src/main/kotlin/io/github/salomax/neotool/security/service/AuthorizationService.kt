package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.domain.rbac.ScopeType
import io.github.salomax.neotool.security.repo.GroupMembershipRepository
import io.github.salomax.neotool.security.repo.GroupRoleAssignmentRepository
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.RoleAssignmentRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import jakarta.inject.Singleton
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID
import io.github.salomax.neotool.security.domain.audit.AuthorizationResult as AuditAuthorizationResult

/**
 * Service for authorization checks.
 * Supports both RBAC-only and hybrid RBAC+ABAC authorization.
 * Handles direct role assignments, group inheritance, and scoped permissions.
 */
@Singleton
class AuthorizationService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val roleAssignmentRepository: RoleAssignmentRepository,
    private val groupMembershipRepository: GroupMembershipRepository,
    private val groupRoleAssignmentRepository: GroupRoleAssignmentRepository,
    private val abacEvaluationService: AbacEvaluationService,
    private val auditService: AuthorizationAuditService,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Check if a user has a specific permission.
     * Checks both direct role assignments and group-inherited roles.
     * Also evaluates ABAC policies if they exist.
     *
     * @param userId The user ID
     * @param permission The permission name (e.g., "transaction:update")
     * @param scopeType Optional scope type (PROFILE, PROJECT, RESOURCE)
     * @param scopeId Optional scope ID (required for PROJECT and RESOURCE scopes)
     * @return AuthorizationResult with decision and reason
     */
    fun checkPermission(
        userId: UUID,
        permission: String,
        scopeType: ScopeType? = null,
        scopeId: UUID? = null,
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
            scopeType,
            scopeId,
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
        scopeType: ScopeType? = null,
        scopeId: UUID? = null,
        now: Instant = Instant.now(),
    ): AuthorizationResult {
        // Collect role IDs once
        val roleIds = collectUserRoleIds(userId, scopeType, scopeId, now)

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
        roleIds: Set<Int>,
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
     * Get all permissions for a user, including direct and group-inherited permissions.
     * Optimized to use batch loading to avoid N+1 queries.
     *
     * @param userId The user ID
     * @param scopeType Optional scope type filter
     * @param scopeId Optional scope ID filter
     * @param now Current timestamp for validity checks
     * @return List of permissions
     */
    fun getUserPermissions(
        userId: UUID,
        scopeType: ScopeType? = null,
        scopeId: UUID? = null,
        now: Instant = Instant.now(),
    ): List<Permission> {
        // Collect all role IDs (direct and group-inherited) using shared logic
        val roleIds = collectUserRoleIds(userId, scopeType, scopeId, now)

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
     * Get all roles for a user, including direct and group-inherited roles.
     * Optimized to use batch loading and shared logic.
     *
     * @param userId The user ID
     * @param scopeType Optional scope type filter
     * @param scopeId Optional scope ID filter
     * @param now Current timestamp for validity checks
     * @return List of roles
     */
    fun getUserRoles(
        userId: UUID,
        scopeType: ScopeType? = null,
        scopeId: UUID? = null,
        now: Instant = Instant.now(),
    ): List<Role> {
        // Collect all role IDs using shared logic
        val roleIds = collectUserRoleIds(userId, scopeType, scopeId, now)

        if (roleIds.isEmpty()) {
            return emptyList()
        }

        // Batch load all role entities at once
        val roles = roleRepository.findByIdIn(roleIds.toList())
        return roles.mapNotNull { it.toDomain() }
    }

    /**
     * Collect all role IDs for a user (direct and group-inherited).
     * This shared method eliminates code duplication between getUserPermissions and getUserRoles.
     * Optimized to use batch loading for group role assignments to avoid N+1 queries.
     *
     * @param userId The user ID
     * @param scopeType Optional scope type filter
     * @param scopeId Optional scope ID filter
     * @param now Current timestamp for validity checks
     * @return Set of unique role IDs
     */
    private fun collectUserRoleIds(
        userId: UUID,
        scopeType: ScopeType?,
        scopeId: UUID?,
        now: Instant,
    ): Set<Int> {
        val roleIds = mutableSetOf<Int>()

        // Get direct role assignments
        val directAssignments = getDirectRoleAssignments(userId, scopeType, scopeId, now)
        roleIds.addAll(directAssignments.map { it.roleId })

        // Get group memberships
        val groupMemberships = groupMembershipRepository.findActiveMembershipsByUserId(userId, now)

        // Batch load group role assignments to avoid N+1 queries
        if (groupMemberships.isNotEmpty()) {
            val groupIds = groupMemberships.map { it.groupId }
            val groupAssignments =
                if (scopeType != null) {
                    groupRoleAssignmentRepository.findValidAssignmentsByGroupIdsAndScope(
                        groupIds,
                        scopeType,
                        scopeId,
                        now,
                    )
                } else {
                    groupRoleAssignmentRepository.findValidAssignmentsByGroupIds(groupIds, now)
                }
            roleIds.addAll(groupAssignments.map { it.roleId })
        }

        return roleIds
    }

    /**
     * Get direct role assignments for a user with optional scope filtering.
     * Extracted to reduce duplication.
     */
    private fun getDirectRoleAssignments(
        userId: UUID,
        scopeType: ScopeType?,
        scopeId: UUID?,
        now: Instant,
    ) = if (scopeType != null) {
        roleAssignmentRepository.findValidAssignmentsByUserIdAndScope(userId, scopeType, scopeId, now)
    } else {
        roleAssignmentRepository.findValidAssignmentsByUserId(userId, now)
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
     * @param scopeType Optional scope type (PROFILE, PROJECT, RESOURCE)
     * @param scopeId Optional scope ID
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
        scopeType: ScopeType? = null,
        scopeId: UUID? = null,
        subjectAttributes: Map<String, Any>? = null,
        resourceAttributes: Map<String, Any>? = null,
        contextAttributes: Map<String, Any>? = null,
    ): AuthorizationResult {
        val now = Instant.now()

        // Step 1: Fetch user context once (role IDs, roles, groups)
        // This eliminates duplicate data fetching between RBAC check and ABAC evaluation
        val userContext = fetchUserContext(userId, scopeType, scopeId, now)

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
        val roleIds: Set<Int>,
        val roles: List<Role>,
        val groups: List<UUID>,
    )

    /**
     * Fetch user context (role IDs, roles, and groups) in a single optimized call.
     * This method collects all necessary data once to avoid duplicate queries.
     *
     * @param userId The user ID
     * @param scopeType Optional scope type filter
     * @param scopeId Optional scope ID filter
     * @param now Current timestamp for validity checks
     * @return UserContext with role IDs, roles, and groups
     */
    private fun fetchUserContext(
        userId: UUID,
        scopeType: ScopeType?,
        scopeId: UUID?,
        now: Instant,
    ): UserContext {
        // Fetch role IDs once (this is the expensive part)
        val roleIds = collectUserRoleIds(userId, scopeType, scopeId, now)

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
    private fun toAuditAuthorizationResult(allowed: Boolean): AuditAuthorizationResult {
        return if (allowed) {
            AuditAuthorizationResult.ALLOWED
        } else {
            AuditAuthorizationResult.DENIED
        }
    }

    /**
     * Convert ABAC PolicyEffect to AuditAuthorizationResult.
     * Helper method to reduce code duplication.
     */
    private fun toAuditAuthorizationResult(
        effect: io.github.salomax.neotool.security.domain.abac.PolicyEffect?,
    ): AuditAuthorizationResult {
        return when (effect) {
            io.github.salomax.neotool.security.domain.abac.PolicyEffect.ALLOW -> AuditAuthorizationResult.ALLOWED
            io.github.salomax.neotool.security.domain.abac.PolicyEffect.DENY -> AuditAuthorizationResult.DENIED
            null -> AuditAuthorizationResult.NOT_EVALUATED
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
